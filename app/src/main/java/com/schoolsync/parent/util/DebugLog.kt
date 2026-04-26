package com.schoolsync.parent.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * OEM workaround — iQOO / OnePlus / Xiaomi strip third-party Debug
 * logs from `adb logcat` on OxygenOS/ColorOS/MIUI. To diagnose issues
 * in that environment we write diagnostic lines to a file inside the
 * app's own cache dir, which adb can pull without root via
 *     adb shell run-as com.schoolsync.parent cat cache/debug.log
 * (debuggable builds only). Safe to leave in place — cache is capped
 * at 50 KB and pruned on every write past that.
 *
 * Intentionally global (no DI) so legacy repositories can call it
 * without lifecycle plumbing. Context resolves once via [initDebugLog]
 * from Application.onCreate; before that, calls no-op.
 */
private var appContext: Context? = null
private const val MAX_BYTES = 50_000
private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

fun initDebugLog(ctx: Context) {
    appContext = ctx.applicationContext
}

fun debugLog(message: String) {
    // Still write to logcat for the rare device that allows it.
    Log.d("SchoolSyncDebug", message)
    val ctx = appContext ?: return
    try {
        val file = File(ctx.cacheDir, "debug.log")
        if (file.exists() && file.length() > MAX_BYTES) {
            file.writeText(file.readText().takeLast(MAX_BYTES / 2))
        }
        file.appendText("${timeFmt.format(Date())}  $message\n")
    } catch (_: Exception) { /* silent */ }
}
