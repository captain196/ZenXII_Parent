package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class CircularDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val title: String = "",
    val body: String = "",
    // Optional rich HTML (e.g. HR styled poster). Rendered in WebView on detail when present.
    val description: String = "",
    val author: String = "",
    val authorId: String = "",
    val authorRole: String = "",         // e.g. "Admin", "HR Manager", "Principal"
    val category: String = "",        // General, Academic, Event, Administrative, Emergency
    val priority: String = "Normal",  // Normal, Important, Urgent
    val targetType: String = "All",   // All, class, role
    val targetClasses: List<String> = emptyList(),
    val targetRoles: List<String> = emptyList(),
    val attachmentUrl: String = "",
    val requireAcknowledgement: Boolean = false,
    val totalRecipients: Int = 0,
    val readCount: Int = 0,
    val channels: List<String> = emptyList(),
    val status: String = "sent",      // draft, sent
    val sentAt: Any? = null,
    val expiresAt: Any? = null
)
