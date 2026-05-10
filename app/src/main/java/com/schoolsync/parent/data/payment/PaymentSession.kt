package com.schoolsync.parent.data.payment

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.remote.FeesApi
import com.schoolsync.parent.data.remote.VerifyPaymentRequest
import com.schoolsync.parent.util.friendlyErrorMessage
import com.schoolsync.parent.util.periodToMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped owner of the Razorpay verify-payment lifecycle.
 *
 * Why this exists separately from FeesViewModel:
 * Razorpay returns the payment_id as soon as the user pays, but our
 * server-side verify pipeline takes 5-30 s on slow networks. If the
 * parent navigates away from the Fees tab during that window, the
 * FeesViewModel is destroyed → its viewModelScope cancels → the in-
 * flight verify HTTP call is killed → the payment is captured by
 * Razorpay but no receipt is written on our side. Even when the
 * server DOES finish writing, the FeesViewModel that comes back later
 * has no way to surface "Payment successful!" because the original
 * coroutine emit never landed anywhere.
 *
 * PaymentSession runs the verify call in its OWN application-scoped
 * coroutine scope (lives as long as the process) and publishes the
 * result via a StateFlow. Any VM (current or future) can observe the
 * latest outcome — so navigating away mid-payment, then back, still
 * surfaces the success screen.
 *
 * State machine:
 *   Idle ──submitVerification──▶ Verifying ──API ok──▶ Confirming
 *                                              │
 *                                              ├─ Firestore receipt found ──▶ Success
 *                                              └─ timeout (still trust API) ─▶ Success(partial)
 *                                              ├─ API returned pending=true ─▶ Pending
 *                                              └─ API errored / not success ─▶ Failure
 *
 * The Confirming step exists to satisfy "Success state ONLY triggers
 * when receipt is created, feeDemands updated, defaulter status
 * updated" — we read the receipt doc back from Firestore before
 * announcing success to the UI. If the receipt is delayed (rare race
 * between API commit and Firestore propagation), we fall back to a
 * trust-the-API-but-mark-partial Success after a bounded wait so the
 * UX never hangs forever.
 *
 * Singleton scope guarantees one payment-in-flight at a time per app
 * instance. A second submission with the same paymentId is rejected.
 */
