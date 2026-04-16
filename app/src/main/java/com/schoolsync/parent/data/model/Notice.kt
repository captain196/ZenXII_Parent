package com.schoolsync.parent.data.model

/**
 * Notice / announcement from the school.
 * Path: Schools/{schoolCode}/Communication/Notices/{noticeId}
 */
data class Notice(
    val noticeId: String = "",
    val title: String = "",
    val body: String = "",
    val bodyHtml: String = "",        // Rich HTML variant for detail view when present
    val date: String = "",
    val timestamp: Long = 0L,
    val author: String = "",
    val authorRole: String = "",       // e.g. "Admin", "HR Manager"
    val category: String = "",
    val attachmentUrl: String = "",
    val priority: String = "Normal",  // Normal, Important, Urgent
    val targetAudience: String = "",  // All, Parents, Students, Teachers
    val rawData: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun fromMap(noticeId: String, data: Map<String, Any?>): Notice {
            return Notice(
                noticeId = noticeId,
                title = (data["title"] ?: data["Title"] ?: "").toString(),
                body = (data["body"] ?: data["Body"] ?: data["message"] ?: data["Message"] ?: "").toString(),
                date = (data["date"] ?: data["Date"] ?: "").toString(),
                timestamp = when (val ts = data["timestamp"] ?: data["Timestamp"]) {
                    is Number -> ts.toLong()
                    is String -> ts.toLongOrNull() ?: 0L
                    else -> 0L
                },
                author = (data["author"] ?: data["Author"] ?: data["posted_by"] ?: "").toString(),
                category = (data["category"] ?: data["Category"] ?: "").toString(),
                attachmentUrl = (data["attachment"] ?: data["Attachment"] ?: data["attachment_url"] ?: "").toString(),
                priority = (data["priority"] ?: data["Priority"] ?: "Normal").toString(),
                targetAudience = (data["target"] ?: data["Target"] ?: data["audience"] ?: "All").toString(),
                rawData = data
            )
        }
    }
}
