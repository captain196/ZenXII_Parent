package com.schoolsync.parent.util

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Useful extension functions used throughout the app.
 */

// ── Context Extensions ──────────────────────────────────────────────────

/** Show a short toast message */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/** Show a long toast message */
fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * Get or generate a persistent device ID.
 * Uses Android ID as primary, falls back to a generated UUID stored in SharedPreferences.
 */
fun Context.getDeviceId(): String {
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
        return androidId
    }
    // Fallback: generate and persist a UUID
    val prefs = getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    val stored = prefs.getString("device_uuid", null)
    if (stored != null) return stored
    val generated = UUID.randomUUID().toString()
    prefs.edit().putString("device_uuid", generated).apply()
    return generated
}

// ── String Extensions ───────────────────────────────────────────────────

/** Capitalize first letter of each word */
fun String.capitalizeWords(): String {
    return split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

/** Safely convert to Int with default */
fun String?.toSafeInt(default: Int = 0): Int {
    return this?.trim()?.toIntOrNull() ?: default
}

/** Safely convert to Double with default */
fun String?.toSafeDouble(default: Double = 0.0): Double {
    return this?.trim()?.toDoubleOrNull() ?: default
}

/** Safely convert to Long with default */
fun String?.toSafeLong(default: Long = 0L): Long {
    return this?.trim()?.toLongOrNull() ?: default
}

/** Check if string is a valid email */
fun String.isValidEmail(): Boolean {
    return android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

/** Mask email for display (e.g., "j***@example.com") */
fun String.maskEmail(): String {
    val parts = split("@")
    if (parts.size != 2) return this
    val local = parts[0]
    val masked = if (local.length > 2) {
        local.first() + "***" + local.last()
    } else {
        local.first() + "***"
    }
    return "$masked@${parts[1]}"
}

/** Mask phone number for display (e.g., "****1234") */
fun String.maskPhone(): String {
    if (length < 4) return this
    return "****" + takeLast(4)
}

// ── Date/Time Extensions ────────────────────────────────────────────────

/** Format a timestamp (millis) to a readable date string */
fun Long.toFormattedDate(pattern: String = "dd MMM yyyy"): String {
    return try {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.format(Date(this))
    } catch (_: Exception) {
        ""
    }
}

/** Format a timestamp to a readable date-time string */
fun Long.toFormattedDateTime(pattern: String = "dd MMM yyyy, hh:mm a"): String {
    return try {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.format(Date(this))
    } catch (_: Exception) {
        ""
    }
}

/** Format a timestamp to relative time (e.g., "2 hours ago", "Yesterday") */
fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 30 -> "${days / 7} weeks ago"
        else -> toFormattedDate()
    }
}

/** Get current academic session string (e.g., "2025-26") */
fun getCurrentSession(): String {
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)

    // Academic year runs April to March
    return if (month >= Calendar.APRIL) {
        "$year-${(year + 1) % 100}"
    } else {
        "${year - 1}-${year % 100}"
    }
}

/** Get current month name */
fun getCurrentMonthName(): String {
    return Constants.getMonthName(Calendar.getInstance().get(Calendar.MONTH))
}

/** Get today's day name (Monday, Tuesday, etc.) */
fun getTodayName(): String {
    val calendar = Calendar.getInstance()
    return when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        Calendar.SUNDAY -> "Sunday"
        else -> "Monday"
    }
}

// ── Number Extensions ───────────────────────────────────────────────────

/** Format a Double as currency (Indian Rupees) */
fun Double.toRupees(): String {
    return if (this == this.toLong().toDouble()) {
        "Rs. ${this.toLong()}"
    } else {
        "Rs. ${"%.2f".format(this)}"
    }
}

/** Format a Float as percentage string */
fun Float.toPercentString(decimals: Int = 1): String {
    return "${"%.${decimals}f".format(this)}%"
}

/** Format a Double as percentage string */
fun Double.toPercentString(decimals: Int = 1): String {
    return "${"%.${decimals}f".format(this)}%"
}

// ── Collection Extensions ───────────────────────────────────────────────

/** Safe get from a map with type casting */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Map<String, Any?>.getAs(key: String): T? {
    return this[key] as? T
}

/** Safe get with default value */
@Suppress("UNCHECKED_CAST")
inline fun <reified T> Map<String, Any?>.getOrDefault(key: String, default: T): T {
    return (this[key] as? T) ?: default
}
