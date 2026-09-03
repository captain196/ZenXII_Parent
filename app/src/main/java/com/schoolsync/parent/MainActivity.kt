package com.schoolsync.parent

import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import android.Manifest
import android.content.Context
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.data.payment.PaymentBridge
import com.schoolsync.parent.data.repository.firestore.SchoolFirestoreRepository
import com.schoolsync.parent.ui.navigation.AppNavGraph
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.SchoolSyncTheme
import com.schoolsync.parent.util.DeepLinkBridge
import com.schoolsync.parent.util.LocaleManager
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

    @Inject
    lateinit var authRepository: com.schoolsync.parent.data.repository.AuthRepository

    /**
     * Apply the user's chosen language before any view or composable is built.
     * Changing the language calls `recreate()`, which re-runs this with the new
     * stored tag — that is the whole switching mechanism.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

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

        // R33 — re-assert this device's push registration on every start.
        //
        // getToken() was called in exactly ONE place: login(). FCM calls
        // onNewToken only when it CREATES or ROTATES a token, not on every
        // launch. So a registration that failed at login — no network, no Play
        // Services, a rules denial — was NEVER retried, and that device stayed
        // permanently unreachable by push until the user happened to log out and
        // back in. Nothing anywhere reported it.
        //
        // Observed on a real device 2026-09-03: a phone logged in as STU0012 with
        // ZERO userDevices rows for its android_id, so no push could ever arrive.
        // Tenant-wide, 42 of 57 device rows are 'stale' and only 14 carry a token,
        // which is consistent with registrations that were never re-asserted.
        //
        // Cheap and idempotent: registerFcmToken() writes with merge, so a
        // healthy device just refreshes lastActive. It bails on its own when no
        // user is signed in, so this is a no-op on the login screen.
        lifecycleScope.launch {
            try {
                val deviceId = tokenManager.deviceId.firstOrNull()
                if (!deviceId.isNullOrBlank()) {
                    val token = com.google.firebase.messaging.FirebaseMessaging
                        .getInstance().token.await()
                    authRepository.registerFcmToken(token, deviceId)
                }
            } catch (e: Exception) {
                // Never fatal: a device that cannot register still uses the app,
                // it just does not get notifications — which is the status quo.
                android.util.Log.w("MainActivity", "FCM re-registration failed: ${e.message}")
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
            // R34 — Support notifications had NO route at all.
            //
            // MARK_REGISTRY emits six support_* types (support_ticket,
            // support_reply, support_assigned, support_resolved, support_reopened,
            // support_closed) and this when() handled none of them, so every one
            // fell through to the quiet no-op and dumped the parent on the
            // dashboard. Observed on device: a TICKET_REPLIED notification opened
            // MainActivity and landed on the home screen, with no way back to the
            // ticket except navigating the drawer by hand.
            //
            // That is worst on this module in particular: the notification is
            // often the ONLY signal a parent gets that the school answered, and
            // support_closed tells them their complaint was ended without their
            // agreement. Making them hunt for the thread from the dashboard is
            // where a complaint quietly dies.
            //
            // ticketId is carried by every support mark's data() in the registry,
            // so deep-link straight into the thread when it is present and fall
            // back to the ticket list when it is not.
            "support_ticket", "support_reply", "support_assigned",
            "support_resolved", "support_reopened", "support_closed"      -> {
                val tid = intent.getStringExtra("ticketId")?.takeIf { it.isNotBlank() }
                if (tid != null) "support_thread/$tid" else "support"
            }
            "fee_reminder", "fee_defaulter_alert", "fee_payment_confirmed" -> "fees"
            "student_absent", "student_late", "attendance_update"         -> "attendance"
            "leave_approved", "leave_rejected"                            -> "leave"
            // homework_created / homework_reviewed are the types FCMService
            // actually emits; keep the legacy two so older payloads still route.
            // homework_created / homework_reviewed carry an optional homeworkId
            // (admin-panel Attendance.php push path). When present, deep-link
            // straight into that homework's detail page; otherwise land on the
            // homework list.
            "homework_assigned", "homework_reminder",
            "homework_created", "homework_reviewed"                       -> {
                val hwId = intent.getStringExtra("homeworkId")?.takeIf { it.isNotBlank() }
                if (hwId != null) "homework?hwId=$hwId" else "homework"
            }
            // LOW-7: thread the examId so the correct exam opens instead of
            // always landing on exam index 0.
            // TODO: the sender does not yet carry studentId; the parent app is
            //  single-student per session, so child-switching isn't actionable
            //  here — revisit if multi-child sessions land.
            "result_published"                                            -> {
                val examId = intent.getStringExtra("examId")?.takeIf { it.isNotBlank() }
                if (examId != null) "results?examId=$examId" else "results"
            }
            // MED-6: exam_scheduled now routes to the Exam Schedule screen
            // (previously misrouted to Results).
            "exam_scheduled"                                              -> {
                val examId = intent.getStringExtra("examId")?.takeIf { it.isNotBlank() }
                if (examId != null) "exams?examId=$examId" else "exams"
            }
            "event", "event_created"                                      -> {
                // Deep-link straight to the EventDetail screen when the payload
                // carries an eventId; otherwise drop back to the events list.
                val eventId = intent.getStringExtra("eventId")?.takeIf { it.isNotBlank() }
                if (eventId != null) "event_detail/$eventId" else "events"
            }
            "birthday_wish"                                                -> "notices"
            // Notice/circular pushes now deep-link to the Notices inbox (was
            // silently dropped, so tapping a notice notification did nothing).
            "notice_created", "circular_created", "notice", "circular"     -> "notices"
            "red_flag", "flag_created", "red_flag_created", "student_flag",
            "red_flag_resolved"                                            -> "red_flags"
            // Story pushes land on the Dashboard, where the story ring lives
            // (unseen stories are highlighted). The push only carries storyId —
            // not a parseable authorId (schoolId itself contains underscores) —
            // so we route to the ring rather than a specific author's viewer.
            "story", "story_created"                                      -> "dashboard"
            // Fallback for dispatchers that forward the raw pushRequests `mark`.
            else -> if (intent.getStringExtra("mark") in setOf("FLAG_CREATED", "FLAG_RESOLVED")) "red_flags" else null
        } ?: return
        // For notice deep-links, thread the tapped notice's id so the Notices
        // screen can auto-expand + scroll to it (was previously dropped, so a tap
        // only opened the list). Other targets ignore the arg.
        val noticeArg = if (target == "notices") {
            intent.getStringExtra("noticeId") ?: intent.getStringExtra("circularId")
        } else null
        DeepLinkBridge.publish(target, noticeArg)
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
                description = description ?: getString(R.string.fees_payment_failed)
            )
        )
    }
}
