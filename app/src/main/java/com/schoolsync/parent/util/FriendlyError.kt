package com.schoolsync.parent.util

import com.schoolsync.parent.R
import android.content.Context
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
fun friendlyErrorMessage(ctx: Context, t: Throwable, fallback: String): String {
    // FirebaseNetworkException is neither a FirebaseAuthException nor an
    // IOException, so without this it falls through to `fallback` and a
    // connectivity failure reads as whatever the call site guessed.
    if (t is com.google.firebase.FirebaseNetworkException) {
        return ctx.getString(R.string.err_no_internet)
    }
    return when (t) {
        is UnknownHostException ->
            ctx.getString(R.string.err_no_internet)

        is SocketTimeoutException ->
            ctx.getString(R.string.err_server_timeout)

        is HttpException -> when (t.code()) {
            401, 403 -> ctx.getString(R.string.err_session_expired)
            404      -> ctx.getString(R.string.err_not_found)
            408      -> ctx.getString(R.string.err_server_timeout)
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
            423      -> ctx.getString(R.string.err_payments_paused)
            in 500..599 -> ctx.getString(R.string.err_server_unavailable)
            else        -> fallback
        }

        // IOException is the parent of UnknownHost/SocketTimeout but we
        // catch it last so the more specific cases win above. Generic
        // network failures (SSL handshake, connection reset, etc.) all
        // land here.
        is IOException ->
            ctx.getString(R.string.err_cannot_reach_server)

        else -> fallback
    }
}
