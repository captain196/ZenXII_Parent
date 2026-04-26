package com.schoolsync.parent.ui.leave

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
    val section: String = ""
)

@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val leaveRepository: LeaveFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaveUiState())
    val uiState: StateFlow<LeaveUiState> = _uiState.asStateFlow()

    private var studentId: String = ""

    init {
        loadStudentInfo()
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
    }

    fun updateLeaveType(type: String) {
        _uiState.update { it.copy(selectedLeaveType = type) }
    }

    fun updateStartDate(date: LocalDate) {
        _uiState.update {
            it.copy(startDate = date, endDate = if (it.endDate != null && it.endDate < date) date else it.endDate)
        }
    }

    fun updateEndDate(date: LocalDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    fun updateReason(reason: String) {
        _uiState.update { it.copy(reason = reason) }
    }

    fun submitLeave() {
        val state = _uiState.value
        val start = state.startDate ?: run {
            _uiState.update { it.copy(errorMessage = "Please select start date") }
            return
        }
        val end = state.endDate ?: run {
            _uiState.update { it.copy(errorMessage = "Please select end date") }
            return
        }
        // Phase 9b: date validation
        val today = LocalDate.now()
        if (start.isBefore(today)) {
            _uiState.update { it.copy(errorMessage = "Start date cannot be in the past") }
            return
        }
        if (end.isBefore(start)) {
            _uiState.update { it.copy(errorMessage = "End date must be on or after start date") }
            return
        }
        if (state.reason.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a reason") }
            return
        }

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val numberOfDays = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            leaveRepository.submitLeave(
                studentId = studentId,
                studentName = state.studentName,
                className = state.className,
                section = state.section,
                leaveType = state.selectedLeaveType,
                startDate = start.format(formatter),
                endDate = end.format(formatter),
                numberOfDays = numberOfDays,
                reason = state.reason
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitSuccess = true,
                        showApplyForm = false
                    )
                }
                loadLeaveHistory()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = err.message)
                }
            }
        }
    }

    fun cancelLeave(leaveId: String) {
        viewModelScope.launch {
            leaveRepository.cancelLeave(leaveId)
                .onSuccess { loadLeaveHistory() }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message) }
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
