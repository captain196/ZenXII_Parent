package com.schoolsync.parent

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.payment.PaymentBridge
import com.schoolsync.parent.data.repository.firestore.SchoolFirestoreRepository
import com.schoolsync.parent.ui.navigation.AppNavGraph
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.SchoolSyncTheme
import com.schoolsync.parent.util.DeepLinkBridge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var schoolFirestoreRepository: SchoolFirestoreRepository

    /** Android 13+ runtime permission request for push notifications. */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — either way the app proceeds */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Preload Razorpay UI assets — cuts cold-start latency on the
        // first Pay tap. Safe to call multiple times.
        Checkout.preload(applicationContext)
        // Phase 8: if the Activity was launched from an FCM-tapped
        // notification, route the user to the relevant screen after
        // the nav graph settles. See DeepLinkBridge + NavGraph consumer.
        publishDeepLinkFromIntent(intent)

        // SW4 (2026-05-26) — activate live session-authority observer.
        // SchoolFirestoreRepository.observeSchool() emits live Firestore
        // snapshots of schools/{schoolCode}; its internal .onEach block
        // propagates currentSession into TokenManager. We subscribe with
        // an empty collector — the side-effect lives in the repository's
        // onEach. Tied to the Activity lifecycle so the snapshot listener
        // cleans up automatically on destroy (via callbackFlow's awaitClose).
        // Failure of this subscription must NEVER crash the Activity;
        // wrapped in try/catch so app continues with frozen-at-login
        // behavior (matches pre-SW4 fallback if observer fails).
        lifecycleScope.launch {
            try {
                schoolFirestoreRepository.observeSchool().collect { /* side-effects via onEach in repo */ }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "ACC_SESSION_OBSERVER_FAILED err=${e.message}")
            }
        }

        setContent {
            val themeMode by tokenManager.themeMode.collectAsState(initial = "system")
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark  // "system" follows OS
            }

            SchoolSyncTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalAppColors.current.bgStart
                ) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // If the app was already running and the user taps a fresh
        // notification, the intent arrives here instead of onCreate.
        publishDeepLinkFromIntent(intent)
    }

    /**
     * Map FCM intent extras (set by FCMService.showNotification) onto a
     * Route-string that AppNavGraph consumes via DeepLinkBridge. Quiet
     * no-op when extras don't contain a supported 'type'.
     */
    private fun publishDeepLinkFromIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("type") ?: return
        val target = when (type) {
            "fee_reminder", "fee_defaulter_alert", "fee_payment_confirmed" -> "fees"
            "student_absent", "student_late", "attendance_update"         -> "attendance"
            "leave_approved", "leave_rejected"                            -> "leave"
            "homework_assigned", "homework_reminder"                      -> "homework"
            "result_published", "exam_scheduled"                          -> "results"
            "event", "event_created"                                      -> {
                // Deep-link straight to the EventDetail screen when the payload
                // carries an eventId; otherwise drop back to the events list.
                val eventId = intent.getStringExtra("eventId")?.takeIf { it.isNotBlank() }
                if (eventId != null) "event_detail/$eventId" else "events"
            }
            "birthday_wish"                                                -> "notices"
            else -> null
        } ?: return
        DeepLinkBridge.publish(target)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Razorpay callbacks ───────────────────────────────────────────────
    // Razorpay invokes these on the Activity that called Checkout.open();
    // we forward the result to PaymentBridge so the calling ViewModel
    // (FeesViewModel) can finish the verify-payment handshake.

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        PaymentBridge.emit(
            PaymentBridge.Event.Success(
                razorpayPaymentId = razorpayPaymentId ?: paymentData?.paymentId ?: "",
                razorpayOrderId = paymentData?.orderId ?: "",
                razorpaySignature = paymentData?.signature ?: ""
            )
        )
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        PaymentBridge.emit(
            PaymentBridge.Event.Failure(
                code = code,
                description = description ?: "Payment failed"
            )
        )
    }
}
