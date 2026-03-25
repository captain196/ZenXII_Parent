package com.schoolsync.parent.data.model

data class InboxMessage(
    val messageId: String = "",
    val conversationId: String = "",
    val otherName: String = "",        // teacher name
    val otherProfilePic: String = "",
    val studentName: String = "",
    val studentClass: String = "",
    val lastMessage: String = "",
    val lastMessageType: String = "text",  // text, image, video, file
    val timestamp: Long = 0L,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val rawData: Map<String, Any?> = emptyMap()
) {
    val initials: String get() {
        val parts = otherName.split(" ").filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    companion object {
        fun fromMap(messageId: String, data: Map<String, Any?>): InboxMessage {
            // Support multiple field name conventions
            return InboxMessage(
                messageId = messageId,
                conversationId = (data["conversationId"] ?: data["conversation_id"] ?: messageId).toString(),
                otherName = (data["otherName"] ?: data["senderName"] ?: data["sender_name"] ?: data["from"] ?: "").toString(),
                otherProfilePic = (data["otherProfilePic"] ?: data["profilePic"] ?: "").toString(),
                studentName = (data["studentName"] ?: data["student_name"] ?: "").toString(),
                studentClass = (data["studentClass"] ?: data["student_class"] ?: "").toString(),
                lastMessage = (data["lastMessage"] ?: data["last_message"] ?: data["message"] ?: "").toString(),
                lastMessageType = (data["lastMessageType"] ?: "text").toString(),
                timestamp = when (val ts = data["lastMessageTime"] ?: data["timestamp"] ?: data["Timestamp"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                unreadCount = when (val uc = data["unreadCount"] ?: data["unread_count"] ?: data["unread"]) {
                    is Number -> uc.toInt()
                    is String -> uc.toIntOrNull() ?: 0
                    else -> 0
                },
                rawData = data
            )
        }
    }
}

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = 0L,
    val type: String = "text",          // text, image, video, file
    val mediaUrl: String = "",
    val mediaThumb: String = "",        // thumbnail for video
    val fileName: String = "",
    val fileSize: Long = 0L,
    val isRead: Boolean = false,
    val readBy: Map<String, Boolean> = emptyMap(),
    val replyTo: ReplyInfo? = null,
    val reaction: String = "",          // emoji reaction
    val isDeleted: Boolean = false,
    val rawData: Map<String, Any?> = emptyMap()
) {
    val isMedia: Boolean get() = type == "image" || type == "video"
    val isFile: Boolean get() = type == "file"

    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "senderId" to senderId,
            "senderName" to senderName,
            "message" to text,
            "timestamp" to timestamp,
            "type" to type,
            "readBy" to readBy,
        )
        if (mediaUrl.isNotBlank()) map["mediaUrl"] = mediaUrl
        if (mediaThumb.isNotBlank()) map["mediaThumb"] = mediaThumb
        if (fileName.isNotBlank()) map["fileName"] = fileName
        if (fileSize > 0) map["fileSize"] = fileSize
        if (replyTo != null) {
            map["replyTo"] = mapOf(
                "messageId" to replyTo.messageId,
                "text" to replyTo.text,
                "senderName" to replyTo.senderName
            )
        }
        if (reaction.isNotBlank()) map["reaction"] = reaction
        return map
    }

    companion object {
        fun fromMap(messageId: String, data: Map<String, Any?>): ChatMessage {
            // Parse replyTo
            val replyData = data["replyTo"] as? Map<*, *>
            val reply = if (replyData != null) {
                ReplyInfo(
                    messageId = replyData["messageId"]?.toString() ?: "",
                    text = replyData["text"]?.toString() ?: "",
                    senderName = replyData["senderName"]?.toString() ?: ""
                )
            } else null

            // Parse readBy
            val readByRaw = data["readBy"] as? Map<*, *>
            val readBy = readByRaw?.mapNotNull { (k, v) ->
                val key = k?.toString() ?: return@mapNotNull null
                key to (v == true || v?.toString() == "true")
            }?.toMap() ?: emptyMap()

            return ChatMessage(
                messageId = messageId,
                senderId = (data["senderId"] ?: data["sender_id"] ?: data["from"] ?: "").toString(),
                senderName = (data["senderName"] ?: data["sender_name"] ?: "").toString(),
                text = (data["text"] ?: data["message"] ?: data["body"] ?: "").toString(),
                timestamp = when (val ts = data["timestamp"] ?: data["Timestamp"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                type = (data["type"] ?: "text").toString(),
                mediaUrl = (data["mediaUrl"] ?: data["attachmentUrl"] ?: data["attachment_url"] ?: "").toString(),
                mediaThumb = (data["mediaThumb"] ?: data["thumbnail"] ?: "").toString(),
                fileName = (data["fileName"] ?: data["file_name"] ?: "").toString(),
                fileSize = when (val fs = data["fileSize"] ?: data["file_size"]) {
                    is Number -> fs.toLong()
                    else -> 0L
                },
                isRead = readBy.isNotEmpty(),
                readBy = readBy,
                replyTo = reply,
                reaction = (data["reaction"] ?: "").toString(),
                isDeleted = data["isDeleted"] == true,
                rawData = data
            )
        }
    }
}

data class ReplyInfo(
    val messageId: String = "",
    val text: String = "",
    val senderName: String = ""
)
