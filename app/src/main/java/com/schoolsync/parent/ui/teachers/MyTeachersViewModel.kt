package com.schoolsync.parent.ui.teachers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository.TeacherEntry
import com.schoolsync.parent.util.ChatLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Aggregated row used by the My Teachers UI. One row per unique teacher,
 * combining all the subjects that teacher teaches to the parent's child's
 * class. The class teacher (if any) bubbles to the top.
 */
data class TeacherRow(
    val teacherId: String,
    val name: String,
    val phone: String,
    val email: String,
    val profilePic: String,
    val position: String,
    val department: String,
    val subjects: List<String>,
    val isClassTeacher: Boolean,
)

data class MyTeachersUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val teachers: List<TeacherRow> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MyTeachersViewModel @Inject constructor(
    private val repo: MyTeachersFirestoreRepository,
    private val chatLauncher: ChatLauncher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyTeachersUiState())
    val uiState: StateFlow<MyTeachersUiState> = _uiState.asStateFlow()

    init { load() }

    fun refresh() = load()

    /** Pull-to-refresh: reload teachers with min spinner time. */
    fun pullRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            try {
                repo.getMyTeachers().fold(
                    onSuccess = { entries ->
                        _uiState.update { it.copy(teachers = entries.toRows()) }
                    },
                    onFailure = { e ->
                        android.util.Log.w("MyTeachersVM", "pullRefresh failed", e)
                        _uiState.update { it.copy(error = e.message ?: "Failed to load teachers") }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("MyTeachersVM", "pullRefresh failed", e)
            }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Fire a "open chat" request through [ChatLauncher]. The Messages
     * ViewModel listens for these and bootstraps the conversation when the
     * user lands on the Messages tab. The caller is responsible for the
     * actual navigation (we don't own NavController in this VM).
     */
    fun requestMessageTeacher(row: TeacherRow) {
        if (row.teacherId.isBlank()) return
        chatLauncher.requestOpenChat(
            teacherId = row.teacherId,
            teacherName = row.name,
            teacherProfilePic = row.profilePic,
        )
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.getMyTeachers().fold(
                onSuccess = { entries ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            teachers = entries.toRows(),
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load teachers",
                        )
                    }
                }
            )
        }
    }

    /**
     * Collapse multiple subject-assignment rows into one row per teacher,
     * carrying the union of subjects and the highest-priority class-teacher
     * flag (any "true" wins).
     */
    private fun List<TeacherEntry>.toRows(): List<TeacherRow> {
        val byTeacher = LinkedHashMap<String, MutableList<TeacherEntry>>()
        for (e in this) {
            val key = e.assignment.teacherId.ifBlank { "_unknown_" }
            byTeacher.getOrPut(key) { mutableListOf() }.add(e)
        }
        return byTeacher.values.map { group ->
            val first = group.first()
            val staff = group.firstOrNull { it.staff != null }?.staff
            TeacherRow(
                teacherId = first.assignment.teacherId,
                name = staff?.name?.takeIf { it.isNotBlank() }
                    ?: first.assignment.teacherName.ifBlank { "Unknown teacher" },
                phone = staff?.phone.orEmpty(),
                email = staff?.email.orEmpty(),
                profilePic = staff?.profilePic.orEmpty(),
                position = staff?.designation?.takeIf { it.isNotBlank() } ?: staff?.position.orEmpty(),
                department = staff?.department.orEmpty(),
                subjects = group
                    .map { it.assignment.subjectName }
                    .filter { it.isNotBlank() }
                    .distinct(),
                isClassTeacher = group.any { it.assignment.isClassTeacher },
            )
        }.sortedWith(
            compareByDescending<TeacherRow> { it.isClassTeacher }
                .thenBy { it.name.lowercase() }
        )
    }
}
