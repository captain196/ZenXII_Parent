package com.schoolsync.parent.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.AttendanceRepository
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.repository.AuthResult
import com.schoolsync.parent.data.repository.StudentRepository
import com.schoolsync.parent.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val themeMode: String = "system",
    val attendancePercent: Float = 0f,

    // Homework stat pill — real pending count for the ward.
    // homeworkLoaded distinguishes "still loading" ("—") from a genuine
    // zero-pending ("All done") state.
    val pendingHomeworkCount: Int = 0,
    val homeworkLoaded: Boolean = false,

    // Class teacher contact card
    val classTeacher: MyTeachersFirestoreRepository.TeacherEntry? = null,
    val classTeacherSubjects: List<String> = emptyList(),
    // True until the first class-teacher fetch resolves — drives a skeleton
    // placeholder so the card doesn't pop in / shift the layout.
    val classTeacherLoading: Boolean = true,

    // Logout
    val showLogoutDialog: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutSuccess: Boolean = false,

    // Change password
    val showChangePassword: Boolean = false,
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isChangingPassword: Boolean = false,
    val passwordChangeSuccess: Boolean = false,

    // Appearance inline toggle
    val showAppearance: Boolean = false,

    // General
    val errorMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val authRepository: AuthRepository,
    private val attendanceRepository: AttendanceRepository,
    private val myTeachersRepo: MyTeachersFirestoreRepository,
    private val homeworkRepo: HomeworkFirestoreRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadTheme()
        loadAttendance()
        loadClassTeacher()
        loadHomework()
    }

    /**
     * Live pending-homework count for the ward — the same source and rule the
     * dashboard uses (a homework is "pending" until the student's submission is
     * submitted/reviewed/complete), so the profile stat never disagrees with
     * the dashboard.
     */
    private fun loadHomework() {
        viewModelScope.launch {
            val user = studentRepository.currentUser.firstOrNull()
            val className = user?.className
            val section = user?.section
            val studentId = user?.userId
            if (className.isNullOrBlank() || section.isNullOrBlank() || studentId.isNullOrBlank()) {
                _uiState.update { it.copy(pendingHomeworkCount = 0, homeworkLoaded = true) }
                return@launch
            }
            try {
                combine(
                    homeworkRepo.observeHomework(className, section),
                    homeworkRepo.observeSubmissionsForStudent(studentId)
                ) { homeworkDocs, submissionsByHwId ->
                    // FIX 2: shared isActionNeeded() predicate (pending OR
                    // incomplete) so this stat agrees with the nav badge/dashboard.
                    homeworkDocs.count { hw ->
                        val status = submissionsByHwId[hw.id]?.status ?: "pending"
                        com.schoolsync.parent.data.model.isActionNeeded(status)
                    }
                }.collect { pending ->
                    _uiState.update { it.copy(pendingHomeworkCount = pending, homeworkLoaded = true) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // normal on teardown
            } catch (e: Exception) {
                android.util.Log.w("ProfileVM", "Homework count load failed", e)
                _uiState.update { it.copy(homeworkLoaded = true) }
            }
        }
    }

    /**
     * Loads the parent's class teacher (the Active teacher flagged as class
     * teacher for the ward's section) plus every subject they deliver to that
     * section, mirroring the My Teachers aggregation. Null when none assigned.
     */
    private fun loadClassTeacher() {
        viewModelScope.launch {
            try {
                myTeachersRepo.getMyTeachers().fold(
                    onSuccess = { entries ->
                        val ct = entries.firstOrNull { it.assignment.isClassTeacher }
                        val subjects = if (ct != null) {
                            entries
                                .filter {
                                    it.assignment.teacherId.isNotBlank() &&
                                        it.assignment.teacherId == ct.assignment.teacherId
                                }
                                .map { it.assignment.subjectName.ifBlank { it.assignment.subjectCode } }
                                .filter { it.isNotBlank() }
                                .distinct()
                        } else emptyList()
                        _uiState.update {
                            it.copy(
                                classTeacher = ct,
                                classTeacherSubjects = subjects,
                                classTeacherLoading = false
                            )
                        }
                    },
                    onFailure = { e ->
                        android.util.Log.w("ProfileVM", "Class teacher load failed", e)
                        _uiState.update { it.copy(classTeacherLoading = false) }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.w("ProfileVM", "Class teacher load exception", e)
                _uiState.update { it.copy(classTeacherLoading = false) }
            }
        }
    }

    private fun loadTheme() {
        viewModelScope.launch {
            tokenManager.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    private fun loadAttendance() {
        viewModelScope.launch {
            try {
                val data = attendanceRepository.getCurrentMonthAttendance()
                val pct = data.attendancePercentage
                android.util.Log.w("ProfileVM", "loadAttendance: pct=$pct present=${data.presentCount} tardy=${data.countOf(com.schoolsync.parent.data.model.AttendanceStatus.TRIP)} working=${data.workingDays} total=${data.totalDays}")
                _uiState.update { it.copy(attendancePercent = pct) }
            } catch (e: Exception) {
                android.util.Log.e("ProfileVM", "loadAttendance FAILED", e)
                _uiState.update { it.copy(attendancePercent = 0f) }
            }
        }
    }

    /** Public so the screen can re-fetch on every visit. */
    fun refreshAttendance() = loadAttendance()

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            tokenManager.saveThemeMode(mode)
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val user = studentRepository.currentUser.firstOrNull()
            _uiState.update { it.copy(isLoading = false, user = user) }
        }
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    fun setShowLogoutDialog(show: Boolean) {
        _uiState.update { it.copy(showLogoutDialog = show) }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true) }

            val result = authRepository.logout()
            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(isLoggingOut = false, logoutSuccess = true, showLogoutDialog = false)
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(isLoggingOut = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    // ── Appearance toggle ───────────────────────────────────────────────────

    fun toggleAppearance() {
        _uiState.update { it.copy(showAppearance = !it.showAppearance) }
    }

    // ── Change password ─────────────────────────────────────────────────────

    fun toggleChangePassword() {
        _uiState.update {
            it.copy(
                showChangePassword = !it.showChangePassword,
                currentPassword = "",
                newPassword = "",
                confirmPassword = "",
                passwordChangeSuccess = false,
                errorMessage = null
            )
        }
    }

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, errorMessage = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun changePassword() {
        val state = _uiState.value

        if (state.currentPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter current password") }
            return
        }
        if (state.newPassword.length < 6) {
            _uiState.update { it.copy(errorMessage = "New password must be at least 6 characters") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isChangingPassword = true, errorMessage = null) }

            val result = authRepository.changePassword(state.currentPassword, state.newPassword)
            when (result) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            passwordChangeSuccess = true,
                            currentPassword = "",
                            newPassword = "",
                            confirmPassword = ""
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isChangingPassword = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
