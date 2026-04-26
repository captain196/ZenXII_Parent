package com.schoolsync.parent.data.repository.firestore

import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.AttendanceDoc
import com.schoolsync.parent.data.model.firestore.AttendanceSummaryDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading attendance data from Firestore (parent-side, read-only).
 *
 * Collections used:
 * - attendance: daily per-student records
 * - attendanceSummary: monthly rollups with dayWise string and stats
 */
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
                Result.failure(Exception("Attendance summary not found for $month"))
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
     * Fetch daily attendance for a student on a specific date.
     * Query: schoolId + studentId + date.
     * Returns null if no attendance record exists for that date.
     */
    suspend fun getDailyAttendance(
        studentId: String,
        date: String
    ): Result<AttendanceDoc?> {
        val schoolCode = getSchoolCode()
            ?: return Result.failure(Exception("School code not available"))

        return try {
            val records = firestoreService.queryDocumentsAs<AttendanceDoc>(
                Constants.Firestore.ATTENDANCE
            ) { ref ->
                ref.whereEqualTo("schoolId", schoolCode)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("date", date)
            }
            Result.success(records.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe today's attendance for a student in real time.
     * Reacts to user profile changes (school code) via [flatMapLatest].
     * Emits null when the document does not exist or identifiers are unavailable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAttendanceToday(studentId: String): Flow<AttendanceDoc?> {
        return tokenManager.user
            .map { user ->
                user.schoolCode.takeIf { it.isNotBlank() }
            }
            .flatMapLatest { schoolCode ->
                if (schoolCode == null) {
                    flowOf(null)
                } else {
                    firestoreService.observeQuery(
                        Constants.Firestore.ATTENDANCE
                    ) { ref ->
                        ref.whereEqualTo("schoolId", schoolCode)
                            .whereEqualTo("studentId", studentId)
                            .whereEqualTo("date", todayDate())
                            .limit(1)
                    }.map { snapshot ->
                        snapshot.documents.firstOrNull()
                            ?.toObject(AttendanceDoc::class.java)
                    }
                }
            }
    }

    private suspend fun getSchoolCode(): String? {
        return tokenManager.user.firstOrNull()?.schoolId?.takeIf { it.isNotBlank() }
    }

    private fun todayDate(): String {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        return "%d-%02d-%02d".format(year, month, day)
    }
}
