package com.schoolsync.parent.ui.lessons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.firestore.LessonPlanDoc
import com.schoolsync.parent.data.model.firestore.SubjectPlanProgressDoc
import com.schoolsync.parent.data.repository.firestore.LessonPlanParentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class MyLessonsUiState(
    val date: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
    val dayLabel: String = SimpleDateFormat("EEEE, d MMM", Locale.US).format(Date()),
    val className: String = "",
    val section: String = "",
    val lessons: List<LessonPlanDoc> = emptyList(),
    val progress: List<SubjectPlanProgressDoc> = emptyList(),
    val isLoadingLessons: Boolean = true,
    val isLoadingProgress: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MyLessonsViewModel @Inject constructor(
    private val repo: LessonPlanParentRepository,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(MyLessonsUiState())
    val ui: StateFlow<MyLessonsUiState> = _ui.asStateFlow()

    init { load() }

    /** Refresh both daily lessons + subject progress for the current date. */
    fun load() {
        viewModelScope.launch {
            val u = tokenManager.user.firstOrNull()
            val cls = u?.className.orEmpty()
            val sec = u?.section.orEmpty()
            _ui.update { it.copy(
                className = cls, section = sec,
                isLoadingLessons = true, isLoadingProgress = true, error = null
            ) }

            // Daily lessons
            repo.getDailyLessons(cls, sec, _ui.value.date).fold(
                onSuccess = { rows ->
                    _ui.update { it.copy(lessons = rows, isLoadingLessons = false) }
                },
                onFailure = { e ->
                    _ui.update { it.copy(
                        lessons = emptyList(), isLoadingLessons = false,
                        error = e.message ?: "Failed to load lessons"
                    ) }
                }
            )

            // Subject progress (parallel-friendly but small N — sequential is fine)
            repo.getSubjectProgress(cls, sec).fold(
                onSuccess = { rows ->
                    _ui.update { it.copy(progress = rows, isLoadingProgress = false) }
                },
                onFailure = {
                    _ui.update { it.copy(progress = emptyList(), isLoadingProgress = false) }
                }
            )
        }
    }

    fun setDate(iso: String) {
        if (iso == _ui.value.date) return
        val pretty = try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso) ?: Date()
            SimpleDateFormat("EEEE, d MMM", Locale.US).format(d)
        } catch (_: Exception) { iso }
        _ui.update { it.copy(date = iso, dayLabel = pretty) }
        load()
    }
}
