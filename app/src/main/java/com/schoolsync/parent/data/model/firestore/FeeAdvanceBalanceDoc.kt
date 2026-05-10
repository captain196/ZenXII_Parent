package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Student's advance (wallet) balance.
 * Collection: `studentAdvanceBalances`
 * Doc ID: `{schoolId}_{studentId}`
 */
data class FeeAdvanceBalanceDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val amount: Double = 0.0,
    val lastReceipt: String = "",
    val lastRefund: String = "",
    val updatedAt: String = ""
)
