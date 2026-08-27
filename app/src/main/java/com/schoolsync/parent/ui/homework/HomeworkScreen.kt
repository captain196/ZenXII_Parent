package com.schoolsync.parent.ui.homework

import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.Homework
import com.schoolsync.parent.ui.components.bouncyClickable
import com.schoolsync.parent.ui.components.staggerIn
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import com.schoolsync.parent.util.AttachmentUrlValidator

// ─── Main Entry Point ───────────────────────────────────────────────────────

@Composable
fun HomeworkScreen(
    onBack: () -> Unit,
    initialHomeworkId: String = "",
    initialTab: String = "",
    viewModel: HomeworkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Deep-link: preselect a list tab (e.g. "pending" when opened from the
    // profile homework stat). Applied once; the user can switch tabs freely
    // afterwards.
    LaunchedEffect(initialTab) {
        if (initialTab.isNotBlank()) viewModel.setTab(initialTab)
    }

    // Deep-link: when opened with a specific homework id (e.g. tapping a
    // dashboard "Today's Homework" row or a search result), jump straight to
    // its detail page. Until it resolves we show a loader — NOT the list — so
    // the user never sees the list flash past on the way to the item.
    // Guarded so it fires only once; afterwards Back returns to the list.
    val context = LocalContext.current
    var deepLinkConsumed by remember(initialHomeworkId) { mutableStateOf(initialHomeworkId.isBlank()) }
    LaunchedEffect(initialHomeworkId, uiState.allHomework, uiState.isLoading) {
        if (!deepLinkConsumed) {
            val match = uiState.allHomework.firstOrNull {
                it.homeworkId == initialHomeworkId || it.hwId == initialHomeworkId
            }
            when {
                match != null -> {
                    viewModel.selectHomework(match)
                    deepLinkConsumed = true
                }
                // Load finished and the id isn't in this section's list (e.g.
                // deleted / wrong section) — stop waiting, fall back to the list.
                // FIX 7: tell the user why we dropped them on the list instead
                // of silently swallowing the deep link.
                !uiState.isLoading -> {
                    deepLinkConsumed = true
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.hw_not_found),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    // True while we're resolving a deep-link and haven't opened the detail yet.
    val deepLinkPending = !deepLinkConsumed && uiState.selectedHomework == null

    // System back must return to the list (or close the submit dialog) when a
    // detail is open — without this the back press popped the whole screen
    // straight to the dashboard.
    BackHandler(enabled = uiState.selectedHomework != null || uiState.showSubmitDialog) {
        when {
            uiState.showSubmitDialog -> viewModel.hideSubmitDialog()
            uiState.selectedHomework != null -> viewModel.selectHomework(null)
        }
    }

    if (deepLinkPending) {
        // Deep-link resolving: a calm full-screen loader instead of the list,
        // so opening a specific homework reads as one step, not list-then-item.
        HomeworkDeepLinkLoading(onBack = onBack)
        return
    }

    AnimatedContent(
        targetState = uiState.selectedHomework,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it / 3 } + fadeOut()
            } else {
                slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it / 3 } + fadeOut()
            }
        },
        label = "homework_nav"
    ) { selected ->
        if (selected != null) {
            HomeworkDetailPage(
                homework = selected,
                onBack = { viewModel.selectHomework(null) },
                onMarkDone = { viewModel.showSubmitDialog(selected) },
                isMarking = uiState.markingDone
            )

            // Phase HW: Submit homework dialog with text input
            if (uiState.showSubmitDialog) {
                SubmitHomeworkDialog(
                    homework = selected,
                    isSubmitting = uiState.markingDone,
                    submitError = uiState.submitError,
                    onSubmit = { text -> viewModel.markAsDone(selected, text) },
                    onDismiss = { viewModel.hideSubmitDialog() }
                )
            }
        } else {
            HomeworkListPage(
                uiState = uiState,
                onBack = onBack,
                onTabChange = viewModel::setTab,
                onSubjectFilter = viewModel::selectSubject,
                onHomeworkClick = viewModel::selectHomework,
                onPullRefresh = { viewModel.pullRefresh() }
            )
        }
    }
}

/**
 * Full-screen loader shown while a deep-linked homework is being resolved, so
 * the list never flashes on the way to a specific item. Carries a back button
 * so a slow load never traps the user.
 */
