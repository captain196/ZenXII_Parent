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
