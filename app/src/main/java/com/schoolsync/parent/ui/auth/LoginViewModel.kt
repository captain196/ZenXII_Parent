package com.schoolsync.parent.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.DevPrefs
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val userId: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
    /** Phase A — true when the just-logged-in user has the
     *  `mustChangePassword` flag set on their students doc. The screen
     *  routes to a force-change-password destination instead of the
     *  normal Main destination when this is true. */
    val mustChangePassword: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context,
    /**
     * Exposed to the screen so the hidden Dev Settings dialog
     * (long-press the app title) can read/write the BASE_URL override
     * without going through a separate VM. Dev-only surface.
     */
    val devPrefs: DevPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val deviceId: String
        @SuppressLint("HardwareIds")
        get() = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    fun onUserIdChange(value: String) {
        _uiState.update { it.copy(userId = value.trim(), errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun login() {
        val state = _uiState.value

        if (state.userId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your Student ID") }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            when (val result = authRepository.login(state.userId, state.password, deviceId)) {
                is AuthResult.Success -> {
                    val mustChange = result.data.mustChangePassword
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginSuccess = true,
                            mustChangePassword = mustChange,
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
