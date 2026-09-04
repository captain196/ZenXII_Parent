package com.schoolsync.parent.data.model

/**
 * One turn in the assistant thread.
 *
 * The transcript lives only in memory for the life of the screen. It is not
 * persisted to disk or to Firestore — a deliberate choice on a credential the
 * whole household shares, and it also keeps replayed token cost bounded.
 */
data class AssistantMessage(
    val role: Role,
    val text: String,
    /** Tool names the server ran for this answer, e.g. get_attendance_summary. */
    val toolsUsed: List<String> = emptyList(),
    /** Set when the assistant prepared a support request for the student. */
    val handoffRoute: String? = null,
    val handoffLabel: String? = null,
    val handoffSubject: String? = null,
    val handoffDetails: String? = null,
    /**
     * The area the model classified the problem as. Carried so the composer opens
     * with its category chip already chosen — without it, Send stays DISABLED
     * (`SupportViewModel.canSubmit` requires a category), so the assistant's
     * "I've prepared this" led to a form the student could not send.
     */
    val handoffCategory: String? = null,
    val isError: Boolean = false,
    /**
     * How many characters are currently revealed, or null once the whole message
     * is shown. This is a PROGRESSIVE REVEAL of an already-complete reply — not
     * streaming. Real streaming needs firebase-functions v6 (`sendChunk`), and
     * this codebase is on 5.1.1 shared by 20 deployed functions.
     */
    val revealChars: Int? = null,
) {
    enum class Role { USER, ASSISTANT }
}

/** What the callable returns for one question. */
data class AssistantReply(
    val text: String,
    val toolsUsed: List<String> = emptyList(),
    val handoffRoute: String? = null,
    val handoffLabel: String? = null,
    val handoffSubject: String? = null,
    val handoffDetails: String? = null,
    /**
     * The area the model classified the problem as. Carried so the composer opens
     * with its category chip already chosen — without it, Send stays DISABLED
     * (`SupportViewModel.canSubmit` requires a category), so the assistant's
     * "I've prepared this" led to a form the student could not send.
     */
    val handoffCategory: String? = null,
)
