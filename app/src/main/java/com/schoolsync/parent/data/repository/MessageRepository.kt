package com.schoolsync.parent.data.repository

import android.net.Uri
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.ChatMessage
import com.schoolsync.parent.data.model.InboxMessage
import com.schoolsync.parent.data.model.ReplyInfo
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for messages (inbox + chat).
 * Inbox path: Schools/{schoolCode}/Communication/Messages/Inbox/parent/{parentDbKey}/
 * Chat path:  Schools/{schoolCode}/Communication/Messages/Chat/{conversationId}/
 */
@Singleton
class MessageRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val tokenManager: TokenManager
) {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // ── Inbox Read Operations ────────────────────────────────────────────

    /**
     * Fetch all inbox messages (one-time read).
     */
    suspend fun getInbox(): List<InboxMessage> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank() || user.parentDbKey.isBlank()) {
            return emptyList()
        }

        val path = Constants.Firebase.messagesInboxPath(
            schoolCode = user.schoolCode,
            parentDbKey = user.parentDbKey
        )

        return try {
            val children = firebaseService.readChildren(path)
            children.map { (messageId, data) ->
                InboxMessage.fromMap(messageId, data)
            }.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Observe inbox messages with real-time updates using callbackFlow.
     * Provides true real-time updates as messages arrive.
     */
    fun observeInbox(schoolCode: String, parentDbKey: String): Flow<List<InboxMessage>> {
        val path = Constants.Firebase.messagesInboxPath(
            schoolCode = schoolCode,
            parentDbKey = parentDbKey
        )

        return firebaseService.observeChildren(path).map { children ->
            children.map { (messageId, data) ->
                InboxMessage.fromMap(messageId, data)
            }.sortedByDescending { it.timestamp }
        }
    }

    /**
     * Observe inbox using current user context from DataStore.
     */
    fun observeInbox(): Flow<List<InboxMessage>> {
        return tokenManager.user.map { user ->
            if (!user.isLoggedIn || user.schoolCode.isBlank() || user.parentDbKey.isBlank()) {
                return@map emptyList()
            }
            try {
                val path = Constants.Firebase.messagesInboxPath(
                    schoolCode = user.schoolCode,
                    parentDbKey = user.parentDbKey
                )
                val children = firebaseService.readChildren(path)
                children.map { (messageId, data) ->
                    InboxMessage.fromMap(messageId, data)
                }.sortedByDescending { it.timestamp }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // ── Chat Read Operations ─────────────────────────────────────────────

    /**
     * Fetch all messages in a conversation (one-time read).
     */
    suspend fun getChatMessages(conversationId: String): List<ChatMessage> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) return emptyList()

        val path = Constants.Firebase.chatPath(
            schoolCode = user.schoolCode,
            conversationId = conversationId
        )

        return try {
            val children = firebaseService.readChildren(path)
            children.map { (messageId, data) ->
                ChatMessage.fromMap(messageId, data)
            }.sortedBy { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Observe chat messages in real-time using callbackFlow.
     * Sets up a persistent listener for the conversation.
     */
    fun observeChat(schoolCode: String, conversationId: String): Flow<List<ChatMessage>> {
        val path = Constants.Firebase.chatPath(
            schoolCode = schoolCode,
            conversationId = conversationId
        )

        return firebaseService.observeChildren(path).map { children ->
            children.map { (messageId, data) ->
                ChatMessage.fromMap(messageId, data)
            }.sortedBy { it.timestamp }
        }
    }

    /**
     * Observe chat using current user context.
     */
    fun observeChat(conversationId: String): Flow<List<ChatMessage>> {
        return tokenManager.user.map { user ->
            if (!user.isLoggedIn || user.schoolCode.isBlank()) {
                return@map emptyList<ChatMessage>()
            }
            try {
                val path = Constants.Firebase.chatPath(
                    schoolCode = user.schoolCode,
                    conversationId = conversationId
                )
                val children = firebaseService.readChildren(path)
                children.map { (messageId, data) ->
                    ChatMessage.fromMap(messageId, data)
                }.sortedBy { it.timestamp }
            } catch (_: Exception) {
                emptyList<ChatMessage>()
            }
        }
    }

    // ── Unread / Mark-as-Read ────────────────────────────────────────────

    /**
     * Get the total unread message count across all inbox items.
     */
    suspend fun getUnreadCount(): Int {
        val inbox = getInbox()
        return inbox.sumOf { it.unreadCount }
    }

    /**
     * Mark a conversation's messages as read by updating the inbox entry.
     * This is one of the few write operations in the parent app.
     */
    suspend fun markAsRead(schoolCode: String, parentDbKey: String, messageId: String) {
        val path = "${Constants.Firebase.messagesInboxPath(schoolCode, parentDbKey)}/$messageId/unreadCount"
        try {
            firebaseService.writeValue(path, 0)
        } catch (_: Exception) {
            // Best effort — non-fatal
        }
    }

    /**
     * Mark as read using current user context.
     */
    suspend fun markAsRead(messageId: String) {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank() || user.parentDbKey.isBlank()) return
        markAsRead(user.schoolCode, user.parentDbKey, messageId)
    }

    // ── Send Operations ──────────────────────────────────────────────────

    /**
     * Send a text message in a conversation.
     *
     * 1. Pushes the message to Chat/{conversationId}/
     * 2. Updates both parent and teacher inbox entries
     * 3. Updates the legacy admin Conversations path
     */
    suspend fun sendTextMessage(
        conversationId: String,
        text: String,
        replyTo: ReplyInfo? = null
    ): Result<ChatMessage> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        val now = System.currentTimeMillis()
        val message = ChatMessage(
            senderId = user.parentDbKey,
            senderName = user.name,
            text = text,
            timestamp = now,
            type = "text",
            replyTo = replyTo,
            readBy = mapOf(user.parentDbKey to true)
        )

        return try {
            val chatPath = Constants.Firebase.chatPath(user.schoolCode, conversationId)
            val messageKey = firebaseService.pushData(chatPath, message.toMap())
                ?: return Result.failure(Exception("Failed to push message"))

            val sentMessage = message.copy(messageId = messageKey)

            // Update inbox entries for both sides + legacy path
            updateInboxAfterSend(
                schoolCode = user.schoolCode,
                conversationId = conversationId,
                parentDbKey = user.parentDbKey,
                lastMessage = text,
                lastMessageType = "text",
                timestamp = now
            )

            updateLegacyConversation(
                schoolCode = user.schoolCode,
                conversationId = conversationId,
                lastMessage = text,
                timestamp = now,
                senderName = user.name,
                senderId = user.parentDbKey
            )

            Result.success(sentMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a media message (image, video, or file).
     *
     * The caller should upload the media first via [uploadMedia] and pass the download URL.
     */
    suspend fun sendMediaMessage(
        conversationId: String,
        mediaUrl: String,
        type: String,
        fileName: String? = null,
        fileSize: Long? = null
    ): Result<ChatMessage> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        val now = System.currentTimeMillis()

        val previewText = when (type) {
            "image" -> "\uD83D\uDCF7 Photo"
            "video" -> "\uD83C\uDFA5 Video"
            "file" -> "\uD83D\uDCCE ${fileName ?: "File"}"
            else -> "\uD83D\uDCCE Attachment"
        }

        val message = ChatMessage(
            senderId = user.parentDbKey,
            senderName = user.name,
            text = previewText,
            timestamp = now,
            type = type,
            mediaUrl = mediaUrl,
            fileName = fileName ?: "",
            fileSize = fileSize ?: 0L,
            readBy = mapOf(user.parentDbKey to true)
        )

        return try {
            val chatPath = Constants.Firebase.chatPath(user.schoolCode, conversationId)
            val messageKey = firebaseService.pushData(chatPath, message.toMap())
                ?: return Result.failure(Exception("Failed to push media message"))

            val sentMessage = message.copy(messageId = messageKey)

            updateInboxAfterSend(
                schoolCode = user.schoolCode,
                conversationId = conversationId,
                parentDbKey = user.parentDbKey,
                lastMessage = previewText,
                lastMessageType = type,
                timestamp = now
            )

            updateLegacyConversation(
                schoolCode = user.schoolCode,
                conversationId = conversationId,
                lastMessage = previewText,
                timestamp = now,
                senderName = user.name,
                senderId = user.parentDbKey
            )

            Result.success(sentMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload media (image/video/file) to Firebase Storage.
     *
     * Storage path: messages/{schoolCode}/{conversationId}/{timestamp}_{filename}
     *
     * @return Download URL string on success.
     */
    suspend fun uploadMedia(uri: Uri, conversationId: String): Result<String> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        return try {
            val timestamp = System.currentTimeMillis()
            val fileName = uri.lastPathSegment ?: "file_$timestamp"
            val storagePath = "messages/${user.schoolCode}/$conversationId/${timestamp}_$fileName"

            val storageRef = storage.reference.child(storagePath)
            storageRef.putFile(uri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add or remove a reaction emoji on a message.
     * Passing an empty string removes the reaction.
     */
    suspend fun reactToMessage(
        conversationId: String,
        messageId: String,
        emoji: String
    ): Result<Unit> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        return try {
            val chatPath = Constants.Firebase.chatPath(user.schoolCode, conversationId)
            val reactionPath = "$chatPath/$messageId/reaction"
            firebaseService.writeValue(reactionPath, emoji.ifBlank { null })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Soft-delete a message by setting isDeleted = true.
     * The message text is also cleared so it cannot be read.
     */
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String
    ): Result<Unit> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolCode.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        return try {
            val chatPath = Constants.Firebase.chatPath(user.schoolCode, conversationId)
            val messagePath = "$chatPath/$messageId"
            firebaseService.updateChildren(messagePath, mapOf(
                "isDeleted" to true,
                "message" to "This message was deleted",
                "mediaUrl" to null,
                "mediaThumb" to null,
                "fileName" to null
            ))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────

    /**
     * After sending a message, update both the parent's and teacher's inbox entries.
     *
     * - Parent inbox: update lastMessage + timestamp, keep unreadCount at 0 (it's our own message).
     * - Teacher inbox: update lastMessage + timestamp, increment unreadCount.
     */
    private suspend fun updateInboxAfterSend(
        schoolCode: String,
        conversationId: String,
        parentDbKey: String,
        lastMessage: String,
        lastMessageType: String,
        timestamp: Long
    ) {
        try {
            // Update parent's own inbox entry
            val parentInboxPath = Constants.Firebase.messagesInboxPath(schoolCode, parentDbKey)
            val parentInboxEntryPath = "$parentInboxPath/$conversationId"
            firebaseService.updateChildren(parentInboxEntryPath, mapOf(
                "lastMessage" to lastMessage,
                "lastMessageType" to lastMessageType,
                "lastMessageTime" to timestamp,
                "timestamp" to timestamp,
                "unreadCount" to 0
            ))

            // Try to find and update the teacher's inbox entry.
            // The teacher's DB key is stored in the conversation metadata or the inbox entry.
            val parentInboxData = firebaseService.readMap(parentInboxEntryPath)
            val teacherDbKey = (parentInboxData["teacherDbKey"]
                ?: parentInboxData["otherDbKey"]
                ?: parentInboxData["recipientDbKey"]
                ?: "").toString()

            if (teacherDbKey.isNotBlank()) {
                val teacherInboxPath =
                    "Schools/$schoolCode/Communication/Messages/Inbox/teacher/$teacherDbKey/$conversationId"
                // Read current unread count so we can increment it
                val teacherInboxData = firebaseService.readMap(teacherInboxPath)
                val currentUnread = when (val uc = teacherInboxData["unreadCount"]) {
                    is Number -> uc.toInt()
                    is String -> uc.toIntOrNull() ?: 0
                    else -> 0
                }
                firebaseService.updateChildren(teacherInboxPath, mapOf(
                    "lastMessage" to lastMessage,
                    "lastMessageType" to lastMessageType,
                    "lastMessageTime" to timestamp,
                    "timestamp" to timestamp,
                    "unreadCount" to (currentUnread + 1)
                ))
            }
        } catch (_: Exception) {
            // Inbox update is best-effort — the chat message was already sent
        }
    }

    /**
     * Update the legacy admin portal conversation path so messages appear
     * in the admin dashboard as well.
     *
     * Path: Schools/{schoolCode}/Messages/Conversations/{conversationId}
     */
    private suspend fun updateLegacyConversation(
        schoolCode: String,
        conversationId: String,
        lastMessage: String,
        timestamp: Long,
        senderName: String,
        senderId: String
    ) {
        try {
            val legacyPath = "Schools/$schoolCode/Messages/Conversations/$conversationId"
            firebaseService.updateChildren(legacyPath, mapOf(
                "lastMessage" to lastMessage,
                "lastMessageTime" to timestamp,
                "lastSenderName" to senderName,
                "lastSenderId" to senderId,
                "updatedAt" to timestamp
            ))
        } catch (_: Exception) {
            // Legacy update is best-effort — non-fatal
        }
    }
}
