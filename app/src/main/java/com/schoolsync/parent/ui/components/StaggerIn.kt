package com.schoolsync.parent.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Subtle stagger-on-first-composition for list items. Each item fades in and
 * slides up by a few pixels, with a small delay scaled by [index].
 *
 * Drop in as the FIRST modifier on a list item:
 * ```
 * items(notices.size) { i ->
 *     NoticeCard(
 *         notice = notices[i],
 *         modifier = Modifier.staggerIn(i)
 *     )
 * }
 * ```
 *
 * - 200ms duration
 * - 30ms per-item stagger, capped to 8 items so a 100-item list doesn't take
 *   3 seconds to fully reveal.
 */
fun Modifier.staggerIn(
    index: Int,
    perItemDelayMs: Int = 30,
    maxStaggeredItems: Int = 8,
    durationMs: Int = 200,
    translateYpx: Float = 16f,
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    val cappedIndex = index.coerceAtMost(maxStaggeredItems)
    val delay = cappedIndex * perItemDelayMs

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs, easing = LinearOutSlowInEasing),
        label = "stagger"
    )

    this
        .alpha(progress)
        .graphicsLayer {
            translationY = (1f - progress) * translateYpx
        }
}
