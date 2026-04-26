package com.schoolsync.parent.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
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
        private const val CHANNEL_ID = "schoolsync_notifications"
        private const val CHANNEL_NAME = "SchoolSync Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications from SchoolSync"
    }

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val deviceId: String
        @SuppressLint("HardwareIds")
        get() = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
                title = notification.title ?: "SchoolSync",
                body = notification.body ?: "",
                data = message.data
            )
        }

        // Handle data-only messages
        if (message.notification == null && message.data.isNotEmpty()) {
            val type = message.data["type"] ?: ""
            val title = message.data["title"] ?: "SchoolSync"
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
                else -> {
                    if (body.isNotBlank()) {
                        showNotification(title = title, body = body, data = message.data)
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

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
