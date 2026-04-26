package com.schoolsync.parent.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * SchoolSync motion language.
 *
 * Named durations and easings so every animation in the app has the same
 * "feel". Use [Motion.standard], [Motion.emphasized], etc. as the
 * `animationSpec` for `tween()`, `animateFloatAsState`, `AnimatedVisibility`,
 * and friends.
 *
 *  - **standard**: 250 ms — default for state changes (toggles, hover, focus)
 *  - **emphasized**: 400 ms — content reveal, expand/collapse, navigation
 *  - **fast**: 150 ms — micro-interactions (chips, ripples, taps)
 *  - **slow**: 600 ms — first-impression entrances (shimmer, hero reveals)
 *
 * Easings follow Material 3 motion guidance:
 *  - StandardEasing for normal transitions
 *  - EmphasizedEasing (more pronounced curve) for content reveal
 */
object Motion {
    // ─── Easings ──────────────────────────────────────────────────────────
    /** Material standard easing curve. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** Material 3 emphasized — pronounced curve for hero/content reveal. */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    /** Decelerate (incoming elements). */
    val Decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    /** Accelerate (outgoing elements). */
    val Accelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // ─── Durations (ms) ───────────────────────────────────────────────────
    const val DurationFast = 150
    const val DurationStandard = 250
    const val DurationEmphasized = 400
    const val DurationSlow = 600

    // ─── Ready-to-use tween specs ─────────────────────────────────────────
    fun <T> fast() = tween<T>(durationMillis = DurationFast, easing = Standard)
    fun <T> standard() = tween<T>(durationMillis = DurationStandard, easing = Standard)
    fun <T> emphasized() = tween<T>(durationMillis = DurationEmphasized, easing = Emphasized)
    fun <T> slow() = tween<T>(durationMillis = DurationSlow, easing = Decelerate)
}
