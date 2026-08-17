package com.schoolsync.parent.ui.redflags

import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.schoolsync.parent.R
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.StudentFlag
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.repository.RedFlagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.retryWhen
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
    
    @ApplicationContext private val appContext: Context,private val redFlagRepository: RedFlagRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val TAG = "RedFlagVM"
        private const val MAX_RETRIES = 3L
    }

    private val _uiState = MutableStateFlow(RedFlagUiState())
    val uiState: StateFlow<RedFlagUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        viewModelScope.launch { redFlagRepository.dumpAuthClaimsForDebug() }
        viewModelScope.launch {
            tokenManager.user.firstOrNull()?.let { user ->
                _uiState.update { it.copy(studentName = user.name) }
            }
        }
        observeFlags()
    }

    /**
     * Live snapshot listener — the single source of truth. First emission
     * flips isLoading off; ongoing emissions keep the list current.
     *
     * Previously a separate one-shot `getAllFlags()` also ran, but it
     * swallowed all errors → returned emptyList() → the screen painted a
     * false "No red flags / all clear" on any failure. For a safety feature
     * that's dangerous, so the one-shot is gone and load/error state is
     * driven purely from this listener.
     *
     * Resilience: transient errors (network blips) auto-retry with backoff
     * so a single hiccup no longer permanently kills live updates (the old
     * terminal `.catch` did). Persistent errors (permission/index) fall
     * through to `.catch` and surface a friendly, actionable message — never
     * the raw Firestore string (which leaks a Firebase console URL).
     */
    private fun observeFlags() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            redFlagRepository.observeFlags()
                .retryWhen { cause, attempt ->
                    if (attempt < MAX_RETRIES) {
                        Log.w(TAG, "Live listener error (attempt ${attempt + 1}), retrying", cause)
                        // Keep showing existing content; only spin if we have nothing yet.
                        _uiState.update { it.copy(isLoading = it.allFlags.isEmpty(), errorMessage = null) }
                        delay(1_000L * (attempt + 1))
                        true
                    } else {
                        false
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Live listener error (final)", e)
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = friendlyError(e))
                    }
                }
                .collect { flags -> applyFlags(flags) }
        }
    }

    /** Manual retry from the error state — re-subscribes the live listener. */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeFlags()
    }

    /**
     * Map a load/listener failure to short, non-technical copy. Never surface
     * the raw exception (a Firestore FAILED_PRECONDITION includes a clickable
     * console URL — alarming and meaningless to a parent).
     */
    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                appContext.getString(R.string.rf_permission_denied)
            msg.contains("FAILED_PRECONDITION", ignoreCase = true) ||
                msg.contains("requires an index", ignoreCase = true) ->
                appContext.getString(R.string.rf_temporarily_unavailable)
            msg.contains("UNAVAILABLE", ignoreCase = true) || msg.contains("network", ignoreCase = true) ->
                appContext.getString(R.string.hw_network_problem)
            else -> appContext.getString(R.string.rf_load_failed_retry)
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
