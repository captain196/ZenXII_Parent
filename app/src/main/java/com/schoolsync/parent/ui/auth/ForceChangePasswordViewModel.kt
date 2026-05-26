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
 * Drives the forced password-change screen that appears immediately after
 * a parent's first login (admin-driven reset path). The screen blocks
 * navigation to the rest of the app until a new password is set.
 *
 * The flow calls the server endpoint `POST /auth/clear_must_change` —
 * the server uses Admin-SDK privileges to update Firebase Auth and clear
 * the `must_change_password` custom claim + Firestore profile field
 * atomically. No "current password" re-auth is needed because the user
 * just signed in with the temporary password seconds ago.
 *
 * For voluntary password changes from a Settings screen (future), use a
 * different ViewModel that calls AuthRepository.changePassword which goes
 * through Firebase client SDK with a recent-login re-auth.
 */
data class ForceChangePasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
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

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun toggleNewVisibility() {
        _uiState.update { it.copy(newVisible = !it.newVisible) }
    }

    fun toggleConfirmVisibility() {
        _uiState.update { it.copy(confirmVisible = !it.confirmVisible) }
    }

    fun submit() {
        val state = _uiState.value
        val errors = validate(state.newPassword, state.confirmPassword)
        if (errors != null) {
            _uiState.update { it.copy(errorMessage = errors) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val res = authRepository.clearMustChange(state.newPassword)) {
                is AuthResult.Success ->
                    _uiState.update { it.copy(isSubmitting = false, success = true) }
                is AuthResult.Error ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = res.message) }
            }
        }
    }

    private fun validate(new: String, confirm: String): String? {
        // Mirrors the server-side policy in Auth_api::clear_must_change.
        if (new.length < 8 || new.length > 72) {
            return "Password must be 8–72 characters."
        }
        if (!new.any { it.isUpperCase() }) return "Must include an uppercase letter."
        if (!new.any { it.isLowerCase() }) return "Must include a lowercase letter."
        if (!new.any { it.isDigit() })     return "Must include a digit."
        if (new != confirm)                return "Passwords do not match."
        // Block the auto-generated password format (e.g. "Sum1504@") from
        // being reused — the entire point of this screen is to replace it.
        if (Regex("^[A-Z][a-z]{2}\\d{4}@$").matches(new)) {
            return "Please choose a different password from the one sent to you."
        }
        return null
    }
}
