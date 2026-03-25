package com.schoolsync.parent.ui.notices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.ui.theme.*

@Composable
fun NoticesScreen(
    onBack: () -> Unit,
    viewModel: NoticesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Text(
                text = "Notices",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.isLoading && uiState.notices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Teal, modifier = Modifier.size(40.dp))
            }
        } else if (uiState.notices.isEmpty()) {
            EmptyNoticesState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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

        // Error
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ErrorRedBg)
                    .padding(12.dp)
            ) {
                Text(text = error, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable(onClick = onClick)
            .padding(16.dp)
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(getCategoryColor(notice.category).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = notice.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = getCategoryColor(notice.category),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = notice.date,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )

        // Preview text (always show when collapsed)
        if (!isExpanded && notice.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notice.body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                if (notice.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = notice.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
                if (notice.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "- ${notice.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

private fun getCategoryColor(category: String) = when (category.lowercase()) {
    "urgent", "important" -> ErrorRed
    "exam", "academic" -> InfoBlue
    "event", "holiday" -> SuccessGreen
    "fee", "payment" -> WarningAmber
    else -> Teal
}

@Composable
private fun EmptyNoticesState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.NotificationsNone,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Notices",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "There are no notices at the moment.\nPull down to refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}
