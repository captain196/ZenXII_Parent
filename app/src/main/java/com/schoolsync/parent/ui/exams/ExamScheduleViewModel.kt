package com.schoolsync.parent.ui.exams

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.ExamSubjectScheduleDoc
import com.schoolsync.parent.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.parent.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamScheduleUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val examName: String = "",
    val subjects: List<ExamSubjectScheduleDoc> = emptyList(),
    /** True when there are simply no exams to show a schedule for. */
    val noExam: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Backs the Exam Schedule (date-sheet) screen. Resolves the student's
 * class/section from the cached [User] and reads the per-class-section
 * [com.schoolsync.parent.data.model.firestore.ExamScheduleDoc] for either the
 * deep-linked exam or (as a fallback) the nearest available exam.
 */
@HiltViewModel
class ExamScheduleViewModel @Inject constructor(
    
    @ApplicationContext private val appContext: Context,private val examFirestoreRepo: ExamFirestoreRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamScheduleUiState())
    val uiState: StateFlow<ExamScheduleUiState> = _uiState.asStateFlow()

    private val argExamId: String =
        savedStateHandle.get<String>(Route.Exams.ARG_EXAM_ID).orEmpty()

    init {
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadInternal()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            loadInternal()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadInternal() {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (user.className.isBlank() || user.section.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = appContext.getString(R.string.exam_no_class_section))
            }
            return
        }

        // Resolve which exam's schedule to show: the deep-linked one, else the
        // first available exam. We fetch the exam list either way so we can
        // display a human-readable exam name.
        val exams = examFirestoreRepo.getAvailableExams().getOrElse { e ->
            Log.e("ExamScheduleVM", appContext.getString(R.string.res_exams_load_failed), e)
            _uiState.update { it.copy(errorMessage = e.message ?: appContext.getString(R.string.res_exams_load_failed)) }
            return
        }

        val exam = when {
            argExamId.isNotBlank() -> exams.firstOrNull { it.id == argExamId }
            else -> exams.firstOrNull()
        }

        if (exam == null && exams.isEmpty()) {
            _uiState.update { it.copy(noExam = true, subjects = emptyList(), examName = "") }
            return
        }

        // If a deep-linked examId wasn't in the list, still try to read its
        // schedule directly (name falls back to the id).
        val examId = exam?.id ?: argExamId
        val examName = exam?.examName?.ifBlank { exam.id } ?: argExamId

        examFirestoreRepo.getExamSchedule(examId, user.className, user.section).fold(
            onSuccess = { doc ->
                _uiState.update {
                    it.copy(
                        examName = examName,
                        subjects = doc?.subjects.orEmpty(),
                        noExam = false,
                        errorMessage = null
                    )
                }
            },
            onFailure = { e ->
                Log.e("ExamScheduleVM", appContext.getString(R.string.exam_schedule_load_failed), e)
                _uiState.update { it.copy(errorMessage = e.message ?: appContext.getString(R.string.exam_schedule_load_failed)) }
            }
        )
    }
}