@Composable
private fun HomeworkDeepLinkLoading(onBack: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = c.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        CircularProgressIndicator(
            color = c.accent,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LIST PAGE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HomeworkListPage(
    uiState: HomeworkUiState,
    onBack: () -> Unit,
    onTabChange: (String) -> Unit,
    onSubjectFilter: (String?) -> Unit,
    onHomeworkClick: (Homework) -> Unit,
    onPullRefresh: () -> Unit = {}
) {
    val c = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glass back button — FIX 6 a11y: 48dp min touch target (glass
            // visual stays 34dp; minimumInteractiveComponentSize expands only
            // the touchable/hit area).
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(11.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.drawer_homework),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (uiState.className.isNotBlank() || uiState.userName.isNotBlank()) {
                // Show whose homework this is (the active child) alongside the
                // class/section — important on multi-child accounts where the
                // class alone doesn't identify the sibling.
                Column(horizontalAlignment = Alignment.End) {
                    if (uiState.userName.isNotBlank()) {
                        Text(
                            text = uiState.userName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                    }
                    if (uiState.className.isNotBlank()) {
                        Text(
                            text = "${uiState.className} - ${uiState.section}",
                            fontSize = 11.sp,
                            color = c.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Status Tabs (pill chips) ────────────────────────────────────
        StatusTabChips(
            selectedTab = uiState.selectedTab,
            allHomework = uiState.allHomework,
            onTabChange = onTabChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Subject Filter Chips ────────────────────────────────────────
        SubjectFilterChips(
            subjects = uiState.subjects,
            selected = uiState.selectedSubject,
            onSelect = onSubjectFilter
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Live-listener error banner (top, persistent) ────────────────
        // Lives above the list so the user sees it immediately on a stale-
        // data condition (network drop, permission denied, missing index).
        // Previously the banner was below the LazyColumn — a fillMaxSize
        // list pushed it off-screen.
        // Only shown when there IS data to caveat as stale. When the list is
        // empty the content area below shows a full error state + Retry
        // instead — otherwise the banner stacked on top of the celebratory
        // "Nothing here" empty state, reading like success-with-a-warning.
        uiState.errorMessage?.takeIf { uiState.allHomework.isNotEmpty() }?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.errorBg)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.hw_stale_fmt, error),
                    color = c.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Content ─────────────────────────────────────────────────────
        // PullToRefreshBox gets weight(1f) so the gesture surface fills
        // exactly the remaining column height (without it the inner
        // fillMaxSize Box wants the full column height and the pull-down
        // indicator can land off-screen).
        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onPullRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
                }
            } else if (uiState.errorMessage != null && uiState.allHomework.isEmpty()) {
                // Listener errored AND we have nothing to show — present a
                // dedicated error state WITH a Retry action instead of the
                // celebratory "Nothing here" empty state (which read like a
                // success). Retry re-runs the live listener via pull-refresh.
                ErrorHomeworkState(
                    message = uiState.errorMessage,
                    onRetry = onPullRefresh
                )
            } else {
                com.schoolsync.parent.ui.common.SwipeablePagerTabs(
                    tabs = homeworkTabKeys,
                    selectedTab = uiState.selectedTab,
                    onTabChange = onTabChange,
                    modifier = Modifier.fillMaxSize()
                ) { tabKey ->
                    // Each page filters allHomework locally. remember()
                    // caches per (data, tab, subject) so the pager's
                    // adjacent-page preload doesn't recompute.
                    val pageItems = remember(uiState.allHomework, tabKey, uiState.selectedSubject) {
                        HomeworkViewModel.filterForTab(
                            uiState.allHomework,
                            tabKey,
                            uiState.selectedSubject
                        )
                    }
                    if (pageItems.isEmpty()) {
                        EmptyHomeworkState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(
                                items = pageItems,
                                key = { _, it -> it.hwId.ifBlank { it.homeworkId } }
                            ) { index, item ->
                                Box(modifier = Modifier.staggerIn(index)) {
                                    HomeworkCard(item = item, onClick = { onHomeworkClick(item) })
                                }
                            }
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                        }
                    }
                }
            }
        }

    }
}

// ── Status Tab Chips (horizontal scrollable pills) ──────────────────────────

/**
 * Label is a @StringRes id, not a String.
 *
 * This is a top-level `val`, evaluated once at class-init outside any
 * composition, so it cannot call stringResource() — and a String captured
 * there would freeze whichever language the process started in and survive
 * recreate(). Resolved at the render site instead.
 */
private data class TabDef(val key: String, @StringRes val labelRes: Int)

private val tabs = listOf(
    TabDef("all", R.string.common_all),
    TabDef("pending", R.string.filter_pending),
    // "Incomplete" = teacher bounced the work back for a redo. It's actionable
    // (the child must resubmit), so it sits right after Pending rather than
    // being buried in "All".
    TabDef("incomplete", R.string.hw_tab_incomplete),
    TabDef("submitted", R.string.hw_tab_submitted),
    TabDef("graded", R.string.hw_tab_graded)
)

// Tab keys handed to the SwipeablePagerTabs pager — derived from `tabs`
// so the chip row and pager can never drift out of sync.
private val homeworkTabKeys: List<String> = tabs.map { it.key }

@Composable
private fun StatusTabChips(
    selectedTab: String,
    allHomework: List<Homework>,
    onTabChange: (String) -> Unit
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val count = when (tab.key) {
                "all" -> allHomework.size
                "pending" -> allHomework.count { it.studentStatus.lowercase().trim() == "pending" }
                "incomplete" -> allHomework.count { it.studentStatus.lowercase().trim() == "incomplete" }
                "submitted" -> allHomework.count { it.studentStatus.lowercase().trim() == "submitted" }
                "graded" -> allHomework.count { val s = it.studentStatus.lowercase().trim(); s == "reviewed" || s == "complete" }
                else -> 0
            }
            val isActive = selectedTab == tab.key
            // Hoisted: Modifier.semantics {} is not a composable scope.
            val tabCd = pluralStringResource(R.plurals.hw_tab_items, count, stringResource(tab.labelRes), count)

            Row(
                modifier = Modifier
                    // FIX 6 a11y: 48dp min touch target for the tab chip (visual
                    // pill size is unchanged).
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isActive) {
                            Modifier.background(c.accent)
                        } else {
                            Modifier
                                .background(Color.Transparent)
                                .border(1.dp, c.glassBorder, RoundedCornerShape(50))
                        }
                    )
                    .clickable { onTabChange(tab.key) }
                    // a11y: announce as a selectable tab with its count so the
                    // monospace badge isn't read as a bare number out of context.
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        selected = isActive
                        contentDescription = tabCd
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) Color.White else c.textSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isActive) Color.White.copy(alpha = 0.25f)
                            else c.glass
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActive) Color.White else c.textSecondary
                    )
                }
            }
        }
    }
}

