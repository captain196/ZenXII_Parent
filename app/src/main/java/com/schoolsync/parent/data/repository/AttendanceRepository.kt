package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AttendanceData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB-based attendance repository (legacy fallback).
 * Primary attendance data now lives in Firestore via [AttendanceFirestoreRepository].
 * This stub returns empty/default data so the build passes while RTDB paths are phased out.
 */
@Singleton
class AttendanceRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {

    /**
     * Fetch current month's attendance for the logged-in student.
     */
    suspend fun getCurrentMonthAttendance(): AttendanceData {
        // TODO: wire to RTDB if legacy path is still needed; Firestore is primary
        val now = java.util.Calendar.getInstance()
        val monthName = java.text.DateFormatSymbols().months[now.get(java.util.Calendar.MONTH)]
        val year = now.get(java.util.Calendar.YEAR)
        return AttendanceData(
            month = monthName,
            year = year,
            rawString = "",
            dailyStatus = emptyList()
        )
    }
}
