package com.schoolsync.parent.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SchoolSync spacing scale.
 *
 * One source of truth for every padding/margin/spacer in the app. Pulled
 * from the design system; values follow a 4dp base grid.
 *
 * Use these everywhere instead of hard-coded `.dp` literals so a single
 * tweak in this file rescales the whole app consistently.
 *
 *   xxs = 2dp   — hairline gaps inside compact rows
 *   xs  = 4dp   — small inline gaps (icon ↔ label)
 *   sm  = 8dp   — tight padding inside chips/badges
 *   md  = 12dp  — default content padding inside cards
 *   lg  = 16dp  — screen edge padding, list-item insets
 *   xl  = 20dp  — section gaps
 *   xxl = 24dp  — large hero spacing
 *   xxxl = 32dp — empty-state padding
 */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,

    // Semantic — readable at the call site
    val cardCornerRadius: Dp = 18.dp,
    val pillCornerRadius: Dp = 50.dp,
    val touchTarget: Dp = 48.dp,
    val iconSm: Dp = 18.dp,
    val iconMd: Dp = 22.dp,
    val iconLg: Dp = 28.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
