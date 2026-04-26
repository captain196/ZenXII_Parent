package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Firestore representation of a chat conversation.
 *
 * Collection: `conversations`
 * Doc id:     {schoolId}_{conversationId}
 *
 * Mirrors the canonical schema written by Messaging_service.php on the
 * admin side. Field names are the camelCase contract from Phase 1-4.
 */
data class ConversationDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val conversationId: String = "",
    /** uid → role label, e.g. {"STF0001":"Teacher","P0001":"Parent"} */
    val participants: Map<String, String> = emptyMap(),
    /** uid → display name */
    val participantNames: Map<String, String> = emptyMap(),
    /** Flat array form of participants.keys — enables array-contains dedup queries */
    val participantIds: List<String> = emptyList(),
    val type: String = "direct",
    val title: String = "",
    val context: Map<String, Any?> = emptyMap(),
    val teacherDbKey: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastSenderId: String = "",
    val lastSenderName: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val status: String = "active"
) {
    /** studentId from the nested context map, with empty fallback. */
    val studentId: String get() = (context["studentId"] as? String).orEmpty()
    /** className from the nested context map. */
    val className: String get() = (context["className"] as? String).orEmpty()
    /** section from the nested context map. */
    val section: String get() = (context["section"] as? String).orEmpty()
}
