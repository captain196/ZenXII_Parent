package com.schoolsync.parent.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.AssistantMessage
import com.schoolsync.parent.data.repository.AssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val isThinking: Boolean = false,
    /** Non-null when the whole feature is unavailable (school hasn't enabled it). */
    val unavailableReason: String? = null,
    val input: String = "",
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    app: Application,
    private val repo: AssistantRepository,
) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(AssistantUiState())
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    fun onInputChange(v: String) = _ui.update { it.copy(input = v) }

    fun send(text: String = _ui.value.input) {
        val q = text.trim()
        if (q.isEmpty() || _ui.value.isThinking) return

        // Replay only the turns the server can use — errors and handoff chrome
        // are local UI state, not conversation.
        val history = _ui.value.messages
            .filterNot { it.isError }
            .map { m ->
                (if (m.role == AssistantMessage.Role.USER) "user" else "assistant") to m.text
            }

        _ui.update {
            it.copy(
                messages = it.messages + AssistantMessage(AssistantMessage.Role.USER, q),
                input = "",
                isThinking = true,
            )
        }

        viewModelScope.launch {
            try {
                val reply = repo.ask(q, history)
                _ui.update {
                    it.copy(
                        isThinking = false,
                        messages = it.messages + AssistantMessage(
                            role = AssistantMessage.Role.ASSISTANT,
                            text = reply.text,
                            toolsUsed = reply.toolsUsed,
                            handoffRoute = reply.handoffRoute,
                            handoffLabel = reply.handoffLabel,
                            handoffSubject = reply.handoffSubject,
                            handoffDetails = reply.handoffDetails,
                        ),
                    )
                }
            } catch (e: Exception) {
                handleFailure(e)
            }
        }
    }

    /**
     * Fail closed and say something true.
     *
     * FAILED_PRECONDITION means the school has not switched the assistant on,
     * or has no academic session set — in both cases the feature is genuinely
     * unavailable, so we hide the composer rather than let the user keep typing
     * into something that cannot answer.
     */
    // TODO(locale): switch these to `ctx.localizedString(...)` once
    // LocaleManager's resContext/localizedString helpers land on main. They
    // exist only in another workstream's uncommitted change today, and this
    // file must not depend on unlanded code. Until then these resolve in the
    // system locale rather than the app's chosen language — the same behaviour
    // as every other ViewModel currently on main.
    private fun handleFailure(e: Exception) {
        val ctx = getApplication<Application>()
        val code = (e as? FirebaseFunctionsException)?.code

        if (code == FirebaseFunctionsException.Code.FAILED_PRECONDITION ||
            code == FirebaseFunctionsException.Code.PERMISSION_DENIED
        ) {
            _ui.update {
                it.copy(
                    isThinking = false,
                    // Never surface raw server text: it is English-only and leaks internals.
                    unavailableReason = ctx.getString(R.string.assistant_unavailable),
                )
            }
            return
        }

        val msg = when (code) {
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                ctx.getString(R.string.assistant_quota_reached)
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                ctx.getString(R.string.assistant_signed_out)
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                ctx.getString(R.string.assistant_too_long)
            else -> ctx.getString(R.string.assistant_generic_error)
        }

        _ui.update {
            it.copy(
                isThinking = false,
                messages = it.messages + AssistantMessage(
                    role = AssistantMessage.Role.ASSISTANT,
                    text = msg,
                    isError = true,
                ),
            )
        }
    }
}
