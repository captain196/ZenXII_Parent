@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.parent.ui.events

import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.parent.data.model.EventMedia
import com.schoolsync.parent.ui.common.FullscreenMediaItem
import com.schoolsync.parent.ui.common.FullscreenMediaViewer
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    onViewPhotos: (String) -> Unit = {},
    viewModel: EventsViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    // rememberSaveable so the open viewer + which page survives config change
    // / process death. -1 = closed.
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(eventId) {
        viewModel.loadEventDetail(eventId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    contentDescription = stringResource(R.string.cd_back),
                    tint = c.textPrimary
                )
            }
            Text(
                text = detailState.event?.title ?: stringResource(R.string.events_details),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            detailState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = c.accent)
                }
            }

            // A real load failure (network / permission / missing index) is
            // distinct from a genuinely deleted event — surface it with a Retry
            // instead of the identical-looking "not found" state that hides the
            // problem and offers no recovery. Mirrors the Events list screen.
            detailState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.events_one_load_failed),
                            style = MaterialTheme.typography.titleLarge,
                            color = c.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.gal_check_connection),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textTertiary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(c.accent.copy(alpha = 0.15f))
                                .clickable { viewModel.loadEventDetail(eventId) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_retry),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.accent
                                )
                            )
                        }
                    }
                }
            }

            detailState.event == null -> {
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
                            text = stringResource(R.string.events_not_found),
                            style = MaterialTheme.typography.titleLarge,
                            color = c.textSecondary
                        )
                    }
                }
            }

            else -> {
                val event = detailState.event!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Cover hero — the event's first photo (or a video poster),
                    // falling back to the linked album's cover. Kept separate from
                    // mediaUrls so video-only events keep their videos.
                    val cover = event.mediaUrls.firstOrNull { it.type == "image" }?.url
                        ?: event.mediaUrls.firstOrNull { !it.thumbnail.isNullOrBlank() }?.thumbnail
                        ?: event.borrowedCoverUrl.takeIf { it.isNotBlank() }
                    if (cover != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(18.dp))
                        ) {
                            AsyncImage(
                                model = cover,
                                contentDescription = event.title.ifBlank { stringResource(R.string.events_photo) },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                        )
                                    )
                            )
                        }
                    } else {
                        // No photo — polished category-colored hero + glyph so the
                        // detail screen reads as intentional rather than empty.
                        val cc = getCategoryColors(event.category, c)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(cc.gradStart, cc.gradEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = eventCategoryIcon(event.category),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    // Category + Status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categoryColor = getCategoryColor(event.category, c)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(categoryColor.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = event.category.replaceFirstChar { it.uppercase() },
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = categoryColor
                                )
                            )
                        }

                        if (event.status.isNotBlank()) {
                            val statusColor = getStatusColor(event.status, c)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(statusColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = event.status.replaceFirstChar { it.uppercase() },
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = statusColor
                                    )
                                )
                            }
                        }
                    }

                    // Event info card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(16.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Date range
                        if (event.startDate.isNotBlank()) {
                            DetailInfoRow(
                                icon = Icons.Filled.CalendarMonth,
                                label = stringResource(R.string.field_date),
                                value = buildString {
                                    append(event.startDate)
                                    if (event.endDate.isNotBlank() && event.endDate != event.startDate) {
                                        append("  to  ")
                                        append(event.endDate)
                                    }
                                },
                                iconColor = c.accent
                            )
                        }

                        // Location
                        if (event.location.isNotBlank()) {
                            DetailInfoRow(
                                icon = Icons.Filled.LocationOn,
                                label = stringResource(R.string.field_location),
                                value = event.location,
                                iconColor = c.coral
                            )
                        }

                        // Organizer
                        if (event.organizer.isNotBlank()) {
                            DetailInfoRow(
                                icon = Icons.Filled.Groups,
                                label = stringResource(R.string.field_organizer),
                                value = event.organizer,
                                iconColor = c.info
                            )
                        }
                    }

                    // View Photos — jump to the full gallery album generated
                    // for this event (admin publishes one galleryAlbums doc per
                    // event that has photos). Hidden entirely when no album.
                    detailState.eventAlbum?.let { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.accent.copy(alpha = 0.15f))
                                .clickable { onViewPhotos(album.albumId) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (album.mediaCount > 0)
                                    "View Photos (${album.mediaCount})" else stringResource(R.string.events_view_photos),
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.accent
                                )
                            )
                        }
                    }

                    // Description
                    if (event.description.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(16.dp)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.field_description),
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.textPrimary,
                                    letterSpacing = 0.3.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = event.description,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = c.textSecondary,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }

                    // Media Gallery
                    if (event.mediaUrls.isNotEmpty()) {
                        Column {
                            Text(
                                text = stringResource(R.string.field_media),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                event.mediaUrls.forEachIndexed { index, media ->
                                    MediaItem(
                                        media = media,
                                        // Both images and videos open in the shared
                                        // fullscreen viewer — videos now play inline
                                        // (no external launch).
                                        onClick = { viewerIndex = index }
                                    )
                                }
                            }
                        }
                    }

                    // Bottom spacer
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Fullscreen media viewer (shared with the Gallery screen). Videos play
    // inline; images support pinch-zoom / swipe paging.
    val event = detailState.event
    if (viewerIndex >= 0 && event != null && event.mediaUrls.isNotEmpty()) {
        val viewerItems = remember(event.mediaUrls) {
            event.mediaUrls.map { m ->
                FullscreenMediaItem(
                    url = m.url,
                    isVideo = m.type == "video",
                    // Video-decode guard: never feed a raw video URL as a poster.
                    posterUrl = when {
                        m.type != "video" -> m.url
                        !m.thumbnail.isNullOrBlank() -> m.thumbnail
                        else -> null
                    }
                )
            }
        }
        FullscreenMediaViewer(
            items = viewerItems,
            initialIndex = viewerIndex,
            onDismiss = { viewerIndex = -1 }
        )
    }
    } // close outer Box
}

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconColor: Color
) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textTertiary,
                    letterSpacing = 0.5.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textPrimary
                )
            )
        }
    }
}

@Composable
private fun MediaItem(media: EventMedia, onClick: () -> Unit = {}) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .width(200.dp)
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(14.dp))
            .background(c.glass)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (media.url.isNotBlank()) {
            // Poster only (photo, or a video's poster frame). Don't feed a raw
            // .mp4 to Coil — a poster-less video shows the play overlay below.
            val posterUrl = when {
                media.type != "video" -> media.url
                !media.thumbnail.isNullOrBlank() -> media.thumbnail
                else -> null
            }

            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = stringResource(R.string.events_media),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Video play overlay
            if (media.type == "video") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = stringResource(R.string.gal_play_video),
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Duration badge
                if (!media.duration.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = media.duration,
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        } else {
            // Placeholder
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}


// Category / status color helpers live in EventColors.kt (shared with
// EventsScreen).
