package com.schoolsync.parent.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.AttendanceRepository
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.repository.AuthResult
import com.schoolsync.parent.data.repository.StudentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val user: User? = null,
    val themeMode: String = "system",
    val attendancePercent: Float = 0f,

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
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadTheme()
        loadAttendance()
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
                _uiState.update { it.copy(attendancePercent = data.attendancePercentage) }
            } catch (_: Exception) {
                _uiState.update { it.copy(attendancePercent = 0f) }
            }
        }
    }

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
