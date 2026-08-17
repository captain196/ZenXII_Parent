package com.schoolsync.parent.ui.leave

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.LeaveApplicationDoc
import com.schoolsync.parent.data.repository.firestore.LeaveFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class LeaveUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val leaveHistory: List<LeaveApplicationDoc> = emptyList(),
    val showApplyForm: Boolean = false,
    val selectedLeaveType: String = "CL",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val reason: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val errorMessage: String? = null,
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    /** id of the leave whose cancel request is currently in flight (null = none). */
    val cancellingId: String? = null
)

/** Spec: student leave span capped at 60 days inclusive. */
private const val MAX_STUDENT_LEAVE_DAYS = 60L

/** PAR-M1: cap the wait on an offline-first Firestore write before we
 *  surface it as a durable (locally-queued) success. */
private const val WRITE_TIMEOUT_MS = 6_000L

// PAR-M2: SavedStateHandle keys for the in-progress leave draft.
private const val KEY_TYPE = "leave_draft_type"
private const val KEY_REASON = "leave_draft_reason"
private const val KEY_START = "leave_draft_start"
private const val KEY_END = "leave_draft_end"
private const val KEY_SHOW = "leave_draft_show"

@HiltViewModel
class LeaveViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val leaveRepository: LeaveFirestoreRepository,
    private val tokenManager: TokenManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveUiState())
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    private var studentId: String = ""

    init {
        restoreDraft()   // PAR-M2: recover an in-progress leave draft on process death
        loadStudentInfo()
    }

    // ── PAR-M2: leave-draft persistence across process death ────────────────
    private fun restoreDraft() {
        val start = savedStateHandle.get<Long>(KEY_START)
            ?.takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) }
        val end = savedStateHandle.get<Long>(KEY_END)
            ?.takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) }
        _uiState.update {
            it.copy(
                selectedLeaveType = savedStateHandle.get<String>(KEY_TYPE) ?: it.selectedLeaveType,
                reason = savedStateHandle.get<String>(KEY_REASON) ?: it.reason,
                startDate = start,
                endDate = end,
                showApplyForm = savedStateHandle.get<Boolean>(KEY_SHOW) ?: it.showApplyForm
            )
        }
    }

    private fun persistDraft() {
        val s = _uiState.value
        savedStateHandle[KEY_TYPE] = s.selectedLeaveType
        savedStateHandle[KEY_REASON] = s.reason
        savedStateHandle[KEY_START] = s.startDate?.toEpochDay() ?: -1L
        savedStateHandle[KEY_END] = s.endDate?.toEpochDay() ?: -1L
        savedStateHandle[KEY_SHOW] = s.showApplyForm
    }

    private fun clearDraft() {
        savedStateHandle[KEY_TYPE] = "CL"
        savedStateHandle[KEY_REASON] = ""
        savedStateHandle[KEY_START] = -1L
        savedStateHandle[KEY_END] = -1L
        savedStateHandle[KEY_SHOW] = false
    }

    private fun loadStudentInfo() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull()
            studentId = user?.userId ?: ""
            val name = user?.name ?: ""
            val cls = user?.className ?: ""
            val sec = user?.section ?: ""

            _uiState.update {
                it.copy(studentName = name, className = cls, section = sec)
            }
            loadLeaveHistory()
        }
    }

    fun loadLeaveHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            leaveRepository.getLeaveHistory(studentId)
                .onSuccess { leaves ->
                    _uiState.update { it.copy(isLoading = false, leaveHistory = leaves) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message)
                    }
                }
        }
    }

    /** Pull-to-refresh: reload leave history with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                loadLeaveHistory()
            } catch (e: Exception) {
                android.util.Log.w("LeaveVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun toggleApplyForm() {
        _uiState.update {
            it.copy(
                showApplyForm = !it.showApplyForm,
                startDate = null,
                endDate = null,
                reason = "",
                selectedLeaveType = "CL",
                submitSuccess = false,
                errorMessage = null
            )
        }
        persistDraft()
    }

    fun updateLeaveType(type: String) {
        _uiState.update { it.copy(selectedLeaveType = type) }
        persistDraft()
    }

    fun updateStartDate(date: LocalDate) {
        _uiState.update {
            it.copy(startDate = date, endDate = if (it.endDate != null && it.endDate < date) date else it.endDate)
        }
        persistDraft()
    }

    fun updateEndDate(date: LocalDate) {
        // Fix (MEDIUM): clamp End >= Start (Start already clamps End forward).
        _uiState.update {
            val start = it.startDate
            it.copy(endDate = if (start != null && date < start) start else date)
        }
        persistDraft()
    }

    fun updateReason(reason: String) {
        _uiState.update { it.copy(reason = reason) }
        persistDraft()
    }

    fun submitLeave() {
        val state = _uiState.value
        // Fix (double-submit race): synchronous in-flight guard BEFORE launch.
        // Two fast taps otherwise both pass validation and create duplicate
        // pending leaves (millis-based ids never collide).
        if (state.isSubmitting) return

        val start = state.startDate ?: run {
            _uiState.update { it.copy(errorMessage = appContext.getString(R.string.leave_select_start)) }
            return
        }
        val end = state.endDate ?: run {
            _uiState.update { it.copy(errorMessage = appContext.getString(R.string.leave_select_end)) }
            return
        }
        // Phase 9b: date validation
        val today = LocalDate.now()
        if (start.isBefore(today)) {
            _uiState.update { it.copy(errorMessage = appContext.getString(R.string.leave_start_in_past)) }
            return
        }
        if (end.isBefore(start)) {
            _uiState.update { it.copy(errorMessage = appContext.getString(R.string.leave_end_before_start)) }
            return
        }
        // Fix (validation): cap span at 60 inclusive days (spec §4, student).
        val spanDays = ChronoUnit.DAYS.between(start, end) + 1
        if (spanDays > MAX_STUDENT_LEAVE_DAYS) {
            _uiState.update { it.copy(errorMessage = appContext.resources.getQuantityString(
                R.plurals.leave_max_days_fmt,
                MAX_STUDENT_LEAVE_DAYS.toInt(), MAX_STUDENT_LEAVE_DAYS.toInt())) }
            return
        }
        val reason = state.reason.trim()
        if (reason.isBlank()) {
            _uiState.update { it.copy(errorMessage = appContext.getString(R.string.leave_enter_reason)) }
            return
        }
        // Fix (validation): overlap guard against existing pending/approved leaves.
        if (overlapsExisting(state.leaveHistory, start, end)) {
            _uiState.update { it.copy(errorMessage = appContext.getString(R.string.leave_overlap)) }
            return
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val numberOfDays = spanDays.toInt()

        // Set the guard SYNCHRONOUSLY so a second tap early-returns above.
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            // PAR-M1: the offline-first Firestore SDK never server-acks a write
            // while offline, so awaiting it hangs the spinner forever. Race the
            // write against a timeout — local writes are durable, so a timeout is
            // treated as success ("will sync when back online").
            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                leaveRepository.submitLeave(
                    studentId = studentId,
                    studentName = state.studentName,
                    className = state.className,
                    section = state.section,
                    leaveType = state.selectedLeaveType,
                    startDate = start.format(formatter),
                    endDate = end.format(formatter),
                    numberOfDays = numberOfDays,
                    reason = reason
                )
            }

            when {
                result == null || result.isSuccess -> {
                    // Server-acked OR queued offline — both are a durable success.
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            submitSuccess = true,
                            showApplyForm = false
                        )
                    }
                    clearDraft()  // PAR-M2: draft consumed
                    // Fix (LOW): non-blocking refresh instead of full-screen spinner.
                    refreshHistory()
                }
                else -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, errorMessage = result.exceptionOrNull()?.message)
                    }
                }
            }
        }
    }

    /**
     * Overlap guard: true if [start]..[end] intersects any existing
     * pending/approved leave (case-insensitive status, per spec).
     */
    private fun overlapsExisting(
        history: List<LeaveApplicationDoc>,
        start: LocalDate,
        end: LocalDate
    ): Boolean {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return history.any { leave ->
            val active = leave.status.equals("pending", ignoreCase = true) ||
                leave.status.equals("approved", ignoreCase = true)
            if (!active) return@any false
            val s = runCatching { LocalDate.parse(leave.startDate, fmt) }.getOrNull()
            val e = runCatching { LocalDate.parse(leave.endDate, fmt) }.getOrNull()
            if (s == null || e == null) return@any false
            // ranges overlap iff start <= existingEnd AND existingStart <= end
            !start.isAfter(e) && !s.isAfter(end)
        }
    }

    /** Reload history without flipping the blocking full-screen spinner. */
    private fun refreshHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            leaveRepository.getLeaveHistory(studentId)
                .onSuccess { leaves ->
                    _uiState.update { it.copy(isRefreshing = false, leaveHistory = leaves) }
                }
                .onFailure {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
        }
    }

    fun cancelLeave(leaveId: String) {
        // Fix (LOW): in-flight guard so a double-tap can't fire two cancels.
        if (_uiState.value.cancellingId != null) return
        _uiState.update { it.copy(cancellingId = leaveId) }
        viewModelScope.launch {
            // PAR-M1: same offline-first hang — race the write against a timeout
            // so the per-row spinner always resolves. Local delete is durable.
            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                leaveRepository.cancelLeave(leaveId)
            }
            when {
                result == null || result.isSuccess -> {
                    _uiState.update { it.copy(cancellingId = null) }
                    refreshHistory()
                }
                else -> {
                    _uiState.update {
                        it.copy(cancellingId = null, errorMessage = result.exceptionOrNull()?.message)
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }
}
