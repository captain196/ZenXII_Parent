package com.schoolsync.parent.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.schoolsync.parent.MainActivity
import com.schoolsync.parent.R
import com.schoolsync.parent.data.repository.AuthRepository
import com.schoolsync.parent.data.repository.AuthResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val deviceId: String
        @SuppressLint("HardwareIds")
        get() = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    override fun onCreate() {
        super.onCreate()
        // Create all channels up front so the manifest default_notification_channel_id
        // always resolves (backgrounded notification-payload pushes rely on it) and
        // per-category muting works. Idempotent.
        NotificationChannels.ensureChannels(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")

        serviceScope.launch {
            try {
                when (val result = authRepository.registerFcmToken(token, deviceId)) {
                    is AuthResult.Success -> {
                        Log.d(TAG, "FCM token registered successfully")
                    }
                    is AuthResult.Error -> {
                        Log.e(TAG, "Failed to register FCM token: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        // Handle notification payload
        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "ZenXii",
                body = notification.body ?: "",
                data = message.data
            )
        }

        // Handle data-only messages
        if (message.notification == null && message.data.isNotEmpty()) {
            val type = message.data["type"] ?: ""
            val title = message.data["title"] ?: "ZenXii"
            val body = message.data["body"] ?: message.data["message"] ?: ""

            when (type) {
                "fee_payment_confirmed" -> {
                    showNotification(
                        title = title.ifBlank { "Fee Payment Confirmed" },
                        body = body.ifBlank { "Your payment has been received" },
                        data = message.data
                    )
                }
                "fee_reminder" -> {
                    showNotification(
                        title = title.ifBlank { "Fee Reminder" },
                        body = body.ifBlank { "You have pending fees" },
                        data = message.data
                    )
                }
                "fee_defaulter_alert" -> {
                    showNotification(
                        title = title.ifBlank { "Fee Alert" },
                        body = body.ifBlank { "Outstanding fees may affect exam access" },
                        data = message.data
                    )
                }
                "student_absent" -> {
                    showNotification(
                        title = title.ifBlank { "Attendance: Absent" },
                        body = body.ifBlank { "Your child was marked Absent today" },
                        data = message.data
                    )
                }
                "student_late" -> {
                    showNotification(
                        title = title.ifBlank { "Attendance: Late" },
                        body = body.ifBlank { "Your child was marked Late today" },
                        data = message.data
                    )
                }
                "leave_approved" -> {
                    showNotification(
                        title = title.ifBlank { "Leave Approved" },
                        body = body.ifBlank { "Your leave application has been approved" },
                        data = message.data
                    )
                }
                "leave_rejected" -> {
                    showNotification(
                        title = title.ifBlank { "Leave Rejected" },
                        body = body.ifBlank { "Your leave application has been rejected" },
                        data = message.data
                    )
                }
                "homework_created" -> {
                    showNotification(
                        title = title.ifBlank { "New Homework" },
                        body = body.ifBlank { "New homework has been assigned" },
                        data = message.data
                    )
                }
                "homework_reviewed" -> {
                    showNotification(
                        title = title.ifBlank { "Homework Graded" },
                        body = body.ifBlank { "Your homework has been reviewed" },
                        data = message.data
                    )
                }
                "notice_created" -> {
                    showNotification(
                        title = title.ifBlank { "New Notice" },
                        body = body.ifBlank { "A new notice has been posted" },
                        data = message.data
                    )
                }
                "circular_created" -> {
                    showNotification(
                        title = title.ifBlank { "New Circular" },
                        body = body.ifBlank { "A new circular has been posted" },
                        data = message.data
                    )
                }
                "event", "event_created" -> {
                    // Backend sends title = "New Event: {…}", body = "{startDate} | {location}".
                    // We keep the server-provided strings but guard against blanks so the
                    // notification always has usable text even on a malformed payload.
                    showNotification(
                        title = title.ifBlank { "New Event" },
                        body  = body.ifBlank  { "Tap to view details" },
                        data  = message.data
                    )
                }
                "birthday_wish" -> {
                    // Admin-sent birthday wish. Also written to the `notices`
                    // collection as an inbox entry for persistence if the
                    // push is swiped away.
                    showNotification(
                        title = title.ifBlank { "🎂 Happy Birthday!" },
                        body  = body.ifBlank  { "Wishing you a wonderful year ahead!" },
                        data  = message.data
                    )
                }
                // Red-flag alert. The Teacher app queues pushRequests{mark:
                // "FLAG_CREATED"}; the server dispatcher's exact `type` string
                // isn't pinned, so match the plausible variants AND fall back to
                // the raw `mark`. High-severity flags get an urgent title.
                "red_flag", "flag_created", "red_flag_created", "student_flag" -> {
                    val severity = message.data["severity"].orEmpty()
                    val urgent = severity.equals("high", ignoreCase = true)
                    showNotification(
                        title = title.ifBlank { if (urgent) "⚠️ Urgent Alert" else "New Alert" },
                        body = body.ifBlank { "A teacher raised an alert about your child. Tap to view." },
                        data = message.data
                    )
                }
                // Flag cleared — reassuring "good news" notification (not urgent).
                "red_flag_resolved" -> {
                    showNotification(
                        title = title.ifBlank { "✅ Flag Resolved" },
                        body = body.ifBlank { "A flag about your child has been resolved." },
                        data = message.data
                    )
                }
                // New story posted by a teacher/admin. The universal dispatcher
                // sends type = "story_created" (idKey storyId). Without this case
                // a data-only story push with a blank body was silently dropped;
                // now it always shows on the STORIES channel (channelForType maps
                // "story"/"story_created" → STORIES).
                "story", "story_created" -> {
                    showNotification(
                        title = title.ifBlank { "New Story" },
                        body = body.ifBlank { "A new story was posted. Tap to view." },
                        data = message.data
                    )
                }
                else -> {
                    // Fallback: some payloads carry the raw pushRequests `mark`
                    // instead of a `type`. Catch the flag marks here so an
                    // unpinned dispatcher still notifies rather than going silent.
                    when (message.data["mark"]) {
                        "FLAG_CREATED" -> showNotification(
                            title = title.ifBlank { "New Alert" },
                            body = body.ifBlank { "A teacher raised an alert about your child. Tap to view." },
                            data = message.data
                        )
                        "FLAG_RESOLVED" -> showNotification(
                            title = title.ifBlank { "✅ Flag Resolved" },
                            body = body.ifBlank { "A flag about your child has been resolved." },
                            data = message.data
                        )
                        else -> if (body.isNotBlank()) {
                            showNotification(title = title, body = body, data = message.data)
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            data.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Resolve the per-category channel for this payload (falls back to
        // GENERAL for unknown/missing types). Channels are created in onCreate.
        val channelId = NotificationChannels.channelForType(data["type"] ?: data["mark"])

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_zenxii)
            .setColor(ContextCompat.getColor(this, R.color.notification_color))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notificationBuilder.build()
        )
    }
}
