package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FieldValue
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.StoryDoc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stories — single source of truth for the parent app.
 *
 * Reads the Firestore collection `stories` (the SAME collection
 * teacher app writes to and admin panel moderates). NO RTDB.
 *
 * Real-time snapshot listener so a teacher uploading from another
 * device, or an admin flagging a story, propagates to every parent's
 * Dashboard within ~100ms.
 *
 * Per-user view-tracking lives in subcollection `stories/{id}/viewers/{userId}`
 * — see [markAsViewed] for the contract. The parent VM uses the
 * presence of the parent's id in that subcollection to drive the
 * "unviewed ring" affordance on StoriesRow.
 */
@Singleton
class StoryFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    companion object {
        const val COLLECTION = "stories"
        const val VIEWERS_SUBCOLLECTION = "viewers"
        const val REACTIONS_SUBCOLLECTION = "reactions"
    }

    /**
     * Real-time stream of all ACTIVE + NON-EXPIRED stories for this
     * parent's school, newest expiry first.
     *
     * Filtering rules (mirrored on every client):
     *   - schoolId == this parent's school
     *   - expiresAt > now (server-side range query)
     *   - status == 'active' (client-side filter — covers admin
     *     moderation actions that propagate via the same listener)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveStories(): Flow<List<StoryDoc>> {
        return tokenManager.user
            // Only the SCHOOL drives the outer listener now. Audience is no
            // longer derived from the token's active-child class/section
            // (which could drift from the server entitlement) — it comes from
            // the server-maintained storyAudience doc below. Prefer the SCH_
            // schoolId claim; fall back to schoolCode.
            .map { user -> user.schoolId.ifBlank { user.schoolCode } }
            // distinctUntilChanged keeps the live listener alive across no-op
            // profile self-heals (dashboard refresh rewrites dob/name/etc.),
            // which otherwise tore down + recreated the listener (blank flash).
            .distinctUntilChanged()
            .flatMapLatest { storedSchool ->
                flow {
                    // The rule authorises a read only when story.schoolId ==
                    // the caller's `school_id` claim; resolve that exact value
                    // (fall back to the stored school only when offline).
                    val claimSchool = runCatching {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            ?.getIdToken(false)?.await()?.claims?.get("school_id")?.toString()
                    }.getOrNull()?.takeIf { it.isNotBlank() }
                    val schoolForQuery = claimSchool ?: storedSchool
                    if (schoolForQuery.isBlank()) {
                        emit(emptyList())
                    } else {
                        // SEC-1 (watertight): the audience filter is driven by
                        // this parent's SERVER-maintained entitlement doc
                        // (storyAudience/{uid}.classKeys) — the EXACT same set
                        // the read rule consults via get(). Because the query
                        // can only request classes the rule will allow,
                        // Firestore never denies a returned doc (one deny would
                        // fail the whole query). Whole-school '*' is always
                        // included, so a missing entitlement doc (refresh
                        // callable not run yet) still yields school-wide
                        // stories — fail-safe, never a cross-class leak. The
                        // inner flatMapLatest re-queries automatically when the
                        // callable creates/updates the entitlement doc.
                        emitAll(
                            observeMyAudienceKeys().flatMapLatest { classKeys ->
                                val audienceFilter = (classKeys +
                                    com.schoolsync.parent.data.model.firestore
                                        .StorySharedConfig.AUDIENCE_ALL).distinct()
                                firestoreService.observeQuery(COLLECTION) { ref ->
                                    ref.whereEqualTo("schoolId", schoolForQuery)
                                        .whereArrayContainsAny("audienceClassKeys", audienceFilter)
                                }.map { snap ->
                                    val nowMs = System.currentTimeMillis()
                                    snap.documents
                                        // Per-doc runCatching: one malformed
                                        // doc must not blank the whole row (M1).
                                        .mapNotNull { doc ->
                                            runCatching { doc.toObject(StoryDoc::class.java) }.getOrNull()
                                        }
                                        .filter { it.status == "active" && it.expiresAtMillis > nowMs }
                                        .sortedByDescending { it.expiresAtMillis }
                                }.onStart { emit(emptyList()) }
                                 .catch { emit(emptyList()) }
                            }
                        )
                    }
                }
            }
    }

    /**
     * LIVE stream of this parent's server-side story audience entitlement —
     * the canonical class-section keys from `storyAudience/{uid}` (maintained
     * by the refreshStoryAudience / onStudentClassChanged Cloud Functions).
     * Emits empty when the doc is missing (callable not run yet) or unreadable,
     * so the caller falls back to whole-school-only. A listener (not a one-shot
     * read) so a freshly-built entitlement propagates to the stories query
     * within ~100ms without a manual refresh.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeMyAudienceKeys(): Flow<List<String>> = callbackFlow {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("storyAudience").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val keys = (snap.get("classKeys") as? List<String>).orEmpty()
                trySend(keys)
            }
        awaitClose { reg.remove() }
    }

    /**
     * LIVE set of story ids this user has already viewed — a real-time
     * snapshot listener over the `viewers` collection-group filtered by
     * userId. Unlike a one-shot hydrate, this keeps the ring's seen/unseen
     * state correct across SEPARATE ViewModel instances: when the full-screen
     * viewer writes a viewer doc, the Dashboard ring's VM (also observing
     * this) sees it within ~100ms and greys the ring immediately.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSeenStoryIds(): Flow<Set<String>> =
        tokenManager.user
            .map { it.userId to it.schoolId.ifBlank { it.schoolCode } }
            .distinctUntilChanged()
            .flatMapLatest { (userId, school) ->
                // SEC-3: the viewers collection-group READ rule is now tenant-
                // bound (viewer.schoolId == caller's school_id claim), so the
                // query MUST carry the same schoolId filter or it is rejected
                // wholesale. Composite CG index schoolId+userId backs this.
                if (userId.isBlank() || school.isBlank()) flowOf(emptySet())
                else callbackFlow {
                    val reg = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collectionGroup(VIEWERS_SUBCOLLECTION)
                        .whereEqualTo("schoolId", school)
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener { snap, err ->
                            if (err != null || snap == null) { trySend(emptySet()); return@addSnapshotListener }
                            trySend(snap.documents.mapNotNull { it.reference.parent.parent?.id }.toSet())
                        }
                    awaitClose { reg.remove() }
                }
            }

    /**
     * Mark a story as viewed by the current parent — guaranteed
     * one-user-one-view via Firestore transaction:
     *
     *   1. tx.get(viewers/{userId})
     *   2. if EXISTS  → no-op (already counted; optionally backfill a blank
     *                  userName on a legacy doc)
     *   3. if MISSING → tx.set(viewers/{userId})
     *
     * SEC-4: this writes ONLY the per-user viewer marker. The story's
     * viewCount aggregate is incremented SERVER-SIDE by the Cloud Function
     * `onStoryViewerCreated`, which fires exactly once per unique viewer doc
     * create — so the client never writes the (forgeable) counter, and a
     * re-view (which is a get-hit, not a create) never inflates it. The
     * transaction still guarantees one-viewer-doc-per-user under a two-device
     * race, which in turn guarantees exactly one server increment.
     */
    suspend fun markAsViewed(storyId: String): Result<Unit> {
        val user = tokenManager.user.firstOrNull()
        val userId = user?.userId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        // Denormalise the viewer's name so the teacher/admin "who saw this"
        // list can show a real name without a per-viewer parent-doc lookup.
        val userName = user.name.orEmpty()
        // C2: stamp the caller's own schoolId so the engagement rule can
        // tenant-bind the write (must equal the token's school_id claim).
        val schoolId = resolveWriteSchoolId(user?.schoolId, user?.schoolCode)
        if (schoolId.isBlank()) return Result.failure(Exception("School not available"))
        return try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val storyRef  = firestore.collection(COLLECTION).document(storyId)
            val viewerRef = storyRef.collection(VIEWERS_SUBCOLLECTION).document(userId)

            firestore.runTransaction { tx ->
                val existing = tx.get(viewerRef)
                if (existing.exists()) {
                    // Already counted — don't inflate viewCount. But
                    // backfill the viewer's name if an earlier (pre-
                    // denormalisation) view left it blank, so the
                    // teacher's "who saw this" list can show a real name.
                    // Also stamp schoolId (legacy docs predate it) so the
                    // update satisfies the tenant-bound engagement rule.
                    if (existing.getString("userName").isNullOrBlank() && userName.isNotBlank()) {
                        tx.update(viewerRef, mapOf("userName" to userName, "schoolId" to schoolId))
                    }
                    return@runTransaction null
                }
                // First view — write ONLY the viewer marker. The CF
                // onStoryViewerCreated increments viewCount server-side.
                tx.set(viewerRef, hashMapOf<String, Any?>(
                    "viewedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "userId"   to userId,
                    "userName" to userName,
                    "schoolId" to schoolId
                ))
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * SEC-1: (re)build this parent's SERVER-SIDE audience entitlement doc
     * `storyAudience/{uid}` by invoking the `refreshStoryAudience` callable
     * Cloud Function (runs under the parent's own auth context — uid +
     * student_ids claim — so it can only build the caller's own entitlement).
     *
     * Call this on login / app-start BEFORE relying on class-targeted stories:
     * until the doc exists, the stories read rule grants only whole-school
     * stories (fail-safe, never a leak). Fire-and-forget — a failure just
     * means the parent temporarily sees only school-wide stories, so callers
     * should NOT block the UI on it.
     */
    suspend fun refreshAudienceIndex(): Result<Unit> = try {
        com.google.firebase.functions.FirebaseFunctions
            .getInstance("us-central1")
            .getHttpsCallable("refreshStoryAudience")
            .call()
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * The schoolId to stamp on engagement writes. The engagement rule checks
     * `request.resource.data.schoolId == request.auth.token.school_id`, so we
     * write the LIVE claim value — the exact thing the rule compares against —
     * falling back to the stored school only if the token can't be read.
     */
    private suspend fun resolveWriteSchoolId(storedSchoolId: String?, storedSchoolCode: String?): String {
        val claim = runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                ?.getIdToken(false)?.await()?.claims?.get("school_id")?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return claim ?: storedSchoolId?.takeIf { it.isNotBlank() } ?: storedSchoolCode.orEmpty()
    }

    /**
     * Set / change / clear the current parent's emoji reaction on a story.
     * One reaction per parent (toggle): tapping the SAME emoji again clears
     * it. A transaction on the per-user `reactions/{userId}` doc guarantees
     * a single consistent marker under concurrent taps:
     *
     *   - no existing reaction → set doc
     *   - existing == emoji     → delete doc (toggle off)
     *   - existing != emoji     → set doc (change)
     *
     * SEC-4: the denormalised `reactionCounts` map on the story doc is
     * derived SERVER-SIDE by the CF onStoryReactionWritten from the
     * before/after emoji of THIS marker — the client never writes the
     * (forgeable) aggregate. reactionCounts is what teacher/admin read.
     */
    suspend fun reactToStory(storyId: String, emoji: String): Result<Unit> {
        val user = tokenManager.user.firstOrNull()
        val userId = user?.userId?.takeIf { it.isNotBlank() }
            ?: return Result.failure(Exception("User ID not available"))
        val userName = user.name.orEmpty()
        // C2: tenant-bind the reaction write to the caller's own school.
        val schoolId = resolveWriteSchoolId(user?.schoolId, user?.schoolCode)
        if (schoolId.isBlank()) return Result.failure(Exception("School not available"))
        return try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val storyRef = firestore.collection(COLLECTION).document(storyId)
            val reactionRef = storyRef.collection(REACTIONS_SUBCOLLECTION).document(userId)

            firestore.runTransaction { tx ->
                val existing = tx.get(reactionRef)
                val prevEmoji = if (existing.exists()) existing.getString("emoji").orEmpty() else ""

                // SEC-4: write ONLY the per-user reaction marker. The story's
                // reactionCounts map is adjusted SERVER-SIDE by the CF
                // onStoryReactionWritten, which derives the toggle-safe delta
                // from the before/after emoji — so the client never writes the
                // (forgeable) aggregate.
                when {
                    prevEmoji.isBlank() -> {
                        tx.set(reactionRef, hashMapOf<String, Any?>(
                            "emoji"     to emoji,
                            "userId"    to userId,
                            "userName"  to userName,
                            "schoolId"  to schoolId,
                            "reactedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                    prevEmoji == emoji -> {
                        // Toggle off.
                        tx.delete(reactionRef)
                    }
                    else -> {
                        // Change reaction.
                        tx.set(reactionRef, hashMapOf<String, Any?>(
                            "emoji"     to emoji,
                            "userId"    to userId,
                            "userName"  to userName,
                            "schoolId"  to schoolId,
                            "reactedAt" to FieldValue.serverTimestamp()
                        ))
                    }
                }
                null
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
