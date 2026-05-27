package com.schoolsync.parent.ui.fees

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.model.FeeHead
import com.schoolsync.parent.data.model.FeeHeadDue
import com.schoolsync.parent.data.model.FeeOverview
import com.schoolsync.parent.data.model.FeePayment
import com.schoolsync.parent.data.model.FeeStructure
import com.schoolsync.parent.data.model.PendingFees
import com.schoolsync.parent.data.model.PendingMonth
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.payment.PaymentBridge
import com.schoolsync.parent.data.payment.PaymentSession
import com.schoolsync.parent.data.remote.CreateOrderRequest
import com.schoolsync.parent.data.remote.CreateOrderResponse
import com.schoolsync.parent.data.remote.FeesApi
import com.schoolsync.parent.data.remote.VerifyPaymentRequest
import com.schoolsync.parent.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.parent.util.friendlyErrorMessage
import com.schoolsync.parent.util.toDateOrNull
import com.schoolsync.parent.util.toEpochMillisOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class FeesTab(val title: String) {
    PENDING("Pending Dues"),
    PAYMENTS("Payments"),
    DISCOUNTS("Discounts"),
    STRUCTURE("Fee Structure"),
}

/**
 * One-shot command to the screen: "open Razorpay checkout with these
 * options." Emitted after a successful order-creation, consumed by
 * FeesScreen's LaunchedEffect.
 */
data class CheckoutRequest(
    val apiKey: String,
    val orderId: String,
    val amountPaise: Long,
    val currency: String,
    val name: String,
    val description: String,
    val prefillEmail: String,
    val prefillContact: String,
    val themeColor: String = "#0F766E"
)

data class PaymentFailure(
    val code: Int = 0,
    val description: String = "",
    /** The months the user was trying to pay — carried through so a
     *  one-tap retry works without re-selecting. */
    val failedMonths: List<String> = emptyList(),
    /** Optional custom amount if it was a partial payment. */
    val failedAmount: Double? = null
)

data class FeesUiState(
    val isLoading: Boolean = true,
    /** Distinct from `isLoading` — true while a pull-to-refresh
     *  gesture is in progress. UI shows the spinner over existing
     *  content rather than swapping it for a skeleton. */
    val isRefreshing: Boolean = false,
    /**
     * Months the user JUST paid for but where the server pipeline
     * hasn't yet pushed the updated demand snapshot. Rendered in a
     * muted "processing…" visual state so the parent sees immediate
     * feedback instead of waiting 10–40 s for the Firestore listener
     * to catch up on slow networks. Cleared automatically when the
     * demand listener reports the months as `paid`/`partial` OR when
     * the verify call returns an error (rollback).
     */
    val pendingConfirmMonths: List<String> = emptyList(),
    // Opens on Pending Dues by default — paying is the primary action;
    // users navigating to Fees almost always want to pay, not read the
    // structure. They can still switch to Fee Structure / Payments tabs.
    val selectedTab: FeesTab = FeesTab.PENDING,
    val overview: FeeOverview = FeeOverview(),
    val errorMessage: String? = null,
    /**
     * Soft "syncing" message shown above the tabs when the gateway
     * captured a payment but the server-side receipt write is still
     * pending (admin reconciliation is replaying it). Distinct from
     * `errorMessage` so the UI can render it in a calm tone instead of
     * red, without dismissing it on the next snackbar.
     */
    val pendingSyncMessage: String? = null,
    val paymentInProgress: Boolean = false,
    /**
     * True iff the global [PaymentFlowOverlay] is currently covering
     * the screen — i.e. PaymentSession is in Verifying or Confirming.
     * Distinct from [paymentInProgress], which is ALSO true during the
     * Razorpay-checkout phase (before the overlay appears). FeesScreen
     * uses this to suppress its in-button spinner / dot animation
     * during the verify window so the overlay is the sole UX surface
     * for those states.
     */
    val isPaymentOverlayActive: Boolean = false,
    val paymentStatus: String? = null,
    val lastReceiptNo: String? = null,
    /** Structured failure for the AlertDialog; null means no active failure. */
    val paymentFailure: PaymentFailure? = null,
    /** Months currently ticked on the Pending Dues tab. Lifted to the
     *  ViewModel so a pull-to-refresh can clear it (the user expects
     *  refresh = reset selection) and so it survives recomposition. */
    val selectedMonths: List<String> = emptyList(),
    /** Per-section Firestore error messages. null = no error. Populated
     *  whenever a listener emits FeeDataState.Error; cleared on success.
     *  UI renders these as error banners with retry in each tab. */
    val demandsError:   String? = null,
    val paymentsError:  String? = null,
    val defaulterError: String? = null,
)

