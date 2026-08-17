package com.schoolsync.parent.ui.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.ui.components.bouncyClickable
import com.schoolsync.parent.ui.components.staggerIn
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.Motion
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

/**
 * Push deep-links carry the RAW notice id ("NOT0001") while a loaded notice's id
 * is the full Firestore doc key ("{schoolId}_NOT0001"). Match tolerantly so the
 * tapped notice can be located to expand + scroll to it.
 */
private fun matchesDeepLinkNoticeId(noticeId: String, pushId: String): Boolean =
    noticeId == pushId || noticeId.endsWith("_$pushId") || pushId.endsWith("_$noticeId")

@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    viewModel: NoticesViewModel = hiltViewModel(),
    deepLinkNoticeId: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-expand AND scroll to a notice arriving from a tapped push. Keyed on
    // the list too: the tapped notice may not have streamed in yet, so retry on
    // each emission and only consume the deep link once located + scrolled to.
    androidx.compose.runtime.LaunchedEffect(deepLinkNoticeId, uiState.notices) {
        val id = deepLinkNoticeId
        if (id.isNullOrBlank()) return@LaunchedEffect
        val idx = uiState.notices.indexOfFirst { matchesDeepLinkNoticeId(it.noticeId, id) }
        if (idx >= 0) {
            viewModel.expandNotice(uiState.notices[idx].noticeId)
            listState.animateScrollToItem(idx)
            onDeepLinkConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // ── Top bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = c.textPrimary
                )
            }
            Text(
                text = stringResource(R.string.notices_title),
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_retry),
                    tint = c.textPrimary
                )
            }
        }

        // ── Body ────────────────────────────────────────────────────
        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() }
        ) {
            Crossfade(
                targetState = uiState.isLoading && uiState.notices.isEmpty(),
                animationSpec = tween(220),
                label = "notices-loading"
            ) { loading ->
            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
                    }
                }
                // A load FAILURE must not masquerade as an empty inbox — for a
                // comms channel that false "all caught up" is dangerous. Show a
                // distinct, retryable error state instead.
                uiState.errorMessage != null && uiState.notices.isEmpty() -> {
                    ErrorNoticesState(onRetry = { viewModel.refresh() })
                }
                uiState.notices.isEmpty() -> {
                    EmptyNoticesState(
                        onRefresh = { viewModel.refresh() }
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = uiState.notices,
                            key = { _, n -> n.noticeId }
                        ) { index, notice ->
                            NoticeCard(
                                notice = notice,
                                isExpanded = uiState.expandedNoticeId == notice.noticeId,
                                onClick = { viewModel.toggleExpanded(notice.noticeId) },
                                modifier = Modifier.staggerIn(index)
                            )
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
            }
        }

        // ── Inline error banner — only when content is already showing (a
        // transient refresh error). The full-screen ErrorNoticesState covers
        // the "failed with nothing to show" case above.
        uiState.errorMessage?.takeIf { uiState.notices.isNotEmpty() }?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.errorBg)
                    .border(1.dp, c.error.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = error,
                    color = c.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: Notice,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val catColor = getCategoryColor(notice.category.ifBlank { "General" }, c)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        // Left color strip — ERP-style category/priority accent
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(catColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Category badge
                if (notice.category.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(catColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = localizedCategoryLabel(notice.category),
                            style = MaterialTheme.typography.labelSmall,
                            color = catColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!notice.isRead) {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = stringResource(R.string.notices_unread),
                    tint = c.accent,
                    modifier = Modifier
                        .padding(top = 4.dp, end = 6.dp)
                        .size(8.dp)
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (isExpanded) R.string.cd_collapse else R.string.cd_expand
                ),
                tint = c.textTertiary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Preview text (always show when collapsed)
        if (!isExpanded && notice.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = notice.body,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Bottom meta row: author (role) · date
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (notice.author.isNotBlank()) {
                Text(
                    text = notice.author,
                    color = c.textTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (notice.authorRole.isNotBlank()) {
                    Text(
                        text = " · ${notice.authorRole}",
                        color = c.textTertiary,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(" · ", color = c.textTertiary, fontSize = 11.sp)
            }
            Text(
                text = notice.date,
                color = c.textTertiary,
                fontSize = 11.sp
            )
        }

        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = Motion.emphasized()),
            exit = shrinkVertically(animationSpec = Motion.emphasized())
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = c.glassBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Attachment chip — opens in browser
                if (notice.attachmentUrl.isNotBlank()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val openAttachmentLabel = stringResource(R.string.notices_attachment_open)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(catColor.copy(alpha = 0.12f))
                            .semantics {
                                role = Role.Button
                                contentDescription = openAttachmentLabel
                            }
                            .clickable {
                                // Only hand http(s) URLs to ACTION_VIEW — never
                                // intent:/file:/custom schemes from server content.
                                val url = notice.attachmentUrl.trim()
                                if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                        )
                                    }.onFailure {
                                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.notices_attachment_no_app), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(ctx, ctx.getString(R.string.notices_attachment_invalid), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            openAttachmentLabel,
                            color = catColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (notice.bodyHtml.isNotBlank()) {
                    // Rich HTML render via WebView (HR-styled posters).
                    // Theme-aware text color so the body is readable in dark
                    // mode (was hardcoded dark slate → invisible on dark bg).
                    val htmlTextColor = String.format(
                        "#%06X", 0xFFFFFF and c.textPrimary.toArgb()
                    )
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            android.webkit.WebView(context).apply {
                                // WRAP_CONTENT so the WebView measures to its content
                                // height instead of collapsing to 0 inside the parent
                                // (the LazyColumn item passes an unbounded height).
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = false
                                // The card/list owns scrolling — disable the WebView's
                                // own so long notices don't double-scroll.
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                                settings.setSupportZoom(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { webView ->
                            val html = """
                                <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                                <style>
                                  body{font-family:system-ui,-apple-system,sans-serif;margin:0;padding:0;
                                       font-size:14px;line-height:1.5;color:$htmlTextColor;}
                                  img{max-width:100%;height:auto;}
                                </style></head>
                                <body>${notice.bodyHtml}</body></html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        },
                        // Floor prevents a zero-height collapse before content measures;
                        // generous ceiling guards a pathologically tall render.
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 4000.dp)
                    )
                } else if (notice.body.isNotBlank()) {
                    Text(
                        text = notice.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        lineHeight = 22.sp
                    )
                }
                if (notice.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.notices_author_format, notice.author),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        }  // close inner Column (card content)
    }      // close outer Row (card + color strip)
}

@Composable
private fun getCategoryColor(category: String, c: AppColors): Color = when (category.lowercase()) {
    "urgent", "important" -> c.error
    "exam", "academic" -> c.info
    "event", "holiday" -> c.success
    "fee", "payment" -> c.warning
    "recruitment" -> c.accent
    "policy" -> c.info
    else -> c.accent
}

@Composable
private fun localizedCategoryLabel(category: String): String = when (category.lowercase()) {
    "urgent" -> stringResource(R.string.notices_category_urgent)
    "important" -> stringResource(R.string.notices_category_important)
    "exam" -> stringResource(R.string.notices_category_exam)
    "academic" -> stringResource(R.string.notices_category_academic)
    "event" -> stringResource(R.string.notices_category_event)
    "holiday" -> stringResource(R.string.notices_category_holiday)
    "fee" -> stringResource(R.string.notices_category_fee)
    "payment" -> stringResource(R.string.notices_category_payment)
    "recruitment" -> stringResource(R.string.notices_category_recruitment)
    "policy" -> stringResource(R.string.notices_category_policy)
    else -> stringResource(R.string.notices_category_general)
}

@Composable
private fun ErrorNoticesState(
    onRetry: () -> Unit
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(c.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    tint = c.error,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.notice_load_failed),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notice_not_confirmation),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            val retryLabel = stringResource(R.string.action_retry)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.accent)
                    .semantics {
                        role = Role.Button
                        contentDescription = retryLabel
                    }
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = c.pillText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.pillText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNoticesState(
    onRefresh: () -> Unit
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(c.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.notices_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.notices_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            val refreshLabel = stringResource(R.string.action_retry)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.accent)
                    .semantics {
                        role = Role.Button
                        contentDescription = refreshLabel
                    }
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = c.pillText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_retry),
                        style = MaterialTheme.typography.labelLarge,
                        color = c.pillText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
