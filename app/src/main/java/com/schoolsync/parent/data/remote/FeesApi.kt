package com.schoolsync.parent.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Fees-payment API exposed by the CodeIgniter admin panel.
 * Authentication: every request carries the parent's Firebase ID
 * token in the Authorization header; the server verifies it via
 * Api_auth and derives the school/session context from the claims.
 */
interface FeesApi {

    @POST("index.php/fee_management/parent_create_order")
    suspend fun createOrder(
        @Header("Authorization") bearer: String,
        @Body body: CreateOrderRequest
    ): CreateOrderResponse

    @POST("index.php/fee_management/parent_verify_payment")
    suspend fun verifyPayment(
        @Header("Authorization") bearer: String,
        @Body body: VerifyPaymentRequest
    ): VerifyPaymentResponse
}

data class CreateOrderRequest(
    val amount: Double,
    val fee_months: List<String>
)

data class CreateOrderResponse(
    val success: Boolean = false,
    val existing: Boolean = false,
    val payment_id: String? = null,
    val gateway_order_id: String? = null,
    val amount: Double = 0.0,
    val amount_paise: Long = 0L,
    val currency: String = "INR",
    val gateway: String? = null,
    val provider: String? = null,
    val api_key: String? = null,
    val mode: String? = null,
    val student_id: String? = null,
    val student_name: String? = null,
    val school_id: String? = null,
    val error: String? = null
)

data class VerifyPaymentRequest(
    val razorpay_order_id: String,
    val razorpay_payment_id: String,
    val razorpay_signature: String
)

data class VerifyPaymentResponse(
    val success: Boolean = false,
    val already_paid: Boolean = false,
    /**
     * Razorpay captured the payment but the server-side receipt write
     * is still pending — admin reconciliation will replay it. Parent
     * app should render a soft "syncing" banner instead of an error.
     */
    val pending: Boolean = false,
    val message: String? = null,
    val receipt_no: String? = null,
    val error: String? = null,
    val details: String? = null
)
