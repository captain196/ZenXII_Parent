package com.schoolsync.parent.ui.results

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.ExamResult
import com.schoolsync.parent.data.model.SubjectResult
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.ExamDoc
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    // MED-4: keep the full exam docs (id + name) so the selector can render
    // human-readable names instead of raw internal IDs.
    val exams: List<ExamDoc> = emptyList(),
    val selectedExamIndex: Int = 0,
    val examResult: ExamResult? = null,
    val examSelectorExpanded: Boolean = false,
    /** Outstanding fees on the active student. >0 → show
     *  the fee-blocked banner so the parent knows results MAY
     *  be withheld depending on the school's policy. */
    val pendingFees: Double = 0.0,
    val errorMessage: String? = null
) {
    val selectedExam: ExamDoc? get() = exams.getOrNull(selectedExamIndex)
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    // LOW-7: optional deep-link target exam (result_published push). When set,
    // the matching exam is preselected once the list loads instead of index 0.
    private val targetExamId: String =
        savedStateHandle.get<String>(Route.Results.ARG_EXAM_ID).orEmpty()

    init {
        loadExams()
        loadDuesSnapshot()
    }

    /** Pull-to-refresh: reload exams + dues with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                loadExams()
                loadDuesSnapshot()
            } catch (e: Exception) {
                Log.w("ResultsVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** Lightweight one-shot dues fetch so the screen can display the
     *  fee-blocked warning banner. We don't gate the UI here — the
     *  server is the source of truth for actual blocking; this is
     *  just an early warning to the parent. */
    private fun loadDuesSnapshot() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            if (user.userId.isBlank()) return@launch
            try {
                val demands = feeFirestoreRepo.getPendingDemands(user.userId).getOrNull().orEmpty()
                val pending = demands.sumOf { (it.netAmount - it.paidAmount).coerceAtLeast(0.0) }
                _uiState.update { it.copy(pendingFees = pending) }
            } catch (e: Exception) {
                Log.w("ResultsVM", "Dues snapshot failed", e)
            }
        }
    }

    private fun loadExams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            examFirestoreRepo.getAvailableExams().fold(
                onSuccess = { examDocs ->
                    // LOW-7: if a deep link named a specific exam, open it;
                    // otherwise default to the first exam in the list.
                    val targetIndex = examDocs.indexOfFirst { it.id == targetExamId }
                        .takeIf { it >= 0 } ?: 0
                    _uiState.update { it.copy(exams = examDocs, selectedExamIndex = targetIndex) }
                    if (examDocs.isNotEmpty()) {
                        loadResult(targetIndex)
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                },
                onFailure = { e ->
                    Log.e("ResultsVM", "Failed to load exams", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load exams"
                        )
                    }
                }
            )
        }
    }

    fun selectExam(index: Int) {
        _uiState.update { it.copy(selectedExamIndex = index, examSelectorExpanded = false) }
        loadResult(index)
    }

    fun toggleExamSelector() {
        _uiState.update { it.copy(examSelectorExpanded = !it.examSelectorExpanded) }
    }

    fun dismissExamSelector() {
        _uiState.update { it.copy(examSelectorExpanded = false) }
    }

    private fun loadResult(examIndex: Int) {
        val exam = _uiState.value.exams.getOrNull(examIndex) ?: return
        val examId = exam.id

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val studentId = user.userId

            if (studentId.isNotBlank()) {
                examFirestoreRepo.getResult(examId, studentId).fold(
                    onSuccess = { resultDoc ->
                        if (resultDoc != null) {
                            val subjects = resultDoc.subjects.map { (subjectName, subjectData) ->
                                SubjectResult(
                                    subjectName = subjectName,
                                    // MED-3: preserve null (absent) rather than coerce to 0.
                                    marksObtained = subjectData.total,
                                    maxMarks = subjectData.maxMarks,
                                    grade = subjectData.grade,
                                    passFail = subjectData.passFail,
                                    absent = subjectData.absent
                                )
                            }
                            val examResult = ExamResult(
                                examId = resultDoc.examId,
                                examName = resultDoc.examName,
                                studentId = resultDoc.studentId,
                                className = resultDoc.className,
                                section = resultDoc.section,
                                session = resultDoc.session,
                                subjects = subjects,
                                totalMarks = resultDoc.totalMarks,
                                maxMarks = resultDoc.maxMarks,
                                // MED-3: null percentage preserved for absentees.
                                percentage = resultDoc.percentage,
                                grade = resultDoc.grade,
                                rank = resultDoc.rank,
                                // HIGH-2: carry the authoritative pass/fail + the
                                // exam's passing threshold (prefer the exam doc,
                                // fall back to the value echoed on the result).
                                passFail = resultDoc.passFail,
                                absent = resultDoc.absent,
                                passingPercent = exam.passingPercent
                                    .takeIf { it > 0 } ?: resultDoc.passingPercent,
                                remarks = resultDoc.passFail
                            )
                            _uiState.update {
                                it.copy(isLoading = false, examResult = examResult)
                            }
                        } else {
                            _uiState.update {
                                it.copy(isLoading = false, examResult = null)
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e("ResultsVM", "Failed to load result", e)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Failed to load results"
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
}
