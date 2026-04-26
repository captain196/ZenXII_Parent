package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Carry-forward dues from a previous session.
 * Collection: `feeCarryForward`
 * Doc ID: `{schoolId}_{session}_{studentId}`
 */
data class FeeCarryForwardDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val previousSession: String = "",
    val studentId: String = "",
    val totalDues: Double = 0.0,
    val unpaidDetails: Map<String, Map<String, Any>> = emptyMap(),
    val carriedAt: String = "",
    val carriedBy: String = ""
)
