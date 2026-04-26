package com.schoolsync.parent.ui.redflags

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.StudentFlag
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.repository.RedFlagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RedFlagUiState(
    val isLoading: Boolean = true,
    /** All flags (active + resolved) returned by the live listener. */
    val allFlags: List<StudentFlag> = emptyList(),
    /** Active flags only, after type filter is applied. */
    val activeFlags: List<StudentFlag> = emptyList(),
    /** Resolved flags only, after type filter is applied. */
    val resolvedFlags: List<StudentFlag> = emptyList(),
    val selectedFilter: String = "all",  // all, homework, behavior, performance
    /** Badge count of unfiltered active flags only (the user-facing "open issues"). */
    val badgeCount: Int = 0,
    val studentName: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class RedFlagViewModel @Inject constructor(
    private val redFlagRepository: RedFlagRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object { private const val TAG = "RedFlagVM" }

    private val _uiState = MutableStateFlow(RedFlagUiState())
    val uiState: StateFlow<RedFlagUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { redFlagRepository.dumpAuthClaimsForDebug() }
        loadInitial()
        observeFlags()
    }

    /**
     * One-shot load that flips isLoading off as soon as the first batch
     * (or empty) comes back. Without this, isLoading would stay true
     * forever if the live listener never fires (e.g. observe path errors
     * before its first emission). The live observer below then takes over
     * for ongoing updates.
     */
    private fun loadInitial() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull()
            if (user != null) {
                _uiState.update { it.copy(studentName = user.name) }
            }

            try {
                val flags = redFlagRepository.getAllFlags()
                applyFlags(flags)
            } catch (e: Exception) {
                Log.e(TAG, "Initial load failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load red flags"
                    )
                }
            }
        }
    }

    /**
     * Live snapshot listener. Emits whenever a teacher creates a flag or
     * an admin resolves one — no manual refresh required. Errors surface
     * via .catch (FirestoreService.observeQuery now uses close(error) so
     * permission-denied / missing-index errors actually propagate).
     */
    private fun observeFlags() {
        viewModelScope.launch {
            redFlagRepository.observeFlags()
                .catch { e ->
                    Log.e(TAG, "Live listener error", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Live updates unavailable"
                        )
                    }
                }
                .collect { flags -> applyFlags(flags) }
        }
    }

    private fun applyFlags(flags: List<StudentFlag>) {
        _uiState.update { state ->
            val (active, resolved) = partitionByStatus(flags, state.selectedFilter)
            state.copy(
                isLoading      = false,
                allFlags       = flags,
                activeFlags    = active,
                resolvedFlags  = resolved,
                badgeCount     = flags.count { it.status == "active" },
                errorMessage   = null
            )
        }
    }

    fun setFilter(filter: String) {
        _uiState.update { state ->
            val (active, resolved) = partitionByStatus(state.allFlags, filter)
            state.copy(
                selectedFilter = filter,
                activeFlags    = active,
                resolvedFlags  = resolved
            )
        }
    }

    private fun partitionByStatus(
        flags: List<StudentFlag>,
        filter: String
    ): Pair<List<StudentFlag>, List<StudentFlag>> {
        val typeFiltered = if (filter == "all") flags else flags.filter { it.type == filter }
        val active   = typeFiltered.filter { it.status == "active" }
        val resolved = typeFiltered.filter { it.status == "resolved" }
        return active to resolved
    }
}
