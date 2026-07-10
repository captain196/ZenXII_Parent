package com.schoolsync.parent.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Canonical notification-channel registry for the Parent app.
 *
 * Android 8+ requires every posted notification to name a channel that has
 * already been created; posting to an unknown channel is **silently dropped**.
 * Previously the app created a single channel (`schoolsync_notifications`)
 * eagerly, but the manifest `default_notification_channel_id` pointed at
 * `school_sync_channel` — a channel that was NEVER created — so any FCM
 * `notification`-payload delivered while the app was backgrounded was dropped.
 *
 * This object owns all channel ids in one place, creates them eagerly, maps a
 * push `type`/`mark` to the right channel for per-category muting, and exposes
 * [GENERAL] as the id the manifest default now points to.
 *
 * [GENERAL] keeps the historical id `schoolsync_notifications` so existing
 * installs don't accumulate an orphaned channel.
 */
object NotificationChannels {

    /** Fallback + manifest `default_notification_channel_id`. Do NOT rename. */
    const val GENERAL = "schoolsync_notifications"
    const val NOTICES = "ch_notices"
    const val HOMEWORK = "ch_homework"
    const val ATTENDANCE = "ch_attendance"
    const val FEES = "ch_fees"
    const val LEAVE = "ch_leave"
    const val EVENTS = "ch_events"
    const val ALERTS = "ch_alerts"
    const val EXAMS = "ch_exams"
    const val GALLERY = "ch_gallery"
    const val STORIES = "ch_stories"

    private data class Def(val id: String, val name: String, val description: String)

    private val CHANNELS = listOf(
        Def(GENERAL, "General", "General notifications from ZenXii"),
        Def(NOTICES, "Notices & Circulars", "New notices and circulars"),
        Def(HOMEWORK, "Homework", "New and graded homework"),
        Def(ATTENDANCE, "Attendance", "Your child's attendance updates"),
        Def(FEES, "Fees", "Fee reminders and payment confirmations"),
        Def(LEAVE, "Leave", "Leave application updates"),
        Def(EVENTS, "Events", "School events"),
        Def(ALERTS, "Alerts", "Important alerts raised by teachers"),
        Def(EXAMS, "Exams & Results", "Exam schedules and published results"),
        Def(GALLERY, "Gallery", "New photos and albums"),
        Def(STORIES, "Stories", "New school stories"),
    )

    /**
     * Create every channel (idempotent — re-creating an existing channel with
     * the same id is a no-op that only refreshes name/description). Safe to call
     * on every service/app start.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        CHANNELS.forEach { def ->
            val channel = NotificationChannel(
                def.id,
                def.name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = def.description
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Map a push `type` (or raw pushRequests `mark`) to a channel id.
     * Unknown / null types fall back to [GENERAL].
     */
    fun channelForType(type: String?): String = when (type?.lowercase()) {
        "notice", "notice_created", "circular", "circular_created" -> NOTICES
        "homework_created", "homework_reviewed" -> HOMEWORK
        "student_absent", "student_late" -> ATTENDANCE
        "fee_payment_confirmed", "fee_reminder", "fee_defaulter_alert" -> FEES
        "leave_approved", "leave_rejected" -> LEAVE
        "event", "event_created" -> EVENTS
        "red_flag", "flag_created", "red_flag_created", "student_flag" -> ALERTS
        "exam_result", "exam_schedule" -> EXAMS
        "gallery_added" -> GALLERY
        "story", "story_created" -> STORIES
        else -> GENERAL
    }
}
