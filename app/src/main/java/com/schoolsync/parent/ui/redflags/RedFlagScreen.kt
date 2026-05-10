package com.schoolsync.parent.ui.redflags

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.StudentFlag
import com.schoolsync.parent.ui.components.staggerIn
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RedFlagScreen(
    onBack: () -> Unit,
    viewModel: RedFlagViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
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
                    tint = c.textPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Red Flags",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.badgeCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(c.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${uiState.badgeCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (uiState.studentName.isNotBlank()) {
                    Text(
                        text = uiState.studentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("all" to "All", "homework" to "Homework", "behavior" to "Behavior", "performance" to "Performance")
            filters.forEach { (key, label) ->
                FilterChip(
                    selected = uiState.selectedFilter == key,
                    onClick = { viewModel.setFilter(key) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (uiState.selectedFilter == key) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = c.accent.copy(alpha = 0.2f),
                        selectedLabelColor = c.accent,
                        containerColor = c.glass,
                        labelColor = c.textSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = c.glassBorder,
                        selectedBorderColor = c.accent.copy(alpha = 0.5f),
                        enabled = true,
                        selected = uiState.selectedFilter == key
                    )
                )
            }
        }

        // Content
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = c.accent, modifier = Modifier.size(40.dp))
            }
        } else if (uiState.activeFlags.isEmpty() && uiState.resolvedFlags.isEmpty()) {
            EmptyFlagsState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.activeFlags.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Active",
                            count = uiState.activeFlags.size,
                            tint = c.error
                        )
                    }
                    itemsIndexed(uiState.activeFlags) { index, flag ->
                        Box(modifier = Modifier.staggerIn(index)) {
                            FlagCard(flag = flag)
                        }
                    }
                }
                if (uiState.resolvedFlags.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SectionHeader(
                            title = "Resolved",
                            count = uiState.resolvedFlags.size,
                            tint = c.textTertiary
                        )
                    }
                    itemsIndexed(uiState.resolvedFlags) { index, flag ->
                        Box(modifier = Modifier.staggerIn(index)) {
                            FlagCard(flag = flag)
                        }
                    }
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
                    .background(c.errorBg)
                    .padding(12.dp)
            ) {
                Text(text = error, color = c.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FlagCard(flag: StudentFlag) {
    val c = LocalAppColors.current
    val severityColor = when (flag.severity.lowercase()) {
        "high" -> c.error
        "medium" -> c.coral
        "low" -> c.warning
        else -> c.warning
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
    ) {
        // Severity indicator bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(100.dp)
                .background(severityColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            when (flag.type.lowercase()) {
                                "homework" -> c.warningBg
                                "behavior" -> c.errorBg
                                "performance" -> c.infoBg
                                else -> c.accentBg
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = flag.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (flag.type.lowercase()) {
                            "homework" -> c.warning
                            "behavior" -> c.error
                            "performance" -> c.info
                            else -> c.accent
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Status badge
                Text(
                    text = if (flag.status == "active") "Active" else "Resolved",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (flag.status == "active") c.error else c.textTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message
            Text(
                text = flag.message,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subject + teacher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (flag.subject.isNotBlank()) {
                    Text(
                        text = flag.subject,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary
                    )
                }
                if (flag.teacherName.isNotBlank()) {
                    Text(
                        text = flag.teacherName,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary
                    )
                }
            }

            // Timestamp
            if (flag.createdAtMs > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(flag.createdAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun EmptyFlagsState() {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Flag,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Red Flags",
                style = MaterialTheme.typography.titleLarge,
                color = c.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No active red flags raised by teachers for your child.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    // Guard against unparseable / missing dates from legacy schema.
    // 0L = no createdAtMs / timestamp / createdAt was extractable;
    // anything below year 2000 = almost certainly a bad parse, not a
    // legitimate flag from the 1970s.
    if (timestamp <= 946_684_800_000L) return "date unavailable"
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        "date unavailable"
    }
}
