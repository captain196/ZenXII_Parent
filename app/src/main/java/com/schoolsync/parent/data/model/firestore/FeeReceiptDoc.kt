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
    /** Server-allocated accounting number, e.g. "2026-27/000001". Blank on
     *  receipts written before the per-session series existed. */
    val receiptDisplayNo: String = "",
    /** The session the accounting number belongs to, e.g. "2026-27". */
    val receiptSeries: String = "",
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
) {
    /**
     * The identifier a human should see and quote — ALWAYS `receiptKey`.
     *
     * ── WHY THIS EXISTS ──────────────────────────────────────────────────
     * A fee receipt carries two identifiers and they are not interchangeable:
     *
     *   receiptNo   "1"   the school-scoped sequence number
     *   receiptKey  "F1"  'F' + receiptNo. The F marks it a FEE receipt, as
     *                     opposed to an admission receipt, which numbers
     *                     separately. This is the PERSISTED key: the
     *                     feeReceipts doc id, the feeReceiptAllocations doc
     *                     id, the journal's JE_FEE_{key} and source_ref, the
     *                     processedPayments marker — and it is what BOTH admin
     *                     surfaces (online payments, reconciliation) display.
     *
     * The app showed them inconsistently: the receipt screen rendered "#1"
     * while its own Copy button copied "F1", and the PDF printed
     * "Receipt #: 1". A parent quoting "receipt 1" to an office that searches
     * "F1" is a support failure, and "1" alone is ambiguous across receipt
     * types.
     *
     * Falls back to a reconstructed key, then to blank, so a malformed
     * document degrades rather than crashing.
     */
    val displayReceiptNo: String
        get() = when {
            // The accounting number, when the server allocated one:
            // "2026-27/000001". Restarts each session per Indian practice
            // (Rule 46(b) CGST — a serial unique within the financial year),
            // which is why it can NEVER be used as a document id.
            receiptDisplayNo.isNotBlank() -> receiptDisplayNo
            // Receipts written before that series existed keep showing their
            // key. They are deliberately not retro-numbered — a stored
            // financial identifier is not rewritten to tidy a display.
            receiptKey.isNotBlank()       -> receiptKey
            receiptNo.isNotBlank()        -> "F$receiptNo"
            else                          -> ""
        }
}
