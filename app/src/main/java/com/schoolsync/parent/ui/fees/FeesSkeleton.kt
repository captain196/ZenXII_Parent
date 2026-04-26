package com.schoolsync.parent.ui.fees

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard

/**
 * Lightweight shimmer placeholders for the Fees tabs. Replaces the
 * old CircularProgressIndicator while data loads — perceived speed
 * goes up because users see card-shaped slots that match the real
 * content layout.
 *
 * Uses a single infinite alpha transition (cheap; no extra dep).
 */
@Composable
private fun ShimmerBlock(width: Dp? = null, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "feesShimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "feesShimmerAlpha"
    )
    val base = (modifier
        .height(height)
        .clip(RoundedCornerShape(6.dp))
        .background(c.shimmerBase.copy(alpha = alpha)))
    if (width != null) Box(modifier = base.width(width)) else Box(modifier = base.fillMaxWidth())
}

@Composable
fun PendingFeesSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Quick-action button row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerBlock(height = 40.dp, modifier = Modifier.weight(1f))
                ShimmerBlock(height = 40.dp, modifier = Modifier.weight(1f))
            }
        }
        // Section label
        item { ShimmerBlock(width = 160.dp, height = 12.dp) }
        // 5 month cards
        items(5) { _ ->
            Column(
                modifier = Modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                                .background(LocalAppColors.current.shimmerBase.copy(alpha = 0.4f))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ShimmerBlock(width = 90.dp, height = 14.dp)
                            ShimmerBlock(width = 50.dp, height = 10.dp)
                        }
                    }
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ShimmerBlock(width = 80.dp, height = 14.dp)
                        ShimmerBlock(width = 60.dp, height = 10.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentsSkeleton() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(4) { _ ->
            Row(
                modifier = Modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(LocalAppColors.current.shimmerBase.copy(alpha = 0.4f))
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerBlock(width = 140.dp, height = 14.dp)
                    ShimmerBlock(width = 90.dp, height = 10.dp)
                }
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerBlock(width = 70.dp, height = 14.dp)
                    ShimmerBlock(width = 40.dp, height = 10.dp)
                }
            }
        }
    }
}
