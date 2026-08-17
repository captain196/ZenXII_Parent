package com.schoolsync.parent.util

import android.content.Context
import com.schoolsync.parent.R

/**
 * Translates canonical English **data** values into display text, at render
 * time only.
 *
 * ## The problem this solves
 *
 * A handful of English strings in ZenXii are simultaneously wire values and UI
 * labels. `"Monday"` is the `day` field on a timetable document *and* part of
 * its document id (`{schoolId}_{session}_{sectionKey}_{day}`). `"January"` is a
 * key. `"Present"` is a stored attendance status. They look like copy, so they
 * are the single easiest thing to translate by accident — and doing so writes
 * `"सोमवार"` into a Firestore key, which corrupts data silently and may not
 * surface for weeks.
 *
 * ## The invariant
 *
 * > The canonical string is never transformed in the data layer.
 *
 * Every function here takes the canonical value and returns display text. Call
 * them from composables and other render sites. **Never** call them in a
 * repository, and never store what they return.
 *
 * ## Passthrough is mandatory
 *
 * Unknown keys return the input unchanged rather than blank or a placeholder.
 * Schools define their own fee heads, and RBAC modules get added server-side
 * without an app release. Those must render their English name, which is
 * readable, rather than an empty cell, which looks broken.
 */
object CanonicalLabels {

    private val DAYS: Map<String, Int> = mapOf(
        "monday" to R.string.day_monday,
        "tuesday" to R.string.day_tuesday,
        "wednesday" to R.string.day_wednesday,
        "thursday" to R.string.day_thursday,
        "friday" to R.string.day_friday,
        "saturday" to R.string.day_saturday,
        "sunday" to R.string.day_sunday
    )

    private val MONTHS: Map<String, Int> = mapOf(
        "january" to R.string.month_january,
        "february" to R.string.month_february,
        "march" to R.string.month_march,
        "april" to R.string.month_april,
        "may" to R.string.month_may,
        "june" to R.string.month_june,
        "july" to R.string.month_july,
        "august" to R.string.month_august,
        "september" to R.string.month_september,
        "october" to R.string.month_october,
        "november" to R.string.month_november,
        "december" to R.string.month_december
    )

    private val ATTENDANCE: Map<String, Int> = mapOf(
        "present" to R.string.attendance_status_present,
        "absent" to R.string.attendance_status_absent,
        "leave" to R.string.attendance_status_leave,
        "holiday" to R.string.attendance_status_holiday,
        "tardy" to R.string.attendance_status_tardy,
        "not recorded" to R.string.attendance_status_not_recorded
    )

    /**
     * Look up [key] in [map], falling back to the key itself.
     *
     * Lower-cased with [java.util.Locale.ROOT], not the display locale — the
     * Turkish dotless-i problem means `"I".lowercase()` under a Turkish locale
     * produces "ı", which would miss every lookup. Lookup keys are machine
     * values, so they get machine casing.
     */
    private fun lookup(ctx: Context, map: Map<String, Int>, key: String?): String {
        if (key.isNullOrBlank()) return ""
        val res = map[key.trim().lowercase(java.util.Locale.ROOT)]
        return if (res != null) ctx.getString(res) else key
    }

    /** `"Monday"` → the weekday name in the app's language. */
    fun day(ctx: Context, canonical: String?): String = lookup(ctx, DAYS, canonical)

    /** `"January"` → the month name in the app's language. */
    fun month(ctx: Context, canonical: String?): String = lookup(ctx, MONTHS, canonical)

    /** `"Present"` → the status name in the app's language. */
    fun attendanceStatus(ctx: Context, canonical: String?): String =
        lookup(ctx, ATTENDANCE, canonical)

    /**
     * A `Calendar.MONTH` int (0-based) → month name in the app's language.
     * Mirrors `Constants.getMonthName`, which stays English because its output
     * is used as a key.
     */
    fun monthByIndex(ctx: Context, calendarMonth: Int): String =
        month(ctx, Constants.getMonthName(calendarMonth))
}
