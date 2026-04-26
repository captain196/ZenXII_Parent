package com.schoolsync.parent.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.DayTimetable
import com.schoolsync.parent.data.model.TimetableSlot
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.TimetableFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

// ─── View mode ───────────────────────────────────────────────────────────────

enum class TimetableViewMode { DAY, WEEK }

// ─── Day pill model ──────────────────────────────────────────────────────────

data class DayPill(
    val dayName: String,          // "Monday"
    val abbreviation: String,     // "Mon"
    val dateNumber: Int,          // 23
    val isToday: Boolean
)

// ─── UI state ────────────────────────────────────────────────────────────────

data class TimetableUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val viewMode: TimetableViewMode = TimetableViewMode.DAY,
    val dayPills: List<DayPill> = emptyList(),
    val selectedDayIndex: Int = 0,
    val slots: List<TimetableSlot> = emptyList(),
    val weekData: Map<String, DayTimetable> = emptyMap(),
    val currentSlotIndex: Int = -1,       // index in slots list
    val nextSlotIndex: Int = -1,          // index of next upcoming class
    val completedCount: Int = 0,          // classes done today
    val totalClassCount: Int = 0,         // total non-break classes today
    val selectedDetail: TimetableSlot? = null,  // detail page
    val selectedDetailDay: String = "",
    val errorMessage: String? = null
) {
    val selectedDay: DayPill?
        get() = dayPills.getOrNull(selectedDayIndex)

    val isSelectedDayToday: Boolean
        get() = selectedDay?.isToday == true

    val currentSlot: TimetableSlot?
        get() = if (currentSlotIndex in slots.indices) slots[currentSlotIndex] else null

    val progressFraction: Float
        get() = if (totalClassCount > 0) completedCount.toFloat() / totalClassCount else 0f
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableFirestoreRepo: TimetableFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        buildDayPills()
        loadTimetable()
    }

    // ── Public actions ───────────────────────────────────────────────────

    fun selectDay(index: Int) {
        _uiState.update { it.copy(selectedDayIndex = index, selectedDetail = null) }
        loadDayData()
    }

    fun switchToDay(dayName: String) {
        val idx = _uiState.value.dayPills.indexOfFirst { it.dayName == dayName }
        if (idx >= 0) {
            _uiState.update { it.copy(viewMode = TimetableViewMode.DAY, selectedDayIndex = idx) }
            loadDayData()
        }
    }

    fun setViewMode(mode: TimetableViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun openDetail(slot: TimetableSlot) {
        if (slot.isBreak) return
        val dayName = _uiState.value.selectedDay?.dayName ?: ""
        _uiState.update { it.copy(selectedDetail = slot, selectedDetailDay = dayName) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(selectedDetail = null) }
    }

    fun refresh() {
        loadTimetable(forceRefresh = true)
    }

    /** Pull-to-refresh: reload timetable with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                loadTimetable(forceRefresh = true)
            } catch (e: Exception) {
                android.util.Log.w("TimetableVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── Private ──────────────────────────────────────────────────────────

    private fun buildDayPills() {
        val today = LocalDate.now()
        val todayDow = today.dayOfWeek

        // If today is Sunday, show NEXT week's Mon-Sat (upcoming schedule)
        // Otherwise show current week's Mon-Sat
        val monday = if (todayDow == DayOfWeek.SUNDAY) {
            today.plusDays(1) // tomorrow is Monday of next week
        } else {
            today.minusDays((todayDow.value - DayOfWeek.MONDAY.value).toLong())
        }

        val days = DayTimetable.DAYS_OF_WEEK
        val pills = days.mapIndexed { index, dayName ->
            val date = monday.plusDays(index.toLong())
            DayPill(
                dayName = dayName,
                abbreviation = DayTimetable.DAY_ABBREVIATIONS[dayName] ?: dayName.take(3),
                dateNumber = date.dayOfMonth,
                isToday = date == today
            )
        }

        // Default to today's day if found, otherwise Monday (first day)
        val selectedIndex = pills.indexOfFirst { it.isToday }.let { if (it < 0) 0 else it }

        _uiState.update { it.copy(dayPills = pills, selectedDayIndex = selectedIndex) }
    }

    private fun loadTimetable(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val user = tokenManager.user.firstOrNull() ?: User.empty()
                if (user.className.isBlank() || user.section.isBlank()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Class info not available")
                    }
                    return@launch
                }

                timetableFirestoreRepo.getTimetable(user.className, user.section).fold(
                    onSuccess = { timetable ->
                        // Inject synthetic break slots into each day so both the
                        // Day view AND the Week grid show lunch/break rows derived
                        // from time gaps. The repository data itself is untouched.
                        val augmentedDays = timetable.days.mapValues { (_, dt) ->
                            dt.copy(slots = injectBreakGaps(dt.slots))
                        }
                        _uiState.update { it.copy(weekData = augmentedDays) }
                        loadDayData()
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Failed to load timetable"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load timetable"
                    )
                }
            }
        }
    }

    private fun loadDayData() {
        val state = _uiState.value
        val pill = state.dayPills.getOrNull(state.selectedDayIndex) ?: return
        val dayTimetable = state.weekData[pill.dayName]
        // weekData has already been augmented with synthetic breaks in loadTimetable;
        // just consume it here.
        val slots = dayTimetable?.slots ?: emptyList()

        val classSlots = slots.filter { !it.isBreak }
        val now = LocalTime.now()
        val isToday = pill.isToday

        var currentIdx = -1
        var nextIdx = -1
        var completedCount = 0

        if (isToday) {
            slots.forEachIndexed { index, slot ->
                if (slot.isBreak) return@forEachIndexed
                val times = parseStartEndTime(slot.time)
                if (times != null) {
                    val (start, end) = times
                    when {
                        now in start..end -> currentIdx = index
                        now.isAfter(end) -> completedCount++
                        nextIdx == -1 && now.isBefore(start) -> nextIdx = index
                    }
                }
            }
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                slots = slots,
                currentSlotIndex = currentIdx,
                nextSlotIndex = nextIdx,
                completedCount = completedCount,
                totalClassCount = classSlots.size
            )
        }
    }

    /** Parse "8:00 - 8:45" into Pair(LocalTime, LocalTime) */
    private fun parseStartEndTime(time: String): Pair<LocalTime, LocalTime>? {
        return try {
            val parts = time.split("-")
            if (parts.size != 2) return null
            val start = parseTime(parts[0].trim())
            val end = parseTime(parts[1].trim())
            if (start != null && end != null) Pair(start, end) else null
        } catch (_: Exception) {
            null
        }
    }

    /** Parse "8:00", "13:15", "8:00AM", "8:00 AM", "1:10PM" into LocalTime */
    private fun parseTime(str: String): LocalTime? {
        return try {
            val raw = str.trim().uppercase()
            val isPm = raw.endsWith("PM")
            val isAm = raw.endsWith("AM")
            val body = if (isPm || isAm) raw.dropLast(2).trim() else raw
            val parts = body.split(":")
            if (parts.size < 2) return null
            var h = parts[0].trim().toInt()
            val m = parts[1].trim().toInt()
            if (isPm && h != 12) h += 12
            if (isAm && h == 12) h = 0
            LocalTime.of(h, m)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Synthesize break slots in the gaps between consecutive class periods.
     * Admin UI currently doesn't persist break/lunch periods to Firestore
     * (see `section_students.php:1478`). Until that's fixed, any gap of
     * >= 10 minutes between periods is rendered as a break — the midday
     * gap typically being lunch. Explicit `isBreak` rows from Firestore
     * still take precedence and are preserved as-is.
     */
    private fun injectBreakGaps(slots: List<TimetableSlot>): List<TimetableSlot> {
        if (slots.isEmpty()) return slots
        val result = mutableListOf<TimetableSlot>()
        val minGapMinutes = 10

        for ((i, slot) in slots.withIndex()) {
            result.add(slot)
            if (i == slots.lastIndex) break
            val next = slots[i + 1]
            // Skip if either side is already an explicit break — no double-injection.
            if (slot.isBreak || next.isBreak) continue

            val thisEnd   = parseTime(slot.time.split("-").getOrNull(1)?.trim().orEmpty())
            val nextStart = parseTime(next.time.split("-").firstOrNull()?.trim().orEmpty())
            if (thisEnd == null || nextStart == null) continue

            val gapMin = java.time.Duration.between(thisEnd, nextStart).toMinutes().toInt()
            if (gapMin < minGapMinutes) continue

            // Lunch heuristic: gap >= 20 min AND crosses 12pm, or gap >= 25 min.
            val crossesNoon = thisEnd.isBefore(LocalTime.NOON) && nextStart.isAfter(LocalTime.of(11, 30))
            val isLunchLike = gapMin >= 25 || (gapMin >= 20 && crossesNoon)
            val label = if (isLunchLike) "Lunch" else "Break"

            val timeLabel = "${formatTimeLabel(thisEnd)} - ${formatTimeLabel(nextStart)}"
            result.add(
                TimetableSlot(
                    periodKey = "__gap_${i}_${label}",
                    time = timeLabel,
                    subject = label,
                    isBreak = true
                )
            )
        }
        return result
    }

    private fun formatTimeLabel(t: LocalTime): String {
        val hour12 = if (t.hour == 0) 12 else if (t.hour > 12) t.hour - 12 else t.hour
        val mer = if (t.hour < 12) "AM" else "PM"
        val mm = t.minute.toString().padStart(2, '0')
        return "$hour12:$mm$mer"
    }
}
