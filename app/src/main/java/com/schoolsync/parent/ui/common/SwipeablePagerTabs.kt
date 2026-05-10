package com.schoolsync.parent.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reusable swipe-paged tab content. Tab chip UI stays in the caller — this
 * component owns only the pager and bidirectional sync with the caller's
 * (selectedTab, onTabChange) state.
 *
 * - Click a chip → onTabChange fires → pager animates to that page
 * - Swipe page  → settled-page change fires onTabChange (only on settled
 *   page, so we don't thrash ViewModel state during a drag)
 *
 * Each page receives its tab key and renders its own content. Filter the
 * data per-tab inside `pageContent` (cache with `remember(deps)`) so adjacent
 * pages preloaded by the pager don't trigger duplicate work.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeablePagerTabs(
    tabs: List<String>,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    pageContent: @Composable (tabKey: String) -> Unit
) {
    val initialPage = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { tabs.size }
    )

    // Suppression flag: true while we're animating to a chip-clicked page.
    // Without this, intermediate `currentPage` updates from the in-flight
    // animation would fire onTabChange → re-trigger the selectedTab effect
    // → cancel the animation. We keep the animation untouched and skip
    // feedback while it runs.
    var isProgrammaticScrolling by remember { mutableStateOf(false) }

    // The pager→selectedTab effect below is keyed on (pagerState, tabs) so
    // it never restarts. Without rememberUpdatedState the closure would
    // capture the FIRST selectedTab forever, and comparing against the
    // stale value silently dropped page-0 transitions when initial tab
    // was "all". Use a state delegate so the closure always reads latest.
    val latestSelectedTab by rememberUpdatedState(selectedTab)

    // External selection -> pager (chip click). Skip if pager is already
    // moving (user is dragging) so we don't fight a manual swipe.
    LaunchedEffect(selectedTab) {
        val target = tabs.indexOf(selectedTab).coerceAtLeast(0)
        if (target != pagerState.currentPage && !pagerState.isScrollInProgress) {
            try {
                isProgrammaticScrolling = true
                pagerState.animateScrollToPage(target)
            } finally {
                isProgrammaticScrolling = false
            }
        }
    }

    // Pager -> external selection. Use `currentPage` (not `settledPage`) so
    // the chip lights up the moment the user crosses the halfway threshold —
    // no waiting for the settle animation to finish.
    LaunchedEffect(pagerState, tabs) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (isProgrammaticScrolling) return@collect
                tabs.getOrNull(page)?.let { tabKey ->
                    if (tabKey != latestSelectedTab) onTabChange(tabKey)
                }
            }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        userScrollEnabled = userScrollEnabled
    ) { page ->
        pageContent(tabs[page])
    }
}
