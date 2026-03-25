package com.schoolsync.parent.ui.results

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.ExamResult
import com.schoolsync.parent.data.model.SubjectResult
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
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
    val examIds: List<String> = emptyList(),
    val selectedExamIndex: Int = 0,
    val examResult: ExamResult? = null,
    val examSelectorExpanded: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val examFirestoreRepo: ExamFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        loadExams()
    }

    private fun loadExams() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            examFirestoreRepo.getAvailableExams().fold(
                onSuccess = { examDocs ->
                    val examIds = examDocs.map { it.id }
                    _uiState.update { it.copy(examIds = examIds) }
                    if (examIds.isNotEmpty()) {
                        loadResult(0)
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
        val examId = _uiState.value.examIds.getOrNull(examIndex) ?: return

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
                                    marksObtained = subjectData.total,
                                    maxMarks = subjectData.maxMarks,
                                    grade = subjectData.grade
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
                                percentage = resultDoc.percentage,
                                grade = resultDoc.grade,
                                rank = resultDoc.rank,
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
