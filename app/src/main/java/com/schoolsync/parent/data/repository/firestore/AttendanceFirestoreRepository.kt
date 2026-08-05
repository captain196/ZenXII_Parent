package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.AttendanceSummaryDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading attendance data from Firestore (parent-side, read-only).
 *
 * Collections used:
 * - attendance: daily per-student records
 * - attendanceSummary: monthly rollups with dayWise string and stats
 */
/**
 * Thrown when a month's attendance summary doc simply does not exist — a
 * legitimate "no data yet" empty state, NOT a failure. Distinct from the
 * generic exceptions raised on network/permission/index errors so the UI can
 * render an empty month vs. a real error+retry differently.
 */
class AttendanceNotFoundException(month: String) :
    Exception("Attendance summary not found for $month")

@Singleton
class AttendanceFirestoreRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch attendance summary for a specific month.
     *
     * Doc id format: `{schoolId}_{studentId}_{YYYY-MM}` — matches what
     * admin `save_student_attendance` and the teacher app both write.
     *
     * @param month either "Month YYYY" (e.g. "April 2026") or
     *              "YYYY-MM" (e.g. "2026-04") — both accepted.
     */
    suspend fun getAttendanceForMonth(
        studentId: String,
        month: String
    ): Result<AttendanceSummaryDoc> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        val monthKey = monthLabelToKey(month)
        val docId = "${schoolCode}_${studentId}_${monthKey}"

        return try {
            val doc = firestoreService.getDocumentAs<AttendanceSummaryDoc>(
                Constants.Firestore.ATTENDANCE_SUMMARY,
                docId
            )
            if (doc != null) {
                Result.success(doc)
            } else {
                // Distinct type so callers can tell a legitimately-absent month
                // (empty state) apart from a real network/permission/index
                // failure — the two must render very differently.
                Result.failure(AttendanceNotFoundException(month))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** "April 2026" → "2026-04". Pass-through if already in YYYY-MM form. */
    private fun monthLabelToKey(monthOrKey: String): String {
        val s = monthOrKey.trim()
        if (s.matches(Regex("^\\d{4}-\\d{2}$"))) return s
        val parts = s.split(" ")
        if (parts.size != 2) return s
        val year = parts[1].toIntOrNull() ?: return s
        val monthNum = monthNameToNumber(parts[0]) ?: return s
        return "%d-%02d".format(year, monthNum)
    }

    private fun monthNameToNumber(name: String): Int? = when (name.lowercase()) {
        "january" -> 1; "february" -> 2; "march" -> 3
        "april" -> 4; "may" -> 5; "june" -> 6
        "july" -> 7; "august" -> 8; "september" -> 9
        "october" -> 10; "november" -> 11; "december" -> 12
        else -> null
    }

    /**
     * Fetch all monthly attendance summaries for a student.
     *
     * Phase 7h (2026-04-08): dropped the `session` filter — the
     * admin writer doesn't use it as a query key and we want this to
     * return both admin-written canonical docs and legacy
     * teacher-app docs in the same result set.
     */
    suspend fun getAttendanceSummary(
        studentId: String
    ): Result<List<AttendanceSummaryDoc>> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val summaries = firestoreService.queryDocumentsAs<AttendanceSummaryDoc>(
                Constants.Firestore.ATTENDANCE_SUMMARY
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
            }
            Result.success(summaries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve the docId-prefix school key. MUST match the resolution used by
     * [StudentFirestoreRepository] (`schoolCode ?: schoolId`) so both repos
     * build identical `{schoolKey}_{studentId}_…` doc ids — a mismatch here
     * would silently read the wrong document and render a blank month.
     */
    private suspend fun getSchoolCode(): String? {
        val user = tokenManager.user.firstOrNull() ?: return null
        return user.schoolCode.takeIf { it.isNotBlank() }
            ?: user.schoolId.takeIf { it.isNotBlank() }
    }
}
