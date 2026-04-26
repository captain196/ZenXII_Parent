package com.schoolsync.parent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Centralised haptics so every screen feels the same.
 *
 * Usage:
 * ```
 * val haptics = rememberAppHaptics()
 * Button(onClick = { haptics.light(); onSubmit() }) { ... }
 * ```
 *
 * On API 24-29 only [HapticFeedbackType.LongPress] / [TextHandleMove] exist;
 * we fall back gracefully there. Compose 1.6 only exposes those two anyway.
 */
class AppHaptics internal constructor(private val raw: HapticFeedback) {
    /** Tap / toggle / chip selection — barely-there. */
    fun light() = raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)

    /** Primary CTA, form submit, confirm. */
    fun medium() = raw.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Successful save / payment / mark-attendance. */
    fun success() = raw.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Error / validation failure / payment decline. */
    fun error() = raw.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Navigation tab switch — subtle tick. */
    fun navTick() = raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val raw = LocalHapticFeedback.current
    return remember(raw) { AppHaptics(raw) }
}
