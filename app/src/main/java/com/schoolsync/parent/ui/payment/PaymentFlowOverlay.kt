package com.schoolsync.parent.ui.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.schoolsync.parent.data.payment.PaymentSession

/**
 * Global overlay rendered at MainScreen level (above NavHost).
 *
 * Why this lives outside any single screen's composition:
 * A parent can tap Pay on Fees, navigate away while the server is
 * still verifying, and still see "Payment successful!" full-screen
 * when the verify completes — because this overlay observes
 * PaymentSession (an app-singleton) directly and renders regardless
 * of which tab is active.
 *
 * State routing:
 *  - Idle              → no-op (overlay invisible, zero touch capture)
 *  - Verifying         → full-screen [PaymentVerifyScreen] with the
 *                        "Verifying with school server" step active
 *  - Confirming        → same [PaymentVerifyScreen] surface, "Recording
 *                        in school records" step active. Renders as a
 *                        smooth in-place transition because both
 *                        branches mount the same composable type.
 *  - Success           → full-screen [PaymentSuccessScreen]
 *  - Pending / Failure → no-op (existing banners in FeesScreen handle
 *                        them; will become full-screen later)
 *
 * The overlay is wired in NavGraph.MainScreen as the LAST child of the
 * outer Box so it draws on top of the NavHost AND the bottom nav. When
 * the state isn't Success, the function returns nothing — Compose
 * doesn't allocate any layout space, so the overlay can't intercept
 * taps that should reach the underlying screen.
 */
@Composable
fun PaymentFlowOverlay(
    onViewReceipt: (receiptDocId: String) -> Unit,
    viewModel: PaymentFlowOverlayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is PaymentSession.State.Idle -> Unit
        is PaymentSession.State.Verifying -> PaymentVerifyScreen(
            phase = VerifyPhase.Verifying,
            months = s.attemptedMonths
        )
        is PaymentSession.State.Confirming -> PaymentVerifyScreen(
            phase = VerifyPhase.Confirming,
            months = s.attemptedMonths
        )
        is PaymentSession.State.Pending -> Unit
        is PaymentSession.State.Failure -> Unit
        is PaymentSession.State.Success -> {
            PaymentSuccessScreen(
                details = s.details,
                onViewReceipt = { docId ->
                    // Acknowledge first so the overlay disappears
                    // synchronously — otherwise the receipt screen
                    // would render UNDER our overlay and look broken.
                    viewModel.acknowledge()
                    onViewReceipt(docId)
                },
                onAcknowledge = { viewModel.acknowledge() }
            )
        }
    }
}
