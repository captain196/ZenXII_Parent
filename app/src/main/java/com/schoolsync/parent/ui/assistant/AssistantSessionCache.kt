package com.schoolsync.parent.ui.assistant

import com.schoolsync.parent.data.model.AssistantMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the conversation alive for as long as the PROCESS lives — and no longer.
 *
 * The AssistantViewModel is scoped to the `Assistant` nav entry, so backing out
 * to check the timetable and returning already destroyed the thread, with no
 * process death involved. That is the loss students actually hit; this fixes it
 * for free, because the bytes never leave RAM.
 *
 * Deliberately NOT persisted, and that is a privacy decision rather than an
 * unfinished one. The Parent app signs in on a household-shared credential AS
 * the student, so writing the transcript to the handset would not merely move
 * bytes to disk — it would make a child's questions about their own records
 * readable by whoever opens the app next, and create a second retention surface
 * that an erasure request cannot reach. The server stores the question text only
 * (studentAssistant.js writeLog), never the reply, so no complete copy of a
 * conversation exists anywhere by design. Keep it that way.
 */
@Singleton
class AssistantSessionCache @Inject constructor() {

    private var turns: List<AssistantMessage> = emptyList()

    fun read(): List<AssistantMessage> = turns

    fun write(v: List<AssistantMessage>) {
        turns = if (v.size > MAX_TURNS) v.takeLast(MAX_TURNS) else v
    }

    /** Logout, and the student's own "New chat". */
    fun clear() { turns = emptyList() }

    private companion object {
        /** Matches the server's replay window; holding more would only waste RAM. */
        const val MAX_TURNS = 40
    }
}
