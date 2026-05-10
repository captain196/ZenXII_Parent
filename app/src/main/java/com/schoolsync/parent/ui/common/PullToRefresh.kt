package com.schoolsync.parent.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.schoolsync.parent.ui.theme.LocalAppColors

/**
 * Reusable pull-to-refresh wrapper. Material3 1.2 (our BoM) doesn't ship
 * PullToRefreshBox so we use the legacy material `pullRefresh` modifier
 * which coexists with material3 cleanly.
 *
 * IMPORTANT: the inner Box must be fillMaxSize — without it the gesture
 * has no surface to attach to and the swipe-down does nothing.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit
) {
    val state = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )
    val c = LocalAppColors.current
    Box(modifier = modifier.pullRefresh(state)) {
        content()
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = c.bgStart,
            contentColor = c.accent
        )
    }
}
