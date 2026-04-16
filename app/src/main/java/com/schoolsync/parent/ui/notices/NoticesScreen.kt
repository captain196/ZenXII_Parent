package com.schoolsync.parent.ui.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.R
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.Motion
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    viewModel: NoticesViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        when {
            uiState.isLoading && uiState.notices.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
                }
            }
            uiState.notices.isEmpty() -> {
                EmptyNoticesState(
                    onRefresh = { viewModel.refresh() }
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.notices,
                        key = { it.noticeId }
                    ) { notice ->
                        NoticeCard(
                            notice = notice,
                            isExpanded = uiState.expandedNoticeId == notice.noticeId,
                            onClick = { viewModel.toggleExpanded(notice.noticeId) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }

        // ── Error toast ─────────────────────────────────────────────
        uiState.errorMessage?.let { error ->
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
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val catColor = getCategoryColor(notice.category.ifBlank { "General" }, c)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable(onClick = onClick)
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
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(catColor.copy(alpha = 0.12f))
                            .clickable {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(notice.attachmentUrl)
                                        )
                                    )
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📎 Open attachment",
                            color = catColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (notice.bodyHtml.isNotBlank()) {
                    // Rich HTML render via WebView (HR-styled posters)
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            android.webkit.WebView(context).apply {
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = false
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { webView ->
                            val html = """
                                <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                                <style>
                                  body{font-family:system-ui,-apple-system,sans-serif;margin:0;padding:0;
                                       font-size:14px;line-height:1.5;color:#1e293b;}
                                  img{max-width:100%;height:auto;}
                                </style></head>
                                <body>${notice.bodyHtml}</body></html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxWidth()
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.accent)
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
