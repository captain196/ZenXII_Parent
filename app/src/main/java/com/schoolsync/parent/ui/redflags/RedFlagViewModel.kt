package com.schoolsync.parent.ui.redflags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.StudentFlag
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.repository.RedFlagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RedFlagUiState(
    val isLoading: Boolean = true,
    val allFlags: List<StudentFlag> = emptyList(),
    val filteredFlags: List<StudentFlag> = emptyList(),
    val selectedFilter: String = "all", // all, homework, behavior, performance
    val badgeCount: Int = 0,
    val studentName: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class RedFlagViewModel @Inject constructor(
    private val redFlagRepository: RedFlagRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RedFlagUiState())
    val uiState: StateFlow<RedFlagUiState> = _uiState.asStateFlow()

    init {
        loadFlags()
    }

    fun loadFlags() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val user = tokenManager.user.firstOrNull() ?: User.empty()
            _uiState.update { it.copy(studentName = user.name) }

            try {
                val flags = redFlagRepository.getActiveFlags()
                val activeCount = flags.count { it.status == "active" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        allFlags = flags,
                        filteredFlags = flags,
                        badgeCount = activeCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load alerts"
                    )
                }
            }
        }
    }

    fun setFilter(filter: String) {
        _uiState.update { state ->
            val filtered = if (filter == "all") {
                state.allFlags
            } else {
                state.allFlags.filter { it.type == filter }
            }
            state.copy(
                selectedFilter = filter,
                filteredFlags = filtered
            )
        }
    }
}
