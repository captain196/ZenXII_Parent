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

    // ── Private ──────────────────────────────────────────────────────────

    private fun buildDayPills() {
        val today = LocalDate.now()
        val todayDow = today.dayOfWeek

        // Build pills for Mon-Sat of the current week
        val monday = today.minusDays((todayDow.value - DayOfWeek.MONDAY.value).toLong())
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

        // Default to today, or Monday if Sunday
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
                        _uiState.update { it.copy(weekData = timetable.days) }
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

    /** Parse "8:00" or "13:15" into LocalTime */
    private fun parseTime(str: String): LocalTime? {
        return try {
            val parts = str.split(":")
            if (parts.size >= 2) {
                LocalTime.of(parts[0].trim().toInt(), parts[1].trim().toInt())
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