@HiltViewModel
class FeesViewModel @Inject constructor(
    private val feeFirestoreRepo: FeeFirestoreRepository,
    private val tokenManager: TokenManager,
    private val feesApi: FeesApi,
    private val paymentSession: PaymentSession,
    private val badgeBus: com.schoolsync.parent.util.BadgeBus,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeesUiState())
    val uiState: StateFlow<FeesUiState> = _uiState.asStateFlow()

    private val _checkoutRequests = Channel<CheckoutRequest>(Channel.BUFFERED)
    val checkoutRequests = _checkoutRequests.receiveAsFlow()

    private var lastAttemptedMonths: List<String> = emptyList()
    private var lastAttemptedAmount: Double? = null

    /**
     * Cached details of the most recently created Razorpay order — used
     * by PaymentFailureDialog "Retry" to relaunch checkout against the
     * SAME orderId instead of cutting a new one. Server-side dedup
     * (`Payment_service::_find_pending_order`) covers the case where we
     * lose this in-memory cache (e.g. after process death), but the
     * client retry without re-`createOrder` is one fewer network hop.
     */
    private data class CachedCheckout(
        val apiKey: String,
        val orderId: String,
        val amountPaise: Long,
        val currency: String,
        val description: String
    )
    private var lastCheckout: CachedCheckout? = null

    /** Tracks the userId we last loaded fees for so we can detect a
     *  sibling switch and fully reset payment state. */
    private var lastLoadedStudentId: String = ""

    /**
     * Single source of truth for "which student does this VM care about
     * right now?". Drives every live listener through `flatMapLatest`,
     * so a sibling switch (TokenManager.user.userId changes) atomically
     * detaches the previous student's listeners and attaches the new
     * student's — no manual re-registration needed.
     */
    private val effectiveStudentId: StateFlow<String> = tokenManager.user
        .map { it.userId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        observePaymentBridge()
        observePaymentSession()
        observeUserChanges()
        observeFeeDemandsLive()
        observeDefaulterLive()
        observeReceiptsLive()
        // One-shot for slow-changing slices (fee structure, scholarships,
        // carry-forward). Listeners cover the high-churn data above.
        loadStaticFeesData()
    }

    /**
     * Bridge PaymentSession's app-scoped state into our screen state.
     * Survives VM destruction: a new VM created after the user navigated
     * away mid-payment will see the latest outcome here and surface the
     * snackbar / error / pending banner accordingly.
     */
    private fun observePaymentSession() {
        viewModelScope.launch {
            paymentSession.state.collect { sessionState ->
                // `pendingConfirmMonths` (the per-row "Processing…"
                // chip) is now derived from PaymentSession state, NOT
                // stored locally in the VM. This way a brand-new VM
                // instance — created after the user navigated away
                // from Fees and back during a payment — still shows the
                // chip correctly because it just reads it off the
                // app-singleton PaymentSession.
                val processingMonths = when (sessionState) {
                    is PaymentSession.State.Verifying  -> sessionState.attemptedMonths
                    is PaymentSession.State.Confirming -> sessionState.attemptedMonths
                    is PaymentSession.State.Pending    -> sessionState.attemptedMonths
                    else -> emptyList()
                }
                when (sessionState) {
                    is PaymentSession.State.Idle -> {
                        _uiState.update {
                            it.copy(
                                pendingConfirmMonths = processingMonths,
                                isPaymentOverlayActive = false
                            )
                        }
                    }
                    is PaymentSession.State.Verifying -> {
                        // Snackbar text is suppressed during overlay
                        // states — PaymentVerifyScreen is the canonical
                        // surface; an additional snackbar would queue
                        // up behind the opaque overlay and reappear
                        // when it closes, fighting the success screen.
                        _uiState.update {
                            it.copy(
                                paymentInProgress = true,
                                isPaymentOverlayActive = true,
                                paymentStatus = null,
                                pendingConfirmMonths = processingMonths
                            )
                        }
                    }
                    is PaymentSession.State.Confirming -> {
                        _uiState.update {
                            it.copy(
                                paymentInProgress = true,
                                isPaymentOverlayActive = true,
                                paymentStatus = null,
                                pendingConfirmMonths = processingMonths
                            )
                        }
                    }
                    is PaymentSession.State.Success -> {
                        // The Success screen is now rendered by
                        // PaymentFlowOverlay (full-screen). We MUST NOT
                        // also show a snackbar here — the overlay is
                        // the canonical surface for success now. We
                        // just clean up local state (status text +
                        // chip) and let the overlay take over.
                        Log.i("FeesVM", "[SESSION SUCCESS] receipt=${sessionState.details.receiptNo} alreadyPaid=${sessionState.details.alreadyPaid} confirmed=${sessionState.details.confirmedFromBackend}")
                        _uiState.update {
                            it.copy(
                                paymentInProgress = false,
                                isPaymentOverlayActive = false,
                                paymentStatus = null,
                                lastReceiptNo = sessionState.details.receiptNo.takeIf { rn -> rn.isNotBlank() },
                                pendingConfirmMonths = emptyList()
                            )
                        }
                        // The overlay's Done button calls
                        // acknowledgeOutcome — we DO NOT auto-ack from
                        // the VM, because that would dismiss the
                        // overlay before the user can read it.
                    }
                    is PaymentSession.State.Pending -> {
                        Log.w("FeesVM", "[SESSION PENDING] msg=${sessionState.message}")
                        _uiState.update {
                            it.copy(
                                paymentInProgress = false,
                                isPaymentOverlayActive = false,
                                paymentStatus = null,
                                pendingSyncMessage = sessionState.message,
                                // Keep chip visible — server will replay
                                // and the demand listener will clear it
                                // when the receipt finally lands.
                                pendingConfirmMonths = processingMonths
                            )
                        }
                        paymentSession.acknowledgeOutcome()
                    }
                    is PaymentSession.State.Failure -> {
                        Log.e("FeesVM", "[SESSION FAILURE] msg=${sessionState.message}")
                        _uiState.update {
                            it.copy(
                                paymentInProgress = false,
                                isPaymentOverlayActive = false,
                                paymentStatus = null,
                                errorMessage = sessionState.message,
                                pendingConfirmMonths = emptyList()
                            )
                        }
                        paymentSession.acknowledgeOutcome()
                    }
                }
            }
        }
    }

    // ── Live listeners (push-based; replace the old loadFees pull pattern) ──

    // Track listener jobs so pull-refresh can cancel + reattach them,
    // recovering any Flow that the repo-level .catch absorbed on failure.
    private var demandsListenerJob:   kotlinx.coroutines.Job? = null
    private var defaulterListenerJob: kotlinx.coroutines.Job? = null
    private var receiptsListenerJob:  kotlinx.coroutines.Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFeeDemandsLive() {
        demandsListenerJob?.cancel()
        demandsListenerJob = viewModelScope.launch {
            effectiveStudentId
                .filter { it.isNotBlank() }
                .flatMapLatest { sid ->
                    feeFirestoreRepo.observeFeeDemands(sid).map { state -> sid to state }
                }
                .collect { (sid, state) ->
                    when (state) {
                        is com.schoolsync.parent.data.model.FeeDataState.Loading -> {
                            _uiState.update { it.copy(isLoading = true, demandsError = null) }
                        }
                        is com.schoolsync.parent.data.model.FeeDataState.Error -> {
                            Log.w("FeesVM", "[DEMANDS ERROR] sid=$sid", state.cause)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    demandsError = state.message,
                                )
                            }
                        }
                        is com.schoolsync.parent.data.model.FeeDataState.Data -> {
                            val demands = state.value
                            val pending = mapDemandsToPendingFees(demands, sid)
                            val unpaidCount = pending.pendingMonths.count { it.status != "Paid" }
                            Log.i("FeesVM", "[DEMANDS PUSH] sid=$sid total_demands=${demands.size} pending_months=$unpaidCount total_due=${pending.totalPending}")
                            badgeBus.setCount("fees", unpaidCount)
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    demandsError = null,
                                    overview = it.overview.copy(pendingFees = pending)
                                )
                            }
                        }
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDefaulterLive() {
        defaulterListenerJob?.cancel()
        defaulterListenerJob = viewModelScope.launch {
            effectiveStudentId
                .filter { it.isNotBlank() }
                .flatMapLatest { sid -> feeFirestoreRepo.observeDefaulterStatus(sid) }
                .collect { state ->
                    when (state) {
                        is com.schoolsync.parent.data.model.FeeDataState.Loading -> { /* no-op */ }
                        is com.schoolsync.parent.data.model.FeeDataState.Error -> {
                            Log.w("FeesVM", "defaulter stream errored", state.cause)
                            _uiState.update { it.copy(defaulterError = state.message) }
                        }
                        is com.schoolsync.parent.data.model.FeeDataState.Data -> {
                            Log.d("FeesVM", "defaulter snapshot: dues=${state.value?.totalDues ?: 0.0}")
                            _uiState.update { it.copy(defaulterError = null) }
                        }
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeReceiptsLive() {
        receiptsListenerJob?.cancel()
        receiptsListenerJob = viewModelScope.launch {
            effectiveStudentId
                .filter { it.isNotBlank() }
                .flatMapLatest { sid ->
                    // Payment history is now a sealed FeeDataState; refunds
                    // still plain list. combine() keeps emitting when either
                    // stream errors because each branch self-recovers.
                    combine(
                        feeFirestoreRepo.observePaymentHistory(sid),
                        feeFirestoreRepo.observeRefundVouchers(sid)
                    ) { paymentsState, refunds ->
                        Triple(paymentsState, refunds, sid)
                    }
                }
                .collect { (paymentsState, refunds, sid) ->
                    when (paymentsState) {
                        is com.schoolsync.parent.data.model.FeeDataState.Loading -> { /* keep existing UI */ }
                        is com.schoolsync.parent.data.model.FeeDataState.Error -> {
                            Log.w("FeesVM", "payment history stream errored", paymentsState.cause)
                            _uiState.update { it.copy(paymentsError = paymentsState.message) }
                        }
                        is com.schoolsync.parent.data.model.FeeDataState.Data -> {
                            val receipts = paymentsState.value
                            val mapped = mapReceiptsToPayments(receipts)
                            val merged = (mapped + mapRefundsToPayments(refunds))
                                .sortedByDescending { parsePaymentDate(it.date) }
                            com.schoolsync.parent.util.debugLog(
                                "observeReceipts: receipts=${receipts.size} mapped=${mapped.size} refunds=${refunds.size} merged=${merged.size}"
                            )
                            _uiState.update {
                                it.copy(
                                    paymentsError = null,
                                    overview = it.overview.copy(paymentHistory = merged)
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Parse the "dd MMM yyyy" display-date string back into an epoch
     * for chronological sort. Returns 0L on any failure so bad rows
     * sink to the bottom rather than crash the list.
     */
    private fun parsePaymentDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                .parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Map refund vouchers into FeePayment rows so they render in the
     * same Payments list as normal receipts. Amount kept negative for
     * an immediate visual cue; mode prefixed with "Refund · ".
     */
    private fun mapRefundsToPayments(
        refunds: List<com.schoolsync.parent.data.model.firestore.FeeRefundVoucherDoc>
    ): List<FeePayment> {
        return refunds.mapNotNull { refund ->
            try {
                val epoch = runCatching {
                    java.time.Instant.parse(refund.processedAt).toEpochMilli()
                }.getOrNull()
                val dateStr = epoch?.let {
                    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(it))
                } ?: ""

                val modeLabel = if (refund.refundMode.isBlank()) {
                    "Refund"
                } else {
                    "Refund · ${refund.refundMode.replaceFirstChar { it.uppercase() }}"
                }

                // Receipt-no column: normal receipts are "F10", "F9"…
                // Refunds mirror that with an "R" prefix pegged to the
                // ORIGINAL receipt being reversed (R10 = refund of F10).
                // Much more readable than the raw REFUND_69E6356C6D47D.
                val displayReceiptNo = when {
                    refund.origReceiptNo.isNotBlank() -> "R${refund.origReceiptNo}"
                    refund.refundId.isNotBlank()      -> "R-${refund.refundId.takeLast(6).uppercase()}"
                    else                              -> "Refund"
                }

                FeePayment(
                    paymentId = refund.id,
                    // refund.amount is stored NEGATIVE by the server so
                    // the ledger math matches the way accountants read
                    // receipts. The payments UI formats "%,.0f" which
                    // already handles the minus sign.
                    amount = refund.amount,
                    date = dateStr,
                    month = refund.feeTitle.ifBlank {
                        if (refund.origReceiptNo.isNotBlank()) "Refund of receipt #${refund.origReceiptNo}"
                        else "Refund"
                    },
                    mode = modeLabel,
                    receiptNo = displayReceiptNo,
                    remarks = refund.reason
                )
            } catch (e: Exception) {
                Log.e("FeesVM", "[REFUND MAP CRASH] refund=${refund.id}", e)
                null
            }
        }
    }

    /**
     * Reset payment state on sibling switch. The data listeners
     * (`observeFeeDemandsLive`, `observeDefaulterLive`, etc.) automatically
     * re-attach to the new student via `effectiveStudentId.flatMapLatest`,
     * so we only need to clear in-flight payment flags + refresh the
     * static slices for the new child here.
     */
    private fun observeUserChanges() {
        viewModelScope.launch {
            tokenManager.user
                .map { it.userId }
                .distinctUntilChanged()
                .collect { newId ->
                    if (lastLoadedStudentId.isNotBlank() && newId.isNotBlank() && newId != lastLoadedStudentId) {
                        Log.d("FeesVM", "Active student changed: $lastLoadedStudentId -> $newId. Resetting payment state.")
                        _uiState.update {
                            it.copy(
                                paymentInProgress = false,
                                paymentStatus     = null,
                                paymentFailure    = null,
                                lastReceiptNo     = null,
                                errorMessage      = null,
                                // Drop the previous child's overview slices that
                                // listeners don't refresh (structure/scholarship/
                                // carry-forward are class-/student-scoped).
                                overview = it.overview.copy(
                                    feeStructure = FeeStructure(),
                                    scholarshipAmount = 0.0,
                                    carryForwardDues = 0.0
                                )
                            )
                        }
                        lastAttemptedMonths = emptyList()
                        lastAttemptedAmount = null
                        loadStaticFeesData()
                    }
                    lastLoadedStudentId = newId
                }
        }
    }

    fun selectTab(tab: FeesTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Public re-trigger of `loadStaticFeesData` (kept for back-compat
     * callers like the navigation-graph entry effect that used to call
     * `loadFees()`). The realtime listeners cover demands / receipts /
     * defaulter — this only re-pulls the slow-changing structure /
     * scholarship / carry-forward slices.
     */
    fun loadFees() {
        loadStaticFeesData()
    }

    /**
     * One-shot fetch of low-change fee data: structure, scholarships,
     * carry-forward. The high-churn slices (demands, receipts, defaulter)
     * flow in via snapshot listeners attached in `init`.
     */
    private fun loadStaticFeesData() {
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            val studentId = user.userId
            val className = user.className
            val section = user.section

            Log.d("FeesVM", "loadStaticFeesData userId=$studentId className=$className section=$section")
            if (studentId.isNotBlank()) lastLoadedStudentId = studentId

            if (studentId.isBlank() || className.isBlank() || section.isBlank()) {
                Log.e("FeesVM", "MISSING: studentId=$studentId className=$className section=$section")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Student info not available")
                }
                return@launch
            }

            try {
                val feeStructure = feeFirestoreRepo.getFeeStructure(className, section)
                    .getOrNull()?.let { doc ->
                        FeeStructure(
                            className = doc.className,
                            section = doc.section,
                            feeHeads = doc.feeHeads.map { head ->
                                FeeHead(
                                    name = head.name,
                                    amount = head.amount,
                                    frequency = head.frequency
                                )
                            },
                            totalAnnualFee = doc.totalAnnualFee
                        )
                    } ?: FeeStructure()

                val carryForward = feeFirestoreRepo.getCarryForward(studentId)
                    .getOrNull()?.totalDues ?: 0.0
                val scholarshipTotal = feeFirestoreRepo.getScholarshipAwards(studentId)
                    .getOrNull()?.sumOf { it.amount } ?: 0.0

                _uiState.update {
                    it.copy(
                        // Don't clobber `isLoading=true` if listeners haven't
                        // delivered their first snapshot yet — the demand
                        // listener flips it to false on first emit.
                        overview = it.overview.copy(
                            feeStructure = feeStructure,
                            carryForwardDues = carryForward,
                            scholarshipAmount = scholarshipTotal
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("FeesVM", "Failed to load static fee data", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = friendlyErrorMessage(
                            e,
                            fallback = "Couldn't load fee details. Please pull to refresh."
                        )
                    )
                }
            }
        }
    }

    /**
     * Pure mapper — Firestore demands → UI PendingFees model.
     * Extracted from the old loadFeesFromFirestore so both the listener
     * path and any future one-shot can share the same logic.
     */
    /**
     * Compute how many days a period is past its grace window.
     *
     * "Month starts on the 1st; 15-day grace". So April 2026 becomes
     * overdue on 16 April 2026. For Jan–Mar (which roll into session
     * year+1), the computation uses sessionYear+1.
     *
     * Yearly Fees is tied to session start (April) so it overdues
     * alongside April.
     *
     * Returns 0 when the month is not yet past the grace window — e.g.
     * June demand viewed in May → 0. This keeps the UI calm during the
     * legitimate window.
     */
    private fun computeOverdueDays(monthLabel: String, sessionStartYear: Int): Int {
        val monthIndex = when (monthLabel) {
            "April"      -> 4
            "May"        -> 5
            "June"       -> 6
            "July"       -> 7
            "August"     -> 8
            "September"  -> 9
            "October"    -> 10
            "November"   -> 11
            "December"   -> 12
            "January"    -> 1
            "February"   -> 2
            "March"      -> 3
            "Yearly Fees" -> 4     // treat as session-start
            else -> return 0
        }
        val year = if (monthIndex in 1..3) sessionStartYear + 1 else sessionStartYear
        val cal = java.util.Calendar.getInstance()
        // Grace: 15 days from the 1st. Overdue starts on day 16.
        cal.set(year, monthIndex - 1, 16, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val overdueAt = cal.timeInMillis
        val now       = System.currentTimeMillis()
        if (now < overdueAt) return 0
        val days = ((now - overdueAt) / (1000L * 60 * 60 * 24)).toInt()
        return days.coerceAtLeast(1)
    }

    private fun mapDemandsToPendingFees(
        demands: List<com.schoolsync.parent.data.model.firestore.FeeDemandDoc>,
        studentId: String
    ): PendingFees {
        if (demands.isEmpty()) return PendingFees(studentId = studentId)
        // BUG-076 defense (2026-05-27): client-side archived skip. Belt-and-
        // suspenders pairing with FeeFirestoreRepository.kt SW4-B upstream
        // .whereNotEqualTo("status","archived") filter. Even when the upstream
        // is unavailable (stale APK without SW4-B, undeployed composite index,
        // Firestore SDK fallback path), archived demands MUST NOT enter the
        // aggregation — otherwise post-promotion phantom dues surface in
        // Total/Paid/Due cards.
        val active = demands.filter { it.status != "archived" }
        if (active.isEmpty()) return PendingFees(studentId = studentId)
        val grouped = active.groupBy { it.month.ifBlank { "Unknown" } }
        val academicOrder = listOf(
            "April","May","June","July","August","September",
            "October","November","December","January","February","March"
        )

        // Pick a session year from any demand — session field is shared
        // across all demands. Format: "2026-27". Used to anchor the
        // "overdue" computation (so April 2026 doesn't register as
        // "overdue" in April 2027).
        val sessionYear: Int = active
            .firstOrNull()?.session?.take(4)?.toIntOrNull()
            ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        val pendingMonths = grouped.map { (month, monthDemands) ->
            val totalNet  = monthDemands.sumOf { it.netAmount }
            val totalPaid = monthDemands.sumOf { it.paidAmount }
            val balance   = (totalNet - totalPaid).coerceAtLeast(0.0)
            val allPaid   = monthDemands.all { it.status == "paid" }
            val anyPartial = monthDemands.any { it.status == "partial" }
            val status = when {
                allPaid -> "Paid"
                anyPartial || totalPaid > 0 -> "Partial"
                else -> "Pending"
            }
            PendingMonth(
                month = month,
                totalAmount = totalNet,
                paidAmount = totalPaid,
                balanceAmount = balance,
                dueDate = "",
                status = status,
                // Only non-Paid months can be "overdue". Helper returns
                // 0 for future / on-time / cleared months.
                overdueDays = if (allPaid) 0 else computeOverdueDays(month, sessionYear),
                feeHeads = monthDemands.map { d ->
                    FeeHeadDue(
                        name = d.feeHead.ifBlank {
                            d.demandId.substringAfter("DEM_")
                                .substringAfter("_")
                                .replace("_", " ")
                                .lowercase()
                                .split(" ")
                                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                        },
                        netAmount = d.netAmount,
                        paidAmount = d.paidAmount,
                        balance = (d.netAmount - d.paidAmount).coerceAtLeast(0.0),
                        status = d.status
                    )
                }
            )
        }.sortedBy { pm ->
            val idx = academicOrder.indexOf(pm.month)
            if (idx >= 0) idx else 99
        }

        val totalPending = pendingMonths
            .filter { it.status != "Paid" }
            .sumOf { it.balanceAmount }
        return PendingFees(
            studentId = studentId,
            totalPending = totalPending,
            pendingMonths = pendingMonths
        )
    }

    /**
     * Pure mapper — Firestore receipt docs → UI FeePayment list, sorted
     * newest first. Listener already imposes a 50-doc limit + DESC order
     * but we re-sort here defensively.
     *
     * Each receipt is mapped inside its own try/catch so a single
     * malformed doc (admin saved a receipt with the wrong feeBreakdown
     * shape, partial-payment with missing fields, etc.) doesn't take
     * down the whole Payments tab — we log the offending receipt id and
     * drop just that row.
     */
    private fun mapReceiptsToPayments(
        receipts: List<com.schoolsync.parent.data.model.firestore.FeeReceiptDoc>
    ): List<FeePayment> {
        return receipts
            .sortedWith(
                compareByDescending<com.schoolsync.parent.data.model.firestore.FeeReceiptDoc> {
                    it.createdAt.toEpochMillisOrNull() ?: 0L
                }.thenByDescending {
                    it.receiptNo.toLongOrNull() ?: 0L
                }
            )
            .mapNotNull { receipt ->
                try {
                    val dateStr = receipt.createdAt.toDateOrNull()?.let {
                        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(it)
                    } ?: ""
                    val breakdownSummary = receipt.feeBreakdown.mapNotNull { item ->
                        // `item` is typed Map<String, Any> but Firestore can
                        // hand back unexpected shapes if the writer misbehaved
                        // — guard with a runtime check so a single bad row
                        // doesn't trip the whole list.
                        if (item !is Map<*, *>) return@mapNotNull null
                        val head = (item["head"] as? String) ?: return@mapNotNull null
                        val rawAmt = item["amount"]
                        val amt = when (rawAmt) {
                            is Number -> rawAmt.toDouble()
                            is String -> rawAmt.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        "$head: Rs. ${"%,.0f".format(amt)}"
                    }.joinToString(" | ")

                    FeePayment(
                        paymentId = receipt.id,
                        amount = receipt.amount,
                        date = dateStr,
                        month = breakdownSummary.ifBlank {
                            receipt.feeMonths.joinToString(", ")
                        },
                        mode = receipt.paymentMode,
                        receiptNo = receipt.receiptNo.ifBlank { receipt.receiptKey },
                        remarks = receipt.remarks
                    )
                } catch (e: Exception) {
                    com.schoolsync.parent.util.debugLog(
                        "[RECEIPT MAP CRASH] receipt=${receipt.id} err=${e.javaClass.simpleName}: ${e.message}"
                    )
                    Log.e("FeesVM", "[RECEIPT MAP CRASH] receipt=${receipt.id} feeBreakdown=${receipt.feeBreakdown}", e)
                    null
                }
            }
    }

    /**
     * Pull-to-refresh entry — toggles `isRefreshing` while the load
     * runs, then clears the Pending Dues selection AFTER the data
     * arrives so the user sees: pull → spinner → data updates → ticks
     * clear (much smoother than clearing first).
     *
     * `minSpinnerMs` guarantees a visible spinner even when the load
     * finishes in <100ms (cached); without it the spinner flashes for
     * a single frame and looks broken.
     */
    fun pullRefresh() {
        viewModelScope.launch {
            Log.d("FeesVM", "pullRefresh: STARTED")
            _uiState.update { it.copy(isRefreshing = true) }
            val startedAt = System.currentTimeMillis()
            val minSpinnerMs = 600L
            // Fully reattach all Firestore listeners so any silently-dead
            // Flow (absorbed by the repo-level .catch) gets a fresh
            // subscription. The existing jobs are cancelled inside each
            // observe*Live call.
            observeFeeDemandsLive()
            observeDefaulterLive()
            observeReceiptsLive()
            try {
                loadFeesAsync()
                Log.d(
                    "FeesVM",
                    "pullRefresh: load complete in ${System.currentTimeMillis() - startedAt}ms — " +
                        "demands=${_uiState.value.overview.pendingFees.pendingMonths.size}"
                )
            } catch (e: Exception) {
                Log.e("FeesVM", "pullRefresh: load failed", e)
            }
            // Hold the spinner for the minimum duration so the user
            // gets clear visual feedback the refresh actually happened.
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < minSpinnerMs) {
                kotlinx.coroutines.delay(minSpinnerMs - elapsed)
            }
            // Clear selection AFTER the data arrives — the previous
            // version cleared too early which made it look like the
            // ticks vanished before any refresh actually happened.
            _uiState.update {
                it.copy(isRefreshing = false, selectedMonths = emptyList())
            }
            Log.d("FeesVM", "pullRefresh: DONE; spinner hidden, selection cleared")
        }
    }

    /** Selection management — lifted from the composable's local state. */
    fun toggleMonth(month: String, unpaidMonthsInOrder: List<String>) {
        val current = _uiState.value.selectedMonths
        // Selection is always a contiguous prefix of unpaidMonthsInOrder
        // — same rule as before, just driven from the VM now.
        val idx = unpaidMonthsInOrder.indexOf(month)
        if (idx < 0) return
        val targetKeep = if (month in current) idx else idx + 1
        val prefix = unpaidMonthsInOrder.take(targetKeep)

        // Auto-bundle Yearly Fees with April — mirrors the admin Fee
        // Counter behavior. Indian-school convention is that the first-
        // month (April) bill always includes annual heads. Without this,
        // a parent paying April alone would leave the annual fee unpaid
        // and it would carry forward silently.
        val next = if ("April" in prefix
            && "Yearly Fees" in unpaidMonthsInOrder
            && "Yearly Fees" !in prefix) {
            prefix + "Yearly Fees"
        } else {
            prefix
        }

        _uiState.update { it.copy(selectedMonths = next) }
    }
    fun selectAllMonths(unpaidMonthsInOrder: List<String>) {
        _uiState.update { it.copy(selectedMonths = unpaidMonthsInOrder) }
    }
    fun clearSelection() {
        _uiState.update { it.copy(selectedMonths = emptyList()) }
    }

    private suspend fun loadFeesAsync() {
        // Used by pullRefresh — re-fetch the static slices. The realtime
        // listeners are always live and don't need re-attachment, but we
        // call this to (a) prove network connectivity, (b) refresh
        // structure/scholarship/carry-forward.
        val user = tokenManager.user.firstOrNull() ?: User.empty()
        val sid = user.userId; val cls = user.className; val sec = user.section
        if (sid.isNotBlank()) lastLoadedStudentId = sid
        if (sid.isBlank() || cls.isBlank() || sec.isBlank()) return

        try {
            val feeStructure = feeFirestoreRepo.getFeeStructure(cls, sec).getOrNull()?.let { doc ->
                FeeStructure(
                    className = doc.className,
                    section = doc.section,
                    feeHeads = doc.feeHeads.map { h ->
                        FeeHead(name = h.name, amount = h.amount, frequency = h.frequency)
                    },
                    totalAnnualFee = doc.totalAnnualFee
                )
            } ?: FeeStructure()
            val carryForward = feeFirestoreRepo.getCarryForward(sid).getOrNull()?.totalDues ?: 0.0
            val scholarshipTotal = feeFirestoreRepo.getScholarshipAwards(sid).getOrNull()
                ?.sumOf { it.amount } ?: 0.0
            _uiState.update {
                it.copy(
                    overview = it.overview.copy(
                        feeStructure = feeStructure,
                        carryForwardDues = carryForward,
                        scholarshipAmount = scholarshipTotal
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("FeesVM", "loadFeesAsync (static slice) failed", e)
        }
    }

    fun refresh() {
        loadFees()
    }

    /**
     * Create an order against the PHP backend and kick off Razorpay
     * checkout. [amountOverride] supports partial payment — when set,
     * that amount is sent to the gateway instead of the computed total.
     * The server allocates the received amount across the selected
     * months (oldest demand first).
     */
    fun initiatePayment(months: List<String>, amountOverride: Double? = null) {
        if (months.isEmpty()) return

        // Compute the amount BEFORE claiming the in-flight slot, so a
        // "nothing to pay" no-op doesn't lock out subsequent legitimate
        // taps via a stuck paymentInProgress=true.
        val overview = _uiState.value.overview
        val computedTotal = overview.pendingFees.pendingMonths
            .filter { it.month in months }
            .sumOf { it.balanceAmount }
        val totalAmount = amountOverride ?: computedTotal
        if (totalAmount <= 0) {
            _uiState.update { it.copy(errorMessage = "Nothing to pay for the selected months.") }
            return
        }

        // Stage B1 atomic claim — `_uiState.update` is documented as
        // atomic (compareAndSet loop). Setting paymentInProgress=true
        // here, BEFORE viewModelScope.launch runs, closes the race
        // window where two rapid Pay-button taps both passed the old
        // check-then-set guard at line 945 before either had updated
        // the StateFlow. `didClaim` captures whether THIS call won the
        // race; only the winner proceeds to call createOrder.
        var didClaim = false
        _uiState.update { current ->
            if (current.paymentInProgress) current
            else {
                didClaim = true
                current.copy(
                    paymentInProgress = true,
                    paymentStatus = "Creating order…",
                    errorMessage = null,
                    paymentFailure = null
                )
            }
        }
        if (!didClaim) {
            Log.w("FeesVM", "initiatePayment ignored — a payment is already in progress")
            return
        }

        viewModelScope.launch {
            // Remember what we tried so a failure dialog can offer "Try again".
            lastAttemptedMonths = months
            lastAttemptedAmount = amountOverride
            Log.i("FeesVM", "[PAY START] months=$months override=$amountOverride total=$totalAmount")

            // Force-refresh the Firebase ID token. Tokens live an hour;
            // if the parent opened the app and then left the Fees screen
            // open for ~59 min before tapping Pay, the cached token can
            // expire mid-flight. `getIdToken(true)` forces a refresh
            // regardless of cache age.
            val idToken = try {
                FirebaseAuth.getInstance().currentUser
                    ?.getIdToken(true)?.await()?.token
            } catch (e: Exception) {
                Log.e("FeesVM", "Failed to fetch Firebase ID token", e)
                null
            }
            if (idToken.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        paymentInProgress = false,
                        paymentStatus = null,
                        errorMessage = "Please re-login to make a payment."
                    )
                }
                return@launch
            }

            val user = tokenManager.user.firstOrNull() ?: User.empty()

            val response: CreateOrderResponse = try {
                feesApi.createOrder(
                    bearer = "Bearer $idToken",
                    body = CreateOrderRequest(amount = totalAmount, fee_months = months)
                )
            } catch (e: Exception) {
                Log.e("FeesVM", "createOrder network error", e)
                // Stage B1: never leak retrofit/IO exception text into
                // the parent-visible banner. Map by class to a calm,
                // actionable line; full exception is logged above.
                _uiState.update {
                    it.copy(
                        paymentInProgress = false,
                        paymentStatus = null,
                        errorMessage = friendlyErrorMessage(
                            e,
                            fallback = "Couldn't start the payment. Please try again in a moment."
                        )
                    )
                }
                return@launch
            }

            if (!response.success || response.gateway_order_id.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        paymentInProgress = false,
                        paymentStatus = null,
                        errorMessage = response.error ?: "Failed to create payment order."
                    )
                }
                return@launch
            }
            Log.i("FeesVM", "[ORDER OK] orderId=${response.gateway_order_id} amount=${response.amount} provider=${response.provider}")

            // Mock provider: no Razorpay checkout to launch — surface
            // a clear message instead of opening an empty SDK.
            if (response.provider.equals("mock", ignoreCase = true) || response.api_key.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        paymentInProgress = false,
                        paymentStatus = null,
                        errorMessage = "Razorpay is not configured. Ask the school admin to enable it."
                    )
                }
                return@launch
            }

            val amountPaise = if (response.amount_paise > 0) {
                response.amount_paise
            } else {
                (response.amount * 100).toLong()
            }

            _uiState.update { it.copy(paymentStatus = "Opening checkout…") }
            val description = "Fees — ${months.joinToString(", ")}"
            // Cache for one-tap retry without a fresh createOrder hop.
            lastCheckout = CachedCheckout(
                apiKey = response.api_key,
                orderId = response.gateway_order_id,
                amountPaise = amountPaise,
                currency = response.currency.ifBlank { "INR" },
                description = description
            )
            _checkoutRequests.trySend(
                CheckoutRequest(
                    apiKey = response.api_key,
                    orderId = response.gateway_order_id,
                    amountPaise = amountPaise,
                    currency = response.currency.ifBlank { "INR" },
                    name = user.schoolDisplayName.ifBlank { "School Fees" },
                    description = description,
                    prefillEmail = user.email,
                    prefillContact = user.phone
                )
            )
        }
    }

    private fun observePaymentBridge() {
        viewModelScope.launch {
            PaymentBridge.events.collect { event ->
                when (event) {
                    is PaymentBridge.Event.Success -> {
                        // Hand off to the app-scoped PaymentSession with
                        // the months we attempted. PaymentSession owns
                        // the chip-relevant state from here on — the VM
                        // derives `pendingConfirmMonths` from
                        // PaymentSession.state, so a brand-new VM (after
                        // navigation) reconstructs the chip correctly.
                        paymentSession.submitVerification(event, lastAttemptedMonths)
                    }
                    is PaymentBridge.Event.Failure -> {
                        _uiState.update {
                            it.copy(
                                paymentInProgress = false,
                                paymentStatus = null,
                                pendingConfirmMonths = emptyList(),
                                paymentFailure = PaymentFailure(
                                    code = event.code,
                                    description = event.description,
                                    failedMonths = lastAttemptedMonths,
                                    failedAmount = lastAttemptedAmount
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * One-tap retry of the last failed payment.
     *
     * Prefers reusing the cached Razorpay orderId so we don't ask the
     * server to mint a fresh one for the same student+months — Razorpay
     * accepts retries against the existing order, and skipping
     * createOrder removes one network round-trip from the retry path.
     * Falls back to a full initiatePayment() if we lost the cache (e.g.
     * VM was recreated between failure and retry).
     */
    fun retryLastFailedPayment() {
        val f = _uiState.value.paymentFailure ?: return
        dismissFailure()
        val cached = lastCheckout
        if (cached == null || f.failedMonths.isEmpty()) {
            initiatePayment(f.failedMonths, f.failedAmount)
            return
        }
        // Stage B1 atomic claim — same race-closing pattern as
        // initiatePayment so a fast double-tap of "Try again" cannot
        // re-open Razorpay checkout twice for one cached order.
        var didClaim = false
        _uiState.update { current ->
            if (current.paymentInProgress) current
            else {
                didClaim = true
                current.copy(
                    paymentInProgress = true,
                    paymentStatus = "Reopening checkout…",
                    errorMessage = null,
                    paymentFailure = null
                )
            }
        }
        if (!didClaim) {
            Log.w("FeesVM", "retry ignored — payment already in progress")
            return
        }
        viewModelScope.launch {
            val user = tokenManager.user.firstOrNull() ?: User.empty()
            _checkoutRequests.trySend(
                CheckoutRequest(
                    apiKey = cached.apiKey,
                    orderId = cached.orderId,
                    amountPaise = cached.amountPaise,
                    currency = cached.currency,
                    name = user.schoolDisplayName.ifBlank { "School Fees" },
                    description = cached.description,
                    prefillEmail = user.email,
                    prefillContact = user.phone
                )
            )
        }
        return
    }

    fun dismissFailure() {
        _uiState.update { it.copy(paymentFailure = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(errorMessage = null, paymentStatus = null) }
    }

    fun dismissPendingSyncMessage() {
        _uiState.update { it.copy(pendingSyncMessage = null) }
    }
}
