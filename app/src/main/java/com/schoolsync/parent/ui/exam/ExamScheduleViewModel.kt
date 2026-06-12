package com.schoolsync.parent.ui.exam

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One subject row in the exam datesheet. */
data class ExamScheduleEntry(
    val subjectName: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val maxTheory: Double,
    val maxPractical: Double,
    val maxTotal: Double,
    val room: String
)

data class ExamScheduleUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val examIds: List<String> = emptyList(),
    /** Display labels parallel to [examIds]. */
    val examNames: List<String> = emptyList(),
    val selectedExamIndex: Int = 0,
    val examSelectorExpanded: Boolean = false,
    val className: String = "",
    val section: String = "",
    val entries: List<ExamScheduleEntry> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class ExamScheduleViewModel @Inject constructor(
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamScheduleUiState())
    val uiState: StateFlow<ExamScheduleUiState> = _uiState.asStateFlow()

    init {
        loadExams()
    }

    /** Pull-to-refresh: reload exams + the current datesheet. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadExams(showSpinner = false)
            delay(400) // min spinner time for a smooth gesture
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadExams(showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showSpinner) _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            examFirestoreRepo.getAvailableExams().fold(
                onSuccess = { examDocs ->
                    // Bare examId for the schedule doc-id; examName for the label.
                    val examIds = examDocs.map { it.examId.ifBlank { it.id } }
                    val examNames = examDocs.map { it.examName.ifBlank { it.examId.ifBlank { it.id } } }
                    _uiState.update { it.copy(examIds = examIds, examNames = examNames) }
                    if (examIds.isNotEmpty()) {
                        loadSchedule(_uiState.value.selectedExamIndex.coerceIn(0, examIds.lastIndex))
                    } else {
                        _uiState.update { it.copy(isLoading = false, entries = emptyList()) }
                    }
                },
                onFailure = { e ->
                    Log.e("ExamScheduleVM", "Failed to load exams", e)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load exams")
                    }
                }
            )
        }
    }

    fun selectExam(index: Int) {
        _uiState.update { it.copy(selectedExamIndex = index, examSelectorExpanded = false) }
        loadSchedule(index)
    }

    fun toggleExamSelector() {
        _uiState.update { it.copy(examSelectorExpanded = !it.examSelectorExpanded) }
    }

    fun dismissExamSelector() {
        _uiState.update { it.copy(examSelectorExpanded = false) }
    }

    private fun loadSchedule(examIndex: Int) {
        val examId = _uiState.value.examIds.getOrNull(examIndex) ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val user = tokenManager.user.firstOrNull() ?: User.empty()
            if (user.className.isBlank() || user.section.isBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = emptyList(),
                        errorMessage = "Class/section not set for this student."
                    )
                }
                return@launch
            }

            examFirestoreRepo.getExamSchedule(examId, user.className, user.section).fold(
                onSuccess = { scheduleDoc ->
                    val entries = (scheduleDoc?.subjects ?: emptyList())
                        .map {
                            ExamScheduleEntry(
                                subjectName = it.subjectName,
                                date = it.date,
                                startTime = it.startTime,
                                endTime = it.endTime,
                                maxTheory = it.maxTheory,
                                maxPractical = it.maxPractical,
                                maxTotal = it.maxTotal,
                                room = it.room
                            )
                        }
                        // Chronological order; blank dates sink to the bottom.
                        .sortedWith(compareBy({ it.date.isBlank() }, { it.date }))
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            className = user.className,
                            section = user.section,
                            entries = entries
                        )
                    }
                },
                onFailure = { e ->
                    Log.e("ExamScheduleVM", "Failed to load exam schedule", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            entries = emptyList(),
                            errorMessage = e.message ?: "Failed to load exam schedule"
                        )
                    }
                }
            )
        }
    }
}
