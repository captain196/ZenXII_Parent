package com.schoolsync.parent.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-screen event bus for "open this chat" requests.
 *
 * Used when one screen (e.g. My Teachers) wants to ask the Messages screen
 * to open a specific conversation with a specific teacher. The request is
 * decoupled from navigation: the producer just emits, the consumer (the
 * Messages ViewModel) collects on init, and the navigator simply moves the
 * user to the Messages tab.
 *
 * Why a singleton SharedFlow instead of nav args:
 *  - Teacher names + profile-pic URLs are awkward to URL-encode in nav routes.
 *  - The Messages ViewModel is recreated when the user enters the Messages
 *    tab, so it can't observe state from another ViewModel directly.
 *  - SharedFlow with replay = 1 means a request emitted before the consumer
 *    exists is still delivered when it appears.
 */
@Singleton
class ChatLauncher @Inject constructor() {

    data class OpenChatRequest(
        val teacherId: String,
        val teacherName: String,
        val teacherProfilePic: String,
    )

    private val _requests = MutableSharedFlow<OpenChatRequest>(
        replay = 1,             // last emitted request is replayed to new collectors
        extraBufferCapacity = 1 // never suspend on emit
    )
    val requests: SharedFlow<OpenChatRequest> = _requests.asSharedFlow()

    fun requestOpenChat(
        teacherId: String,
        teacherName: String,
        teacherProfilePic: String = "",
    ) {
        _requests.tryEmit(
            OpenChatRequest(
                teacherId = teacherId,
                teacherName = teacherName,
                teacherProfilePic = teacherProfilePic,
            )
        )
    }

    /**
     * Drop the cached replay value after it has been consumed so that a
     * later visit to the Messages tab doesn't reopen the same chat.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumeRequest() {
        _requests.resetReplayCache()
    }
}
