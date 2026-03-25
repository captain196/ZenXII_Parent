package com.schoolsync.parent.data.repository

import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AttendanceData
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for student profile and dashboard aggregation.
 * Reads from: Users/Parents/{parentDbKey}/{studentId}/
 */
@Singleton
class StudentRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    /** Current cached user from DataStore */
    val currentUser: Flow<User> = tokenManager.user

    /**
     * Fetch the student's full profile from Firebase RTDB.
     * Path: Users/Parents/{parentDbKey}/{studentId}/
     */
    suspend fun fetchStudentProfile(parentDbKey: String, studentId: String): Map<String, Any?> {
        val path = Constants.Firebase.studentProfilePath(parentDbKey, studentId)
        return firebaseService.readMap(path)
    }

    /**
     * Observe the student's profile node for real-time changes.
     */
    fun observeStudentProfile(parentDbKey: String, studentId: String): Flow<Map<String, Any?>> {
        val path = Constants.Firebase.studentProfilePath(parentDbKey, studentId)
        return firebaseService.observeMap(path)
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

        // Fetch pending fees total
        var pendingFeeAmount = 0.0
        try {
            val pendingPath = Constants.Firebase.pendingFeesPath(
                schoolCode = user.schoolCode,
                session = user.session,
                studentId = user.userId
            )
            val pendingData = firebaseService.readMap(pendingPath)
            pendingData.forEach { (_, value) ->
                when (value) {
                    is Number -> pendingFeeAmount += value.toDouble()
                    is Map<*, *> -> {
                        val amount = (value["amount"] as? Number)?.toDouble() ?: 0.0
                        val status = value["status"]?.toString() ?: "Pending"
                        if (status.equals("Pending", ignoreCase = true) ||
                            status.equals("Overdue", ignoreCase = true)
                        ) {
                            pendingFeeAmount += amount
                        }
                    }
                }
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
