package com.schoolsync.parent.ui.fees

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.firestore.FeeReceiptDoc
import com.schoolsync.parent.data.model.firestore.FeeRefundVoucherDoc
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.util.periodToMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SchoolMetaUi(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val gstin: String = "",
    val logoUrl: String = ""
)

/**
 * Per-month payment status. Drives the "Partial" badge + remaining-
 * balance line in the receipt UI.
 *
 *  - `paidThisReceipt` — amount allocated to that month BY THIS receipt
 *    (sum of `allocated` from feeReceiptAllocations).
 *  - `remainingAfter`  — total balance still owed on that month AFTER
 *    this receipt landed (sum of `balance` across ALL unpaid demands
 *    for that month, read from feeDemands — NOT the allocation doc,
 *    which only lists demands touched by this receipt).
 */
data class MonthAllocation(
    val month: String,
    val paidThisReceipt: Double,
    val remainingAfter: Double
)

/**
 * Per-fee-head allocation for the receipt — drives the breakdown card
 * "Tuition Fee · Rs 500 / Rs 2,000".
 */
data class HeadAllocation(
    val head: String,
    val allocatedThisReceipt: Double,
    val totalAmount: Double
)

data class ReceiptDetailUiState(
    val isLoading: Boolean = true,
    val receipt: FeeReceiptDoc? = null,
    val user: User? = null,
    val schoolMeta: SchoolMetaUi = SchoolMetaUi(),
    /**
     * Per-month allocation breakdown for THIS receipt. Empty list
     * means: either no allocation doc found OR fetch failed (the UI
     * renders without the partial badge in that case).
     */
    val allocations: List<MonthAllocation> = emptyList(),
    /** Per-fee-head allocation for THIS receipt → drives breakdown rows. */
    val headAllocations: List<HeadAllocation> = emptyList(),
    /** Convenience: true when at least one month has remaining > 0. */
    val isPartial: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val tokenManager: TokenManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val receiptId: String = savedStateHandle.get<String>("receiptId") ?: ""

    private val _uiState = MutableStateFlow(ReceiptDetailUiState())
    val uiState: StateFlow<ReceiptDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            if (receiptId.isBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid receipt.") }
                return@launch
            }

            // Refund vouchers live in a different collection — detect by
            // doc-id pattern ("…_REFUND_…") and route accordingly so a
            // tap on a refund row in the Payments list opens a meaningful
            // screen instead of "Receipt not found".
            if (receiptId.contains("_REFUND_")) {
                val rRes = feeFirestoreRepo.getRefundVoucher(receiptId)
                rRes.fold(
                    onSuccess = { refund ->
                        if (refund == null) {
                            _uiState.update { it.copy(isLoading = false, errorMessage = "Refund not found.") }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    receipt = refund.toReceiptDocAdapter(),
                                    user = user
                                )
                            }
                            loadSchoolMeta(user.schoolId)
                            // Refunds don't have allocation/demand docs
                            // of their own — skip the partial-badge
                            // loader. The receipt UI renders cleanly
                            // without it.
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load refund.") }
                    }
                )
                return@launch
            }

            val res = feeFirestoreRepo.getReceipt(receiptId)
            res.fold(
                onSuccess = { doc ->
                    if (doc == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Receipt not found.") }
                    } else {
                        _uiState.update { it.copy(isLoading = false, receipt = doc, user = user) }
                        // Fire-and-forget school meta lookup so the
                        // PDF can include the logo / address / GSTIN.
                        loadSchoolMeta(user.schoolId)
                        // Allocation lookup so the receipt can show the
                        // "Partial" badge + per-month remaining balance.
                        loadAllocationAndDemands(
                            schoolId = user.schoolId,
                            session = user.session,
                            studentId = doc.studentId.ifBlank { user.userId },
                            receiptNo = doc.receiptNo.ifBlank { doc.receiptKey.removePrefix("F") },
                            receiptFeeMonths = doc.feeMonths,
                            feeBreakdown = doc.feeBreakdown
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load receipt.") }
                }
            )
        }
    }

    /**
     * Convert a refund voucher into a FeeReceiptDoc shape so the
     * existing receipt UI can render it unchanged. The values are
     * faithful to the refund data; amount stays NEGATIVE so the
     * existing "%,.2f" format renders "-Rs 49.99" as a clear ledger
     * debit. paymentMode is prefixed with "Refund · " so the mode
     * column is unambiguous.
     */
    private fun FeeRefundVoucherDoc.toReceiptDocAdapter(): FeeReceiptDoc {
        val r = this
        // Label columns: "R10" for the receipt-no chip; original
        // "REFUND_XXX" stays accessible via receiptKey for any PDF /
        // audit path that wants the full identifier.
        val displayReceiptNo = when {
            r.origReceiptNo.isNotBlank() -> "R${r.origReceiptNo}"
            r.refundId.isNotBlank()      -> "R-${r.refundId.takeLast(6).uppercase()}"
            else                         -> "R"
        }
        val modeLabel = if (r.refundMode.isBlank()) {
            "Refund"
        } else {
            "Refund · ${r.refundMode.replaceFirstChar { it.uppercase() }}"
        }
        // Breakdown: one row so the "FEE BREAKDOWN" card shows which
        // head was refunded. Rs amount is the absolute value so the
        // break-down reads naturally ("Computer Fee · Rs 25").
        val headLabel = r.feeTitle.ifBlank { "Refund" }
        val absAmount = kotlin.math.abs(r.amount)
        return FeeReceiptDoc(
            id              = r.id,
            schoolId        = r.id.substringBefore('_'),
            session         = "",
            receiptNo       = displayReceiptNo,
            receiptKey      = r.id.substringAfterLast('_'),   // e.g. "REFUND_69E6356C6D47D"
            studentId       = r.studentId,
            studentName     = r.studentName,
            className       = r.className,
            section         = r.section,
            fatherName      = "",
            amount          = r.amount,
            inputAmount     = r.amount,
            allocatedAmount = r.amount,
            advanceCredit   = 0.0,
            discount        = 0.0,
            fine            = 0.0,
            netAmount       = r.amount,
            paymentMode     = modeLabel,
            feeMonths       = if (r.feeTitle.isNotBlank()) listOf(r.feeTitle) else emptyList(),
            allocatedMonths = emptyList(),
            feeBreakdown    = listOf(
                mapOf("head" to headLabel, "amount" to absAmount)
            ),
            remarks         = buildString {
                if (r.origReceiptNo.isNotBlank()) append("Refund of receipt #${r.origReceiptNo}")
                if (r.reason.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(r.reason)
                }
            },
            collectedBy     = r.processedBy,
            txnId           = r.refundId,
            createdAt       = r.processedAt,
            updatedAt       = r.processedAt
        )
    }

    /**
     * Build the partial/full picture for the receipt by reading TWO
     * docs:
     *
     *   1. `feeReceiptAllocations/{...}` — what THIS receipt paid for
     *      each demand (per-head allocated amount).
     *
     *   2. `feeDemands` for the student in this session — the LIVE
     *      balance for every demand, used to determine whether the
     *      months on this receipt are now fully cleared OR still owe
     *      money on demands NOT touched by this receipt.
     *
     * Why we need both:
     *   The allocation doc only lists demands the receipt actually
     *   touched. If the parent paid Rs 500 against February (covers
     *   only Computer Fee 500), the allocation shows
     *   `Computer balance=0` — but February still owes Library 300 +
     *   Tuition 2000 because those weren't touched. Reading
     *   feeDemands directly catches that.
     *
     * Best-effort: any failure leaves the receipt UI without the
     * partial badge (rather than incorrectly claiming "cleared").
     */
    private fun loadAllocationAndDemands(
        schoolId: String,
        session: String,
        studentId: String,
        receiptNo: String,
        receiptFeeMonths: List<String>,
        feeBreakdown: List<Map<String, Any>>
    ) {
        if (schoolId.isBlank() || session.isBlank() || receiptNo.isBlank()) return
        viewModelScope.launch {
            // ── 1) allocation doc → per-head + per-month breakdown ──
            val perHead = mutableMapOf<String, Double>()      // head → allocated by this receipt
            val perMonthAlloc = mutableMapOf<String, Double>() // month → allocated by this receipt
            try {
                val allocId = "${schoolId}_${session}_F${receiptNo}"
                val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("feeReceiptAllocations").document(allocId).get().await()
                if (snap?.exists() == true) {
                    @Suppress("UNCHECKED_CAST")
                    val rows = (snap.data?.get("allocations") as? List<Map<String, Any?>>) ?: emptyList()
                    for (row in rows) {
                        val period = (row["period"] as? String).orEmpty()
                        // periodToMonth preserves "Yearly Fees" — substringBefore(' ')
                        // chopped it to "Yearly" and the receipt UI then split the
                        // yearly row into a phantom "Yearly" bucket that never
                        // reconciled with the receipt's feeMonths=["Yearly Fees"].
                        val monthName = periodToMonth(period).ifEmpty { period }
                        val head = (row["fee_head"] as? String).orEmpty()
                        val allocated = (row["allocated"] as? Number)?.toDouble() ?: 0.0
                        if (monthName.isNotEmpty()) {
                            perMonthAlloc[monthName] = (perMonthAlloc[monthName] ?: 0.0) + allocated
                        }
                        if (head.isNotEmpty()) {
                            perHead[head] = (perHead[head] ?: 0.0) + allocated
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ReceiptDetailVM", "allocation load failed", e)
            }

            // ── 2) demands → per-month REAL remaining + isPartial ──
            val perMonthRemaining = mutableMapOf<String, Double>()
            try {
                if (studentId.isNotBlank() && receiptFeeMonths.isNotEmpty()) {
                    val demandsSnap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("feeDemands")
                        .whereEqualTo("schoolId", schoolId)
                        .whereEqualTo("session", session)
                        .whereEqualTo("studentId", studentId)
                        .get().await()
                    for (doc in demandsSnap.documents) {
                        val data = doc.data ?: continue
                        val period = (data["period"] as? String).orEmpty()
                        val monthName = periodToMonth(period).ifEmpty { period }
                        if (monthName !in receiptFeeMonths) continue
                        val status = (data["status"] as? String).orEmpty()
                        val balance = (data["balance"] as? Number)?.toDouble() ?: 0.0
                        if (status != "paid" && balance > 0.005) {
                            perMonthRemaining[monthName] = (perMonthRemaining[monthName] ?: 0.0) + balance
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ReceiptDetailVM", "demands load failed", e)
            }

            // ── 3) merge into UI rows ──
            val monthList = receiptFeeMonths.ifEmpty { perMonthAlloc.keys.toList() }
                .map { month ->
                    MonthAllocation(
                        month = month,
                        paidThisReceipt = perMonthAlloc[month] ?: 0.0,
                        remainingAfter = perMonthRemaining[month] ?: 0.0
                    )
                }

            // Per-head: total = head amount from feeBreakdown,
            // allocated = sum from allocations (0 if head wasn't touched).
            val headList = feeBreakdown.mapNotNull { row ->
                if (row !is Map<*, *>) return@mapNotNull null
                val head = (row["head"] as? String) ?: return@mapNotNull null
                val total = ((row["amount"] as? String)?.toDoubleOrNull()
                    ?: (row["amount"] as? Number)?.toDouble()) ?: 0.0
                HeadAllocation(
                    head = head,
                    allocatedThisReceipt = perHead[head] ?: 0.0,
                    totalAmount = total
                )
            }

            _uiState.update {
                it.copy(
                    allocations = monthList,
                    headAllocations = headList,
                    // Partial == any month on this receipt still has
                    // non-zero balance (across ALL its demands, not
                    // just the ones touched by this receipt).
                    isPartial = monthList.any { m -> m.remainingAfter > 0.005 }
                )
            }
        }
    }

    /**
     * Fetch one-line `schools/{schoolId}` metadata used by the PDF
     * header. Best-effort — if Firestore fails the receipt still
     * renders, just without the logo / address / GSTIN block.
     */
    private fun loadSchoolMeta(schoolId: String) {
        if (schoolId.isBlank()) return
        viewModelScope.launch {
            try {
                val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("schools").document(schoolId).get().await()
                val data = snap?.data ?: return@launch
                val meta = SchoolMetaUi(
                    name    = (data["name"] as? String).orEmpty(),
                    address = listOfNotNull(
                        data["address"] as? String,
                        data["city"]    as? String,
                        data["state"]   as? String,
                        data["pincode"] as? String
                    ).filter { it.isNotBlank() }.joinToString(", "),
                    phone   = (data["phone"]   as? String).orEmpty(),
                    email   = (data["email"]   as? String).orEmpty(),
                    gstin   = (data["gstin"]   as? String).orEmpty(),
                    logoUrl = (data["logoUrl"] as? String
                        ?: data["logo"] as? String
                        ?: "").trim()
                )
                _uiState.update { it.copy(schoolMeta = meta) }
            } catch (e: Exception) {
                android.util.Log.w("ReceiptDetailVM", "schoolMeta load failed", e)
            }
        }
    }
}
