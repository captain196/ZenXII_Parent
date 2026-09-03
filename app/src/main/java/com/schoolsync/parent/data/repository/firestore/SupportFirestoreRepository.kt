package com.schoolsync.parent.data.repository.firestore

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.SupportMessageDoc
import com.schoolsync.parent.data.model.firestore.SupportTicketDoc
import com.schoolsync.parent.util.Constants
import com.schoolsync.parent.util.ImageCompressor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Support Desk — the Parent app's side of parent→school ticketing.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *  THE RULES ARE NARROW. THIS CLASS EXISTS TO MATCH THEM EXACTLY.
 * ─────────────────────────────────────────────────────────────────────────────
 * A parent may:
 *   • create a ticket — with a fixed field set, server timestamps, status
 *     "open", lane "normal", no assignedTo, no ticketNo, ≤3 attachments, and
 *     only for a student in their own claim
 *   • read their OWN tickets and their messages
 *   • create a message on their own thread
 *
 * A parent may NOT update or delete anything. `allow update: if false`.
 *
 * That last point is the important one. Reopening a resolved ticket is done by
 * REPLYING to it — a Cloud Function performs the status transition. The earlier
 * design let the client patch `status` directly, and because a rules allowlist
 * constrains which keys change rather than what values they take, a parent
 * could have written "closed" and buried their own ticket, or junk and dropped
 * it out of every staff filter. Do not add an update path here.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 *  SESSION SCOPING — THE DOCUMENTED EXCEPTION
 * ─────────────────────────────────────────────────────────────────────────────
 * Every other query in this app filters by school AND academic session. Support
 * filters by school only. A fee dispute raised in March must stay readable in
 * April after the session rolls. sessionId is WRITTEN for reporting and never
 * queried. A future sweep that "fixes" this will orphan every ticket at
 * year end.
 */
