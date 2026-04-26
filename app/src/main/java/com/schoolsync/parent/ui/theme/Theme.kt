package com.schoolsync.parent.ui.theme

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private fun buildDarkScheme(c: AppColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = Color.White,
    primaryContainer = c.accentSecondary,
    onPrimaryContainer = c.accent,
    secondary = c.slateBlue,
    onSecondary = c.textPrimary,
    secondaryContainer = SlateBlueLight,
    onSecondaryContainer = c.textPrimary,
    tertiary = c.info,
    onTertiary = Color.White,
    background = c.bgStart,
    onBackground = c.textPrimary,
    surface = c.surfaceDark,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceElevated,
    onSurfaceVariant = c.textSecondary,
    error = c.error,
    onError = Color.White,
    errorContainer = c.errorBg,
    onErrorContainer = c.error,
    outline = c.glassBorder,
    outlineVariant = c.divider,
    inverseSurface = c.textPrimary,
    inverseOnSurface = c.bgStart,
    surfaceTint = c.accent
)

private fun buildLightScheme(c: AppColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = Color.White,
    primaryContainer = c.accentBg,
    onPrimaryContainer = c.accent,
    secondary = c.slateBlue,
    onSecondary = Color.White,
    secondaryContainer = c.accentBg,
    onSecondaryContainer = c.textPrimary,
    tertiary = c.info,
    onTertiary = Color.White,
    background = c.bgMid,
    onBackground = c.textPrimary,
    surface = c.surfaceElevated,
    onSurface = c.textPrimary,
    surfaceVariant = c.surfaceDark,
    onSurfaceVariant = c.textSecondary,
    error = c.error,
    onError = Color.White,
    errorContainer = c.errorBg,
    onErrorContainer = c.error,
    outline = c.glassBorder,
    outlineVariant = c.divider,
    inverseSurface = c.textPrimary,
    inverseOnSurface = c.bgStart,
    surfaceTint = c.accent
)

@Composable
fun SchoolSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkColors else LightColors
    val colorScheme = if (darkTheme) buildDarkScheme(appColors) else buildLightScheme(appColors)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = appColors.statusBarColor.toArgb()
            window.navigationBarColor = appColors.statusBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = appColors.lightStatusBar
                isAppearanceLightNavigationBars = appColors.lightStatusBar
            }
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalSpacing provides Spacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// ─── Glass Card Modifiers (theme-aware) ──────────────────────────────────────

@Composable
fun Modifier.glassCard(cornerRadius: Dp = 20.dp): Modifier {
    val c = LocalAppColors.current
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(c.glass)
        .border(BorderStroke(1.dp, c.glassBorder), RoundedCornerShape(cornerRadius))
}

@Composable
fun Modifier.glassCardElevated(cornerRadius: Dp = 20.dp): Modifier {
    val c = LocalAppColors.current
    return this
        .clip(RoundedCornerShape(cornerRadius))
        .background(Brush.verticalGradient(listOf(c.glassHighlight, c.glass)))
        .border(BorderStroke(1.dp, c.glassBorder), RoundedCornerShape(cornerRadius))
}

@Composable
fun Modifier.gradientBackground(): Modifier {
    val c = LocalAppColors.current
    return this
        .background(c.bgStart)
        .drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(c.bgStart, c.bgMid, c.bgEnd, c.bgStart),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                )
            )
        }
}

@Composable
fun Modifier.accentGradientBackground(): Modifier {
    val c = LocalAppColors.current
    return this.background(Brush.horizontalGradient(listOf(c.accent, c.accentSecondary)))
}

@Composable
fun Modifier.tealGradientBackground(): Modifier = accentGradientBackground()
