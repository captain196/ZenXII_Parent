@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.parent.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.schoolsync.parent.util.AttachmentUrlValidator

/**
 * One unified fullscreen media item, normalized from either an event's
 * [com.schoolsync.parent.data.model.EventMedia] (type inferred from URL
 * extension) or a gallery [com.schoolsync.parent.data.model.GalleryMedia]
 * (explicit `type`). The viewer only ever reads these fields.
 *
 * [posterUrl] is a directly-showable image (photo url, or a video's poster
 * frame) and is `null` for a poster-less video — the viewer NEVER feeds a raw
 * video URL to Coil (video-decode guard); poster-less videos rely on the
 * inline ExoPlayer instead.
 */
data class FullscreenMediaItem(
    val url: String,
    val isVideo: Boolean,
    val posterUrl: String?,
    val caption: String = ""
)

/**
 * The single, shared fullscreen media viewer used by both EventDetailScreen and
 * GalleryDetailScreen (previously two divergent copies).
 *
 * Behaviour (from the more robust GalleryDetail implementation):
 *   • single-finger horizontal swipe pages; 2-finger pinch zooms (or 1-finger
 *     pan once zoomed) so swipe-to-next keeps working
 *   • pan is clamped to the scaled image bounds
 *   • zoom resets on page change; `userScrollEnabled = !isZoomed` locks paging
 *     while zoomed
 *   • double-tap toggles 1x / 2.5x zoom
 *   • dot indicators for 2–20 items, caption overlay, "n of N" index counter
 *
 * Video pages play INLINE with a media3 ExoPlayer (default controls). The URL
 * is re-validated with [AttachmentUrlValidator]; an untrusted host shows a
 * placeholder instead of a player. The player auto-plays only while its page
 * is the active one, and is released on dispose.
 */
@Composable
fun FullscreenMediaViewer(
    items: List<FullscreenMediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIndexChange: (Int) -> Unit = {}
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    var showControls by remember { mutableStateOf(true) }

    // Zoom/pan for the CURRENT page only; reset on page change so each item
    // opens un-zoomed. Hoisted so the pager can lock scrolling while zoomed.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val isZoomed = scale > 1.02f

    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls && !isZoomed) 1f else 0f,
        animationSpec = tween(200), label = "controls"
    )

    LaunchedEffect(pagerState.currentPage) {
        onIndexChange(pagerState.currentPage)
        scale = 1f; offsetX = 0f; offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            // Lock paging while zoomed so a pan can't accidentally flip pages.
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            val isActive = page == pagerState.currentPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // Zoom/pan only for image pages; a video page hosts its
                        // own player (with its own touch handling), so we must
                        // not intercept its gestures.
                        if (isActive && !item.isVideo) Modifier
                            // Consumes ONLY 2-finger gestures (or 1-finger while
                            // already zoomed) so a normal single-finger swipe
                            // still reaches the pager.
                            .pointerInput(page) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.count { it.pressed }
                                        val pan = event.calculatePan()
                                        if (pressed >= 2) {
                                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                            scale = newScale
                                            val maxX = (size.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                            val maxY = (size.height * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                            event.changes.forEach { it.consume() }
                                        } else if (pressed == 1 && scale > 1.02f) {
                                            val maxX = (size.width * (scale - 1f) / 2f).coerceAtLeast(0f)
                                            val maxY = (size.height * (scale - 1f) / 2f).coerceAtLeast(0f)
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(page) {
                                detectTapGestures(
                                    onTap = { showControls = !showControls },
                                    onDoubleTap = {
                                        if (scale > 1.5f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                                        else scale = 2.5f
                                    }
                                )
                            }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    // Inline playback. Re-validate the URL (https + trusted
                    // Storage host); an untrusted host shows a placeholder, not
                    // a player. Only the active page's player auto-plays.
                    InlineVideoPage(url = item.url, isActive = isActive)
                } else if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.caption.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isActive) Modifier.graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ) else Modifier
                            )
                    )
                }
            }
        }

        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer(alpha = controlsAlpha)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} of ${items.size}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        // Caption overlay at bottom
        val currentMedia = items.getOrNull(pagerState.currentPage)
        if (currentMedia?.caption?.isNotBlank() == true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .graphicsLayer(alpha = controlsAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = currentMedia.caption,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Dot indicators
        if (items.size > 1 && items.size <= 20) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer(alpha = controlsAlpha)
                    .padding(bottom = if (currentMedia?.caption?.isNotBlank() == true) 60.dp else 40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Inline video page. Builds a media3 [ExoPlayer] against the VALIDATED https
 * Storage URL only; an untrusted host renders a placeholder instead. Auto-plays
 * only while [isActive] (paused when swiped away) and releases on dispose.
 */
@Composable
private fun InlineVideoPage(url: String, isActive: Boolean) {
    val context = LocalContext.current
    val isValid = remember(url) {
        AttachmentUrlValidator.validate(url) is AttachmentUrlValidator.Result.Valid
    }

    if (!isValid) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.VideocamOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "This video can't be played.",
                    style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    // Auto-play only the active page; pause the rest (adjacent pages may stay
    // composed by the pager).
    LaunchedEffect(isActive) {
        player.playWhenReady = isActive
        if (!isActive) player.pause()
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
