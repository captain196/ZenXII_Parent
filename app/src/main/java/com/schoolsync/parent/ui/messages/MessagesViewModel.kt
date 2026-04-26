package com.schoolsync.parent.ui.messages

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.model.ChatMessage
import com.schoolsync.parent.data.model.InboxMessage
import com.schoolsync.parent.data.model.ReplyInfo
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.repository.MessageRepository
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.util.ChatLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class MessagesUiState(
    val isLoading: Boolean = true,
    val inbox: List<InboxMessage> = emptyList(),
    val selectedConversation: InboxMessage? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val messageText: String = "",
    val isSending: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val isInChatView: Boolean = false,
    val currentUserId: String = "",
    val currentUserName: String = "",
    val replyingTo: ChatMessage? = null,
    val selectedMediaUri: Uri? = null,
    val selectedMediaType: String = "",
    val showImageViewer: Boolean = false,
    val viewerImageUrl: String = "",
    val errorMessage: String? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val tokenManager: TokenManager,
    private val chatLauncher: ChatLauncher,
    private val badgeBus: com.schoolsync.parent.util.BadgeBus,
) : ViewModel() {

    private fun publishMessagesBadge(inbox: List<InboxMessage>) {
        badgeBus.setCount("messages", inbox.sumOf { it.unreadCount })
    }

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    /** Job that observes the inbox in real-time; cancelled when entering a chat. */
    private var inboxObserverJob: Job? = null

    /** Job that observes chat messages in real-time; cancelled when leaving the chat. */
    private var chatObserverJob: Job? = null

    /** Full (unfiltered) inbox list so search can re-filter without a network call. */
    private var fullInbox: List<InboxMessage> = emptyList()

    /** Cached user profile to avoid re-reading DataStore on every action. */
    private var cachedUser: User = User.empty()

    // ── Init ─────────────────────────────────────────────────────────────

    init {
        loadCurrentUser()
        loadInbox()
        observeChatLaunchRequests()
    }

    /**
     * Listens for `ChatLauncher.requestOpenChat(...)` calls from anywhere
     * else in the app and opens (or bootstraps) the requested conversation.
     * Each request is consumed exactly once so revisiting the Messages tab
     * doesn't reopen a stale chat.
     */
    private fun observeChatLaunchRequests() {
        viewModelScope.launch {
            chatLauncher.requests.collect { req ->
                chatLauncher.consumeRequest()
                openConversationWithTeacher(
                    teacherId = req.teacherId,
                    teacherName = req.teacherName,
                    teacherProfilePic = req.teacherProfilePic,
                )
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            tokenManager.user
                .catch { /* DataStore read failure is non-fatal */ }
                .collect { user ->
                    cachedUser = user
                    _uiState.update {
                        it.copy(
                            currentUserId = user.parentDbKey.ifBlank { user.userId },
                            currentUserName = user.name
                        )
                    }
                }
        }
    }

    // ── 1. Load Inbox (real-time) ────────────────────────────────────────

    fun loadInbox() {
        inboxObserverJob?.cancel()

        inboxObserverJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // Fast one-time fetch so the list appears immediately.
                val initial = messageRepository.getInbox()
                fullInbox = initial
                publishMessagesBadge(initial)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        inbox = applySearchFilter(initial, it.searchQuery)
                    )
                }

                // Then subscribe to real-time changes.
                // NOTE: Inbox node is keyed by `schoolId` (the resolved school
                // node name e.g. "Demo" or "SCH_XXXXXX"), NOT `schoolCode`
                // (the login code e.g. "10004"). Using the wrong field here
                // returned an empty list and overwrote the working
                // one-time fetch — the bug behind "No messages yet".
                val user = cachedUser
                if (user.isLoggedIn && user.schoolId.isNotBlank() && user.parentDbKey.isNotBlank()) {
                    messageRepository.observeInbox(user.schoolId, user.parentDbKey)
                        .catch { e ->
                            _uiState.update {
                                it.copy(errorMessage = e.message ?: "Failed to observe inbox")
                            }
                        }
                        .collect { messages ->
                            fullInbox = messages
                            publishMessagesBadge(messages)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    inbox = applySearchFilter(messages, it.searchQuery)
                                )
                            }
                        }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load messages"
                    )
                }
            }
        }
    }

    // ── 1.5. Open or create conversation with a specific teacher ─────────

    /**
     * Triggered when the user taps "Message Teacher" on the My Teachers
     * screen. Bootstraps a 1:1 conversation (idempotent — no duplicates if
     * one already exists), then opens it in the chat view exactly as if the
     * user had tapped that conversation in the inbox list.
     *
     * The teacher info is needed up-front because the conversation may not
     * yet exist in the inbox, so we synthesize an [InboxMessage] for the
     * chat screen header.
     */
    fun openConversationWithTeacher(
        teacherId: String,
        teacherName: String,
        teacherProfilePic: String = "",
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = messageRepository.startConversationWithTeacher(
                teacherId = teacherId,
                teacherName = teacherName,
                teacherProfilePic = teacherProfilePic,
            )

            result.fold(
                onSuccess = { convId ->
                    val synthetic = InboxMessage(
                        messageId = convId,
                        conversationId = convId,
                        otherName = teacherName,
                        otherProfilePic = teacherProfilePic,
                        studentName = cachedUser.name,
                        studentClass = "${cachedUser.className} ${cachedUser.section}".trim(),
                        lastMessage = "",
                        lastMessageType = "text",
                        timestamp = 0L,
                        unreadCount = 0,
                    )
                    openConversation(synthetic)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to open chat",
                        )
                    }
                }
            )
        }
    }

    // ── 2. Open Conversation ─────────────────────────────────────────────

    fun openConversation(message: InboxMessage) {
        // Stop inbox listener to save resources while chatting.
        inboxObserverJob?.cancel()
        chatObserverJob?.cancel()

        _uiState.update {
            it.copy(
                selectedConversation = message,
                isInChatView = true,
                isLoading = true,
                chatMessages = emptyList(),
                messageText = "",
                replyingTo = null,
                selectedMediaUri = null,
                selectedMediaType = "",
                errorMessage = null
            )
        }

        // Mark as read (fire-and-forget).
        viewModelScope.launch {
            try {
                messageRepository.markAsRead(message.messageId)
            } catch (_: Exception) { }
        }

        // Load messages and start real-time observer.
        chatObserverJob = viewModelScope.launch {
            // One-time fetch for instant display.
            try {
                val messages = messageRepository.getChatMessages(message.conversationId)
                _uiState.update { it.copy(isLoading = false, chatMessages = messages) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load chat"
                    )
                }
            }

            // Real-time observer via callbackFlow.
            // Same schoolId vs schoolCode caveat as observeInbox above.
            val user = cachedUser
            if (user.isLoggedIn && user.schoolId.isNotBlank()) {
                try {
                    messageRepository.observeChat(user.schoolId, message.conversationId)
                        .catch { e ->
                            _uiState.update {
                                it.copy(errorMessage = e.message ?: "Lost real-time connection")
                            }
                        }
                        .collect { messages ->
                            _uiState.update { it.copy(chatMessages = messages) }
                        }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(errorMessage = e.message ?: "Real-time updates unavailable")
                    }
                }
            }
        }
    }

    // ── 3. Go Back to List ───────────────────────────────────────────────

    fun goBackToList() {
        chatObserverJob?.cancel()
        chatObserverJob = null

        _uiState.update {
            it.copy(
                isInChatView = false,
                selectedConversation = null,
                chatMessages = emptyList(),
                messageText = "",
                replyingTo = null,
                selectedMediaUri = null,
                selectedMediaType = "",
                showImageViewer = false,
                viewerImageUrl = "",
                errorMessage = null
            )
        }

        // Refresh inbox to pick up updated unread counts.
        loadInbox()
    }

    // ── 4. Text Input ────────────────────────────────────────────────────

    fun onMessageTextChange(text: String) {
        _uiState.update { it.copy(messageText = text) }
    }

    // ── 5. Send Message ──────────────────────────────────────────────────

    fun sendMessage() {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return

        // If the user has media selected, delegate to the media path.
        if (state.selectedMediaUri != null) {
            sendMediaMessage(state.selectedMediaUri, state.selectedMediaType)
            return
        }

        val text = state.messageText.trim()
        if (text.isEmpty()) return

        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val replyInfo = state.replyingTo?.let { msg ->
                ReplyInfo(
                    messageId = msg.messageId,
                    text = msg.text.take(100),
                    senderName = msg.senderName
                )
            }

            val result = messageRepository.sendTextMessage(
                conversationId = conversation.conversationId,
                text = text,
                replyTo = replyInfo
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            messageText = "",
                            replyingTo = null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = e.message ?: "Failed to send message"
                        )
                    }
                }
            )
        }
    }

    // ── 6. Send Media Message ────────────────────────────────────────────

    fun sendMediaMessage(uri: Uri, type: String) {
        val state = _uiState.value
        val conversation = state.selectedConversation ?: return

        _uiState.update {
            it.copy(
                isSending = true,
                isUploading = true,
                uploadProgress = 0f,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Step 1: Upload the media file.
                _uiState.update { it.copy(uploadProgress = 0.1f) }

                val uploadResult = messageRepository.uploadMedia(
                    uri = uri,
                    conversationId = conversation.conversationId
                )

                val mediaUrl = uploadResult.getOrElse { e ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isUploading = false,
                            uploadProgress = 0f,
                            errorMessage = e.message ?: "Upload failed"
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(uploadProgress = 0.8f) }

                // Step 2: Send the chat message with the download URL.
                val sendResult = messageRepository.sendMediaMessage(
                    conversationId = conversation.conversationId,
                    mediaUrl = mediaUrl,
                    type = type,
                    fileName = uri.lastPathSegment
                )

                sendResult.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                isUploading = false,
                                uploadProgress = 0f,
                                messageText = "",
                                replyingTo = null,
                                selectedMediaUri = null,
                                selectedMediaType = ""
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                isUploading = false,
                                uploadProgress = 0f,
                                errorMessage = e.message ?: "Failed to send media"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        isUploading = false,
                        uploadProgress = 0f,
                        errorMessage = e.message ?: "Failed to send media"
                    )
                }
            }
        }
    }

    // ── 7 & 8. Reply ────────────────────────────────────────────────────

    fun setReplyTo(message: ChatMessage?) {
        _uiState.update { it.copy(replyingTo = message) }
    }

    fun clearReply() {
        _uiState.update { it.copy(replyingTo = null) }
    }

    // ── 9. React to Message ──────────────────────────────────────────────

    fun reactToMessage(message: ChatMessage, emoji: String) {
        val conversation = _uiState.value.selectedConversation ?: return

        viewModelScope.launch {
            // Toggle: if the same emoji is already set, remove it.
            val emojiToSet = if (message.reaction == emoji) "" else emoji

            val result = messageRepository.reactToMessage(
                conversationId = conversation.conversationId,
                messageId = message.messageId,
                emoji = emojiToSet
            )

            result.onFailure { e ->
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to react")
                }
            }
        }
    }

    // ── 10. Delete Message ───────────────────────────────────────────────

    fun deleteMessage(message: ChatMessage) {
        val conversation = _uiState.value.selectedConversation ?: return
        val userId = _uiState.value.currentUserId

        // Only allow deleting own messages.
        if (message.senderId != userId) {
            _uiState.update { it.copy(errorMessage = "You can only delete your own messages") }
            return
        }

        viewModelScope.launch {
            val result = messageRepository.deleteMessage(
                conversationId = conversation.conversationId,
                messageId = message.messageId
            )

            result.onFailure { e ->
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to delete message")
                }
            }
        }
    }

    // ── 11 & 12. Image Viewer ────────────────────────────────────────────

    fun openImageViewer(url: String) {
        _uiState.update {
            it.copy(showImageViewer = true, viewerImageUrl = url)
        }
    }

    fun closeImageViewer() {
        _uiState.update {
            it.copy(showImageViewer = false, viewerImageUrl = "")
        }
    }

    // ── 13 & 14. Media Selection ─────────────────────────────────────────

    fun setSelectedMedia(uri: Uri?, type: String) {
        _uiState.update {
            it.copy(selectedMediaUri = uri, selectedMediaType = type)
        }
    }

    fun clearSelectedMedia() {
        _uiState.update {
            it.copy(selectedMediaUri = null, selectedMediaType = "")
        }
    }

    // ── Delete conversation (per-user) ───────────────────────────────────

    /**
     * "Delete chat" — removes only this parent's inbox stub. The shared
     * Conversations doc + chat history stay intact for the other side.
     * If the deleted conversation is currently open, exits the chat view.
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            val result = messageRepository.deleteConversationForMe(conversationId)
            result.onSuccess {
                // Optimistic: drop locally so the inbox updates instantly.
                fullInbox = fullInbox.filterNot { it.conversationId == conversationId }
                publishMessagesBadge(fullInbox)
                _uiState.update {
                    val stillInOpenChat = it.selectedConversation?.conversationId == conversationId
                    it.copy(
                        inbox = applySearchFilter(fullInbox, it.searchQuery),
                        // If the deleted convo was the one being viewed, close it.
                        isInChatView = if (stillInOpenChat) false else it.isInChatView,
                        selectedConversation = if (stillInOpenChat) null else it.selectedConversation,
                        chatMessages = if (stillInOpenChat) emptyList() else it.chatMessages,
                    )
                }
                // Re-attach the inbox observer if we exited a chat.
                if (!_uiState.value.isInChatView) loadInbox()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to delete conversation")
                }
            }
        }
    }

    // ── 15. Search Inbox ─────────────────────────────────────────────────

    fun searchInbox(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                inbox = applySearchFilter(fullInbox, query)
            )
        }
    }

    // ── 16. Format Timestamp ─────────────────────────────────────────────

    fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return ""

        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1_000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            hours < 48 -> "Yesterday"
            hours < 168 -> {
                // Within 7 days -- show day name (e.g. "Tuesday").
                val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
            else -> {
                val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                val nowCal = Calendar.getInstance()
                if (msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
                } else {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────

    /**
     * Filter the inbox list by search query. Stubs created by
     * [MessageRepository.startConversationWithTeacher] (empty lastMessage,
     * timestamp == 0L) are intentionally KEPT visible — the user opened the
     * chat from "Message Teacher", so it should appear in their inbox even
     * before the first message is exchanged.
     */
    private fun applySearchFilter(
        inbox: List<InboxMessage>,
        query: String
    ): List<InboxMessage> {
        if (query.isBlank()) return inbox

        val q = query.lowercase(Locale.getDefault())
        return inbox.filter { msg ->
            msg.otherName.lowercase(Locale.getDefault()).contains(q) ||
                    msg.studentName.lowercase(Locale.getDefault()).contains(q) ||
                    msg.lastMessage.lowercase(Locale.getDefault()).contains(q)
        }
    }

    override fun onCleared() {
        super.onCleared()
        inboxObserverJob?.cancel()
        chatObserverJob?.cancel()
    }
}
