package com.schoolsync.parent.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase A Part 2 — drives the forced password-change screen that
 * appears immediately after a parent's first login. The screen blocks
 * navigation to the rest of the app until a new password is set.
 *
 * Implementation note: we DO require the user to re-enter their current
 * password here, even though they just typed it to log in. Firebase Auth
 * needs a "recent login" window for password updates (about 5 min), and
 * reading network conditions, slow typing, or splash → force-change cold
 * starts can blow that window. The current password lets us re-auth
 * immediately before the update, which makes the change reliable.
 */
data class ForceChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val currentVisible: Boolean = false,
    val newVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class ForceChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForceChangePasswordUiState())
    val uiState: StateFlow<ForceChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, errorMessage = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun toggleCurrentVisibility() {
        _uiState.update { it.copy(currentVisible = !it.currentVisible) }
    }

    fun toggleNewVisibility() {
        _uiState.update { it.copy(newVisible = !it.newVisible) }
    }

    fun toggleConfirmVisibility() {
        _uiState.update { it.copy(confirmVisible = !it.confirmVisible) }
    }

    fun submit() {
        val state = _uiState.value
        val errors = validate(state.currentPassword, state.newPassword, state.confirmPassword)
        if (errors != null) {
            _uiState.update { it.copy(errorMessage = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            // Pass the typed currentPassword so AuthRepository.changePassword
            // re-authenticates with Firebase Auth (refreshing the recent-
            // login window) before calling updatePassword. Without this the
            // update fails with `requires-recent-login` after a few minutes.
            when (val res = authRepository.changePassword(state.currentPassword, state.newPassword)) {
                is AuthResult.Success ->
                    _uiState.update { it.copy(isSubmitting = false, success = true) }
                is AuthResult.Error ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
            }
        }
    }

    private fun validate(current: String, new: String, confirm: String): String? {
        if (current.isBlank()) return "Please enter your current password."
        if (new.length < 6) return "New password must be at least 6 characters."
        if (new == current) return "New password must be different from the current one."
        if (new != confirm) return "Passwords do not match."
        // Block the auto-generated password format (e.g. "Sum1504@") from
        // being reused — the entire point of this screen is to replace it.
        if (Regex("^[A-Z][a-z]{2}\\d{4}@$").matches(new)) {
            return "Please choose a different password from the one sent to you."
        }
        return null
    }
}
