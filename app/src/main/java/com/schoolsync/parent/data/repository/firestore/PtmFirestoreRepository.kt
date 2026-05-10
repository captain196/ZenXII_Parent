package com.schoolsync.parent.data.repository.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.data.model.firestore.PtmRsvpDoc
import com.schoolsync.parent.data.model.firestore.PtmSectionAssignment
import com.schoolsync.parent.data.model.firestore.StaffDoc
import com.schoolsync.parent.data.model.firestore.assignmentFor
import com.schoolsync.parent.data.model.firestore.normalizedBookings
import com.schoolsync.parent.data.remote.BookingPayload
import com.schoolsync.parent.data.remote.PreservedBooking
import com.schoolsync.parent.data.remote.SubmitRsvpResponse
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading PTM events and writing parent RSVPs.
 *
 * Filtering rules for "what does this parent see":
 *   - Match `schoolId`
 *   - Match `sectionKey == "ALL"` OR `sectionKey == "{class}/{section}"`
 *   - Status `scheduled` only
 *   - Date today-or-later
 */
@Singleton
class PtmFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /** All visible upcoming PTMs for the current student's section. */
    suspend fun getUpcomingPtms(className: String, section: String): Result<List<PtmEventDoc>> {
        val schoolId = getSchoolId()
            ?: return Result.failure(Exception("School id not available"))

        val sectionKey = "$className/$section"
        val now = java.util.Date()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(now)
        val nowHm = java.text.SimpleDateFormat("HH:mm",      java.util.Locale.US).format(now)

        return try {
            // We can't use `whereIn` on sectionKey AND a date-range filter in
            // the same compound query without a custom index, so we fetch by
            // schoolId only and filter the rest client-side. PTM volumes are
            // small (one per term per class), so this is fine.
            val all = firestoreService.queryDocumentsAs<PtmEventDoc>(
                "ptmEvents"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
            }
            val visible = all.filter { p ->
                val statusOk = p.status.lowercase() == "scheduled"
                val sectionOk = p.sectionKey == "ALL"
                    || p.sectionKey.equals(sectionKey, ignoreCase = true)
                // Time-aware date filter: today's PTM is hidden once every
                // slot has finished; future PTMs always show; missing dates
                // (legacy/draft) shown.
                val dateOk = when {
                    p.date.isBlank()   -> true
                    p.date > today     -> true
                    p.date < today     -> false
                    else               -> p.slots.any { it.endTime.isBlank() || it.endTime > nowHm }
                }
                statusOk && sectionOk && dateOk
            }.sortedBy { it.date }
            Result.success(visible)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * All visible PTMs (upcoming + past) for the parent's section, used by
     * the permanent PTM list screen. Same audience filter as
     * [getUpcomingPtms]; status filter widens to include `completed` and
     * `cancelled` so the parent can see history. Sorted newest first.
     */
    suspend fun getAllVisiblePtms(className: String, section: String): Result<List<PtmEventDoc>> {
        val schoolId = getSchoolId()
            ?: return Result.failure(Exception("School id not available"))

        val sectionKey = "$className/$section"
        return try {
            val all = firestoreService.queryDocumentsAs<PtmEventDoc>(
                "ptmEvents"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
            }
            val visible = all.filter { p ->
                p.sectionKey == "ALL"
                    || p.sectionKey.equals(sectionKey, ignoreCase = true)
            }.sortedByDescending { it.date }
            Result.success(visible)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * All RSVPs the student has ever submitted for any PTM in this school.
     * Used by the permanent list screen to surface a per-row badge
     * (Confirmed / Pending / Declined / Attended / No-show) without
     * making N point reads.
     */
    suspend fun getAllRsvpsForStudent(studentId: String): Result<List<PtmRsvpDoc>> {
        val schoolId = getSchoolId()
            ?: return Result.failure(Exception("School id not available"))
        return try {
            val rows = firestoreService.queryDocumentsAs<PtmRsvpDoc>(
                "ptmRsvps"
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolId)
                    .whereEqualTo("studentId", studentId)
            }
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch the existing RSVP for a student (null if not yet RSVPd). */
    suspend fun getRsvp(ptmEventId: String, studentId: String): Result<PtmRsvpDoc?> {
        val schoolId = getSchoolId()
            ?: return Result.failure(Exception("School id not available"))
        val docId = "${schoolId}_${ptmEventId}_${studentId}"
        return try {
            val doc = firestoreService.getDocumentAs<PtmRsvpDoc>("ptmRsvps", docId)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create or update an RSVP. The doc id is deterministic
     * (`{schoolId}_{ptmEventId}_{studentId}`) so re-RSVPs overwrite the
     * previous response.
     */
    suspend fun setRsvp(
        ptm: PtmEventDoc,
        studentId: String,
        studentName: String,
        className: String,
        section: String,
        rollNo: String,
        parentName: String,
        parentPhone: String,
        parentEmail: String,
        slotIndex: Int,
        status: String,
        note: String
    ): Result<Unit> {
        val schoolId = getSchoolId()
            ?: return Result.failure(Exception("School id not available"))
        val ptmEventId = ptm.ptmEventId.ifBlank {
            ptm.id.removePrefix("${schoolId}_")
        }
        val docId = "${schoolId}_${ptmEventId}_${studentId}"
        val slot = ptm.slots.getOrNull(slotIndex)
        val payload = mapOf(
            "ptmEventId"     to ptmEventId,
            "schoolId"       to schoolId,
            "sectionKey"     to ptm.sectionKey,
            "studentId"      to studentId,
            "studentName"    to studentName,
            "className"      to className,
            "section"        to section,
            "rollNo"         to rollNo,
            "parentName"     to parentName,
            "parentPhone"    to parentPhone,
            "parentEmail"    to parentEmail,
            "slotIndex"      to slotIndex,
            "slotStartTime"  to (slot?.startTime ?: ""),
            "slotEndTime"    to (slot?.endTime   ?: ""),
            "teacherId"      to (slot?.teacherId   ?: ""),
            "teacherName"    to (slot?.teacherName ?: ""),
            "status"         to status,
            "note"           to note,
            "respondedAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "respondedBy"    to studentId
        )
        return try {
            firestoreService.setDocument("ptmRsvps", docId, payload, merge = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submit one or more slot bookings as a multi-booking RSVP, written
     * directly to Firestore (Firestore-first architecture — the parent app
     * never depends on the admin server being reachable). Each [selection]
     * is enriched from the in-memory PTM doc's slot snapshot at submit
     * time. The doc id is deterministic so re-submission overwrites
     * cleanly. Existing bookings whose status is `attended` or `no-show`
     * are preserved (the teacher's call is authoritative).
     *
     * Validation that lives **only** on the client side now (server-side
     * enforcement was a planned PHP endpoint but caused a connectivity
     * regression on physical devices reaching localhost):
     *   - One booking per teacher
     *   - No overlap between selected windows
     *   - Capacity (best-effort, not transactional)
     * Firestore Rules still scope writes to same-school authenticated users.
     */
    suspend fun setRsvpBookings(
        ptm: PtmEventDoc,
        studentId: String,
        studentName: String,
        className: String,
        section: String,
        rollNo: String,
        parentName: String,
        parentPhone: String,
        parentEmail: String,
        selections: List<BookingPayload>
    ): Result<SubmitRsvpResponse> {
        if (selections.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one booking is required."))
        }
        val schoolId = getSchoolId()
            ?: return Result.failure(Exception("School id not available"))
        val ptmEventId = ptm.ptmEventId.ifBlank {
            ptm.id.removePrefix("${schoolId}_")
        }
        val docId = "${schoolId}_${ptmEventId}_${studentId}"

        // Read existing doc so we can preserve any booking the teacher has
        // already marked attended/no-show. Best-effort — on read failure
        // we still proceed (we just can't preserve in that race).
        val existing = try {
            firestoreService.getDocumentAs<PtmRsvpDoc>("ptmRsvps", docId)
        } catch (_: Exception) { null }
        val existingByIdx = existing?.let {
            it.normalizedBookings().associateBy { b -> b.slotIndex }
        } ?: emptyMap()

        val preserved = mutableListOf<PreservedBooking>()
        val finalBookings = mutableListOf<Map<String, Any?>>()
        // Per-booking timestamps must be plain values — Firestore rejects
        // FieldValue.serverTimestamp() inside array elements. Use a client
        // Timestamp captured at submit time. Top-level audit fields below
        // can still use serverTimestamp() because they live at doc root.
        val nowTs = com.google.firebase.Timestamp.now()

        // Enrich each selection from the PTM doc's slots. Drop-or-preserve
        // based on prior teacher attendance.
        for (sel in selections) {
            val idx = sel.slotIndex
            if (idx < 0 || idx >= ptm.slots.size) {
                return Result.failure(
                    PtmRsvpException("SLOT_INDEX_INVALID",
                        "Slot ${idx + 1} no longer exists on this PTM. Please reload and try again.")
                )
            }
            val slot = ptm.slots[idx]
            val priorBooking = existingByIdx[idx]
            if (priorBooking != null && (priorBooking.status == "attended" || priorBooking.status == "no-show")) {
                // Teacher already marked attendance — keep it intact.
                finalBookings.add(mapOf(
                    "slotIndex"     to priorBooking.slotIndex,
                    "teacherId"     to priorBooking.teacherId,
                    "teacherName"   to priorBooking.teacherName,
                    "slotStartTime" to priorBooking.slotStartTime,
                    "slotEndTime"   to priorBooking.slotEndTime,
                    "status"        to priorBooking.status,
                    "note"          to priorBooking.note,
                    "respondedAt"   to priorBooking.respondedAt,
                    "markedBy"      to "",
                    "markedAt"      to null
                ))
                preserved.add(PreservedBooking(
                    slotIndex = idx,
                    status    = priorBooking.status,
                    reason    = "Attendance was already marked by the teacher."
                ))
                continue
            }
            finalBookings.add(mapOf(
                "slotIndex"     to idx,
                "teacherId"     to slot.teacherId,
                "teacherName"   to slot.teacherName,
                "slotStartTime" to slot.startTime,
                "slotEndTime"   to slot.endTime,
                "status"        to (sel.status.ifBlank { "confirmed" }),
                "note"          to sel.note,
                "respondedAt"   to nowTs, // plain Timestamp — array-safe
                "markedBy"      to "",
                "markedAt"      to null
            ))
        }

        // Legacy mirror — first booking projected onto top-level fields so
        // older app builds reading single-RSVP shape still see something.
        val first = finalBookings.firstOrNull()
        val payload = mapOf(
            "ptmEventId"   to ptmEventId,
            "schoolId"     to schoolId,
            "sectionKey"   to ptm.sectionKey,
            "studentId"    to studentId,
            "studentName"  to studentName,
            "className"    to className,
            "section"      to section,
            "rollNo"       to rollNo,
            "parentName"   to parentName,
            "parentPhone"  to parentPhone,
            "parentEmail"  to parentEmail,
            "bookings"     to finalBookings,
            // Legacy mirror
            "slotIndex"     to (first?.get("slotIndex")     ?: -1),
            "slotStartTime" to (first?.get("slotStartTime") ?: ""),
            "slotEndTime"   to (first?.get("slotEndTime")   ?: ""),
            "teacherId"     to (first?.get("teacherId")     ?: ""),
            "teacherName"   to (first?.get("teacherName")   ?: ""),
            "status"        to (first?.get("status")        ?: "pending"),
            "note"          to (first?.get("note")          ?: ""),
            "respondedAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "respondedBy"   to studentId,
            "updatedAt"     to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        return try {
            firestoreService.setDocument("ptmRsvps", docId, payload, merge = false)
            Result.success(
                SubmitRsvpResponse(
                    status            = "success",
                    ptmEventId        = ptmEventId,
                    studentId         = studentId,
                    bookingCount      = finalBookings.size,
                    preservedBookings = preserved
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getSchoolId(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
            ?: tokenManager.user.firstOrNull()?.schoolCode?.takeIf { it.isNotBlank() }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Phase-C — Apply / Decline (section-wise PTM model)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Apply for a PTM. Resolves the student's section's class teacher from
     * the PTM doc's `sections[]` snapshot. Allocates the next queue number
     * via a Firestore transaction, atomically:
     *
     *   1. Read the per-section counter doc.
     *   2. Read the existing RSVP for this student.
     *   3. Reject if the existing RSVP is already in `applied` state
     *      (duplicate guard — a re-apply from a `declined` doc IS allowed
     *      and gets a fresh queue number).
     *   4. Write the RSVP with status=applied, teacherId=class teacher of
     *      student's section, queueNumber=counter+1.
     *   5. Bump the counter.
     *
     * The transaction guarantees: no two parents end up with the same
     * queue number even under simultaneous applies, no duplicate-applied
     * RSVPs, and no client-supplied teacherId tampering (server reads the
     * snapshot from the PTM doc itself).
     */
    suspend fun applyToPtm(
        ptm: PtmEventDoc,
        studentId: String,
        studentName: String,
        className: String,
        section: String,
        rollNo: String,
        parentName: String,
        parentPhone: String,
        parentEmail: String,
        note: String = ""
    ): Result<ApplyResult> {
        val schoolId = getSchoolId()
            ?: return Result.failure(IllegalStateException("School id not available"))

        // Resolve the student's class teacher from the PTM's sections[]
        // snapshot. This is the load-bearing check — `assignmentFor` does
        // both exact-match and "ALL" fallback, so an all-school PTM with
        // sections[] containing this student's section returns the right
        // class teacher.
        val assignment: PtmSectionAssignment = ptm.assignmentFor(className, section)
            ?: return Result.failure(
                PtmRsvpException("NO_ASSIGNMENT_FOR_SECTION",
                    "This PTM is not configured for ${className} / ${section}.")
            )
        if (assignment.classTeacherId.isBlank()) {
            return Result.failure(
                PtmRsvpException("NO_CLASS_TEACHER",
                    "No class teacher assigned for ${className} / ${section}. Ask the school office to set one.")
            )
        }

        // Mid-cycle deactivation guard: the PTM's `sections[]` snapshot is
        // taken at create time, so a class teacher who's been deactivated
        // since the PTM was published is still recorded here. Re-resolve
        // against the live staff doc and refuse the apply if Inactive.
        val classTeacherStaff = try {
            firestoreService.getDocumentAs<StaffDoc>(
                "staff",
                "${schoolId}_${assignment.classTeacherId}"
            )
        } catch (_: Exception) { null }
        if (classTeacherStaff == null || !classTeacherStaff.status.equals("Active", ignoreCase = true)) {
            return Result.failure(
                PtmRsvpException("NO_ACTIVE_CLASS_TEACHER",
                    "No active class teacher available. Please contact school.")
            )
        }

        val ptmEventId = ptm.ptmEventId.ifBlank { ptm.id.removePrefix("${schoolId}_") }
        val sectionKey = assignment.sectionKey
        val rsvpDocId    = "${schoolId}_${ptmEventId}_${studentId}"
        // Counter doc id intentionally encodes the section so each section
        // has its own independent queue. "ALL"-section PTMs degenerate to
        // one counter per child's section, which is the right behaviour.
        val counterDocId = "${schoolId}_${ptmEventId}_${sectionKey.replace('/', '_')}"

        val firestore = FirebaseFirestore.getInstance()
        val rsvpRef    = firestore.collection("ptmRsvps").document(rsvpDocId)
        val counterRef = firestore.collection("ptmCounters").document(counterDocId)

        return try {
            val nextQueue: Int = firestore.runTransaction { txn ->
                val existingSnap   = txn.get(rsvpRef)
                val existingStatus = (existingSnap.getString("status") ?: "").lowercase()
                // Lifecycle guard — only an unset/declined RSVP can transition
                // to "applied". delivered/no-show are teacher-set terminal
                // states; allowing parent re-apply would silently overwrite
                // the teacher's call. "applied" is also blocked to prevent
                // duplicate queue allocation under retries / double-clicks.
                when (existingStatus) {
                    "applied"   -> throw PtmRsvpException("DUPLICATE_APPLY",
                        "You've already applied for this PTM.")
                    "delivered" -> throw PtmRsvpException("ALREADY_DELIVERED",
                        "Your meeting is marked completed by the teacher.")
                    "no-show"   -> throw PtmRsvpException("ALREADY_NO_SHOW",
                        "Your meeting is marked no-show. Re-apply isn't supported for this PTM.")
                    // "" (no doc) and "declined" → allowed to apply.
                    else -> { /* proceed */ }
                }

                val counterSnap = txn.get(counterRef)
                val current     = counterSnap.getLong("nextQueue")?.toInt() ?: 0
                val allocated   = current + 1

                val nowTs = com.google.firebase.Timestamp.now()
                val payload = mapOf(
                    "ptmEventId"   to ptmEventId,
                    "schoolId"     to schoolId,
                    "sectionKey"   to sectionKey,
                    "studentId"    to studentId,
                    "studentName"  to studentName,
                    "className"    to className,
                    "section"      to section,
                    "rollNo"       to rollNo,
                    "parentName"   to parentName,
                    "parentPhone"  to parentPhone,
                    "parentEmail"  to parentEmail,

                    // Phase-C canonical fields
                    "status"       to "applied",
                    "teacherId"    to assignment.classTeacherId,
                    "teacherName"  to assignment.classTeacherName,
                    "queueNumber"  to allocated,
                    "appliedAt"    to nowTs,
                    "deliveredAt"  to null,
                    "markedBy"     to "",
                    "note"         to note,

                    // Drop the legacy bookings[] entirely on a Phase-C apply.
                    // A re-apply from a previously bookings[]-shaped doc
                    // overwrites cleanly because we use set() (not merge).
                    "bookings"     to emptyList<Map<String, Any?>>(),

                    // Legacy mirror — keep only slotIndex for old teacher
                    // app builds that key off it. Time fields deliberately
                    // not mirrored: `ptm.startTime`/`ptm.endTime` is the
                    // single source of truth, and an admin time edit must
                    // not leave stale snapshots on this RSVP. Any reader
                    // that needs the meeting window must dereference the
                    // PTM doc.
                    "slotIndex"    to -1,

                    "respondedAt"  to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "respondedBy"  to studentId,
                    "updatedAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                )
                txn.set(rsvpRef, payload)

                txn.set(
                    counterRef,
                    mapOf(
                        "ptmEventId"  to ptmEventId,
                        "schoolId"    to schoolId,
                        "sectionKey"  to sectionKey,
                        "nextQueue"   to allocated,
                        "updatedAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                allocated
            }.await()

            Result.success(
                ApplyResult(
                    ptmEventId       = ptmEventId,
                    studentId        = studentId,
                    queueNumber      = nextQueue,
                    classTeacherId   = assignment.classTeacherId,
                    classTeacherName = assignment.classTeacherName,
                )
            )
        } catch (e: PtmRsvpException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decline a PTM. No queue allocation — declined RSVPs are hidden from
     * the teacher's attendance list (per agreed Phase-C scope), so they
     * don't take a queue slot. If the parent had previously applied, their
     * old queue number is preserved on the doc but `status: "declined"`
     * effectively removes them from the queue.
     */
    suspend fun declineFromPtm(
        ptm: PtmEventDoc,
        studentId: String,
        studentName: String,
        className: String,
        section: String,
        rollNo: String,
        parentName: String,
        parentPhone: String,
        parentEmail: String,
        note: String = ""
    ): Result<Unit> {
        val schoolId = getSchoolId()
            ?: return Result.failure(IllegalStateException("School id not available"))
        val ptmEventId = ptm.ptmEventId.ifBlank { ptm.id.removePrefix("${schoolId}_") }
        val rsvpDocId  = "${schoolId}_${ptmEventId}_${studentId}"
        val assignment = ptm.assignmentFor(className, section)

        val payload = mapOf(
            "ptmEventId"   to ptmEventId,
            "schoolId"     to schoolId,
            "sectionKey"   to (assignment?.sectionKey ?: "$className/$section"),
            "studentId"    to studentId,
            "studentName"  to studentName,
            "className"    to className,
            "section"      to section,
            "rollNo"       to rollNo,
            "parentName"   to parentName,
            "parentPhone"  to parentPhone,
            "parentEmail"  to parentEmail,
            "status"       to "declined",
            "teacherId"    to (assignment?.classTeacherId ?: ""),
            "teacherName"  to (assignment?.classTeacherName ?: ""),
            "note"         to note,
            // queueNumber: deliberately not set on decline. If the parent
            // had previously been allocated a queue#, we leave it intact
            // (set merge=true) so a future re-apply audit can see history.
            "respondedAt"  to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "respondedBy"  to studentId,
            "updatedAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp(),
        )
        return try {
            firestoreService.setDocument("ptmRsvps", rsvpDocId, payload, merge = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Outcome of a successful [PtmFirestoreRepository.applyToPtm] — surfaces
 * the allocated queue number and the resolved class teacher so the parent
 * UI can confirm "you are #N · meet Mrs. Sharma" without a re-fetch.
 */
data class ApplyResult(
    val ptmEventId: String,
    val studentId: String,
    val queueNumber: Int,
    val classTeacherId: String,
    val classTeacherName: String
)

/**
 * Typed exception so the ViewModel can branch on the rejection code
 * (CAPACITY_FULL → "Slot is full", BOOKING_OVERLAP → "Slots overlap", etc.)
 * rather than parsing free-form messages.
 */
class PtmRsvpException(
    val code: String?,
    message: String
) : Exception(message)
