package com.schoolsync.parent.data.repository

import android.net.Uri
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.storage.FirebaseStorage
import com.schoolsync.parent.data.firebase.FirebaseService
import com.schoolsync.parent.data.firebase.FirestoreService
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.ChatMessage
import com.schoolsync.parent.data.model.InboxMessage
import com.schoolsync.parent.data.model.ReplyInfo
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.ConversationDoc
import com.schoolsync.parent.data.model.firestore.InboxDoc
import com.schoolsync.parent.data.model.firestore.MessageDoc
import com.schoolsync.parent.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for messages (inbox + chat).
 *
 * Phase 5: Firestore-first via [firestoreService] (collections
 * `conversations`, `messages`, `messageInboxes`). RTDB stays wired as
 * a best-effort mirror so older builds and unmigrated data still work.
 *
 * Inbox path (RTDB legacy): Schools/{schoolCode}/Communication/Messages/Inbox/parent/{parentDbKey}/
 * Chat path  (RTDB legacy): Schools/{schoolCode}/Communication/Messages/Chat/{conversationId}/
 */
@Singleton
class MessageRepository @Inject constructor(
    private val firebaseService: FirebaseService,
    private val firestoreService: FirestoreService,
    private val tokenManager: TokenManager
) {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private companion object {
        const val COL_CONVERSATIONS = "conversations"
        const val COL_MESSAGES      = "messages"
        const val COL_INBOXES       = "messageInboxes"
    }

    // ── Firestore doc id helpers (must match Messaging_service.php) ───────

    private fun convDocId(schoolId: String, convId: String) = "${schoolId}_$convId"
    private fun msgDocId(schoolId: String, convId: String, msgId: String) =
        "${schoolId}_${convId}_$msgId"
    private fun inboxDocId(schoolId: String, role: String, userId: String, convId: String) =
        "${schoolId}_${role.lowercase()}_${userId}_$convId"

    /** Generate a unique, time-sortable message id. */
    private fun newMsgId(): String =
        "MSG_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"

    // ── Inbox Read Operations ────────────────────────────────────────────

    /**
     * Fetch all inbox messages (one-time read).
     *
     * Firestore-first: query `messageInboxes` where schoolId+role+userId
     * match this parent. Falls back to RTDB if Firestore is empty (e.g.
     * pre-Phase-5f backfill or transient outage).
     */
    suspend fun getInbox(): List<InboxMessage> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolId.isBlank() || user.parentDbKey.isBlank()) {
            return emptyList()
        }

        // 1. Firestore primary
        try {
            val docs = firestoreService.queryDocumentsAs<InboxDoc>(COL_INBOXES) { ref ->
                ref.whereEqualTo("schoolId", user.schoolId)
                    .whereEqualTo("role", "parent")
                    .whereEqualTo("userId", user.parentDbKey)
            }
            if (docs.isNotEmpty()) {
                return docs.map { it.toInboxMessage() }
                    .sortedByDescending { it.timestamp }
            }
        } catch (e: Exception) {
            android.util.Log.w("InboxDbg", "Firestore inbox query failed, falling back to RTDB", e)
        }

        // 2. RTDB fallback
        val path = Constants.Firebase.messagesInboxPath(
            schoolCode = user.schoolId,
            parentDbKey = user.parentDbKey
        )
        return try {
            firebaseService.readChildren(path)
                .map { (messageId, data) -> InboxMessage.fromMap(messageId, data) }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            android.util.Log.e("InboxDbg", "RTDB inbox read failed", e)
            emptyList()
        }
    }

    /** Convert a Firestore [InboxDoc] into the legacy UI model. */
    private fun InboxDoc.toInboxMessage(): InboxMessage = InboxMessage(
        messageId = conversationId,
        conversationId = conversationId,
        otherName = otherName.ifBlank { otherPartyName },
        studentName = studentName,
        studentClass = studentClass.ifBlank { className },
        lastMessage = lastMessage,
        lastMessageType = lastMessageType,
        timestamp = lastMessageTime,
        unreadCount = unreadCount,
        rawData = mapOf(
            "teacherDbKey" to teacherDbKey,
            "otherDbKey" to otherDbKey,
            "recipientDbKey" to recipientDbKey,
            "otherPartyId" to otherPartyId,
            "otherPartyRole" to otherPartyRole
        )
    )

    /**
     * Observe inbox messages with real-time updates.
     *
     * Phase 5: Firestore snapshot listener on `messageInboxes` filtered
     * by schoolId+role+userId. Updates flow as soon as the admin or
     * teacher writes through Messaging_service.
     */
    fun observeInbox(schoolCode: String, parentDbKey: String): Flow<List<InboxMessage>> {
        return firestoreService.observeQuery(COL_INBOXES) { ref ->
            ref.whereEqualTo("schoolId", schoolCode)
                .whereEqualTo("role", "parent")
                .whereEqualTo("userId", parentDbKey)
        }.map { snapshot ->
            snapshot.toObjects(InboxDoc::class.java)
                .map { it.toInboxMessage() }
                .sortedByDescending { it.timestamp }
        }
    }

    /**
     * Observe inbox using current user context from DataStore.
     */
    fun observeInbox(): Flow<List<InboxMessage>> {
        return tokenManager.user.map { user ->
            if (!user.isLoggedIn || user.schoolId.isBlank() || user.parentDbKey.isBlank()) {
                return@map emptyList()
            }
            try {
                val path = Constants.Firebase.messagesInboxPath(
                    schoolCode = user.schoolId,
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
     * Firestore-first; RTDB fallback for unmigrated conversations.
     */
    suspend fun getChatMessages(conversationId: String): List<ChatMessage> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolId.isBlank()) return emptyList()

        // 1. Firestore primary
        try {
            val docs = firestoreService.queryDocumentsAs<MessageDoc>(COL_MESSAGES) { ref ->
                ref.whereEqualTo("schoolId", user.schoolId)
                    .whereEqualTo("conversationId", conversationId)
            }
            if (docs.isNotEmpty()) {
                return docs.map { it.toChatMessage() }.sortedBy { it.timestamp }
            }
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "Firestore chat query failed, falling back to RTDB", e)
        }

        // 2. RTDB fallback
        val path = Constants.Firebase.chatPath(
            schoolCode = user.schoolId,
            conversationId = conversationId
        )
        return try {
            firebaseService.readChildren(path)
                .map { (messageId, data) -> ChatMessage.fromMap(messageId, data) }
                .sortedBy { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Convert a Firestore [MessageDoc] into the legacy UI model. */
    private fun MessageDoc.toChatMessage(): ChatMessage = ChatMessage(
        messageId = messageId.ifBlank { id },
        senderId = senderId,
        senderName = senderName,
        senderRole = senderRole,
        text = text,
        timestamp = timestamp,
        type = type,
        mediaUrl = mediaUrl.ifBlank { attachmentUrl },
        mediaThumb = mediaThumb,
        fileName = fileName,
        fileSize = fileSize,
        readBy = readBy,
        reaction = reaction,
        isDeleted = isDeleted
    )

    /**
     * Observe chat messages in real-time. Firestore snapshot listener
     * filtered by schoolId+conversationId.
     */
    fun observeChat(schoolCode: String, conversationId: String): Flow<List<ChatMessage>> {
        return firestoreService.observeQuery(COL_MESSAGES) { ref ->
            ref.whereEqualTo("schoolId", schoolCode)
                .whereEqualTo("conversationId", conversationId)
        }.map { snapshot ->
            snapshot.toObjects(MessageDoc::class.java)
                .map { it.toChatMessage() }
                .sortedBy { it.timestamp }
        }
    }

    /**
     * Observe chat using current user context.
     */
    fun observeChat(conversationId: String): Flow<List<ChatMessage>> {
        return tokenManager.user.map { user ->
            if (!user.isLoggedIn || user.schoolId.isBlank()) {
                return@map emptyList<ChatMessage>()
            }
            try {
                val path = Constants.Firebase.chatPath(
                    schoolCode = user.schoolId,
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
     * Mark a conversation as read. Phase 5: Firestore-first via the
     * `messageInboxes` doc, RTDB mirror best-effort. The `messageId`
     * argument is actually the conversationId (legacy naming).
     */
    suspend fun markAsRead(schoolCode: String, parentDbKey: String, messageId: String) {
        val now = System.currentTimeMillis()
        val fields = mapOf(
            "unreadCount" to 0,
            "lastSeenAt" to now
        )
        // 1. Firestore primary
        try {
            firestoreService.setDocument(
                COL_INBOXES,
                inboxDocId(schoolCode, "parent", parentDbKey, messageId),
                fields + mapOf(
                    "schoolId" to schoolCode,
                    "role" to "parent",
                    "userId" to parentDbKey,
                    "conversationId" to messageId
                ),
                merge = true
            )
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "Firestore markAsRead failed", e)
        }
        // 2. RTDB mirror
        try {
            val entryPath = "${Constants.Firebase.messagesInboxPath(schoolCode, parentDbKey)}/$messageId"
            firebaseService.updateChildren(entryPath, fields)
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Mark as read using current user context.
     */
    suspend fun markAsRead(messageId: String) {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolId.isBlank() || user.parentDbKey.isBlank()) return
        markAsRead(user.schoolId, user.parentDbKey, messageId)
    }

    // ── Delete (per-user "Delete chat for me") ───────────────────────────

    /**
     * "Delete for me" — removes only this parent's inbox stub for the
     * conversation. The shared `Conversations/{id}` doc and the chat
     * history under `Chat/{id}` are left intact so the other participant
     * (teacher / admin) still sees the conversation. Mirrors WhatsApp's
     * "Delete chat" behaviour.
     *
     * Best-effort: never throws if the entry was already gone.
     */
    suspend fun deleteConversationForMe(conversationId: String): Result<Unit> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolId.isBlank() || user.parentDbKey.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }
        return try {
            // 1. Firestore primary — delete the inbox stub doc.
            try {
                firestoreService.deleteDocument(
                    COL_INBOXES,
                    inboxDocId(user.schoolId, "parent", user.parentDbKey, conversationId)
                )
            } catch (e: Exception) {
                android.util.Log.w("MessageRepo", "Firestore inbox delete failed", e)
            }
            // 2. RTDB mirror
            try {
                val path = Constants.Firebase.messagesInboxPath(
                    schoolCode = user.schoolId,
                    parentDbKey = user.parentDbKey
                ) + "/$conversationId"
                firebaseService.writeValue(path, null)
            } catch (_: Exception) { /* best-effort */ }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Conversation Bootstrap ───────────────────────────────────────────

    /**
     * Idempotently start (or re-open) a 1:1 conversation between the logged-in
     * parent and a teacher. Designed to be called from "Message Teacher" CTAs
     * where no conversation may exist yet.
     *
     * Writes:
     *  - Conversations/{convId} metadata (only if absent)
     *  - Inbox/parent/{parentDbKey}/{convId} stub with full teacher metadata
     *    so the existing send-flow can find `teacherDbKey` on first message.
     *  - Inbox/teacher/{teacherId}/{convId} stub with full parent/student
     *    metadata so the teacher app's existing inbox listener picks it up.
     *
     * Both stubs use `lastMessage = ""` and `timestamp = 0L` so they don't
     * pollute either inbox until a real message is sent (the inbox UI must
     * filter these out — see [MessagesViewModel.applySearchFilter]).
     *
     * The conversationId is deterministic so the same parent+teacher+student
     * combination always resolves to the same chat — no duplicates.
     */
    suspend fun startConversationWithTeacher(
        teacherId: String,
        teacherName: String,
        teacherProfilePic: String = "",
    ): Result<String> {
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        if (!user.isLoggedIn || user.schoolId.isBlank() || user.parentDbKey.isBlank()) {
            return Result.failure(IllegalStateException("Parent not logged in"))
        }
        if (teacherId.isBlank()) {
            return Result.failure(IllegalArgumentException("teacherId required"))
        }

        return try {
            // Deterministic id — `P` for parent-initiated, includes student id
            // so a parent with multiple children gets one thread per child.
            val convId = "CONV_P_${user.parentDbKey}_T${teacherId}_S${user.userId}"
            val basePath = "Schools/${user.schoolId}/Communication/Messages"
            val convPath = "$basePath/Conversations/$convId"

            // Create the conversation metadata only if it doesn't already
            // exist. We never overwrite an in-flight chat.
            val existing = firebaseService.readMap(convPath)
            if (existing.isEmpty()) {
                val convDoc = mapOf(
                    "schoolId" to user.schoolId,
                    "conversationId" to convId,
                    "participants" to mapOf(
                        user.parentDbKey to "Parent",
                        teacherId to "Teacher"
                    ),
                    "participantNames" to mapOf(
                        user.parentDbKey to user.name,
                        teacherId to teacherName
                    ),
                    "participantIds" to listOf(user.parentDbKey, teacherId),
                    "type" to "direct",
                    "context" to mapOf(
                        "studentId" to user.userId,
                        "studentName" to user.name,
                        "className" to user.className,
                        "section" to user.section
                    ),
                    "teacherDbKey" to teacherId,
                    "createdBy" to user.parentDbKey,
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis(),
                    "status" to "active",
                    "lastMessage" to "",
                    "lastMessageTime" to 0L,
                    "lastSenderId" to "",
                    "lastSenderName" to ""
                )

                // 1. Firestore primary
                try {
                    firestoreService.setDocument(
                        COL_CONVERSATIONS,
                        convDocId(user.schoolId, convId),
                        convDoc,
                        merge = false
                    )
                } catch (e: Exception) {
                    android.util.Log.w("MessageRepo", "Firestore conv create failed", e)
                }

                // 2. RTDB mirror (canonical camelCase)
                firebaseService.writeValue(convPath, convDoc)
            }

            // Parent inbox stub. Must include `teacherDbKey` so that
            // updateInboxAfterSend() can find the recipient on the first
            // text without re-reading conversation metadata.
            val parentInboxPath =
                Constants.Firebase.messagesInboxPath(user.schoolId, user.parentDbKey) + "/$convId"
            val parentInboxFields = mapOf(
                "schoolId" to user.schoolId,
                "role" to "parent",
                "userId" to user.parentDbKey,
                "conversationId" to convId,
                "otherName" to teacherName,
                "otherPartyId" to teacherId,
                "otherPartyName" to teacherName,
                "otherPartyRole" to "Teacher",
                "otherProfilePic" to teacherProfilePic,
                "studentName" to user.name,
                "studentClass" to "${user.className} ${user.section}".trim(),
                "className" to user.className,
                "section" to user.section,
                "lastMessage" to "",
                "lastMessageType" to "text",
                "lastMessageTime" to 0L,
                "lastSeenAt" to 0L,
                "unreadCount" to 0,
                "teacherDbKey" to teacherId,
                "otherDbKey" to teacherId,
                "recipientDbKey" to teacherId
            )
            // 1. Firestore primary
            try {
                firestoreService.setDocument(
                    COL_INBOXES,
                    inboxDocId(user.schoolId, "parent", user.parentDbKey, convId),
                    parentInboxFields,
                    merge = true
                )
            } catch (e: Exception) {
                android.util.Log.w("MessageRepo", "Firestore parent inbox seed failed", e)
            }
            // 2. RTDB mirror
            firebaseService.updateChildren(parentInboxPath, parentInboxFields)

            // We deliberately do NOT seed the teacher inbox here. The
            // existing updateInboxAfterSend() flow creates it from scratch
            // on the first real message — reading `teacherDbKey` out of
            // the parent inbox stub above. Skipping the teacher seed keeps
            // empty placeholder conversations from polluting the teacher's
            // inbox until the parent actually says something.

            Result.success(convId)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        if (!user.isLoggedIn || user.schoolId.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        val now = System.currentTimeMillis()
        val msgId = newMsgId()
        val message = ChatMessage(
            messageId = msgId,
            senderId = user.parentDbKey,
            senderName = user.name,
            senderRole = "parent",
            text = text,
            timestamp = now,
            type = "text",
            replyTo = replyTo,
            readBy = mapOf(user.parentDbKey to true)
        )

        return try {
            // 1. Firestore primary write (canonical).
            val msgPayload = mapOf(
                "schoolId" to user.schoolId,
                "conversationId" to conversationId,
                "messageId" to msgId,
                "senderId" to user.parentDbKey,
                "senderName" to user.name,
                "senderRole" to "parent",
                "text" to text,
                "timestamp" to now,
                "type" to "text",
                "readBy" to mapOf(user.parentDbKey to true)
            )
            firestoreService.setDocument(
                COL_MESSAGES,
                msgDocId(user.schoolId, conversationId, msgId),
                msgPayload,
                merge = false
            )

            // 2. RTDB best-effort mirror so older builds keep working.
            try {
                val chatPath = Constants.Firebase.chatPath(user.schoolId, conversationId)
                firebaseService.writeValue("$chatPath/$msgId", message.toMap())
            } catch (e: Exception) {
                android.util.Log.w("MessageRepo", "RTDB chat mirror failed", e)
            }

            val sentMessage = message

            // Update inbox entries for both sides. The canonical conversation
            // metadata (under Communication/Messages/Conversations) is updated
            // by the admin/teacher writers; we no longer mirror to the orphan
            // legacy path (Schools/{code}/Messages/Conversations) — Phase 4.
            updateInboxAfterSend(
                schoolCode = user.schoolId,
                conversationId = conversationId,
                parentDbKey = user.parentDbKey,
                lastMessage = text,
                lastMessageType = "text",
                timestamp = now
            )

            updateConversationMetadata(
                schoolCode = user.schoolId,
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
        if (!user.isLoggedIn || user.schoolId.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        val now = System.currentTimeMillis()

        val previewText = when (type) {
            "image" -> "\uD83D\uDCF7 Photo"
            "video" -> "\uD83C\uDFA5 Video"
            "file" -> "\uD83D\uDCCE ${fileName ?: "File"}"
            else -> "\uD83D\uDCCE Attachment"
        }

        val msgId = newMsgId()
        val message = ChatMessage(
            messageId = msgId,
            senderId = user.parentDbKey,
            senderName = user.name,
            senderRole = "parent",
            text = previewText,
            timestamp = now,
            type = type,
            mediaUrl = mediaUrl,
            fileName = fileName ?: "",
            fileSize = fileSize ?: 0L,
            readBy = mapOf(user.parentDbKey to true)
        )

        return try {
            // 1. Firestore primary write.
            val msgPayload = mapOf(
                "schoolId" to user.schoolId,
                "conversationId" to conversationId,
                "messageId" to msgId,
                "senderId" to user.parentDbKey,
                "senderName" to user.name,
                "senderRole" to "parent",
                "text" to previewText,
                "timestamp" to now,
                "type" to type,
                "mediaUrl" to mediaUrl,
                "fileName" to (fileName ?: ""),
                "fileSize" to (fileSize ?: 0L),
                "readBy" to mapOf(user.parentDbKey to true)
            )
            firestoreService.setDocument(
                COL_MESSAGES,
                msgDocId(user.schoolId, conversationId, msgId),
                msgPayload,
                merge = false
            )

            // 2. RTDB best-effort mirror.
            try {
                val chatPath = Constants.Firebase.chatPath(user.schoolId, conversationId)
                firebaseService.writeValue("$chatPath/$msgId", message.toMap())
            } catch (e: Exception) {
                android.util.Log.w("MessageRepo", "RTDB media mirror failed", e)
            }

            val sentMessage = message

            updateInboxAfterSend(
                schoolCode = user.schoolId,
                conversationId = conversationId,
                parentDbKey = user.parentDbKey,
                lastMessage = previewText,
                lastMessageType = type,
                timestamp = now
            )

            updateConversationMetadata(
                schoolCode = user.schoolId,
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
        if (!user.isLoggedIn || user.schoolId.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        return try {
            val timestamp = System.currentTimeMillis()
            val fileName = uri.lastPathSegment ?: "file_$timestamp"
            val storagePath = "messages/${user.schoolId}/$conversationId/${timestamp}_$fileName"

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
        if (!user.isLoggedIn || user.schoolId.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        return try {
            val chatPath = Constants.Firebase.chatPath(user.schoolId, conversationId)
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
        if (!user.isLoggedIn || user.schoolId.isBlank()) {
            return Result.failure(IllegalStateException("User not logged in"))
        }

        return try {
            val chatPath = Constants.Firebase.chatPath(user.schoolId, conversationId)
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
        // ── Parent's own inbox stub ─────────────────────────────────────
        val parentInboxFields = mapOf(
            "schoolId" to schoolCode,
            "role" to "parent",
            "userId" to parentDbKey,
            "conversationId" to conversationId,
            "lastMessage" to lastMessage,
            "lastMessageType" to lastMessageType,
            "lastMessageTime" to timestamp,
            "unreadCount" to 0,
            "lastSeenAt" to timestamp
        )

        // 1. Firestore primary
        try {
            firestoreService.setDocument(
                COL_INBOXES,
                inboxDocId(schoolCode, "parent", parentDbKey, conversationId),
                parentInboxFields,
                merge = true
            )
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "Firestore parent inbox merge failed", e)
        }

        // 2. RTDB best-effort mirror
        try {
            val parentInboxPath = Constants.Firebase.messagesInboxPath(schoolCode, parentDbKey)
            val parentInboxEntryPath = "$parentInboxPath/$conversationId"
            firebaseService.updateChildren(parentInboxEntryPath, mapOf(
                "lastMessage" to lastMessage,
                "lastMessageType" to lastMessageType,
                "lastMessageTime" to timestamp,
                "unreadCount" to 0,
                "lastSeenAt" to timestamp
            ))
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "RTDB parent inbox mirror failed", e)
        }

        // ── Teacher inbox stub (the recipient) ─────────────────────────
        // Resolve teacherDbKey from the conversation doc (Phase 4 fix:
        // teacherDbKey now lives on the conversation, not just the parent
        // stub). Falls back to RTDB lookup if Firestore is empty.
        val teacherDbKey = try {
            val convDoc = firestoreService.getDocumentAs<ConversationDoc>(
                COL_CONVERSATIONS, convDocId(schoolCode, conversationId)
            )
            convDoc?.teacherDbKey.orEmpty()
        } catch (_: Exception) { "" }.ifBlank {
            try {
                val parentInboxPath = Constants.Firebase.messagesInboxPath(schoolCode, parentDbKey)
                val data = firebaseService.readMap("$parentInboxPath/$conversationId")
                (data["teacherDbKey"] ?: data["otherDbKey"] ?: data["recipientDbKey"] ?: "").toString()
            } catch (_: Exception) { "" }
        }

        if (teacherDbKey.isBlank()) return

        // Read current unread to increment (Firestore REST has no atomic
        // increment via this client; the read-modify-write race window is
        // tiny in practice).
        val currentUnread = try {
            val existing = firestoreService.getDocumentAs<InboxDoc>(
                COL_INBOXES, inboxDocId(schoolCode, "teacher", teacherDbKey, conversationId)
            )
            existing?.unreadCount ?: 0
        } catch (_: Exception) { 0 }

        val parentInboxStubUser = tokenManager.user.firstOrNull() ?: User.empty()
        val studentNameInbox = parentInboxStubUser.name
        val studentClassInbox = "${parentInboxStubUser.className} ${parentInboxStubUser.section}".trim()

        val teacherInboxFields = mapOf(
            "schoolId" to schoolCode,
            "role" to "teacher",
            "userId" to teacherDbKey,
            "conversationId" to conversationId,
            "otherPartyId" to parentDbKey,
            "otherPartyName" to parentInboxStubUser.name,
            "otherPartyRole" to "Parent",
            "otherName" to parentInboxStubUser.name,
            "className" to parentInboxStubUser.className,
            "section" to parentInboxStubUser.section,
            "studentName" to studentNameInbox,
            "studentClass" to studentClassInbox,
            "lastMessage" to lastMessage,
            "lastMessageType" to lastMessageType,
            "lastMessageTime" to timestamp,
            "lastSenderId" to parentDbKey,
            "lastSenderName" to parentInboxStubUser.name,
            "unreadCount" to (currentUnread + 1)
        )

        // 1. Firestore primary
        try {
            firestoreService.setDocument(
                COL_INBOXES,
                inboxDocId(schoolCode, "teacher", teacherDbKey, conversationId),
                teacherInboxFields,
                merge = true
            )
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "Firestore teacher inbox merge failed", e)
        }

        // 2. RTDB best-effort mirror (legacy alias `lastTimestamp` for
        // older Teacher app builds — Phase 3 schedules its removal).
        try {
            val teacherInboxPath =
                "Schools/$schoolCode/Communication/Messages/Inbox/teacher/$teacherDbKey/$conversationId"
            firebaseService.updateChildren(teacherInboxPath, teacherInboxFields + mapOf(
                "lastTimestamp" to timestamp
            ))
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "RTDB teacher inbox mirror failed", e)
        }
    }

    /**
     * Update the canonical conversation metadata so the admin dashboard
     * and teacher app see the latest preview/timestamp without having to
     * scan the chat history.
     *
     * Path: Schools/{schoolCode}/Communication/Messages/Conversations/{conversationId}
     */
    private suspend fun updateConversationMetadata(
        schoolCode: String,
        conversationId: String,
        lastMessage: String,
        timestamp: Long,
        senderName: String,
        senderId: String
    ) {
        val fields = mapOf(
            "schoolId" to schoolCode,
            "conversationId" to conversationId,
            "lastMessage" to lastMessage,
            "lastMessageTime" to timestamp,
            "lastSenderName" to senderName,
            "lastSenderId" to senderId,
            "updatedAt" to timestamp
        )

        // 1. Firestore primary
        try {
            firestoreService.setDocument(
                COL_CONVERSATIONS,
                convDocId(schoolCode, conversationId),
                fields,
                merge = true
            )
        } catch (e: Exception) {
            android.util.Log.w("MessageRepo", "Firestore conv metadata failed", e)
        }

        // 2. RTDB best-effort mirror
        try {
            val convPath = "Schools/$schoolCode/Communication/Messages/Conversations/$conversationId"
            firebaseService.updateChildren(convPath, fields)
        } catch (_: Exception) {
            // Best-effort — non-fatal
        }
    }
}
