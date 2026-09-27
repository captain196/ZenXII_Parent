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
    val error: String? = null,

    // ── P5/P14 · THE NORMALIZED CHECKOUT CONTRACT ────────────────────────
    // The server resolves WHICH provider this school currently uses and
    // tells the app which checkout ENGINE that provider needs. The app must
    // never decide the provider itself, and must never map school -> provider.
    //
    // Nullable with null defaults ON PURPOSE: an older server build, or the
    // legacy config path, simply omits these keys. Null therefore means
    // "the server did not say", which the app treats as the historical
    // native_sdk behaviour rather than as an error — that is what keeps
    // already-installed builds working against either server version.
    //
    // Known engines: "native_sdk" | "webview" | "hosted_redirect" | "upi_intent"
    val checkout_type: String? = null,
    val capabilities: CheckoutCapabilities? = null
)

/**
 * What the school's ACTIVE provider can actually do. Sent by the server from
 * the adapter's own capabilities() declaration — never inferred client-side,
 * and never a hardcoded assumption about a particular provider.
 */
data class CheckoutCapabilities(
    val cards: Boolean = false,
    val upi: Boolean = false,
    val net_banking: Boolean = false,
    val wallets: Boolean = false
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
     * is still pending â€” admin reconciliation will replay it. Parent
     * app should render a soft "syncing" banner instead of an error.
     */
    val pending: Boolean = false,
    val message: String? = null,
    val receipt_no: String? = null,
    val error: String? = null,
    val details: String? = null
)
