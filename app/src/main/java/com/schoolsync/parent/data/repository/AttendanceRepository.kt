package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AttendanceData
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy facade kept for `ProfileViewModel` compatibility.
 * Delegates to [AttendanceFirestoreRepository] (Phase 7g) — RTDB has been
 * fully retired for student attendance reads on the parent side.
 */
@Singleton
class AttendanceRepository @Inject constructor(
    private val firestoreRepo: AttendanceFirestoreRepository,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch the current month's attendance summary for the logged-in student.
     * Returns an empty [AttendanceData] if no document exists yet.
     */
    suspend fun getCurrentMonthAttendance(): AttendanceData {
        val cal = java.util.Calendar.getInstance()
        val monthName = java.text.DateFormatSymbols().months[cal.get(java.util.Calendar.MONTH)]
        val year = cal.get(java.util.Calendar.YEAR)
        val monthLabel = "$monthName $year"

        val studentId = tokenManager.user.firstOrNull()?.userId.orEmpty()
        android.util.Log.w("AttendanceRepo", "getCurrentMonthAttendance: studentId='$studentId' monthLabel='$monthLabel'")
        if (studentId.isBlank()) {
            android.util.Log.w("AttendanceRepo", "getCurrentMonthAttendance: BLANK studentId, returning empty")
            return AttendanceData.decodeOrEmpty(monthName, year, null)
        }

        val result = firestoreRepo.getAttendanceForMonth(studentId, monthLabel)
        val raw = result.getOrNull()?.dayWise.orEmpty()
        val pct = result.getOrNull()?.percentage ?: -1.0
        android.util.Log.w("AttendanceRepo", "getCurrentMonthAttendance: dayWise='${raw.take(15)}...' serverPct=$pct localPct=${AttendanceData.decodeOrEmpty(monthName, year, raw.ifBlank { null }).attendancePercentage}")
        return AttendanceData.decodeOrEmpty(monthName, year, raw.ifBlank { null })
    }
}
