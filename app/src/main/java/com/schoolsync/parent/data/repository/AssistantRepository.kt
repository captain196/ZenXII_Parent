package com.schoolsync.parent.data.repository

import com.google.firebase.Firebase
import com.google.firebase.functions.functions
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.AssistantReply
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the `studentAssistant` Gen-2 callable.
 *
 * AUTHORISATION comes from the verified ID token, never from this payload. We
 * do send a `studentId`, but only to SELECT among children the token already
 * authorises: the server checks it against the `student_ids` claim and refuses
 * anything outside it. There is no schoolId in the payload and adding one would
 * do nothing — the server ignores it.
 *
 * The conversation is stateless server-side: the client owns the transcript and
 * replays it. Nothing is persisted between sessions, which is a privacy choice
 * on a household-shared credential as much as a cost one.
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val tokenManager: TokenManager,
) {

    /**
     * @param history prior turns, oldest first. Roles are "user" / "assistant".
     * @throws com.google.firebase.functions.FirebaseFunctionsException with a
     *         code the caller maps to a message — notably RESOURCE_EXHAUSTED
     *         (daily quota) and FAILED_PRECONDITION (feature off for school).
     */
    suspend fun ask(
        message: String,
        history: List<Pair<String, String>>,
    ): AssistantReply {
        // Which child this question is about. The server authorises this value
        // against the `student_ids` claim and refuses anything outside it.
        //
        // Today this is always the login identity, because there is no child
        // switcher in this app — so it equals the `student_id` claim and the
        // server's selection branch never fires. It is sent anyway so that the
        // day a switcher lands, the wiring is already correct and already
        // authorised server-side rather than trusted from the request.
        //
        // This comment previously claimed it prevented the assistant answering
        // about the wrong child. It cannot: there is no switcher to disagree with.
        val activeStudentId = tokenManager.user.firstOrNull()?.userId.orEmpty()

        val payload = mapOf(
            "message" to message,
            "studentId" to activeStudentId,
            "messages" to history.map { (role, content) ->
                mapOf("role" to role, "content" to content)
            },
        )

        // The SDK default (~70s) is SHORTER than the function's own 120s budget,
        // so a slow-but-successful answer was killed client-side and reported to
        // the student as their fault, while the server ran on and billed for it.
        val result = Firebase.functions
            .getHttpsCallable(CALLABLE)
            .withTimeout(CALL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?> ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val tools = (data["toolsUsed"] as? List<Any?>)
            ?.mapNotNull { it as? String }
            .orEmpty()

        @Suppress("UNCHECKED_CAST")
        val handoff = data["handoff"] as? Map<String, Any?>

        return AssistantReply(
            text = (data["reply"] as? String).orEmpty(),
            toolsUsed = tools,
            handoffRoute = handoff?.get("route") as? String,
            handoffLabel = handoff?.get("buttonLabel") as? String,
            // The assistant tells the student it has prepared this. Dropping it
            // here is what made that a broken promise and an empty form.
            handoffSubject = handoff?.get("suggestedSubject") as? String,
            handoffDetails = handoff?.get("suggestedDetails") as? String,
            // Server-validated against the rules allowlist; blank when the model
            // gave something unusable, and a blank one is simply not applied.
            handoffCategory = handoff?.get("category") as? String,
        )
    }

    private companion object {
        const val CALLABLE = "studentAssistant"
        /** Must be >= the function's timeoutSeconds (120). */
        const val CALL_TIMEOUT_SECONDS = 130L
    }
}
