@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.parent.ui.splash

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

data class WalkthroughPage(
    val icon: ImageVector,
    val iconBgBuilder: (AppColors) -> List<Color>,
    /**
     * A @StringRes id, not a String. This list is a top-level `val`, evaluated
     * once at class-init outside any composition, so it cannot call
     * stringResource() — and a String captured there would freeze whichever
     * language the process happened to start in and survive recreate().
     */
    @StringRes val titleRes: Int,
    // Same reasoning as titleRes above — this list is a top-level val, so the
    // description must be a resource id too, not a resolved String.
    @StringRes val descriptionRes: Int
)

val parentPages = listOf(
    WalkthroughPage(
        icon = Icons.Filled.TrendingUp,
        iconBgBuilder = { c -> listOf(c.success, Color(0xFF16A34A)) },
        titleRes = R.string.splash_track_progress,
        descriptionRes = R.string.wt_desc_monitor
    ),
    WalkthroughPage(
        icon = Icons.Filled.Notifications,
        iconBgBuilder = { c -> listOf(c.accent, c.accentSecondary) },
        titleRes = R.string.splash_stay_informed,
        descriptionRes = R.string.wt_desc_notify
    ),
    WalkthroughPage(
        icon = Icons.Filled.AccountBalanceWallet,
        iconBgBuilder = { c -> listOf(c.purple, Color(0xFF7C3AED)) },
        titleRes = R.string.splash_manage_fees,
        descriptionRes = R.string.wt_desc_fees
    )
)

@Composable
fun WalkthroughScreen(
    onFinished: () -> Unit
) {
    val colors = LocalAppColors.current
    val pagerState = rememberPagerState(pageCount = { parentPages.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == parentPages.size - 1

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
            }
    ) {
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) { page ->
            WalkthroughPageContent(parentPages[page], colors)
        }

        // Bottom bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(parentPages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    val color by animateColorAsState(
                        targetValue = if (isActive) colors.accent else colors.textTertiary.copy(alpha = 0.4f),
                        animationSpec = tween(300), label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onFinished) {
                    Text(
                        text = stringResource(R.string.common_skip),
                        color = colors.textSecondary,
                        fontSize = 15.sp
                    )
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinished()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = if (colors.isDark) colors.bgStart else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = if (isLastPage) stringResource(R.string.splash_get_started) else stringResource(R.string.wt_next),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    if (!isLastPage) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkthroughPageContent(page: WalkthroughPage, colors: AppColors) {
    val iconBg = page.iconBgBuilder(colors)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(iconBg.first(), RoundedCornerShape(32.dp))
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = iconBg,
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = stringResource(page.titleRes),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(page.descriptionRes),
            fontSize = 16.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}
