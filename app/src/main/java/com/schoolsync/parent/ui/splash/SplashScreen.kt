package com.schoolsync.parent.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.parent.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

private val ZenzGreen = Color(0xFF2DB87A)
private val ZenzDark = Color(0xFF1E2D3D)

@Composable
fun SplashScreen(
    onNavigateToWalkthrough: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    isLoggedIn: Boolean,
    hasSeenOnboarding: Boolean
) {
    val colors = LocalAppColors.current
    var phase by remember { mutableIntStateOf(0) }
    // Phase 0 = initial, 1 = logo appears, 2 = text appears, 3 = tagline appears

    // ── Logo animations ──
    val logoScale by animateFloatAsState(
        targetValue = when (phase) { 0 -> 0.2f; 1 -> 1.1f; else -> 1f },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(400), label = "logoAlpha"
    )

    // ── Green triangle draws in (clip reveal) ──
    val greenReveal by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing), label = "greenReveal"
    )
    val darkReveal by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(700, delayMillis = 250, easing = FastOutSlowInEasing), label = "darkReveal"
    )

    // ── Text animations ──
    val titleAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(500), label = "titleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 20f,
        animationSpec = tween(500, easing = FastOutSlowInEasing), label = "titleOffset"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(400), label = "subAlpha"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(400, delayMillis = 150), label = "tagAlpha"
    )

    // ── Pulsing glow ──
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowRadius"
    )

    // ── Sequencing ──
    LaunchedEffect(Unit) {
        delay(300)
        phase = 1  // logo
        delay(800)
        phase = 2  // title
        delay(500)
        phase = 3  // subtitle + tagline
        delay(1000)
        when {
            isLoggedIn -> onNavigateToMain()
            !hasSeenOnboarding -> onNavigateToWalkthrough()
            else -> onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgStart)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(colors.bgStart, colors.bgMid, colors.bgEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // ── ZENZ Logo ──
            Box(contentAlignment = Alignment.Center) {
                // Glow behind logo
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .alpha(if (phase >= 1) glowAlpha else 0f)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZenzGreen.copy(alpha = 0.5f),
                                ZenzGreen.copy(alpha = 0f)
                            ),
                            center = center,
                            radius = size.minDimension / 2f * glowRadius
                        )
                    )
                }

                // Z Logo canvas
                Canvas(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                ) {
                    val w = size.width
                    val h = size.height
                    // Green: top bar + diagonal stroke (connected Z shape)
                    val greenPath = Path().apply {
                        moveTo(w * 0.07f, h * 0.03f)
                        lineTo(w * 0.93f, h * 0.03f)
                        lineTo(w * 0.93f, h * 0.27f)
                        lineTo(w * 0.67f, h * 0.27f)
                        lineTo(w * 0.27f, h * 0.73f)
                        lineTo(w * 0.07f, h * 0.73f)
                        close()
                    }
                    // Dark: bottom bar
                    val darkPath = Path().apply {
                        moveTo(w * 0.07f, h * 0.73f)
                        lineTo(w * 0.93f, h * 0.73f)
                        lineTo(w * 0.93f, h * 0.97f)
                        lineTo(w * 0.07f, h * 0.97f)
                        close()
                    }

                    drawPath(greenPath, color = ZenzGreen, alpha = greenReveal)
                    drawPath(darkPath, color = if (colors.isDark) Color(0xFF8AA0B8) else ZenzDark, alpha = darkReveal)
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Brand name ──
            Text(
                text = "ZENZ",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = 8.sp,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .offset(y = titleOffsetY.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SCHOOL MANAGEMENT",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(subtitleAlpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Tagline ──
            Text(
                text = "Parent Portal",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ZenzGreen,
                modifier = Modifier.alpha(taglineAlpha)
            )
        }
    }
}
