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
    /** True once this parent has opened the notice (from circularReads). */
    val isRead: Boolean = false,
)
