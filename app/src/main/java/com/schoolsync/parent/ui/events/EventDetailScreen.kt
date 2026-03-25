@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.parent.ui.events

import android.content.Intent
import android.net.Uri

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.schoolsync.parent.data.model.EventMedia
import com.schoolsync.parent.ui.theme.AppColors
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    var viewerIndex by remember { mutableIntStateOf(-1) } // -1 = closed

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
                    contentDescription = "Back",
                    tint = c.textPrimary
                )
            }
            Text(
                text = detailState.event?.title ?: "Event Details",
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
                            text = "Event not found",
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
                                label = "Date",
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
                                label = "Location",
                                value = event.location,
                                iconColor = c.coral
                            )
                        }

                        // Organizer
                        if (event.organizer.isNotBlank()) {
                            DetailInfoRow(
                                icon = Icons.Filled.Groups,
                                label = "Organizer",
                                value = event.organizer,
                                iconColor = c.info
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
                                text = "Description",
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
                                text = "Media",
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
                                        onClick = {
                                            if (media.type == "video") {
                                                // Open video externally
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(media.url), "video/*")
                                                }
                                                context.startActivity(intent)
                                            } else {
                                                // Open fullscreen image viewer
                                                viewerIndex = index
                                            }
                                        }
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

    // Fullscreen gallery viewer
    val event = detailState.event
    if (viewerIndex >= 0 && event != null && event.mediaUrls.isNotEmpty()) {
        GalleryViewer(
            mediaList = event.mediaUrls,
            initialIndex = viewerIndex,
            onDismiss = { viewerIndex = -1 },
            onVideoPlay = { url ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                }
                context.startActivity(intent)
            }
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
            // Use thumbnail for videos, url for images
            val imageUrl = if (media.type == "video" && !media.thumbnail.isNullOrBlank()) {
                media.thumbnail
            } else {
                media.url
            }

            AsyncImage(
                model = imageUrl,
                contentDescription = "Event media",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

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
                        contentDescription = "Play video",
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

@Composable
private fun GalleryViewer(
    mediaList: List<EventMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onVideoPlay: (String) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, mediaList.lastIndex),
        pageCount = { mediaList.size }
    )
    var showControls by remember { mutableStateOf(true) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = tween(250), label = "ctrl"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Swipeable pages ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1
        ) { page ->
            val media = mediaList[page]
            val imageUrl = if (media.type == "video" && !media.thumbnail.isNullOrBlank())
                media.thumbnail!! else media.url

            var scale by remember { mutableFloatStateOf(1f) }
            var offX by remember { mutableFloatStateOf(0f) }
            var offY by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1f; offX = 0f; offY = 0f
                                } else {
                                    scale = 3f
                                    offX = (size.width / 2f - tapOffset.x) * 2f
                                    offY = (size.height / 2f - tapOffset.y) * 2f
                                }
                            }
                        )
                    }
                    .pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offX += pan.x * scale
                                offY += pan.y * scale
                            } else {
                                offX = 0f; offY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offX, translationY = offY
                        )
                )

                // Video play button overlay
                if (media.type == "video") {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { onVideoPlay(media.url) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer(alpha = controlsAlpha)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${pagerState.currentPage + 1} / ${mediaList.size}",
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp)) // balance close btn
        }

        // ── Bottom thumbnail strip ──
        val scope = rememberCoroutineScope()
        if (mediaList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer(alpha = controlsAlpha)
                    .padding(bottom = 32.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                mediaList.forEachIndexed { i, media ->
                    val isActive = i == pagerState.currentPage
                    val thumbUrl = if (media.type == "video" && !media.thumbnail.isNullOrBlank())
                        media.thumbnail!! else media.url
                    val thumbAlpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.4f,
                        animationSpec = tween(200), label = "thumb$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 52.dp else 44.dp)
                            .graphicsLayer(alpha = thumbAlpha)
                            .clip(RoundedCornerShape(if (isActive) 10.dp else 8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .then(
                                if (isActive) Modifier.border(
                                    2.dp, Color.White, RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(i)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (media.type == "video") {
                            Icon(
                                Icons.Filled.PlayCircle, null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }
    }
}

@Composable
private fun getCategoryColor(category: String, c: AppColors): Color {
    return when (category.lowercase()) {
        "cultural" -> c.purple
        "sports" -> c.success
        "academic" -> c.info
        "exam" -> c.error
        "holiday" -> c.warning
        else -> c.accent
    }
}

@Composable
private fun getStatusColor(status: String, c: AppColors): Color {
    return when (status.lowercase()) {
        "scheduled" -> c.info
        "ongoing", "active" -> c.success
        "completed", "finished" -> c.textTertiary
        "cancelled" -> c.error
        "postponed" -> c.warning
        else -> c.textSecondary
    }
}
