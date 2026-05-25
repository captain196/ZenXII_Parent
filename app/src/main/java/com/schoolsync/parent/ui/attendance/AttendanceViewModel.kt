package com.schoolsync.parent.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AttendanceData
import com.schoolsync.parent.data.model.AttendanceStatus
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.parent.util.debugLog
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
    val tardy: Int = 0,
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
    val dayOfMonth: Int,
    /** Recorded arrival time ("HH:mm") on tardy days; null otherwise. */
    val arrivalTime: String? = null
)

data class AttendanceUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
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
    val totalSchoolDays: Int = 0,
    /** Late threshold in "HH:mm" format from school config. Default 08:30. */
    val lateThreshold: String = "08:30"
)

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceFirestoreRepo: AttendanceFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    /** In-flight loads — cancelled before a new one starts so concurrent
     *  triggers (lifecycle resume, month switch, pull-refresh) don't race
     *  to overwrite the UI state with stale month data. */
    private var loadAttendanceJob: kotlinx.coroutines.Job? = null
    private var loadExtrasJob: kotlinx.coroutines.Job? = null

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

        loadAttendanceJob?.cancel()
        loadAttendanceJob = viewModelScope.launch {
            // Phase 7s: clear any data from the previously-selected month
            // BEFORE we hit the network, so the screen never renders one
            // month's percentages on top of another month's recent-day
            // list (which was happening when switching to a month that
            // has no Firestore doc and the failure handler kept the
            // stale state).
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    attendanceData = null,
                    stats = AttendanceStats(),
                    todayStatus = null,
                    recentDays = emptyList(),
                    totalSchoolDays = 0
                )
            }

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

                        // Phase 9b: re-derive percentage from the doc's
                        // counts instead of trusting the stored field.
                        // The stored `percentage` may be stale if the doc
                        // was last written by old code that didn't include
                        // tardy in the formula.
                        val working = summaryDoc.present + summaryDoc.absent + summaryDoc.leave + summaryDoc.tardy
                        val derivedPct = if (working > 0) {
                            (summaryDoc.present + summaryDoc.tardy).toFloat() / working * 100f
                        } else 0f

                        val stats = AttendanceStats(
                            totalDays = summaryDoc.totalDays,
                            present = summaryDoc.present,
                            absent = summaryDoc.absent,
                            leave = summaryDoc.leave,
                            holiday = summaryDoc.holiday,
                            tardy = summaryDoc.tardy,
                            percentage = derivedPct
                        )

                        val today = LocalDate.now()
                        val todayStatus = if (month.yearMonth == YearMonth.now()) {
                            data.statusForDay(today.dayOfMonth)
                        } else null

                        val recentDays = buildRecentDays(data, month.yearMonth, summaryDoc.lateTimes)
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
                        // Phase 7s: "no document for this month" is a
                        // legitimate empty state, not an error worth
                        // showing the user. Reset all the per-month
                        // fields to defaults so the UI renders an
                        // empty month cleanly instead of carrying
                        // over the previous selection's numbers.
                        debugLog("[AttendanceVM][I] No attendance for ${month.monthName} ${month.year}: ${e.message}")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                attendanceData = AttendanceData.decodeOrEmpty(
                                    month = month.monthName,
                                    year = month.year,
                                    rawString = null
                                ),
                                stats = AttendanceStats(),
                                todayStatus = null,
                                recentDays = emptyList(),
                                totalSchoolDays = 0
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
    /** Public alias so the screen can re-trigger streak/comparison loads on resume. */
    fun refreshExtras() = loadExtras()

    /** Pull-to-refresh: reload attendance + extras with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                loadAttendance()
                loadExtras()
            } catch (e: Exception) {
                debugLog("[AttendanceVM][W] pullRefresh failed: ${e.message}")
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadExtras() {
        loadExtrasJob?.cancel()
        loadExtrasJob = viewModelScope.launch {
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
                                // Phase 7h (2026-04-08): canonical month key is "YYYY-MM";
                                // legacy docs still carry "Month YYYY" so match either.
                                val canonicalKey = "%d-%02d".format(ym.year, ym.monthValue)
                                val legacyLabel = "${getFullMonthName(ym.monthValue)} ${ym.year}"
                                val abbrev = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                val matchingSummary = summaries.find {
                                    it.month == canonicalKey || it.month == legacyLabel
                                }
                                comparisons.add(
                                    MonthlyComparison(
                                        monthAbbrev = abbrev,
                                        percentage = matchingSummary?.percentage?.toFloat() ?: 0f
                                    )
                                )
                            }

                            // Streak computation from dayWise strings
                            val curCanonical = "%d-%02d".format(now.year, now.monthValue)
                            val curLegacy = "${getFullMonthName(now.monthValue)} ${now.year}"
                            val prevMonth = now.minusMonths(1)
                            val prevCanonical = "%d-%02d".format(prevMonth.year, prevMonth.monthValue)
                            val prevLegacy = "${getFullMonthName(prevMonth.monthValue)} ${prevMonth.year}"

                            val currentDayWise = summaries.find {
                                it.month == curCanonical || it.month == curLegacy
                            }?.dayWise ?: ""
                            val prevDayWise = summaries.find {
                                it.month == prevCanonical || it.month == prevLegacy
                            }?.dayWise ?: ""

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
                        debugLog("[AttendanceVM][W] Firestore extras failed: ${e.message}")
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
    private fun buildRecentDays(
        data: AttendanceData,
        yearMonth: YearMonth,
        lateTimes: Map<String, Map<String, String>> = emptyMap()
    ): List<RecentDay> {
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
            val arrivalTime = if (status == AttendanceStatus.TRIP) {
                lateTimes[day.toString()]?.get("time")?.takeIf { it.isNotBlank() }
            } else null
            result.add(
                RecentDay(
                    dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    dateStr = "%02d/%02d/%d".format(date.dayOfMonth, date.monthValue, date.year),
                    status = status,
                    dayOfMonth = day,
                    arrivalTime = arrivalTime
                )
            )
        }

        return result
    }
}
