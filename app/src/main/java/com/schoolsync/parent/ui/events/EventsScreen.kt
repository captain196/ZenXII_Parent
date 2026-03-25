package com.schoolsync.parent.ui.events

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit = {},
    viewModel: EventsViewModel = hiltViewModel()
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
            Text(
                text = "Events",
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = c.accent)
                }
            }

            uiState.events.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.EventNote,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Upcoming Events",
                            style = MaterialTheme.typography.titleLarge,
                            color = c.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Upcoming events will appear here when published by the school.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    items(uiState.events, key = { it.eventId }) { event ->
                        EventListCard(
                            event = event,
                            onClick = { onEventClick(event.eventId) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        // Error message
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.errorBg)
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
private fun EventListCard(
    event: Event,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val (categoryColor, categoryGradStart, categoryGradEnd) = getCategoryColors(event.category, c)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .clickable(onClick = onClick)
            .animateContentSize()
    ) {
        // Gradient accent bar at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(categoryGradStart, categoryGradEnd)
                    )
                )
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Category pill + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = event.category.replaceFirstChar { it.uppercase() },
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = categoryColor,
                            letterSpacing = 0.3.sp
                        )
                    )
                }

                // Status badge
                if (event.status.isNotBlank()) {
                    val statusColor = getStatusColor(event.status, c)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = event.status.replaceFirstChar { it.uppercase() },
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        )
                    }
                }
            }

            // Title
            Text(
                text = event.title,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Date range
            if (event.startDate.isNotBlank()) {
                InfoRow(
                    icon = Icons.Filled.CalendarMonth,
                    text = buildString {
                        append(event.startDate)
                        if (event.endDate.isNotBlank() && event.endDate != event.startDate) {
                            append("  -  ")
                            append(event.endDate)
                        }
                    },
                    color = c.textSecondary,
                    iconColor = c.accent
                )
            }

            // Location
            if (event.location.isNotBlank()) {
                InfoRow(
                    icon = Icons.Filled.LocationOn,
                    text = event.location,
                    color = c.textSecondary,
                    iconColor = c.coral
                )
            }

            // Organizer
            if (event.organizer.isNotBlank()) {
                InfoRow(
                    icon = Icons.Filled.Person,
                    text = event.organizer,
                    color = c.textTertiary,
                    iconColor = c.info
                )
            }

            // Description preview
            if (event.description.isNotBlank()) {
                Text(
                    text = event.description,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = c.textSecondary,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Media preview row
            if (event.mediaUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    event.mediaUrls.forEach { media ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.glass),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageUrl = if (media.type == "video" && !media.thumbnail.isNullOrBlank()) {
                                media.thumbnail
                            } else {
                                media.url
                            }
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (media.type == "video") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircle,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    text: String,
    color: Color,
    iconColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                color = color
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class CategoryColorSet(
    val main: Color,
    val gradStart: Color,
    val gradEnd: Color
)

@Composable
private fun getCategoryColors(category: String, c: com.schoolsync.parent.ui.theme.AppColors): CategoryColorSet {
    return when (category.lowercase()) {
        "cultural" -> CategoryColorSet(c.purple, c.banner3Start, c.banner3End)
        "sports" -> CategoryColorSet(c.success, c.banner2Start, c.banner2End)
        "academic" -> CategoryColorSet(c.info, c.banner1Start, c.banner1End)
        "exam" -> CategoryColorSet(c.error, c.error.copy(alpha = 0.5f), c.error.copy(alpha = 0.2f))
        "holiday" -> CategoryColorSet(c.warning, c.warning.copy(alpha = 0.5f), c.warning.copy(alpha = 0.2f))
        else -> CategoryColorSet(c.accent, c.banner1Start, c.banner1End)
    }
}

@Composable
private fun getStatusColor(status: String, c: com.schoolsync.parent.ui.theme.AppColors): Color {
    return when (status.lowercase()) {
        "scheduled" -> c.info
        "ongoing", "active" -> c.success
        "completed", "finished" -> c.textTertiary
        "cancelled" -> c.error
        "postponed" -> c.warning
        else -> c.textSecondary
    }
}
