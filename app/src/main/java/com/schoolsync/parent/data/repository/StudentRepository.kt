package com.schoolsync.parent.data.repository

import android.util.Log
import com.google.firebase.firestore.Query
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
 * Student profile — Firestore-only.
 *   Source: Firestore `students/{schoolId}_{studentId}`
 *
 * The previous RTDB fallback at `Users/Parents/{parentDbKey}/{studentId}/`
 * was removed per the project's absolute NO-RTDB policy (P0c migration).
 * Every student profile that the admin panel writes lands in Firestore via
 * Entity_firestore_sync::syncStudent — there is no scenario where a real
 * student exists in RTDB but not Firestore. If Firestore returns nothing,
 * the student genuinely does not exist (or the user is signed into the
 * wrong school) and the caller should treat that as the empty case.
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
     * Fetch the student's full profile from Firestore.
     *
     * Returns the doc as a Map<String, Any?> for caller compatibility (the
     * dashboard / profile decoders shape themselves around the same keys
     * the admin panel writes). Returns an empty map when:
     *   - schoolId is not yet known (signed-out / pre-init state)
     *   - the Firestore read fails (offline / permission / transient)
     *   - the doc genuinely does not exist
     *
     * The `parentDbKey` parameter is retained for API compatibility — it is
     * no longer used for any RTDB lookup but kept so callers don't have to
     * change their call sites.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchStudentProfile(parentDbKey: String, studentId: String): Map<String, Any?> {
        val schoolId = tokenManager.user.firstOrNull()?.schoolId ?: ""
        if (schoolId.isBlank()) {
            Log.d(TAG, "fetchStudentProfile: schoolId unavailable for $studentId")
            return emptyMap()
        }
        return try {
            val fsDocId = "${schoolId}_$studentId"
            val fsMap = firestoreService.getDocumentMap(Constants.Firestore.STUDENTS, fsDocId)
            if (fsMap.isNullOrEmpty()) {
                Log.d(TAG, "Firestore: no doc for student $studentId")
                emptyMap()
            } else {
                fsMap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore read failed for student $studentId", e)
            emptyMap()
        }
    }

    /**
     * Observe the student's profile for real-time changes via Firestore.
     *
     * Emits the doc data on every snapshot. Emits an empty map when the
     * doc does not exist or schoolId is not yet known. No RTDB fallback —
     * the admin panel writes Firestore as the source of truth.
     */
    @Suppress("UNUSED_PARAMETER")
    fun observeStudentProfile(parentDbKey: String, studentId: String): Flow<Map<String, Any?>> = flow {
        val schoolId = tokenManager.user.firstOrNull()?.schoolId ?: ""
        if (schoolId.isBlank()) {
            emit(emptyMap())
            return@flow
        }

        val fsDocId = "${schoolId}_$studentId"
        firestoreService.observeDocument(Constants.Firestore.STUDENTS, fsDocId).collect { snapshot ->
            val fsMap = snapshot?.data
            emit(if (fsMap != null && fsMap.isNotEmpty()) fsMap else emptyMap())
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
            val snapshot = firestoreService.queryDocuments(Constants.Firestore.NOTICES_FS) { ref ->
                ref.whereEqualTo("schoolId", user.schoolCode)
                    .orderBy("sentAt", Query.Direction.DESCENDING)
                    .limit(5)
            }
            recentNoticeCount = snapshot.size()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count recent notices", e)
        }

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