@Singleton
class SupportFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    // Not injected: this app does not provide FirebaseStorage through Hilt.
    // MessageRepository resolves it the same way.
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    companion object {
        /** Rules reject a create carrying more than this. */
        const val MAX_ATTACHMENTS = 3

        /**
         * Matches the rules-layer cap. Enforced server-side against a counter,
         * so this is only here to give a decent message instead of a denial.
         */
        const val MAX_OPEN_TICKETS = 5

        /** Fixed catalogue. Hostel is absent on purpose — it has its own module. */
        val CATEGORIES = listOf(
            "fees", "transport", "academics", "attendance", "exams",
            "certificates", "health", "app", "conduct", "other"
        )

        /** Statuses that count as still-open for the pre-check. */
        val ACTIVE_STATUSES = setOf("open", "assigned", "reopened")

        private const val ID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"  // Crockford: no I, L, O, U
    }

    // ── identity ─────────────────────────────────────────────────────────────

    private suspend fun schoolId(): String? =
        tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }

    private suspend fun userId(): String? =
        tokenManager.user.firstOrNull()?.userId?.takeIf { it.isNotBlank() }

    private suspend fun userName(): String =
        tokenManager.user.firstOrNull()?.name ?: ""

    private suspend fun session(): String =
        tokenManager.user.firstOrNull()?.session ?: ""

    private fun docId(schoolId: String, ticketId: String) = "${schoolId}_$ticketId"

    /**
     * A fresh ticket id.
     *
     * Generated ONCE when the compose screen opens, not at submit — that is
     * what makes the create idempotent (E-02). A double-tap, or a retry after a
     * dropped connection, writes the same document key twice and the second
     * write overwrites the first instead of producing a duplicate ticket.
     *
     * Crockford alphabet so a number read aloud over the phone is unambiguous.
     */
    fun newTicketId(): String {
        val rnd = SecureRandom()
        val sb = StringBuilder("TKT_")
        repeat(16) { sb.append(ID_ALPHABET[rnd.nextInt(ID_ALPHABET.length)]) }
        return sb.toString()
    }

    /** Unique message document id. Random, never a sequence. */
    /**
     * An IDEMPOTENT message id, derived from what the message IS.
     *
     * This replaces a random-per-call id whose rationale reasoned only about
     * COLLISION — "two people replying in the same second must not land on the
     * same key". That was true and remains true: a hash over the sender and body
     * separates two different people just as well. What it missed is RETRY: the
     * same person sending the same body twice, believing the first attempt
     * failed. (The random generator is now removed rather than left unused, so
     * nothing invites re-wiring it.)
     *
     * Confirmed on device 2026-09-02 (SD-T2-010): the identical body sent twice
     * stored TWO documents, MSG_PAP2RAZ8249VBRQ8XB1A and MSG_1P0E619ZKPCKCXCXEVSD.
     * The panel was hardened for exactly this in B4 and refuses a duplicate staff
     * reply; the parent surface — where a retry is far MORE likely, since R1's
     * history is that an empty-looking thread invites re-sending — was not.
     *
     * Deriving the id from (ticket, sender, body, 5-minute bucket) makes the
     * duplicate unexpressible rather than detected: the retry addresses the same
     * document. Because supportMessages is create-only in the rules, that second
     * write is refused server-side and the thread keeps exactly one copy — the
     * end state a retrying user actually wants.
     *
     * The bucket is deliberately coarse: a parent who genuinely sends "ok" twice
     * an hour apart gets two messages, which is correct. Only a rapid re-send of
     * identical text collapses.
     *
     * NO read is performed to check for an existing document. A blocking read
     * would break R1 — the offline fix depends on the write being ENQUEUED, and
     * a read cannot be served offline.
     */
    private fun idempotentMessageId(ticketId: String, uid: String, body: String): String {
        val bucket = System.currentTimeMillis() / 300_000L   // 5-minute window
        val seed = listOf(ticketId, uid, "parent", body, bucket.toString()).joinToString("|")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder("MSG_")
        for (i in 0 until 10) hex.append(String.format(java.util.Locale.ROOT, "%02x", digest[i]))  // i18n-ignore: hex format
        return hex.toString()
    }

    // ── reads ────────────────────────────────────────────────────────────────

    /**
     * This parent's tickets, newest first.
     *
     * Index 2: [schoolId, reporterId, createdAt DESC]. Note the absence of a
     * session filter — see the class header.
     */
    fun observeMyTickets(): Flow<List<SupportTicketDoc>> = flow {
        val school = schoolId()
        val uid = userId()
        if (school == null || uid == null) {
            emit(emptyList()); return@flow
        }
        emitAll(
            firestoreService.observeQuery(Constants.Firestore.SUPPORT_TICKETS) { ref ->
                ref.whereEqualTo("schoolId", school)
                    .whereEqualTo("reporterId", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(100)
            }.map { snap -> snap.toObjects(SupportTicketDoc::class.java) }
        )
    }

    /** One ticket, live. Null when it does not exist or is not this parent's. */
    fun observeTicket(ticketId: String): Flow<SupportTicketDoc?> = flow {
        val school = schoolId() ?: run { emit(null); return@flow }
        emitAll(
            firestoreService.observeDocument(Constants.Firestore.SUPPORT_TICKETS, docId(school, ticketId))
                .map { snap -> snap?.toObject(SupportTicketDoc::class.java) }
        )
    }

    /**
     * The thread, oldest first.
     *
     * Index 4: [schoolId, ticketId, reporterId, createdAt ASC].
     *
     * reporterId is NOT redundant with ticketId. Firestore evaluates a listen
     * STATICALLY: the supportMessages read rule requires
     * `resource.data.reporterId == request.auth.uid`, so the query must filter
     * on reporterId too or Firestore cannot prove every match passes and
     * rejects the whole listen with PERMISSION_DENIED. Found on device UAT
     * 2026-08-28: the ticket created fine and the thread stayed empty.
     */
    fun observeMessages(ticketId: String): Flow<List<SupportMessageDoc>> = flow {
        val school = schoolId() ?: run { emit(emptyList()); return@flow }
        val uid = userId() ?: run { emit(emptyList()); return@flow }
        emitAll(
            firestoreService.observeQuery(Constants.Firestore.SUPPORT_MESSAGES) { ref ->
                ref.whereEqualTo("schoolId", school)
                    .whereEqualTo("ticketId", ticketId)
                    .whereEqualTo("reporterId", uid)
                    .orderBy("createdAt", Query.Direction.ASCENDING)
                    .limit(200)
            }.map { snap -> snap.toObjects(SupportMessageDoc::class.java) }
        )
    }

    /** How many of this parent's tickets are still open. Drives the pre-check. */
    suspend fun openTicketCount(): Int {
        val school = schoolId() ?: return 0
        val uid = userId() ?: return 0
        // Counted client-side from the parent's own recent tickets rather than
        // with whereIn("status", ...), which would need a composite index
        // [schoolId, reporterId, status] that nobody planned and that is not
        // deployed. This reuses index 2, already live.
        return try {
            firestoreService.queryDocuments(Constants.Firestore.SUPPORT_TICKETS) { ref ->
                ref.whereEqualTo("schoolId", school)
                    .whereEqualTo("reporterId", uid)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(40)
            }.toObjects(SupportTicketDoc::class.java)
                .count { it.status in ACTIVE_STATUSES }
        } catch (e: Exception) {
            0   // Never block composing because a count failed. The server caps anyway.
        }
    }

    // ── attachments ──────────────────────────────────────────────────────────

    /**
     * Compress and upload one photo. Returns the FILENAME to store.
     *
     * Upload happens BEFORE the ticket document is written (E-04). If it fails,
     * no ticket exists and the parent retries a compose screen with their text
     * intact. The reverse order strands a ticket pointing at a file that was
     * never uploaded — visible to staff as an attachment that will not open.
     *
     * The returned value is a bare filename, never a path or download URL. The
     * panel derives `schools/{schoolId}/support/{ticketId}/{name}` and refuses
     * to sign anything it did not derive, so a cross-tenant path cannot be
     * expressed here at all.
     */
    suspend fun uploadAttachment(
        context: Context,
        ticketId: String,
        uri: Uri,
        index: Int
    ): Result<String> {
        val school = schoolId() ?: return Result.failure(IllegalStateException("Not signed in."))
        val uid = userId() ?: return Result.failure(IllegalStateException("Not signed in."))

        val compressed = ImageCompressor.compress(context, uri).getOrElse { e ->
            return Result.failure(e)
        }

        val fileName = "$index.jpg"
        return try {
            // The reporter id is a PATH SEGMENT, not decoration. Attachments are
            // uploaded BEFORE the ticket document exists, so a storage rule has
            // no ticket to read ownership from — carrying the owner in the path
            // is the only way "you may write only your own files" can be stated.
            // Storage: schools/{schoolId}/support/{reporterId}/{ticketId}/{n}.jpg
            // The panel rebuilds this same path from the ticket it authorised.
            val ref = storage.reference.child("schools/$school/support/$uid/$ticketId/$fileName")
            // Content type is set explicitly rather than inferred: the panel
            // validates it server-side, and an unset type reads as
            // application/octet-stream and is refused.
            val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
            ref.putBytes(compressed.bytes, meta).await()
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve one of THIS ticket's attachment filenames to a download URL.
     *
     * The path is rebuilt here from the signed-in identity — school + uid — and
     * never from anything the ticket document carries, so a tampered
     * `attachments` entry cannot point the reader at another parent's object.
     * `fileName` is the bare name the upload returned; the Storage rule then
     * checks `uid == reporterId` on the path segment, which is what actually
     * enforces ownership.
     *
     * Returns a failure rather than throwing: one unreadable attachment must not
     * take down a thread the parent needs to read.
     */
    suspend fun attachmentUrl(ticketId: String, fileName: String): Result<String> {
        val school = schoolId() ?: return Result.failure(IllegalStateException("Not signed in."))
        val uid = userId() ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            val ref = storage.reference.child("schools/$school/support/$uid/$ticketId/$fileName")
            Result.success(ref.downloadUrl.await().toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── writes ───────────────────────────────────────────────────────────────

    /**
     * Raise a ticket.
     *
     * The field set is exactly what the rules permit — no more. In particular
     * there is no `assignedTo`, no `ticketNo` and no `messageCount`: the rules
     * reject a create carrying the first two, and a Cloud Function owns all
     * three afterwards.
     *
     * Timestamps use serverTimestamp() because the rules require
     * `createdAt == request.time`. A client clock is not acceptable here: an
     * earlier design let the client choose lastMessageAt, and since that field
     * is the queue's sort key, a parent could have pinned themselves to the top
     * of every triager's queue permanently by writing a date in 2099.
     */
    suspend fun createTicket(
        ticketId: String,
        category: String,
        subject: String,
        body: String,
        studentId: String,
        studentName: String,
        className: String,
        attachments: List<String>,
        isAnonymous: Boolean = false
    ): Result<String> {
        val school = schoolId() ?: return Result.failure(IllegalStateException("Not signed in."))
        val uid = userId() ?: return Result.failure(IllegalStateException("Not signed in."))
        if (attachments.size > MAX_ATTACHMENTS) {
            return Result.failure(IllegalArgumentException("At most $MAX_ATTACHMENTS photos."))
        }

        val now = FieldValue.serverTimestamp()
        val ticket = mapOf(
            "schoolId" to school,
            "ticketId" to ticketId,
            "sessionId" to session(),          // recorded, never filtered on
            "lane" to "normal",                // final shape; only value v1 writes
            "category" to category,
            "subject" to subject,
            "studentId" to studentId,
            "studentName" to studentName,
            "className" to className,
            "reporterId" to uid,
            "reporterName" to userName(),
            "isAnonymous" to isAnonymous,
            "status" to "open",
            "attachments" to attachments,      // FILENAMES only
            "createdAt" to now,
            "lastMessageAt" to now,
            "lastParentReplyAt" to now
        )

        // Resolved BEFORE either enqueue. userName() suspends, and a suspension
        // between the two enqueues is precisely what the caller's timeout can
        // cancel — which would reintroduce the bug this fix exists to remove,
        // just in a narrower window.
        val senderName = userName()

        return try {
            // BOTH writes are ENQUEUED before EITHER is awaited. This ordering is
            // the whole fix, not a style choice.
            //
            // ref.set() queues the mutation locally and synchronously; the Task
            // it returns reports only the server ack, which never arrives while
            // offline. The previous shape awaited the ticket write and then
            // wrote the message on the next line — so offline, the caller's 6s
            // timeout cancelled the coroutine before that line was ever reached.
            // The ticket synced later carrying none of what the parent typed,
            // the draft was cleared, and the UI reported success. The parent
            // believed they had been heard; the desk saw an empty ticket flagged
            // "awaiting us".
            //
            // set() with a known key, not add(): the id was minted when the
            // screen opened, so a retry overwrites rather than duplicating.
            //
            // ORDER MATTERS. The supportMessages create rule requires the ticket
            // to exist (exists() + get() on it), and Firestore replays queued
            // mutations in enqueue order. The ticket must therefore be queued
            // first — which is why this is two explicit calls rather than two
            // coroutines that could race.
            val ticketAck = firestoreService.enqueueDocument(
                Constants.Firestore.SUPPORT_TICKETS,
                docId(school, ticketId),
                ticket
            )
            // The opening message carries the body. The ticket holds the
            // subject; the thread holds what was actually said.
            val messageAck = enqueueMessage(school, uid, senderName, ticketId, body)

            // Both are durable from here. Awaiting only reports the server ack;
            // a caller timing this out still loses nothing.
            ticketAck.await()
            messageAck.await()
            Result.success(ticketId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reply on a thread.
     *
     * This is ALSO how a resolved ticket is reopened — a Cloud Function makes
     * the status transition when it sees a parent message on a resolved ticket
     * inside the reopen window. The client never touches status.
     *
     * The document id is a Firestore auto-id. A client cannot safely allocate a
     * sequence number, and two people replying in the same second would collide
     * on a computed key and silently overwrite one another.
     */
    suspend fun appendMessage(ticketId: String, body: String): Result<Unit> {
        val school = schoolId() ?: return Result.failure(IllegalStateException("Not signed in."))
        val uid = userId() ?: return Result.failure(IllegalStateException("Not signed in."))

        val msg = mapOf(
            "schoolId" to school,
            "ticketId" to ticketId,
            // Denormalised so the read rule needs no get() — 50 fewer reads on a
            // 50-message thread, and zero exposure to the missing-parent rules
            // failure that broke Stories for a month.
            "reporterId" to uid,
            "senderType" to "parent",
            "senderId" to uid,
            "senderName" to userName(),
            "body" to body,
            "attachments" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            // FirestoreService exposes no add() helper, so the id is minted here.
            // Any unique value works — the rules place no constraint on a message
            // document id, only on its CONTENTS. What matters is that it is not
            // a computed sequence: two people replying in the same second must
            // not land on the same key.
            firestoreService.enqueueDocument(
                Constants.Firestore.SUPPORT_MESSAGES,
                idempotentMessageId(ticketId, uid, body),
                msg
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queue a parent message and hand back its ack Task WITHOUT awaiting.
     *
     * Exists so createTicket can get the opening message into the local write
     * queue before it blocks on anything. Same document shape as
     * [appendMessage], and the same idempotent id — see [idempotentMessageId].
     *
     * The OPENING message is the one SD-T2-010 is named for, and the one where a
     * retry is most likely: R1's failure mode left tickets with no message at
     * all, and the natural response to a thread that looks empty is to send it
     * again. A random id per call made that produce a second copy.
     */
    private fun enqueueMessage(
        school: String,
        uid: String,
        senderName: String,
        ticketId: String,
        body: String
    ): Task<Void> {
        val msg = mapOf(
            "schoolId" to school,
            "ticketId" to ticketId,
            "reporterId" to uid,
            "senderType" to "parent",
            "senderId" to uid,
            "senderName" to senderName,
            "body" to body,
            "attachments" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return firestoreService.enqueueDocument(
            Constants.Firestore.SUPPORT_MESSAGES,
            idempotentMessageId(ticketId, uid, body),
            msg
        )
    }

    /**
     * ENGLISH label for a category key — deliberately not localized.
     *
     * This value is written to Firestore as the ticket `subject` when the
     * parent leaves it blank, and school staff read that subject in the
     * admin panel. Translating it would put the parent's language into the
     * school's triage queue. For DISPLAY in the app use
     * SupportViewModel.categoryLabelLocalized().
     */
    fun categoryLabel(key: String): String = when (key) {
        "fees" -> "Fees & Payments"
        "transport" -> "Transport"
        "academics" -> "Academics & Homework"
        "attendance" -> "Attendance"
        "exams" -> "Exams & Results"
        "certificates" -> "Certificates & Documents"
        "health" -> "Health & Safety"
        "app" -> "App & Login"
        "conduct" -> "Staff Conduct"
        else -> "Other"
    }
}
