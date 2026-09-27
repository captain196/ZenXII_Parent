package com.schoolsync.parent.util

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Convert a thrown [Throwable] into a calm, user-facing message suitable
 * for surfacing in a snackbar / banner / dialog.
 *
 * Stage B1 hardening 2026-05-10. Previously `errorMessage = e.message`
 * leaked raw retrofit/IO/stack-trace text into the parent's UI:
 *
 *   "Failed to verify: retrofit2.HttpException HTTP 502 Bad Gateway"
 *   "java.net.SocketTimeoutException: timeout"
 *
 * That's both unprofessional AND unactionable for a parent. The mapper
 * below turns the most common transport-layer failures into
 * action-oriented copy. Callers should still log the original [t] at
 * Log.e level — this helper is for the UI surface only, not a substitute
 * for proper logging.
 *
 * [fallback] is used when the throwable doesn't match a known shape;
 * pick a fallback that's specific to the calling site (e.g. "Couldn't
 * verify payment" vs. "Couldn't load fees"). Use a non-null fallback —
 * we never want the parent to see "null" or a stack trace.
 *
 * Cross-references:
 *   - PaymentSession.submitVerification — verify-call failure path
 *   - FeesViewModel.initiatePayment    — createOrder failure path
 *   - FeesViewModel.loadFeesAsync      — fee-structure load failure
 */
fun friendlyErrorMessage(t: Throwable, fallback: String): String {
    return when (t) {
        is UnknownHostException ->
            "No internet connection. Please reconnect and try again."

        is SocketTimeoutException ->
            "The school server took too long to respond. Please try again in a moment."

        is HttpException -> when (t.code()) {
            401, 403 -> "Your session has expired. Please log out and log in again."
            404      -> "We couldn't find that on the server. Please refresh and try again."
            408      -> "The school server took too long to respond. Please try again in a moment."
            // 423 Locked — emitted by either:
            //   • MY_Controller::_abort_if_session_frozen (R1.1)
            //     code='SESSION_FROZEN' — year-end rollover in progress
            //   • MY_Controller::_abort_if_period_locked  (L1.0)
            //     code='PERIOD_LOCKED'  — accounting period is closed
            // Both surface as 423 with structured `code` field. This
            // util is body-agnostic; if a future caller wants distinct
            // copy per code it should parse the response payload. For
            // now the unified message covers both — the parent's next
            // action is identical (try later / contact school).
            423      -> "The school has temporarily paused new payments (year-end close or period lock). Please try again shortly or contact the school office."
            in 500..599 -> "The school server is temporarily unavailable. Please try again shortly."
            else        -> fallback
        }

        // IOException is the parent of UnknownHost/SocketTimeout but we
        // catch it last so the more specific cases win above. Generic
        // network failures (SSL handshake, connection reset, etc.) all
        // land here.
        is IOException ->
            "Couldn't reach the school server. Please check your connection and try again."

        else -> fallback
    }
}

/**
 * Turn a Razorpay checkout failure into a calm, actionable sentence.
 *
 * ── WHY THIS EXISTS ───────────────────────────────────────────────────────
 * `Checkout.onPaymentError(code, description, …)` hands back the gateway's RAW
 * JSON in `description`, and the failure dialog rendered it verbatim. A parent
 * was shown:
 *
 *   {"error":{"code":"BAD_REQUEST_ERROR","description":"Payment Failed",
 *    "source":"customer","step":"payment_authentication",
 *    "reason":"payment_error","metadata":{}}}
 *
 * That is the same leak Stage B1 fixed for transport errors above — this path
 * simply never went through the mapper. Unprofessional, and worse,
 * unactionable: nothing in that blob tells a parent what to do next.
 *
 * ── WHAT IT SAYS, AND WHY ─────────────────────────────────────────────────
 * The useful signal is `step` — where in the flow the payment died. Razorpay's
 * own vocabulary is stable and each step maps to a genuinely different action
 * for the parent. `source: "customer"` at `payment_authentication` is the
 * ordinary "the bank did not approve it" case, which is a bank conversation,
 * not a school one.
 *
 * ── ON "NO MONEY WAS DEDUCTED" ────────────────────────────────────────────
 * Deliberately NOT claimed as an absolute. A payment that fails at
 * authentication was never captured, but banks do occasionally place and then
 * release a hold, and a parent who sees a pending debit after being told
 * "no money was deducted" will not trust the next thing the app says. The copy
 * states what is certain — the payment did not complete, the fees are still
 * due — and tells them what to do if they see a debit.
 *
 * The raw payload is still worth logging; it is the only place `reason` and
 * `metadata` survive for a support conversation.
 */
fun friendlyGatewayError(rawDescription: String?): String {
    val raw = rawDescription.orEmpty()

    // Not JSON at all — the SDK sometimes passes a plain sentence.
    if (!raw.trimStart().startsWith("{")) {
        return raw.ifBlank {
            "The payment could not be completed. Your fees are still due — please try again."
        }
    }

    // Raw string: the pattern is mostly quotes and backslashes, and escaping it
    // through a normal string is how it ends up subtly wrong.
    fun field(name: String): String =
        Regex(""""$name"\s*:\s*"([^"]*)"""").find(raw)?.groupValues?.get(1).orEmpty()

    val step   = field("step")
    val reason = field("reason")

    return when {
        step == "payment_authentication" ->
            "Your bank did not approve this payment. This is usually a wrong OTP, an expired " +
            "card, or a limit on online transactions. Your fees are still due — try again, or " +
            "use a different card or UPI."

        step == "payment_initiation" ->
            "The payment could not be started with your bank. Your fees are still due — please " +
            "try again in a moment."

        reason == "payment_cancelled" ->
            "The payment was cancelled before it completed. Your fees are still due."

        reason.contains("insufficient", ignoreCase = true) ->
            "The payment was declined for insufficient funds. Your fees are still due."

        // A real sentence from the gateway is better than our generic one, but
        // only if it is not the useless catch-all Razorpay sends most often.
        field("description").isNotBlank() && field("description") != "Payment Failed" ->
            field("description")

        else ->
            "The payment could not be completed. Your fees are still due — please try again."
    }
}
