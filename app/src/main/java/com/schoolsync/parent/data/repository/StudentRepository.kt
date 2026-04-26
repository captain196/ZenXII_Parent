package com.schoolsync.parent.data.repository

import android.util.Log
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AttendanceData
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

/**
 * Repository for student profile and dashboard aggregation.
 *
 * Student profile — Firestore-first with RTDB fallback (Phase 2):
 *   PRIMARY:  Firestore `students/{schoolId}_{studentId}`
 *   FALLBACK: RTDB `Users/Parents/{parentDbKey}/{studentId}/`
 *
 * The fallback exists because some older students may not yet have their
 * Firestore mirror populated. It will be removed in Phase 3 once every
 * student record is confirmed migrated.
 */
@Singleton
class StudentRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService,
    private val feeFirestoreRepository: FeeFirestoreRepository,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "StudentRepository"
    }

    /** Current cached user from DataStore */
    val currentUser: Flow<User> = tokenManager.user

    /**
     * Fetch the student's full profile.
     * Tries Firestore first; falls back to RTDB if Firestore is empty or fails.
     *
     * Public signature preserved for caller compatibility — still returns a
     * Map<String, Any?> shaped like the RTDB node so existing decoders work
     * against either source.
     */
    suspend fun fetchStudentProfile(parentDbKey: String, studentId: String): Map<String, Any?> {
        val schoolId = tokenManager.user.firstOrNull()?.schoolId ?: ""

        if (schoolId.isNotBlank()) {
            try {
                val fsDocId = "${schoolId}_$studentId"
                val fsMap = firestoreService.getDocumentMap(Constants.Firestore.STUDENTS, fsDocId)
                if (!fsMap.isNullOrEmpty()) {
                    Log.d(TAG, "Firestore: loaded student $studentId")
                    return fsMap
                }
                Log.d(TAG, "Firestore empty for student $studentId; falling back to RTDB")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore read failed for student $studentId; falling back to RTDB", e)
            }
        } else {
            Log.d(TAG, "schoolId unavailable; reading student $studentId from RTDB")
        }

        val rtdbPath = Constants.Firebase.studentProfilePath(parentDbKey, studentId)
        return firebaseService.readMap(rtdbPath)
    }

    /**
     * Observe the student's profile for real-time changes.
     *
     * Strategy:
     *   1. If schoolId is known, subscribe to the Firestore doc. Each snapshot
     *      is emitted as a Map. If the Firestore doc is missing, that snapshot
     *      triggers a one-shot RTDB read and emits that instead.
     *   2. If schoolId is unavailable, degrade to the pure-RTDB observable.
     *
     * The fallback is per-emission, not per-subscription — so a student whose
     * Firestore mirror lands after the app opens will transparently start
     * receiving Firestore updates without any reconnection.
     */
    fun observeStudentProfile(parentDbKey: String, studentId: String): Flow<Map<String, Any?>> = flow {
        val schoolId = tokenManager.user.firstOrNull()?.schoolId ?: ""
        val rtdbPath = Constants.Firebase.studentProfilePath(parentDbKey, studentId)

        if (schoolId.isBlank()) {
            firebaseService.observeMap(rtdbPath).collect { emit(it) }
            return@flow
        }

        val fsDocId = "${schoolId}_$studentId"
        firestoreService.observeDocument(Constants.Firestore.STUDENTS, fsDocId).collect { snapshot ->
            val fsMap = snapshot?.data
            if (fsMap != null && fsMap.isNotEmpty()) {
                emit(fsMap)
            } else {
                // Firestore doc missing — one-shot RTDB read for this emission
                try {
                    emit(firebaseService.readMap(rtdbPath))
                } catch (e: Exception) {
                    Log.w(TAG, "RTDB fallback read failed for student $studentId", e)
                    emit(emptyMap())
                }
            }
        }
    }

    /**
     * Get a quick dashboard summary: today's attendance status, pending fee total,
     * recent notice count, etc. This aggregates from multiple paths.
     */
    suspend fun getDashboardSummary(): DashboardSummary {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) {
            return DashboardSummary.empty()
        }

        val calendar = Calendar.getInstance()
        val currentMonth = Constants.getMonthName(calendar.get(Calendar.MONTH))
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        // Fetch today's attendance
        val currentYear = calendar.get(Calendar.YEAR)
        val monthKey = "$currentMonth $currentYear"
        val attendancePath = Constants.Firebase.attendancePath(
            schoolCode = user.schoolCode,
            session = user.session,
            className = user.className,
            section = user.section,
            studentId = user.userId,
            month = monthKey
        )

        var todayStatus: String? = null
        var monthlyPercentage = 0f
        var prevMonthPercentage: Float? = null

        try {
            val rawString = firebaseService.readString(attendancePath)
            if (!rawString.isNullOrBlank()) {
                val attendance = AttendanceData.decode(currentMonth, currentYear, rawString)
                val status = attendance.statusForDay(currentDay)
                todayStatus = status?.label
                monthlyPercentage = attendance.attendancePercentage
            }
        } catch (_: Exception) {}

        // Fetch previous month's attendance for change calculation
        try {
            val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val prevMonth = Constants.getMonthName(prevCal.get(Calendar.MONTH))
            val prevYear = prevCal.get(Calendar.YEAR)
            val prevMonthKey = "$prevMonth $prevYear"
            val prevPath = Constants.Firebase.attendancePath(
                schoolCode = user.schoolCode,
                session = user.session,
                className = user.className,
                section = user.section,
                studentId = user.userId,
                month = prevMonthKey
            )
            val prevRaw = firebaseService.readString(prevPath)
            if (!prevRaw.isNullOrBlank()) {
                val prevAttendance = AttendanceData.decode(prevMonth, prevYear, prevRaw)
                if (prevAttendance.workingDays > 0) {
                    prevMonthPercentage = prevAttendance.attendancePercentage
                }
            }
        } catch (_: Exception) {}

        // Fetch pending fees total — Firestore only (feeDemands collection).
        // Per project policy, no RTDB fallback. Sums netAmount - paidAmount
        // across all non-paid demands, matching what FeesViewModel shows.
        var pendingFeeAmount = 0.0
        try {
            feeFirestoreRepository.getPendingDemands(user.userId)
                .getOrNull()
                ?.forEach { demand ->
                    pendingFeeAmount += (demand.netAmount - demand.paidAmount).coerceAtLeast(0.0)
                }
        } catch (_: Exception) {}

        // Count recent notices (last 5)
        var recentNoticeCount = 0
        try {
            val noticesPath = Constants.Firebase.noticesPath(user.schoolCode)
            val notices = firebaseService.readChildrenLimited(noticesPath, 5)
            recentNoticeCount = notices.size
        } catch (_: Exception) {}

        val attendanceChange = if (prevMonthPercentage != null && monthlyPercentage > 0f) {
            monthlyPercentage - prevMonthPercentage
        } else null

        return DashboardSummary(
            studentName = user.name,
            className = user.className,
            section = user.section,
            rollNo = user.rollNo,
            schoolName = user.schoolDisplayName,
            todayAttendance = todayStatus,
            monthlyAttendancePercent = monthlyPercentage,
            attendanceChange = attendanceChange,
            pendingFeeAmount = pendingFeeAmount,
            recentNoticeCount = recentNoticeCount,
            profilePicUrl = user.profilePic
        )
    }
}

data class DashboardSummary(
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val rollNo: String = "",
    val schoolName: String = "",
    val todayAttendance: String? = null,
    val monthlyAttendancePercent: Float = 0f,
    val attendanceChange: Float? = null,
    val pendingFeeAmount: Double = 0.0,
    val recentNoticeCount: Int = 0,
    val profilePicUrl: String = ""
) {
    companion object {
        fun empty() = DashboardSummary()
    }
}
