package com.schoolsync.parent.service

import com.schoolsync.parent.R
import androidx.annotation.StringRes
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

    /**
     * Name and description are @StringRes ids, not Strings.
     *
     * CHANNELS is an object-level `val`, evaluated once at class-init with no
     * Context in scope — and a String captured there would freeze whichever
     * language the process started in. They are resolved inside
     * [ensureChannels], which already receives a Context.
     *
     * `id` stays a plain String: channel ids are WIRE VALUES that Android and
     * the manifest match on. Translating one would orphan the user's existing
     * channel settings and silently drop notifications posted to the old id.
     */
    private data class Def(
        val id: String,
        @StringRes val nameRes: Int,
        @StringRes val descriptionRes: Int,
    )

    private val CHANNELS = listOf(
        Def(GENERAL, R.string.notif_ch_general, R.string.notif_ch_general_desc),
        Def(NOTICES, R.string.notif_ch_notices, R.string.notif_ch_notices_desc),
        Def(HOMEWORK, R.string.notif_ch_homework, R.string.notif_ch_homework_desc),
        Def(ATTENDANCE, R.string.notif_ch_attendance, R.string.notif_ch_attendance_desc),
        Def(FEES, R.string.notif_ch_fees, R.string.notif_ch_fees_desc),
        Def(LEAVE, R.string.notif_ch_leave, R.string.notif_ch_leave_desc),
        Def(EVENTS, R.string.notif_ch_events, R.string.notif_ch_events_desc),
        Def(ALERTS, R.string.notif_ch_alerts, R.string.notif_ch_alerts_desc),
        Def(EXAMS, R.string.notif_ch_exams, R.string.notif_ch_exams_desc),
        Def(GALLERY, R.string.notif_ch_gallery, R.string.notif_ch_gallery_desc),
        Def(STORIES, R.string.notif_ch_stories, R.string.notif_ch_stories_desc),
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
                context.getString(def.nameRes),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(def.descriptionRes)
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
        "red_flag", "flag_created", "red_flag_created", "student_flag", "red_flag_resolved" -> ALERTS
        "exam_result", "exam_schedule" -> EXAMS
        "gallery_added" -> GALLERY
        "story", "story_created" -> STORIES
        else -> GENERAL
    }
}
