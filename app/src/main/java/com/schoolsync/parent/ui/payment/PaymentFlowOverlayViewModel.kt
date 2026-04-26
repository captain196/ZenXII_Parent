package com.schoolsync.parent.ui.payment

import androidx.lifecycle.ViewModel
import com.schoolsync.parent.data.payment.PaymentSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin VM that exists solely so the [PaymentFlowOverlay] composable can
 * use `hiltViewModel()` to inject a Hilt-managed [PaymentSession]
 * instance instead of relying on the consumer to thread it down.
 *
 * No state of its own — just re-exposes [PaymentSession.state].
 */
@HiltViewModel
class PaymentFlowOverlayViewModel @Inject constructor(
    private val paymentSession: PaymentSession
) : ViewModel() {
    val state: StateFlow<PaymentSession.State> = paymentSession.state

    /** Called by the overlay's success-screen Done button. */
    fun acknowledge() = paymentSession.acknowledgeOutcome()
}
