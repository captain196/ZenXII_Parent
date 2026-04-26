package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

/**
 * Represents a fee payment receipt for a student.
 *
 * Collection: `feeReceipts`
 * Doc ID: auto-generated or receipt number.
 */
data class FeeReceiptDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val session: String = "",
    val receiptNo: String = "",
    val receiptKey: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val fatherName: String = "",
    val amount: Double = 0.0,
    // Phase 11 standardized money fields (server dual-emits snake +
    // camel; Firestore Kotlin SDK matches camelCase). amount stays as
    // alias of inputAmount for back-compat. allocatedAmount is what
    // actually went to demands; advanceCredit is a legacy field that
    // pre-dates overpayment rejection (always 0 for new receipts, kept
    // so historical receipts still render the carry-forward line).
    val inputAmount: Double = 0.0,
    val allocatedAmount: Double = 0.0,
    val advanceCredit: Double = 0.0,
    val discount: Double = 0.0,
    val fine: Double = 0.0,
    val netAmount: Double = 0.0,
    val paymentMode: String = "",
    val feeMonths: List<String> = emptyList(),
    /** After Phase 11 patch this reflects the actual allocated months
     *  (post-allocation truth). Falls back to feeMonths when missing. */
    val allocatedMonths: List<String> = emptyList(),
    val feeBreakdown: List<Map<String, Any>> = emptyList(),
    val remarks: String = "",
    val collectedBy: String = "",
    /** Server-side transaction reference (e.g. "RZP_20260419_ab12cd"
     *  for Razorpay; "TXN_20260419..." for cash). Shown in receipt UI
     *  for trust + customer-support reference. */
    val txnId: String = "",
    val createdAt: Any? = null,
    val updatedAt: Any? = null
)
