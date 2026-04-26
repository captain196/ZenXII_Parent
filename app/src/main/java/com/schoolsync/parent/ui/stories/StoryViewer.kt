package com.schoolsync.parent.ui.stories

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import com.schoolsync.parent.data.model.TeacherStoryGroup
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val STORY_DURATION_MS = 5000L
private const val LONG_PRESS_TIMEOUT_MS = 180L
private const val DISMISS_THRESHOLD_PX = 250f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryViewer(
    storyGroups: List<TeacherStoryGroup>,
    initialTeacherId: String,
    onClose: () -> Unit,
    onStoryViewed: (String) -> Unit
) {
    if (storyGroups.isEmpty()) {
        onClose()
        return
    }

    val initialPage = storyGroups.indexOfFirst { it.teacherId == initialTeacherId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { storyGroups.size }
    val scope = rememberCoroutineScope()

    // Hardware back button → close viewer (Round 1d).
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val group = storyGroups[pageIndex]
            TeacherStoryPage(
                group = group,
                isCurrentPage = pagerState.currentPage == pageIndex,
                onClose = onClose,
                onStoryViewed = onStoryViewed,
                onAllStoriesInGroupFinished = {
                    // Round 1d — skip to next teacher instead of
                    // closing. If there's no next teacher, close.
                    scope.launch {
                        val next = pageIndex + 1
                        if (next < storyGroups.size) {
                            pagerState.animateScrollToPage(next)
                        } else {
                            onClose()
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeacherStoryPage(
    group: TeacherStoryGroup,
    isCurrentPage: Boolean,
    onClose: () -> Unit,
    onStoryViewed: (String) -> Unit,
    onAllStoriesInGroupFinished: () -> Unit
) {
    val stories = group.stories
    if (stories.isEmpty()) return

    // Round 2a — open on first unseen story (WhatsApp/Instagram behavior).
    // If all are seen, start from 0.
    var currentStoryIndex by remember(group.teacherId) {
        val firstUnseen = stories.indexOfFirst { !it.isViewed }
        mutableIntStateOf(if (firstUnseen >= 0) firstUnseen else 0)
    }
    val currentStory = stories.getOrNull(currentStoryIndex) ?: return

    // Round 2b — prefetch next story's image via Coil so tap-next is instant.
    val prefetchContext = LocalContext.current
    LaunchedEffect(currentStoryIndex, group.teacherId) {
        val next = stories.getOrNull(currentStoryIndex + 1) ?: return@LaunchedEffect
        if (next.mediaUrl.isBlank()) return@LaunchedEffect
        if (next.type.equals("video", ignoreCase = true)) return@LaunchedEffect
        val req = ImageRequest.Builder(prefetchContext)
            .data(next.mediaUrl)
            .build()
        prefetchContext.imageLoader.enqueue(req)
    }

    // Round 2c — video mute toggle. Default muted (Stories start silent
    // on IG/WhatsApp). Only shown on video stories.
    var isMuted by remember { mutableStateOf(true) }

    // ── Gestures state ────────────────────────────────────────────
    // Paused while the user is long-pressing the screen (Round 1b).
    // Chrome fades out in this state for a cleaner view.
    var isPaused by remember { mutableStateOf(false) }
    // Vertical drag distance — negative means pulled down (Round 1c).
    // Box.offset follows this 1:1 while dragging; animates back to 0
    // on release if below threshold, else calls onClose.
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // ── Media load state (Round 1d — spinner + error overlays) ───
    var mediaLoadState by remember(currentStory.storyId) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    // Mark story as viewed as soon as it becomes current.
    LaunchedEffect(currentStory.storyId, isCurrentPage) {
        if (isCurrentPage) onStoryViewed(currentStory.storyId)
    }

    // ── Pausable progress driver (Round 1b) ───────────────────────
    // Count elapsed ms by accumulating frame deltas. Stops ticking
    // whenever isPaused or !isCurrentPage. When it reaches the cap,
    // advances to the next story (or finishes the group).
    var elapsedMs by remember(currentStoryIndex, group.teacherId) { mutableLongStateOf(0L) }
    // Per-page frame-delta tracker (Round 2d — previously file-level,
    // which leaked across viewer instances and pager pages).
    var lastFrameNanos by remember(currentStoryIndex, group.teacherId) { mutableLongStateOf(0L) }
    LaunchedEffect(currentStoryIndex, isCurrentPage, isPaused) {
        if (!isCurrentPage || isPaused) return@LaunchedEffect
        // Reset the frame reference on (re)start so the first tick
        // doesn't add a stale delta from a previous run.
        lastFrameNanos = 0L
        // Don't restart from 0 if we're resuming after a pause.
        while (elapsedMs < STORY_DURATION_MS) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val delta = if (lastFrameNanos == 0L) 0L else (now - lastFrameNanos) / 1_000_000L
            lastFrameNanos = now
            elapsedMs += delta.coerceIn(0L, 64L)  // cap a stutter
        }
        // Story finished — advance or skip-to-next-teacher.
        if (currentStoryIndex < stories.size - 1) {
            currentStoryIndex++
        } else {
            onAllStoriesInGroupFinished()
        }
    }

    // Visual progress = elapsed / total (clamped).
    val progress = (elapsedMs.toFloat() / STORY_DURATION_MS).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Round 1c — swipe-down-to-dismiss drives translationY.
            // Only pulling downward counts; an upward pull is ignored
            // so the user can't drag the viewer off the top.
            .graphicsLayer {
                translationY = dragOffset.value.coerceAtLeast(0f)
            }
            // Outer gesture layer: vertical drag for dismiss.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        scope.launch {
                            dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                        }
                    },
                    onDragEnd = {
                        if (dragOffset.value > DISMISS_THRESHOLD_PX) {
                            onClose()
                        } else {
                            scope.launch { dragOffset.animateTo(0f, tween(180)) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffset.animateTo(0f, tween(180)) }
                    }
                )
            }
            // Inner gesture layer: tap zones + hold-to-pause.
            // awaitEachGesture lets us cleanly separate short-tap
            // (navigate) from long-press (pause-until-release).
            .pointerInput(currentStoryIndex, stories.size) {
                val longPressMs = LONG_PRESS_TIMEOUT_MS
                val leftZone = size.width / 3f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    // Race: either user lifts within longPressMs (short
                    // tap → navigate) or the timeout fires (long press
                    // → pause until release).
                    val up = withTimeoutOrNull(longPressMs) {
                        waitForUpOrCancellation()
                    }
                    if (up != null) {
                        // Short tap — navigate.
                        if (down.position.x < leftZone) {
                            // Tap left zone: previous story
                            if (currentStoryIndex > 0) {
                                currentStoryIndex--
                                elapsedMs = 0L   // restart progress
                            }
                        } else {
                            // Tap right zone: next story, or finish group
                            if (currentStoryIndex < stories.size - 1) {
                                currentStoryIndex++
                                elapsedMs = 0L
                            } else {
                                onAllStoriesInGroupFinished()
                            }
                        }
                    } else {
                        // Long-press in progress — pause until release.
                        isPaused = true
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            isPaused = false
                        }
                    }
                }
            }
    ) {
        // ── Media (image or video) ────────────────────────────────
        val isVideo = currentStory.type.equals("video", ignoreCase = true)
        if (isVideo) {
            VideoStoryPlayer(
                url = currentStory.mediaUrl,
                isCurrentPage = isCurrentPage,
                isPaused = isPaused,
                isMuted = isMuted
            )
        } else {
            AsyncImage(
                model = currentStory.mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onState = { state -> mediaLoadState = state }
            )
            when (mediaLoadState) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Couldn't load story media.\nTap to close.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> Unit
            }
        }

        // ── Chrome (top + bottom UI) — fades out while paused ───
        AnimatedVisibility(
            visible = !isPaused,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopChrome(
                stories = stories,
                currentStoryIndex = currentStoryIndex,
                currentProgress = progress,
                isCurrentPage = isCurrentPage,
                group = group,
                storyCreatedAt = currentStory.createdAt,
                isVideo = isVideo,
                isMuted = isMuted,
                onToggleMute = { isMuted = !isMuted },
                onClose = onClose
            )
        }

        if (currentStory.caption.isNotBlank()) {
            AnimatedVisibility(
                visible = !isPaused,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box {
                    // Bottom gradient backing
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                    )
                    Text(
                        text = currentStory.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TopChrome(
    stories: List<com.schoolsync.parent.data.model.Story>,
    currentStoryIndex: Int,
    currentProgress: Float,
    isCurrentPage: Boolean,
    group: TeacherStoryGroup,
    storyCreatedAt: Long,
    isVideo: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onClose: () -> Unit
) {
    Box {
        // Top gradient — fades UI over media for readability.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Progress bars — one per story in group, current filling.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                stories.forEachIndexed { index, _ ->
                    val barProgress = when {
                        index < currentStoryIndex -> 1f
                        index == currentStoryIndex && isCurrentPage -> currentProgress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Teacher info + close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (group.teacherPic.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = group.teacherPic,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        val initials = group.teacherName
                            .split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("")
                            .ifBlank { "T" }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = group.teacherName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatStoryTime(storyCreatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isVideo) {
                        IconButton(onClick = onToggleMute) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  VIDEO PLAYBACK (Round 1a)
//  Media3 ExoPlayer wrapped in an AndroidView. Tied to lifecycle:
//  - Builds a new player per-URL
//  - Pauses when isPaused (hold-to-pause) OR when pager scrolls away
//  - Releases on dispose (critical — ExoPlayer leaks background threads
//    if released too late)
// ═════════════════════════════════════════════════════════════════
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoStoryPlayer(
    url: String,
    isCurrentPage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    // Pause on off-screen or user-hold; resume otherwise.
    LaunchedEffect(isCurrentPage, isPaused, player) {
        player.playWhenReady = isCurrentPage && !isPaused
    }
    // Mute toggle (Round 2c).
    LaunchedEffect(isMuted, player) {
        player.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false     // no controls; taps drive the gesture layer
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ═════════════════════════════════════════════════════════════════
//  Helpers
// ═════════════════════════════════════════════════════════════════

private fun formatStoryTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val hours = diff / (1000 * 60 * 60)
    val minutes = diff / (1000 * 60)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> try {
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
        } catch (_: Exception) { "" }
    }
}
