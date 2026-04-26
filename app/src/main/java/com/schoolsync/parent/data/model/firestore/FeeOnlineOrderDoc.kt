package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Online payment order tracking.
 * Collection: `feeOnlineOrders`
 * Doc ID: `{schoolId}_{orderId}`
 */
data class FeeOnlineOrderDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val amount: Double = 0.0,
    val status: String = "",      // requested, order_created, paying, completed, failed
    val gatewayOrderId: String = "",
    val gatewayPaymentId: String = "",
    val receiptNo: String = "",
    val createdAt: String = "",
    val completedAt: String = ""
)
