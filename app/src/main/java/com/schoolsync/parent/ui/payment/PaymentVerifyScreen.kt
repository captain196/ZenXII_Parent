package com.schoolsync.parent.ui.payment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.schoolsync.parent.ui.components.rememberAppHaptics
import com.schoolsync.parent.ui.theme.LocalAppColors

/**
 * Phase the [PaymentVerifyScreen] should render. Mapped 1:1 from the
 * Verifying / Confirming branches of
 * [com.schoolsync.parent.data.payment.PaymentSession.State] —
 * Success is owned by [PaymentSuccessScreen] and Pending / Failure are
 * still surfaced by FeesScreen banners.
 */
enum class VerifyPhase { Verifying, Confirming }

/**
 * Full-screen "we've got your money, now let's record it" surface
 * shown by [PaymentFlowOverlay] for the brief 1-4 s window between
 * Razorpay capture and the Firestore receipt write.
 *
 * Why this exists:
 * Without this overlay the parent sees nothing but a tiny in-button
 * spinner and a snackbar — for a payment that just took thousands of
 * rupees from their account. That feels broken even though the
 * server pipeline is doing exactly the right thing. The full-screen
 * surface communicates "your money is safe, we're recording it".
 *
 * State-machine truthful — no fake progress, no artificial delays.
 * Each step transition reflects a real PaymentSession state change.
 *
 *  1. Payment captured              — always done by the time we render
 *  2. Verifying with school server  — active during Verifying
 *  3. Recording in school records   — active during Confirming
 *
 * Touch / back behaviour:
 * Absorbs all taps so the underlying NavHost can't be reached. The
 * back button is consumed silently — leaving mid-verify would orphan
 * the payment from the user's perspective even though the server
 * pipeline keeps going.
 */
@Composable
fun PaymentVerifyScreen(
    phase: VerifyPhase,
    months: List<String>
) {
    val c = LocalAppColors.current
    val haptics = rememberAppHaptics()

    // One subtle tick when the overlay first appears (signals "we
    // received your payment, hang on") and another when verify
    // completes and we move to recording. No tick into Success —
    // PaymentSuccessScreen owns its own appearance.
    LaunchedEffect(Unit) { haptics.light() }
    LaunchedEffect(phase) {
        if (phase == VerifyPhase.Confirming) haptics.light()
    }

    // Stage B1 escape-hatch survivability. Verify is normally 1-4s, so
    // most users never see anything beyond the spinning ladder. But if
    // the call hangs (network drops, server slow) we MUST give them a
    // way out — locking back forever is unprofessional and erodes
    // trust. Two-stage relief:
    //   • At 20s — surface a calm "still working in background" hint
    //     so the user knows the app isn't frozen and that closing it
    //     won't lose the payment.
    //   • At 30s — release the BackHandler so a back-press genuinely
    //     leaves the screen. PaymentSession is app-singleton scoped,
    //     so the verify call keeps running and the success/failure
    //     overlay will reappear on the next app foreground.
    var showStuckHint by remember { mutableStateOf(false) }
    var allowBackEscape by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(20_000)
        showStuckHint = true
        delay(10_000) // total 30s from screen entry
        allowBackEscape = true
    }

    // BackHandler intercepts back ONLY while we're still in the normal
    // verify window. After 30s, allow the back press to bubble to the
    // activity (which backstacks/backgrounds) — PaymentSession survives.
    BackHandler(enabled = !allowBackEscape) { /* swallow */ }

    // .clickable absorbs taps so they can't fall through to the
    // NavHost beneath the overlay — same pattern as PaymentSuccessScreen.
    val blockerInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgStart)
            .clickable(
                interactionSource = blockerInteraction,
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            Text(
                "Processing your payment",
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            val monthsLabel = when {
                months.isEmpty() -> "Securely recording your payment"
                months.size == 1 -> "${months.first()} fee"
                else -> months.joinToString(", ") + " fees"
            }
            Text(
                monthsLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            val step1 = StepState.Done
            val step2 = if (phase == VerifyPhase.Verifying) StepState.Active else StepState.Done
            val step3 = if (phase == VerifyPhase.Confirming) StepState.Active else StepState.Pending

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                StepRow(
                    title = "Payment captured",
                    subtitle = "Confirmed by Razorpay",
                    state = step1,
                    showConnector = true,
                    connectorActive = true
                )
                StepRow(
                    title = "Verifying with school server",
                    subtitle = "Securing your transaction",
                    state = step2,
                    showConnector = true,
                    connectorActive = step2 == StepState.Done
                )
                StepRow(
                    title = "Recording in school records",
                    subtitle = "Updating fees and receipt",
                    state = step3,
                    showConnector = false
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Default state — calm "stay here" pill. Hidden once the
            // 20s stuck-hint kicks in, since the longer message
            // supersedes it.
            if (!showStuckHint) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.warningBg)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Please don't close the app",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.warning,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // 20s+ — give the user reassurance that closing the app
                // is now safe (PaymentSession is app-singleton; the
                // verify call survives backgrounding) and, after another
                // 10s, that back actually works.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Still working in the background",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (allowBackEscape)
                            "You can press back to leave — your receipt will appear in the Fees list once verification completes."
                        else
                            "Your payment is safe with Razorpay. The receipt will appear once the school server responds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = c.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Secured by Razorpay",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private enum class StepState { Done, Active, Pending }

@Composable
private fun StepRow(
    title: String,
    subtitle: String,
    state: StepState,
    showConnector: Boolean,
    connectorActive: Boolean = false
) {
    val c = LocalAppColors.current

    // Always create the infinite transition so composition shape stays
    // identical across state changes — only its value is consumed when
    // the row is Active.
    val transition = rememberInfiniteTransition(label = "step-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "step-pulse-alpha"
    )

    val ringColor = when (state) {
        StepState.Done -> c.success
        StepState.Active -> c.accent
        StepState.Pending -> c.textTertiary
    }
    val titleColor = when (state) {
        StepState.Pending -> c.textTertiary
        else -> c.textPrimary
    }
    val subtitleColor = when (state) {
        StepState.Pending -> c.textTertiary.copy(alpha = 0.7f)
        else -> c.textSecondary
    }
    val ringAlpha = when (state) {
        StepState.Active -> pulse
        StepState.Pending -> 0.4f
        StepState.Done -> 1f
    }
    val ringWidth = if (state == StepState.Active) 1.5.dp else 1.dp
    val circleBg = when (state) {
        StepState.Done -> c.success.copy(alpha = 0.18f)
        StepState.Active -> c.accent.copy(alpha = 0.18f)
        StepState.Pending -> Color.Transparent
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .border(ringWidth, ringColor.copy(alpha = ringAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    StepState.Done -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = c.success,
                        modifier = Modifier.size(20.dp)
                    )
                    StepState.Active -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = c.accent,
                        strokeWidth = 1.5.dp
                    )
                    StepState.Pending -> Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c.textTertiary.copy(alpha = 0.4f))
                    )
                }
            }
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(
                            if (connectorActive) c.success.copy(alpha = 0.5f)
                            else c.textTertiary.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.padding(
                top = 2.dp,
                bottom = if (showConnector) 10.dp else 6.dp
            )
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
    }
}
