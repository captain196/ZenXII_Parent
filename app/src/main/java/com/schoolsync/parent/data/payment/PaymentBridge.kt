package com.schoolsync.parent.data.payment

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bridges Razorpay Checkout's Activity-level callbacks
 * (PaymentResultWithDataListener) to whichever ViewModel kicked off the
 * checkout. Razorpay's SDK invokes its callbacks on the Activity that
 * calls Checkout.open(), so MainActivity is the listener of record —
 * but the verify-payment logic lives in FeesViewModel. MainActivity
 * forwards every payment event through this singleton channel.
 */
object PaymentBridge {

    sealed class Event {
        data class Success(
            val razorpayPaymentId: String,
            val razorpayOrderId: String,
            val razorpaySignature: String
        ) : Event()

        data class Failure(
            val code: Int,
            val description: String
        ) : Event()
    }

    private val channel = Channel<Event>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    fun emit(event: Event) {
        channel.trySend(event)
    }
}