@Singleton
class PaymentSession @Inject constructor(
    private val feesApi: FeesApi,
    private val tokenManager: TokenManager
) {
    /**
     * Snapshot of everything the Success screen needs to render. Built
     * from the verify-API response + a defensive Firestore read of the
     * receipt doc. If the receipt read fails or times out, the screen
     * still renders cleanly with whatever fields are populated; the
     * `confirmedFromBackend` flag tells the UI whether the values came
     * from the canonical Firestore doc or fell back to the API.
     */
    data class SuccessDetails(
        val receiptNo: String,
        val receiptKey: String,
        /** Full Firestore doc ID `{schoolId}_{receiptKey}` — pass straight
         *  to ReceiptDetailScreen route. Empty when API didn't return a
         *  receipt number. */
        val receiptDocId: String,
        val transactionId: String,    // razorpay_payment_id
        val orderId: String,
        val amount: Double,           // 0 if not yet known
        val months: List<String>,     // empty if not yet known
        val timestamp: Long,
        val alreadyPaid: Boolean,
        val confirmedFromBackend: Boolean,
        /**
         * True when ANY touched month still has a non-zero balance after
         * this payment. Drives the "Partial payment" vs "Cleared" copy on
         * the success screen and the receipt screen. False when we
         * couldn't determine (allocation doc fetch failed) — UI then
         * shows the neutral "Payment recorded" message instead of
         * lying about clearance.
         */
        val isPartial: Boolean = false,
        /**
         * Per-month remaining balance after this payment (only the months
         * touched by this receipt). Empty when allocation read failed.
         */
        val remainingByMonth: Map<String, Double> = emptyMap()
    )

    sealed class State {
        object Idle : State()
        /**
         * `attemptedMonths` is the list of month names the parent
         * selected on the Pending Dues tab before tapping Pay. We carry
         * it through every "in-flight" state so a NEW FeesViewModel
         * (e.g. created after the user navigated away and came back)
         * can render the per-row "Processing…" chip purely from
         * PaymentSession state — no per-VM local state needed.
         */
        data class Verifying(
            val paymentId: String,
            val orderId: String,
            val attemptedMonths: List<String>
        ) : State()
        /** API succeeded; reading Firestore to confirm receipt landed. */
        data class Confirming(
            val receiptNo: String,
            val paymentId: String,
            val orderId: String,
            val attemptedMonths: List<String>
        ) : State()
        data class Success(val details: SuccessDetails) : State()
        /**
         * Razorpay captured the money but our backend write is deferred
         * (admin reconciliation will replay). Show as soft "syncing"
         * banner, NOT as a hard failure. The attemptedMonths are kept
         * so the row chips stay "Processing" until reconciliation
         * lands and the demand listener flips them.
         */
        data class Pending(
            val message: String,
            val completedAt: Long,
            val attemptedMonths: List<String> = emptyList()
        ) : State()
        data class Failure(val message: String, val completedAt: Long) : State()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Submit a Razorpay success event to the server for verification.
     * Returns immediately; the API call runs in the app-scoped scope
     * and updates `state` when it completes.
     *
     * Called from FeesViewModel's PaymentBridge.Event.Success handler.
     *
     * Duplicate submission with the SAME paymentId while a verify is
     * still in flight is a no-op (Razorpay can fire the callback twice
     * on slow connections).
     */
    fun submitVerification(
        event: PaymentBridge.Event.Success,
        attemptedMonths: List<String> = emptyList()
    ) {
        val current = _state.value
        if (current is State.Verifying && current.paymentId == event.razorpayPaymentId) {
            Log.w("PaymentSession", "submit ignored — already verifying paymentId=${event.razorpayPaymentId}")
            return
        }
        if (current is State.Confirming && current.paymentId == event.razorpayPaymentId) {
            Log.w("PaymentSession", "submit ignored — already confirming paymentId=${event.razorpayPaymentId}")
            return
        }
        _state.value = State.Verifying(
            paymentId = event.razorpayPaymentId,
            orderId = event.razorpayOrderId,
            attemptedMonths = attemptedMonths
        )

        scope.launch {
            val verifyT0 = System.currentTimeMillis()
            Log.i("PaymentSession", "[VERIFY START] payment=${event.razorpayPaymentId}")

            val idToken = try {
                FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
            } catch (e: Exception) {
                Log.e("PaymentSession", "id-token fetch failed", e)
                null
            }
            if (idToken.isNullOrBlank()) {
                _state.value = State.Failure(
                    "Session expired during verification — please re-login and check the fees list.",
                    System.currentTimeMillis()
                )
                return@launch
            }

            val response = try {
                feesApi.verifyPayment(
                    bearer = "Bearer $idToken",
                    body = VerifyPaymentRequest(
                        razorpay_order_id   = event.razorpayOrderId,
                        razorpay_payment_id = event.razorpayPaymentId,
                        razorpay_signature  = event.razorpaySignature
                    )
                )
            } catch (e: Exception) {
                Log.e("PaymentSession", "verifyPayment network error", e)
                // Stage B1: replace raw e.message leakage with a calm,
                // action-oriented message. The parent always needs to
                // know their money was captured (Razorpay confirmed it
                // before our call ever fires) — we just couldn't reach
                // OUR server to record it. friendlyErrorMessage maps
                // the transport-layer exception class; we prepend the
                // "Razorpay captured" reassurance.
                val friendly = friendlyErrorMessage(
                    e,
                    fallback = "We couldn't reach the school server to record this payment."
                )
                _state.value = State.Failure(
                    "Payment received by Razorpay. $friendly The receipt will appear in your Fees list once the school server responds.",
                    System.currentTimeMillis()
                )
                return@launch
            }

            Log.i("PaymentSession", "[VERIFY DONE] success=${response.success} pending=${response.pending} receipt=${response.receipt_no} elapsed=${System.currentTimeMillis() - verifyT0}ms")

            when {
                response.pending -> {
                    _state.value = State.Pending(
                        message = response.error ?: "Payment received — generating receipt. Please refresh in a minute.",
                        completedAt = System.currentTimeMillis(),
                        attemptedMonths = attemptedMonths
                    )
                }
                !response.success -> {
                    _state.value = State.Failure(
                        message = response.error ?: "Verification failed.",
                        completedAt = System.currentTimeMillis()
                    )
                }
                else -> {
                    val receiptNo = response.receipt_no.orEmpty()
                    if (receiptNo.isBlank()) {
                        // API said success but didn't give us a receipt
                        // number — emit a partial-info Success so the
                        // user still gets confirmation but the screen
                        // hides the receipt-related rows.
                        _state.value = State.Success(
                            SuccessDetails(
                                receiptNo = "",
                                receiptKey = "",
                                receiptDocId = "",
                                transactionId = event.razorpayPaymentId,
                                orderId = event.razorpayOrderId,
                                amount = 0.0,
                                months = attemptedMonths,
                                timestamp = System.currentTimeMillis(),
                                alreadyPaid = response.already_paid,
                                confirmedFromBackend = false
                            )
                        )
                        return@launch
                    }
                    // Transition through Confirming so the UI can show
                    // a brief "Verifying with school records…" beat
                    // while we read the receipt back from Firestore.
                    _state.value = State.Confirming(
                        receiptNo = receiptNo,
                        paymentId = event.razorpayPaymentId,
                        orderId = event.razorpayOrderId,
                        attemptedMonths = attemptedMonths
                    )
                    val details = confirmFromBackend(
                        receiptNo = receiptNo,
                        paymentId = event.razorpayPaymentId,
                        orderId = event.razorpayOrderId,
                        alreadyPaid = response.already_paid,
                        attemptedMonths = attemptedMonths
                    )
                    _state.value = State.Success(details)
                }
            }
        }
    }

    /**
     * Read the receipt back from Firestore to confirm the server-side
     * write actually landed before announcing Success to the UI.
     *
     * Polls up to 6 times with 1 s spacing (so the user-perceived
     * Confirming → Success transition takes at most ~6 s after the
     * verify API returns; on a healthy network the receipt is usually
     * already there from the very first read).
     *
     * On full timeout we STILL emit Success — but with
     * `confirmedFromBackend = false`, hidden amount, and the UI shows a
     * softer message. Better than blocking forever; the demand listener
     * on the next Fees-screen open will catch up shortly.
     */
    private suspend fun confirmFromBackend(
        receiptNo: String,
        paymentId: String,
        orderId: String,
        alreadyPaid: Boolean,
        attemptedMonths: List<String>
    ): SuccessDetails {
        val schoolId = try {
            tokenManager.user.firstOrNull()?.schoolId
        } catch (_: Exception) { null }

        val session = try { tokenManager.user.firstOrNull()?.session } catch (_: Exception) { null }
        val docId = if (schoolId.isNullOrBlank()) "" else "${schoolId}_F${receiptNo}"
        // Fall back to attemptedMonths so the success screen always
        // shows SOMETHING for "fee cleared" — even if Firestore is
        // slow and confirmFromBackend times out.
        val baseDetails = SuccessDetails(
            receiptNo = receiptNo,
            receiptKey = "F$receiptNo",
            receiptDocId = docId,
            transactionId = paymentId,
            orderId = orderId,
            amount = 0.0,
            months = attemptedMonths,
            timestamp = System.currentTimeMillis(),
            alreadyPaid = alreadyPaid,
            confirmedFromBackend = false
        )
        if (schoolId.isNullOrBlank()) {
            Log.w("PaymentSession", "confirmFromBackend: no schoolId — using API-only details")
            return baseDetails
        }
        val confirmT0 = System.currentTimeMillis()
        repeat(6) { attempt ->
            try {
                val snap = firestore.collection("feeReceipts").document(docId).get().await()
                if (snap.exists()) {
                    val data = snap.data ?: emptyMap<String, Any?>()
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val studentIdInReceipt = (data["studentId"] as? String).orEmpty()
                    @Suppress("UNCHECKED_CAST")
                    val months = (data["feeMonths"] as? List<String>) ?: emptyList()
                    // Read the FULL month status (across all unpaid
                    // demands for these months, not just allocation
                    // touchpoints). This is the only way to correctly
                    // distinguish "I paid one head" (partial) vs "I
                    // paid all unpaid heads for the month" (full).
                    val (isPartial, remaining) = readMonthStatus(
                        schoolId = schoolId,
                        session = session,
                        studentId = studentIdInReceipt,
                        feeMonths = months
                    )
                    Log.i("PaymentSession", "[CONFIRM OK] doc=$docId amount=$amount months=$months partial=$isPartial remaining=$remaining attempt=$attempt elapsed=${System.currentTimeMillis() - confirmT0}ms")
                    return baseDetails.copy(
                        amount = amount,
                        months = months,
                        confirmedFromBackend = true,
                        isPartial = isPartial,
                        remainingByMonth = remaining
                    )
                }
            } catch (e: Exception) {
                Log.w("PaymentSession", "confirmFromBackend read failed (attempt $attempt): ${e.message}")
            }
            delay(1_000)
        }
        Log.w("PaymentSession", "[CONFIRM TIMEOUT] doc=$docId — emitting partial Success")
        return baseDetails
    }

    /**
     * Determine partial-vs-full + per-month remaining by querying
     * `feeDemands` for the months this receipt covered.
     *
     * IMPORTANT: the allocation doc alone is NOT enough. The
     * allocation only lists demands TOUCHED by this receipt. Example
     * bug we hit: parent paid Rs 500 against February → allocation
     * shows only "Computer Fee allocated=500 balance=0". The other 2
     * demands (Library + Tuition) for February aren't in the
     * allocation because they weren't touched — but the month is still
     * partial because those demands are unpaid. Reading from
     * feeDemands directly avoids that false negative.
     *
     * Returns (false, emptyMap) when we can't read the demands — the
     * UI then renders a neutral "Payment recorded" message instead of
     * incorrectly claiming "cleared".
     */
    private suspend fun readMonthStatus(
        schoolId: String,
        session: String?,
        studentId: String,
        feeMonths: List<String>
    ): Pair<Boolean, Map<String, Double>> {
        if (session.isNullOrBlank() || studentId.isBlank() || feeMonths.isEmpty()) {
            return false to emptyMap()
        }
        return try {
            val snap = firestore.collection("feeDemands")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("session", session)
                .whereEqualTo("studentId", studentId)
                .get().await()
            // Group remaining balances per month for demands that are
            // NOT fully paid AND whose month is in our receipt's
            // feeMonths list.
            val byMonth = mutableMapOf<String, Double>()
            for (doc in snap.documents) {
                val data = doc.data ?: continue
                val period = (data["period"] as? String).orEmpty()
                // periodToMonth preserves "Yearly Fees"; substringBefore(' ')
                // chopped it to "Yearly" and a Yearly demand never matched
                // the receipt's feeMonths list — partial payments were
                // silently marked cleared on the success screen.
                val monthName = periodToMonth(period).ifEmpty { period }
                if (monthName !in feeMonths) continue
                val status = (data["status"] as? String).orEmpty()
                val balance = (data["balance"] as? Number)?.toDouble() ?: 0.0
                if (status != "paid" && balance > 0.005) {
                    byMonth[monthName] = (byMonth[monthName] ?: 0.0) + balance
                }
            }
            val anyPartial = byMonth.isNotEmpty()
            anyPartial to byMonth
        } catch (e: Exception) {
            Log.w("PaymentSession", "readMonthStatus failed: ${e.message}")
            false to emptyMap()
        }
    }

    /**
     * Acknowledge the current outcome — called by the Success/Failure/
     * Pending screen's "Done" button so the next visit doesn't re-show
     * the same stale outcome. Idempotent for Idle.
     */
    fun acknowledgeOutcome() {
        val v = _state.value
        if (v is State.Success || v is State.Failure || v is State.Pending) {
            _state.value = State.Idle
        }
    }
}