// ── Subject Filter Chips ────────────────────────────────────────────────────

@Composable
private fun SubjectFilterChips(
    subjects: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val c = LocalAppColors.current
    // Defensive normalisation \u2014 collapse common spelling variants from
    // legacy free-text data so we don't render "Mathemactics" + "Mathematics"
    // as separate chips. The data cleanup script handles the docs themselves;
    // this is the second line of defence in case any legacy or in-flight
    // homework still carries a typo.
    val canonicalSubjects = subjects
        .asSequence()
        .map { canonicalSubjectName(it) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toList()

    // Hide the entire subject row if there's only one subject in the list \u2014
    // an "All / Mathematics" choice is meaningless when Mathematics is the
    // only option, and the empty-looking "All" duplicates the status row's
    // All chip above. The row reappears automatically as soon as a second
    // subject shows up.
    if (canonicalSubjects.size <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        SubjectChip(
            emoji = "\uD83D\uDCDA",
            label = stringResource(R.string.common_all),
            isSelected = selected == null,
            color = c.accent,
            onClick = { onSelect(null) }
        )
        canonicalSubjects.forEach { subject ->
            val info = getSubjectInfo(subject)
            val color = resolveSubjectColor(info.colorKey, c)
            SubjectChip(
                emoji = info.emoji,
                label = subject,
                isSelected = selected.equals(subject, ignoreCase = true),
                color = color,
                onClick = { onSelect(subject) }
            )
        }
    }
}

/**
 * Collapses common subject-name typos and aliases to a canonical form so
 * the parent app's subject filter doesn't show duplicates from legacy
 * free-text homework. Authoritative data is fixed by the admin/teacher
 * dropdown + one-shot cleanup scripts; this function is the read-side
 * safety net.
 */
private fun canonicalSubjectName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return when (trimmed.lowercase()) {
        "maths", "math", "mathmatics", "mathemactics", "mathematicss" -> "Mathematics"
        "sci"                                                          -> "Science"
        else -> trimmed
    }
}

