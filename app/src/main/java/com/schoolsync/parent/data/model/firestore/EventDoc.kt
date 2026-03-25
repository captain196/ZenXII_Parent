package com.schoolsync.parent.data.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Represents a school event in Firestore.
 *
 * Collection: `events`
 * Doc ID: auto-generated or `{schoolId}_{eventId}`
 */
data class EventDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val startDate: String = "",       // "2026-04-15"
    val endDate: String = "",
    val location: String = "",
    val status: String = "upcoming",  // "upcoming", "ongoing", "completed", "cancelled"
    val mediaUrls: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
)
