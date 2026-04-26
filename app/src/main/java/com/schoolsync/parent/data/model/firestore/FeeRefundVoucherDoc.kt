package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Refund voucher written by Fee_refund_service when an admin processes
 * an approved refund. The doc lets the parent app surface refunds as
 * ledger entries alongside normal receipts on the Payments screen.
 *
 * Collection: `feeRefundVouchers`
 * Doc ID pattern: `{schoolId}_{session}_{refundReceiptKey}` where
 *   refundReceiptKey = "REFUND_" + refund-id-suffix (e.g. REFUND_69E60BA00D75C).
 *
 * Amount is stored as a NEGATIVE value to mirror classic ledger math
 * (money leaving the school). The receipt display layer should either
 * `abs(amount)` when rendering the amount, or render the negative
 * verbatim as a visual cue.
 */
data class FeeRefundVoucherDoc(
    @DocumentId
    val id: String = "",
    val type: String = "refund",
    val refundId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val feeTitle: String = "",
    /** Negative. Absolute value = rupees refunded to the parent. */
    val amount: Double = 0.0,
    /** "cash" | "bank_transfer" | "cheque" | "online" */
    val refundMode: String = "",
    val origReceiptNo: String = "",
    val reason: String = "",
    val processedBy: String = "",
    /** ISO 8601 string. */
    val processedAt: String = ""
)
