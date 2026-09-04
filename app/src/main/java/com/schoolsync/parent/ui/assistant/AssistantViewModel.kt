package com.schoolsync.parent.ui.assistant

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.schoolsync.parent.util.localizedString

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val isThinking: Boolean = false,
    /** Non-null when the whole feature is unavailable (school hasn't enabled it). */
    val unavailableReason: String? = null,
    val input: String = "",
    /**
     * A thread existed before the process was killed and is gone. The content is
     * deliberately not recoverable — only the FACT is, so the screen can say so
     * instead of silently appearing to have forgotten the student.
     */
    val threadWasCleared: Boolean = false,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    app: Application,
    private val repo: AssistantRepository,
    private val cache: AssistantSessionCache,
    private val saved: SavedStateHandle,
) : AndroidViewModel(app) {

    private var revealJob: kotlinx.coroutines.Job? = null

    private val _ui = MutableStateFlow(
        AssistantUiState(
            // Survives back-navigation and rotation, because it lives in the
            // process rather than in this ViewModel.
            messages = cache.read(),
            // The ONLY thing that crosses process death: one boolean saying a
            // thread existed. Not a word of what was in it. Costs ~2 bytes of the
            // Binder saved-state budget and cannot leak anything, but it is the
            // difference between "this app forgot me" and a stated limit.
            threadWasCleared = saved.get<Boolean>(KEY_HAD_THREAD) == true && cache.read().isEmpty(),
        )
    )
    val ui: StateFlow<AssistantUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.collect { st ->
                cache.write(st.messages)
                if (st.messages.isNotEmpty()) saved[KEY_HAD_THREAD] = true
            }
        }
    }

    /** The student's own erase. They previously had no way to clear this at all. */
    fun newChat() {
        finishReveal()
        cache.clear()
        saved[KEY_HAD_THREAD] = false
        _ui.value = AssistantUiState()
    }

    fun onInputChange(v: String) = _ui.update { it.copy(input = v) }

    fun send(text: String = _ui.value.input) {
        val q = text.trim()
        if (q.isEmpty() || _ui.value.isThinking) return
        // A tap while text is still appearing means "I've read it" — show the
        // rest at once so history is never replayed to the server truncated.
        finishReveal()

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
                            handoffCategory = reply.handoffCategory,
                            revealChars = 0,
                        ),
                    )
                }
                revealLast(reply.text.length)
            } catch (e: Exception) {
                handleFailure(e)
            }
        }
    }

    /**
     * Reveal the last message progressively.
     *
     * This is presentation only — the reply already arrived in full. It replaces
     * a 3-9 second motionless spinner with text that appears, which is the single
     * biggest perceived-speed change available without a server change.
     *
     * It is NOT streaming and the code should not pretend otherwise: nothing
     * arrives sooner, and the total wait is unchanged. Real streaming needs
     * firebase-functions v6 `sendChunk`; this codebase is on 5.1.1, shared by
     * 20 deployed functions, so that upgrade is a blast-radius decision.
     *
     * Cancelling is safe: `finishReveal()` on the next send drops the cursor, so
     * a half-revealed message can never be replayed to the server truncated.
     */
    private fun revealLast(total: Int) {
        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            var shown = 0
            while (shown < total) {
                shown = (shown + CHARS_PER_TICK).coerceAtMost(total)
                _ui.update { st ->
                    val last = st.messages.lastOrNull() ?: return@update st
                    if (last.revealChars == null) return@update st
                    st.copy(messages = st.messages.dropLast(1) + last.copy(revealChars = shown))
                }
                delay(TICK_MS)
            }
            finishReveal()
        }
    }

    /** Show the last message in full and stop any reveal in flight. */
    private fun finishReveal() {
        revealJob?.cancel(); revealJob = null
        _ui.update { st ->
            val last = st.messages.lastOrNull() ?: return@update st
            if (last.revealChars == null) return@update st
            st.copy(messages = st.messages.dropLast(1) + last.copy(revealChars = null))
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
    // localizedString, NOT getString: `getApplication()` is the Application
    // Context, whose locale is fixed when the PROCESS starts. Changing the
    // language recreates the Activity but never the Application, so a plain
    // getString here keeps serving the launch language — reported from the
    // field as the quota message staying Tamil after switching to English.
    private fun handleFailure(e: Exception) {
        val ctx = getApplication<Application>()
        val code = (e as? FirebaseFunctionsException)?.code

        // ONLY failed-precondition latches the screen shut. It means the school has
        // not switched the assistant on, or has no academic session set — a genuine
        // entitlement state that will not change while the student sits here.
        //
        // PERMISSION_DENIED is NOT that, and latching it was wrong: it is raised
        // per-request when the ID token's student_ids claim does not cover the
        // student being asked about — the stale-token case after a child switch,
        // which a re-login fixes. Latching told a recoverable user their SCHOOL had
        // no assistant, and removed the composer so they could not even retry.
        if (code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
            _ui.update {
                it.copy(
                    isThinking = false,
                    // Never surface raw server text: it is English-only and leaks internals.
                    unavailableReason = ctx.localizedString(R.string.assistant_unavailable),
                )
            }
            return
        }

        // Log before mapping. Until now nothing was written on failure, so a live
        // outage showed up on the phone as "could not connect" and left NOTHING in
        // logcat to tell you why — the cause had to be dug out of Cloud Function
        // logs. The code alone is enough to separate "our bug" from "their outage".
        Log.w(TAG, "studentAssistant failed: code=${code ?: "non-functions"}", e)

        val msg = when (code) {
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                ctx.localizedString(R.string.assistant_quota_reached)
            // Both mean "this identity cannot ask that" and both are fixed by a
            // re-login, which is exactly what this string says.
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                ctx.localizedString(R.string.assistant_signed_out)
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                ctx.localizedString(R.string.assistant_too_long)

            // The server rejected the message itself — over-length is the only way
            // this happens today, and the server rejects rather than truncating.
            // "Try again" would be false advice; shortening it is the actual fix.
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.OUT_OF_RANGE ->
                ctx.localizedString(R.string.assistant_bad_request)

            // The service is down, not the connection. Observed for real on
            // 2026-09-04: Vertex returned 403 "dunning decision is deny" — a
            // BILLING suspension on the Cloud project — and the app told the
            // student "could not connect, please try again", which is wrong on
            // both counts. It was not the connection, and retrying cannot help.
            FirebaseFunctionsException.Code.INTERNAL,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.ABORTED,
            FirebaseFunctionsException.Code.UNIMPLEMENTED,
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.DATA_LOSS ->
                ctx.localizedString(R.string.assistant_service_down)

            // CANCELLED is us tearing the scope down, not a failure worth a bubble.
            FirebaseFunctionsException.Code.CANCELLED -> null

            else -> ctx.localizedString(R.string.assistant_generic_error)
        }

        // No bubble for CANCELLED — but the spinner still has to stop. Returning
        // early here skipped the isThinking reset below, which left the thinking
        // bubble on screen forever, the header stuck on "Looking that up…" and the
        // composer disabled, with Back as the only way out.
        if (msg == null) {
            _ui.update { it.copy(isThinking = false) }
            return
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

    private companion object {
        const val KEY_HAD_THREAD = "assistant_had_thread"
        const val TAG = "Assistant"

        /** ~6 chars per frame ≈ 375 chars/sec — fast enough not to feel slow. */
        const val CHARS_PER_TICK = 6
        const val TICK_MS = 16L
    }
}
