package com.schoolsync.parent.ui.attendance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AttendanceData
import com.schoolsync.parent.data.model.AttendanceStatus
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class MonthOption(
    val year: Int,
    val month: Int,
    val monthName: String,
    val displayName: String,
    val yearMonth: YearMonth
)

data class AttendanceStats(
    val totalDays: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val leave: Int = 0,
    val holiday: Int = 0,
    val percentage: Float = 0f
)

data class MonthlyComparison(
    val monthAbbrev: String,
    val percentage: Float
)

data class RecentDay(
    val dayName: String,
    val dateStr: String,
    val status: AttendanceStatus,
    val dayOfMonth: Int
)

data class AttendanceUiState(
    val isLoading: Boolean = true,
    val months: List<MonthOption> = emptyList(),
    val selectedMonthIndex: Int = 0,
    val attendanceData: AttendanceData? = null,
    val stats: AttendanceStats = AttendanceStats(),
    val errorMessage: String? = null,
    val todayStatus: AttendanceStatus? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val monthlyComparison: List<MonthlyComparison> = emptyList(),
    val recentDays: List<RecentDay> = emptyList(),
    val user: User = User.empty(),
    val dayOfYear: Int = 0,
    val totalSchoolDays: Int = 0
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        loadUser()
        initializeMonths()
        loadAttendance()
        loadExtras()
    }

    private fun loadUser() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            _uiState.update { it.copy(user = user) }
        }
    }

    private fun initializeMonths() {
        val now = YearMonth.now()
        val months = (0..11).map { offset ->
            val ym = now.minusMonths(offset.toLong())
            val monthName = getFullMonthName(ym.monthValue)
            MonthOption(
                year = ym.year,
                month = ym.monthValue,
                monthName = monthName,
                displayName = "${ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${ym.year}",
                yearMonth = ym
            )
        }
        _uiState.update { it.copy(months = months) }
    }

    private fun getFullMonthName(monthValue: Int): String {
        return when (monthValue) {
            1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
            5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
            9 -> "September"; 10 -> "October"; 11 -> "November"; 12 -> "December"
            else -> ""
        }
    }

    fun selectMonth(index: Int) {
        _uiState.update { it.copy(selectedMonthIndex = index) }
        loadAttendance()
    }

    fun loadAttendance() {
        val state = _uiState.value
        val month = state.months.getOrNull(state.selectedMonthIndex) ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Primary: load from Firestore
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val studentId = user.userId

            if (studentId.isNotBlank()) {
                val firestoreMonth = "${month.monthName} ${month.year}" // e.g., "March 2026"
                val firestoreResult = attendanceFirestoreRepo.getAttendanceForMonth(studentId, firestoreMonth)

                firestoreResult.fold(
                    onSuccess = { summaryDoc ->
                        // Map Firestore AttendanceSummaryDoc → existing AttendanceData
                        val data = AttendanceData.decodeOrEmpty(
                            month = month.monthName,
                            year = month.year,
                            rawString = summaryDoc.dayWise
                        )

                        val stats = AttendanceStats(
                            totalDays = summaryDoc.totalDays,
                            present = summaryDoc.present,
                            absent = summaryDoc.absent,
                            leave = summaryDoc.leave,
                            holiday = summaryDoc.holiday,
                            percentage = summaryDoc.percentage.toFloat()
                        )

                        val today = LocalDate.now()
                        val todayStatus = if (month.yearMonth == YearMonth.now()) {
                            data.statusForDay(today.dayOfMonth)
                        } else null

                        val recentDays = buildRecentDays(data, month.yearMonth)
                        val dayOfYear = today.dayOfYear
                        val totalSchoolDays = if (summaryDoc.totalDays > 0) {
                            summaryDoc.workingDays
                        } else 0

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                attendanceData = data,
                                stats = stats,
                                todayStatus = todayStatus,
                                recentDays = recentDays,
                                dayOfYear = dayOfYear,
                                totalSchoolDays = totalSchoolDays
                            )
                        }
                    },
                    onFailure = { e ->
                        Log.e("AttendanceVM", "Firestore attendance failed", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Failed to load attendance"
                            )
                        }
                    }
                )
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Student info not available")
                }
            }
        }
    }

    /**
     * Load streak info and monthly comparison data.
     * These require fetching multiple months so we do it in background.
     * Primary: Firestore, Fallback: RTDB.
     */
    private fun loadExtras() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val studentId = user.userId

            if (studentId.isNotBlank()) {
                // Try Firestore first: get all monthly summaries for this student
                val summaryResult = attendanceFirestoreRepo.getAttendanceSummary(studentId)
                summaryResult.fold(
                    onSuccess = { summaries ->
                        try {
                            val now = YearMonth.now()
                            val comparisons = mutableListOf<MonthlyComparison>()

                            for (offset in 2 downTo 0) {
                                val ym = now.minusMonths(offset.toLong())
                                val monthKey = "${getFullMonthName(ym.monthValue)} ${ym.year}"
                                val abbrev = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                val matchingSummary = summaries.find { it.month == monthKey }
                                comparisons.add(
                                    MonthlyComparison(
                                        monthAbbrev = abbrev,
                                        percentage = matchingSummary?.percentage?.toFloat() ?: 0f
                                    )
                                )
                            }

                            // Streak computation from dayWise strings
                            val currentMonthKey = "${getFullMonthName(now.monthValue)} ${now.year}"
                            val prevMonth = now.minusMonths(1)
                            val prevMonthKey = "${getFullMonthName(prevMonth.monthValue)} ${prevMonth.year}"

                            val currentDayWise = summaries.find { it.month == currentMonthKey }?.dayWise ?: ""
                            val prevDayWise = summaries.find { it.month == prevMonthKey }?.dayWise ?: ""

                            val combinedStatuses = mutableListOf<AttendanceStatus>()
                            prevDayWise.forEach { combinedStatuses.add(AttendanceStatus.fromCode(it)) }
                            currentDayWise.forEach { combinedStatuses.add(AttendanceStatus.fromCode(it)) }

                            val (currentStreak, bestStreak) = computeStreaks(combinedStatuses)

                            _uiState.update {
                                it.copy(
                                    currentStreak = currentStreak,
                                    bestStreak = bestStreak,
                                    monthlyComparison = comparisons
                                )
                            }
                        } catch (_: Exception) {
                            // Non-critical, leave defaults
                        }
                    },
                    onFailure = { e ->
                        Log.w("AttendanceVM", "Firestore extras failed", e)
                        // Non-critical, leave defaults
                    }
                )
            }
            // No studentId — leave defaults
        }
    }

    /**
     * Compute current streak (consecutive present days ending at the last recorded day)
     * and the best streak (longest run of consecutive present days).
     */
    private fun computeStreaks(statuses: List<AttendanceStatus>): Pair<Int, Int> {
        if (statuses.isEmpty()) return Pair(0, 0)

        var bestStreak = 0
        var currentRun = 0

        for (status in statuses) {
            if (status == AttendanceStatus.PRESENT) {
                currentRun++
                if (currentRun > bestStreak) bestStreak = currentRun
            } else if (status != AttendanceStatus.HOLIDAY &&
                status != AttendanceStatus.VACATION &&
                status != AttendanceStatus.TRIP
            ) {
                // Absent or Leave breaks the streak
                currentRun = 0
            }
            // Holidays/vacations/trips don't break streaks, but don't add either
        }

        // Current streak: walk backwards from the last entry
        var currentStreak = 0
        for (i in statuses.indices.reversed()) {
            val s = statuses[i]
            if (s == AttendanceStatus.PRESENT) {
                currentStreak++
            } else if (s == AttendanceStatus.HOLIDAY ||
                s == AttendanceStatus.VACATION ||
                s == AttendanceStatus.TRIP
            ) {
                // Skip holidays, don't break
                continue
            } else {
                break
            }
        }

        return Pair(currentStreak, bestStreak)
    }

    /**
     * Build a list of recent days (up to last 7 recorded days) from the attendance data.
     */
    private fun buildRecentDays(data: AttendanceData, yearMonth: YearMonth): List<RecentDay> {
        if (data.dailyStatus.isEmpty()) return emptyList()

        val today = LocalDate.now()
        val result = mutableListOf<RecentDay>()

        // Walk backwards from the last recorded day (or today if current month)
        val lastDay = if (yearMonth == YearMonth.from(today)) {
            minOf(today.dayOfMonth, data.dailyStatus.size)
        } else {
            data.dailyStatus.size
        }

        for (day in lastDay downTo 1) {
            if (result.size >= 7) break
            val status = data.statusForDay(day) ?: continue
            val date = yearMonth.atDay(day)
            result.add(
                RecentDay(
                    dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    dateStr = "%02d/%02d/%d".format(date.dayOfMonth, date.monthValue, date.year),
                    status = status,
                    dayOfMonth = day
                )
            )
        }

        return result
    }
}