@Composable
private fun SubjectChip(
    emoji: String,
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            // FIX 6 a11y: 48dp min touch target for the subject chip (visual
            // pill size is unchanged).
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .then(
                if (isSelected) {
                    Modifier
                        .background(color.copy(alpha = 0.15f))
                        .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
                } else {
                    Modifier.background(c.accentBg)
                }
            )
            .clickable(onClick = onClick)
            // a11y: read "<subject> subject filter" with selected state; the
            // emoji glyph alone carries no text alternative for TalkBack.
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = "$label subject filter"
            }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) color else c.textSecondary
            )
        }
    }
}

// ── Homework Card (unified for all tabs) ────────────────────────────────────

@Composable
private fun HomeworkCard(item: Homework, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val priority = HomeworkViewModel.derivePriority(item)
    val info = getSubjectInfo(item.subject)
    val subjectColor = resolveSubjectColor(info.colorKey, c)
    val priorityColor = when (priority) {
        Priority.HIGH -> c.error
        Priority.MEDIUM -> c.warning
        Priority.LOW -> c.success
    }
    // Flag overdue (due date passed, still unsubmitted) with a red "Overdue"
    // pill instead of the amber "Pending" one. isOverdue() already excludes
    // completed/submitted work, so only genuinely-missed homework is flagged;
    // it stays in the list and the count by design — this only changes how it
    // reads, not whether it shows.
    val statusInfo = if (HomeworkViewModel.isOverdue(item))
        resolveStatusInfo("overdue", c)
    else
        resolveStatusInfo(item.studentStatus, c)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(18.dp))
            .bouncyClickable(onClick = onClick)
    ) {
        // Left priority bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(priorityColor)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Subject emoji icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(subjectColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                // a11y: decorative — the subject name is in the subtitle text.
                Text(
                    text = info.emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = item.title.ifBlank { stringResource(R.string.drawer_homework) },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Teacher + subject subtitle
                Text(
                    text = buildString {
                        if (item.teacherName.isNotBlank()) append(item.teacherName)
                        if (item.teacherName.isNotBlank() && item.subject.isNotBlank()) append(" \u00B7 ")
                        if (item.subject.isNotBlank()) append(item.subject)
                    },
                    fontSize = 11.sp, // FIX 6 a11y: was 10sp
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Meta row: due date pill, status pill, attachments
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Due date pill
                    if (item.dueDate.isNotBlank()) {
                        DotPill(
                            text = HomeworkViewModel.dueDateLabel(LocalContext.current, item),
                            dotColor = priorityColor,
                            bgColor = priorityColor.copy(alpha = 0.15f),
                            textColor = priorityColor
                        )
                    }
                    // Status pill
                    DotPill(
                        text = statusInfo.label,
                        dotColor = statusInfo.color,
                        bgColor = statusInfo.bgColor,
                        textColor = statusInfo.color
                    )
                    // Attachment count
                    val attachCount = item.attachments.size
                    if (attachCount > 0) {
                        Text(
                            text = "\uD83D\uDCCE $attachCount",
                            fontSize = 11.sp, // FIX 6 a11y: was 9sp
                            fontWeight = FontWeight.Bold,
                            color = c.accent,
                            // a11y: paperclip emoji has no text alternative \u2014 give
                            // TalkBack a spoken label instead of the glyph + number.
                            modifier = Modifier.semantics {
                                contentDescription =
                                    "$attachCount attachment${if (attachCount == 1) "" else "s"}"
                            }
                        )
                    }
                }

                // Teacher mark fallback \u2014 surfaces a teacher's evaluation
                // when the student didn't submit work themselves.
                if (item.hasTeacherMark && item.studentStatus == "pending") {
                    Spacer(modifier = Modifier.height(6.dp))
                    // Trim remark before isNotBlank() so a whitespace-only
                    // value doesn't render as "score: 7 \u00B7 " (trailing dot
                    // with invisible body).
                    val trimmedRemark = item.teacherMarkRemark.trim()
                    Text(
                        text = "Evaluated (no submission) \u2014 score: ${item.teacherMarkScore}" +
                               if (trimmedRemark.isNotEmpty()) " \u00B7 $trimmedRemark" else "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.warning
                    )
                }
            }

            // Right chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.common_open),
                tint = c.textTertiary,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

// ── Dot Pill (small dot + label) ────────────────────────────────────────────

@Composable
private fun DotPill(
    text: String,
    dotColor: Color,
    bgColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp, // FIX 6 a11y: was 9sp
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ── Error State (with retry) ────────────────────────────────────────────────

@Composable
private fun ErrorHomeworkState(message: String, onRetry: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "⚠️", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.hw_load_failed),
                style = MaterialTheme.typography.titleMedium,
                color = c.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent)
            ) {
                Text(text = stringResource(R.string.action_retry), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyHomeworkState() {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "\uD83C\uDF89", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.hw_nothing_here),
                style = MaterialTheme.typography.titleMedium,
                color = c.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.hw_none_in_category),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DETAIL PAGE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HomeworkDetailPage(
    homework: Homework,
    onBack: () -> Unit,
    onMarkDone: () -> Unit,
    isMarking: Boolean
) {
    val c = LocalAppColors.current
    val priority = HomeworkViewModel.derivePriority(homework)
    val info = getSubjectInfo(homework.subject)
    val subjectColor = resolveSubjectColor(info.colorKey, c)
    val priorityColor = when (priority) {
        Priority.HIGH -> c.error
        Priority.MEDIUM -> c.warning
        Priority.LOW -> c.success
    }
    val statusInfo = if (HomeworkViewModel.isOverdue(homework))
        resolveStatusInfo("overdue", c)
    else
        resolveStatusInfo(homework.studentStatus, c)
    val isAlreadyDone = HomeworkViewModel.isCompleted(homework)
    // A teacher can evaluate a homework even when the student never submitted
    // (studentStatus stays "pending" but a teacherMark exists). In that case
    // the item is already graded, so the parent must not be able to submit
    // over it — treat it the same as "done" for the submit button.
    val alreadyGraded = homework.hasTeacherMark
    val canSubmit = !isAlreadyDone && !alreadyGraded

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (canSubmit) 80.dp else 0.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glass back button — FIX 6 a11y: 48dp min touch target
                // (visual stays 34dp).
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(c.glass)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(11.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = c.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = homework.subject.ifBlank { stringResource(R.string.drawer_homework) },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = subjectColor
                    )
                    if (homework.teacherName.isNotBlank()) {
                        Text(
                            text = homework.teacherName,
                            fontSize = 11.sp,
                            color = c.textSecondary
                        )
                    }
                }
                // Priority badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(priorityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (priority) {
                            Priority.HIGH -> "Urgent"
                            Priority.MEDIUM -> stringResource(R.string.hw_due_soon)
                            Priority.LOW -> "Relaxed"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = priorityColor
                    )
                }
            }

            // ── Title ───────────────────────────────────────────────────
            Text(
                text = homework.title.ifBlank { stringResource(R.string.drawer_homework) },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Meta Pills Row ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (homework.dueDate.isNotBlank()) {
                    DotPill(
                        text = HomeworkViewModel.dueDateLabel(LocalContext.current, homework),
                        dotColor = priorityColor,
                        bgColor = priorityColor.copy(alpha = 0.15f),
                        textColor = priorityColor
                    )
                }
                DotPill(
                    text = statusInfo.label,
                    dotColor = statusInfo.color,
                    bgColor = statusInfo.bgColor,
                    textColor = statusInfo.color
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Instructions ────────────────────────────────────────────
            if (homework.description.isNotBlank()) {
                SectionLabel(text = stringResource(R.string.section_instructions))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glassCard(14.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = homework.description,
                        fontSize = 13.sp,
                        color = c.textSecondary,
                        lineHeight = (13 * 1.7).sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Attachments ─────────────────────────────────────────────
            //
            // Finding #30 — closed by Step 5 (2026-05-15). The Row below
            // is now clickable; taps go through [AttachmentUrlValidator]
            // which enforces an https-only + firebasestorage.googleapis.com
            // host allowlist before dispatching [Intent.ACTION_VIEW]. See
            // util/AttachmentUrlValidator.kt for the full rejection
            // taxonomy and telemetry. The legacy `homework.attachments:
            // List<String>` field is read here intentionally — Step 2
            // dual-emit guarantees this list always carries the same
            // canonical download URLs as `homework.attachmentObjects`,
            // so backward compatibility is preserved.
            if (homework.attachments.isNotEmpty()) {
                val context = LocalContext.current
                SectionLabel(text = stringResource(R.string.hw_attachments_from_teacher))
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    homework.attachments.forEach { attachment ->
                        // Heuristic: pull the last path segment, strip the
                        // query string, and only trust it if it actually
                        // looks like a filename (has a "."). Otherwise the
                        // path-less URL "https://example.com" would surface
                        // "com" as the attachment label.
                        // FIX 3: derive the filename from the URL's last path
                        // segment when it looks like a real file (has a "."),
                        // else fall back to a generic label. `attachmentName` was
                        // never populated on the live path, so its removal here
                        // changes nothing except dropping the dead reference.
                        val rawTail = attachment.substringAfterLast("/").substringBefore("?")
                        val fileName = when {
                            rawTail.isBlank() -> "Attachment"
                            !rawTail.contains('.') -> "Attachment"
                            else -> rawTail
                        }
                        val isPdf = fileName.endsWith(".pdf", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(12.dp)
                                .clickable {
                                    AttachmentUrlValidator.openAttachmentSafely(
                                        context = context,
                                        rawUrl = attachment,
                                        fileName = fileName
                                    )
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // File icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.error.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPdf)
                                        Icons.Filled.PictureAsPdf else Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = c.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    // FIX 8: the tap dispatches ACTION_VIEW (opens
                                    // the file), it does not download — label it
                                    // accurately.
                                    text = stringResource(R.string.hw_tap_to_open),
                                    fontSize = 11.sp,
                                    color = c.textTertiary
                                )
                            }
                            // Download icon
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = stringResource(R.string.common_download),
                                tint = c.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Submission Status ───────────────────────────────────────
            SectionLabel(text = stringResource(R.string.section_submission))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassCard(14.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status emoji
                val statusEmoji = when (homework.studentStatus.lowercase().trim()) {
                    "incomplete" -> "\u270F\uFE0F"
                    "complete", "done" -> "\uD83D\uDCCB"
                    "submitted" -> "\u2705"
                    "reviewed" -> "\uD83C\uDFC6"
                    else -> "\u23F3"
                }
                // a11y: the emoji is decorative — the adjacent statusInfo.label
                // text already conveys the status to TalkBack, so clear the
                // glyph's own semantics to avoid an unreadable announcement.
                Text(
                    text = statusEmoji,
                    fontSize = 28.sp,
                    modifier = Modifier.clearAndSetSemantics { }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = statusInfo.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusInfo.color
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (homework.studentStatus.lowercase().trim()) {
                            "incomplete" -> stringResource(R.string.hw_state_started)
                            "complete", "done" -> stringResource(R.string.hw_state_ready)
                            "submitted" -> stringResource(R.string.hw_state_submitted)
                            "reviewed" -> stringResource(R.string.hw_state_reviewed)
                            else -> stringResource(R.string.hw_state_not_started)
                        },
                        fontSize = 11.sp,
                        color = c.textSecondary
                    )
                }
            }

            // ── Teacher evaluation without a student submission ─────────
            // When the teacher graded the item but the student never
            // submitted (studentStatus is still "pending"), the "reviewed"
            // block below won't fire. Surface the score/remark here so the
            // detail page matches the list card, and rely on canSubmit to
            // hide the "Mark as done" button.
            if (alreadyGraded && homework.studentStatus.lowercase().trim() != "reviewed") {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glassCard(14.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hw_evaluated_no_submission),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.warning
                    )
                    if (homework.teacherMarkScore >= 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.field_score),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${homework.teacherMarkScore}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.success
                            )
                        }
                    }
                    val remark = homework.teacherMarkRemark.trim()
                    if (remark.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.hw_teacher_remark),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = remark,
                            fontSize = 14.sp,
                            color = c.textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // ── Score & Remark (visible when reviewed) ──────────────────
            if (homework.studentStatus.lowercase().trim() == "reviewed" &&
                (homework.score >= 0 || homework.feedback.isNotBlank())
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glassCard(14.dp)
                        .padding(16.dp)
                ) {
                    if (homework.score >= 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.field_score),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${homework.score}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.success
                            )
                        }
                    }
                    if (homework.feedback.isNotBlank()) {
                        if (homework.score >= 0) Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.hw_teacher_remark),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = homework.feedback,
                            fontSize = 14.sp,
                            color = c.textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tips ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassCard(14.dp)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(text = "\uD83D\uDCA1", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(HomeworkViewModel.subjectTip(homework.subject)),
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Sticky bottom button ────────────────────────────────────────
        if (canSubmit) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(c.bgStart.copy(alpha = 0.95f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onMarkDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accent,
                        contentColor = if (c.isDark) Color(0xFF0E1015) else Color.White
                    ),
                    enabled = !isMarking
                ) {
                    if (isMarking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = if (c.isDark) Color(0xFF0E1015) else Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        // Teacher bounced this back for a redo \u2014 label the action
                        // as a resubmit so the intent is unmistakable.
                        val isRedo = homework.studentStatus.lowercase().trim() == "incomplete"
                        Text(
                            text = if (isRedo) stringResource(R.string.hw_redo_resubmit) else stringResource(R.string.hw_mark_done),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Section Label ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    val c = LocalAppColors.current
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = c.textTertiary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

// ── Status info resolution ──────────────────────────────────────────────────

private data class StatusInfo(
    val label: String,
    val color: Color,
    val bgColor: Color
)

@Composable
private fun resolveStatusInfo(status: String, c: AppColors): StatusInfo = when (status.lowercase().trim()) {
    "pending", "not submitted" -> StatusInfo("Pending", c.warning, c.warningBg)
    "incomplete" -> StatusInfo("Incomplete", c.error, c.errorBg)
    "complete", "done" -> StatusInfo("Complete", c.success, c.successBg)
    "submitted" -> StatusInfo("Submitted", c.purple, c.purpleBg)
    "reviewed" -> StatusInfo("Reviewed", c.success, c.successBg)
    "pending review" -> StatusInfo(stringResource(R.string.hw_under_review), c.accent, c.accent.copy(alpha = 0.12f))
    "overdue" -> StatusInfo("Overdue", c.error, c.errorBg)
    else -> StatusInfo("Pending", c.warning, c.warningBg)
}

// ── Subject color resolution ────────────────────────────────────────────────

@Composable
private fun resolveSubjectColor(colorKey: String, c: AppColors): Color = when (colorKey) {
    "accent" -> c.accent
    "success" -> c.success
    "purple" -> c.purple
    "coral" -> c.coral
    "teal" -> c.teal
    "warning" -> c.warning
    "info" -> c.info
    "error" -> c.error
    else -> c.accent
}

// ═══════════════════════════════════════════════════════════════
// Phase HW: Submit Homework Dialog — lets the student/parent
// write what they did before marking as done.
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SubmitHomeworkDialog(
    homework: com.schoolsync.parent.data.model.Homework,
    isSubmitting: Boolean,
    submitError: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    var text by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            androidx.compose.material3.Text(
                stringResource(R.string.hw_submit),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = c.textPrimary
            )
        },
        text = {
            Column {
                androidx.compose.material3.Text(
                    homework.title,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = c.textPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    "${homework.subject} — Due: ${HomeworkViewModel.dueDateFullLabel(homework)}",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Text(
                    stringResource(R.string.hw_describe_optional),
                    color = c.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { androidx.compose.material3.Text(stringResource(R.string.hw_note_hint)) },
                    maxLines = 5,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.textTertiary.copy(alpha = 0.3f),
                        cursorColor = c.accent,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                )
                if (submitError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.material3.Text(
                        submitError,
                        color = c.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            // Description is optional — "Mark as done" must work even with no
            // text, so the button stays tappable (only disabled while sending).
            val trimmed = text.trim()
            androidx.compose.material3.Button(
                onClick = { onSubmit(trimmed) },
                enabled = !isSubmitting,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = c.accent
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            ) {
                if (isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    androidx.compose.material3.Text(stringResource(R.string.common_submit))
                }
            }
        },
        dismissButton = {
            if (!isSubmitting) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    androidx.compose.material3.Text(stringResource(R.string.common_cancel), color = c.textSecondary)
                }
            }
        },
        containerColor = c.bgEnd,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    )
}
