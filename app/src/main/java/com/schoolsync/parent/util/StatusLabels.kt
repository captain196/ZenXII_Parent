package com.schoolsync.parent.util

import android.content.Context
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.AttendanceStatus

/**
 * Display labels for status enums.
 *
 * These are **extension functions in `util/`, deliberately not `@StringRes`
 * properties on the enums themselves.** Putting a resource id on
 * `AttendanceStatus` would drag an `R` import into `data/model/`, which would
 * make those classes depend on the Android framework and break the JVM unit
 * tests that currently run against them without a device.
 *
 * The enum's own `code` and `label` constructor arguments stay exactly as they
 * are: `PRESENT('P', "Present")`. `code` is what is written to RTDB, and
 * `label` remains the canonical English value for anything that stores or
 * compares. Only rendering goes through here.
 */

/** The status name in the app's language. */
fun AttendanceStatus.displayLabel(ctx: Context): String = when (this) {
    AttendanceStatus.PRESENT -> ctx.getString(R.string.attendance_status_present)
    AttendanceStatus.ABSENT -> ctx.getString(R.string.attendance_status_absent)
    AttendanceStatus.LEAVE -> ctx.getString(R.string.attendance_status_leave)
    AttendanceStatus.HOLIDAY -> ctx.getString(R.string.attendance_status_holiday)
    AttendanceStatus.TRIP -> ctx.getString(R.string.attendance_status_tardy)
    AttendanceStatus.VACATION -> ctx.getString(R.string.attendance_status_not_recorded)
}
