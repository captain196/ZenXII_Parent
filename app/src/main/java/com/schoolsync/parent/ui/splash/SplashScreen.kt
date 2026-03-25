package com.schoolsync.parent.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.parent.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToWalkthrough: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToMain: () -> Unit,
    isLoggedIn: Boolean,
    hasSeenOnboarding: Boolean
) {
    val colors = LocalAppColors.current
    var startAnimation by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec = tween(800, easing = FastOutSlowInEasing), label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600), label = "logoAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400), label = "titleAlpha"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 700), label = "subtitleAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500)
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
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Outer glow
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(glowScale)
                        .alpha(glowAlpha)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(colors.accent.copy(alpha = 0.4f), colors.accent.copy(alpha = 0f)),
                                    center = center,
                                    radius = size.minDimension / 2f
                                )
                            )
                        }
                )
                // Logo circle
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(colors.accent, colors.accentSecondary),
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height)
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = "SchoolSync",
                        tint = if (colors.isDark) colors.bgStart else androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SchoolSync",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.alpha(titleAlpha)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Parent Portal",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.accent,
                modifier = Modifier.alpha(subtitleAlpha)
            )
        }
    }
}
