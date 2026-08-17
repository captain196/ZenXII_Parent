package com.schoolsync.parent.ui.stories

import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.schoolsync.parent.data.model.Story
import com.schoolsync.parent.data.model.TeacherStoryGroup
import com.schoolsync.parent.data.model.firestore.StorySharedConfig
import com.schoolsync.parent.util.StoryVideoCache
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val IMAGE_DURATION_MS = 5000L
private const val DISMISS_THRESHOLD_PX = 250f
/** Upward swipe distance that opens the WhatsApp-style reaction tray. */
private const val SWIPE_UP_THRESHOLD_PX = 80f
/** Drag distance over which the story fully fades out while swiping down. */
private const val DISMISS_DISTANCE_PX = 620f
/** Pinch-out below this scale (on release) dismisses the story. */
private const val DISMISS_ZOOM_OUT = 0.75f
/** Lower bound the pinch can shrink to (enables the zoom-out-to-dismiss). */
private const val MIN_ZOOM_OUT = 0.5f
/**
 * How long a story video may sit in STATE_BUFFERING before we treat it as
 * failed. ExoPlayer never emits an error for "still buffering", so without a
 * watchdog a stalled video holds the viewer on a black frame indefinitely.
 */
private const val VIDEO_LOAD_TIMEOUT_MS = 12000L

/**
 * Full-screen story viewer for the PARENT app — mirrors the teacher
 * viewer's gesture model exactly, plus v1 emoji reactions + persistent
 * view tracking:
 *   • unified gesture path for image AND video (tap zones, hold-to-pause,
 *     pinch-zoom in to 4× / out to dismiss, swipe-down to dismiss with a
 *     fade that reveals the dashboard behind, horizontal swipe → next
 *     person via the pager)
 *   • video progress tied to actual playback; onPlayerError never leaves
 *     the viewer stuck on a black frame
 *   • the progress/time bar lingers ~1s on hold then fades, and is frozen
 *     + hidden while dismissing
 *
 * Rendered as an OVERLAY above the dashboard (see NavGraph) so the
 * swipe-down / pinch-out fade reveals the page it was opened from.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryViewer(
    storyGroups: List<TeacherStoryGroup>,
    /** True while the backing VM's Firestore listener is still loading.
     *  On a cold open the viewer's own StoryViewModel starts empty and
     *  re-runs its listener, so groups arrive a beat later — we must NOT
     *  treat that transient empty as "nothing to show" and pop back. */
    isLoading: Boolean,
    initialTeacherId: String,
    myReactions: Map<String, String>,
    onClose: () -> Unit,
    onStoryViewed: (String) -> Unit,
    /** Called when a story is watched to the end (not tapped past). */
    onStoryCompleted: (String) -> Unit,
    onReact: (storyId: String, emoji: String) -> Unit
) {
    BackHandler(onBack = onClose)

    if (storyGroups.isEmpty()) {
        // Honor isLoading: while the backing VM's Firestore listener is still
        // loading (including the onStart empty placeholder before the first
        // real snapshot), show a spinner and NEVER auto-close. We only close
        // when NOT loading AND the set is genuinely empty. With the hoisted
        // shared StoryViewModel the overlay opens onto warm data, so this
        // branch is normally never even reached — but honoring isLoading makes
        // it correct regardless. (The old blind `delay(1500); onClose()`,
        // which ignored isLoading, is what flash-closed the viewer on first tap.)
        if (isLoading) {
            val loadingCd = stringResource(R.string.story_loading)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .semantics { contentDescription = loadingCd },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
            }
        } else {
            // Genuinely nothing to show — close once (no artificial delay).
            LaunchedEffect(Unit) { onClose() }
        }
        return
    }

    // ── Freeze the running order for this viewing session ─────────────
    //
    // `storyGroups` is LIVE and sorted with `.thenByDescending { hasUnviewed }`,
    // so the instant a parent dwells 500ms on a teacher, that teacher flips to
    // seen and RE-SORTS below everyone still unseen — while being viewed. With
    // an unkeyed pager that silently swapped the slot's author mid-swipe, which
    // is why tapping one teacher could land on another.
    //
    // WhatsApp and Instagram pin the running order when the viewer opens. We
    // snapshot the ORDER only; content stays live via the re-map below.
    // Freezing waits until the tapped teacher is PRESENT, because the audience
    // entitlement resolves asynchronously and an early snapshot could omit
    // them (-1 → page 0 → the wrong person).
    var frozenOrder by remember(initialTeacherId) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(storyGroups, initialTeacherId) {
        if (frozenOrder == null && storyGroups.any { it.teacherId == initialTeacherId }) {
            frozenOrder = storyGroups.map { it.teacherId }
        }
    }
    // Decisive breadcrumb — see the Teacher app's equivalent. Tells us whether
    // the freeze gate ever opened, which separates "no page rendered" from
    // "page rendered but the dwell never fired".
    LaunchedEffect(storyGroups.size, frozenOrder == null) {
        com.schoolsync.parent.util.debugLog(
            "Story.viewer GATE groups=${storyGroups.size} target=$initialTeacherId " +
            "present=${storyGroups.any { it.teacherId == initialTeacherId }} frozen=${frozenOrder != null}"
        )
    }

    val order = frozenOrder
    if (order == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
        }
        return
    }
    // Frozen ids re-mapped onto live groups; teachers appearing mid-session are
    // appended rather than injected, so nothing shifts under the current page.
    val ordered = remember(storyGroups, order) {
        val byId = storyGroups.associateBy { it.teacherId }
        order.mapNotNull { byId[it] } + storyGroups.filter { it.teacherId !in order }
    }
    if (ordered.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }

    val initialPage = ordered.indexOfFirst { it.teacherId == initialTeacherId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { ordered.size }
    val scope = rememberCoroutineScope()

    // No opaque background here — each page paints its own black backdrop
    // whose alpha fades during a swipe-down / pinch-out dismiss, revealing
    // whatever is behind the viewer (the dashboard it was opened from).
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            // WITHOUT a key, Compose reuses page slots POSITIONALLY, so any
            // list change made slot N render a different teacher.
            key = { index -> ordered[index].teacherId },
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            StoryPage(
                // WhatsApp-style cube: the page turns around the edge you drag
                // toward, so a swipe reads as moving to the next PERSON.
                modifier = Modifier.graphicsLayer {
                    val offset =
                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                    val clamped = offset.coerceIn(-1f, 1f)
                    cameraDistance = 18f * density
                    rotationY = clamped * -60f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        pivotFractionX = if (clamped < 0f) 0f else 1f,
                        pivotFractionY = 0.5f
                    )
                    alpha = 1f - (kotlin.math.abs(clamped) * 0.35f)
                },
                group = ordered[pageIndex],
                isCurrentPage = pagerState.currentPage == pageIndex,
                myReactions = myReactions,
                onClose = onClose,
                onStoryViewed = onStoryViewed,
                onStoryCompleted = onStoryCompleted,
                onReact = onReact,
                onGroupFinished = {
                    scope.launch {
                        val next = pageIndex + 1
                        if (next < ordered.size) pagerState.animateScrollToPage(next) else onClose()
                    }
                }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun StoryPage(
    modifier: Modifier = Modifier,
    group: TeacherStoryGroup,
    isCurrentPage: Boolean,
    myReactions: Map<String, String>,
    onClose: () -> Unit,
    onStoryViewed: (String) -> Unit,
    /** Called when a story is watched to the end (not tapped past). */
    onStoryCompleted: (String) -> Unit,
    onReact: (storyId: String, emoji: String) -> Unit,
    onGroupFinished: () -> Unit
) {
    val stories = group.stories
    if (stories.isEmpty()) return

    var index by remember(group.teacherId) {
        val firstUnseen = stories.indexOfFirst { !it.isViewed }
        mutableIntStateOf(if (firstUnseen >= 0) firstUnseen else 0)
    }
    val story = stories.getOrNull(index) ?: return
    val isVideo = story.type.equals("video", ignoreCase = true)

    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    val dragOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // WhatsApp-style reaction tray: swipe up opens it, which pauses the
    // story; tapping an emoji reacts + plays a floating pop, then closes.
    var trayVisible by remember(story.storyId) { mutableStateOf(false) }
    var reactDrag by remember { mutableFloatStateOf(0f) }   // cumulative vertical drag; negative = up
    var poppedEmoji by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(story.storyId, isCurrentPage) {
        // Require a brief dwell before counting a view, so merely swiping PAST
        // an author's first story (or flicking through the pager) doesn't
        // inflate the view count. If the page changes before the delay elapses
        // the effect is cancelled and no view is recorded.
        com.schoolsync.parent.util.debugLog(
            "Story.viewer PAGE teacher=${group.teacherId} story=${story.storyId} isCurrent=$isCurrentPage"
        )
        if (isCurrentPage) {
            kotlinx.coroutines.delay(500)
            com.schoolsync.parent.util.debugLog("Story.viewer DWELL fired story=${story.storyId}")
            onStoryViewed(story.storyId)
        }
    }

    // ── Progress state ─────────────────────────────────────────────
    var imageDisplayed by remember(index, group.teacherId) { mutableStateOf(false) }
    // A broken/expired mediaUrl used to leave the spinner up forever (the
    // 5s timer only starts after onSuccess), stranding the viewer. Track
    // load failure so we can show an error and auto-advance instead.
    var imageFailed by remember(index, group.teacherId) { mutableStateOf(false) }
    var imageElapsed by remember(index, group.teacherId) { mutableLongStateOf(0L) }
    var videoProgress by remember(index, group.teacherId) { mutableFloatStateOf(0f) }

    // Pinch-to-zoom for ALL media (image AND video). Two-finger pinch
    // scales/pans the media and springs back when the fingers lift.
    val mediaZoom = remember(index, group.teacherId) { Animatable(1f) }
    val mediaOffsetX = remember(index, group.teacherId) { Animatable(0f) }
    val mediaOffsetY = remember(index, group.teacherId) { Animatable(0f) }
    val mediaZoomed = mediaZoom.value > 1.01f

    // Dismiss progress (0 → 1) from a downward swipe OR a pinch-out below
    // 1×. Drives the fade/shrink of the story + backdrop fade, and (via
    // isDismissing) freezes the timer + hides the bar mid-swipe.
    val dragProgress = (dragOffset.value / DISMISS_DISTANCE_PX).coerceIn(0f, 1f)
    val zoomOutProgress = ((1f - mediaZoom.value) / (1f - MIN_ZOOM_OUT)).coerceIn(0f, 1f)
    val dismissProgress = maxOf(dragProgress, zoomOutProgress)
    val isDismissing = dismissProgress > 0.01f
    val cardScale = 1f - dragProgress * 0.25f

    // Chrome visibility. On hold we keep the bar ~1s then fade; zooming or
    // dismissing hides it at once (handled at AnimatedVisibility below).
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isPaused, mediaZoomed) {
        when {
            mediaZoomed -> chromeVisible = false
            !isPaused -> chromeVisible = true
            else -> {
                chromeVisible = true
                kotlinx.coroutines.delay(1000)
                chromeVisible = false
            }
        }
    }

    // Image progress driver — frozen while dismissing so the timer doesn't
    // advance (or appear to restart) during a swipe-down.
    LaunchedEffect(index, isCurrentPage, isPaused, isVideo, mediaZoomed, imageDisplayed, isDismissing, trayVisible) {
        if (isVideo || !isCurrentPage || isPaused || mediaZoomed || isDismissing || trayVisible) return@LaunchedEffect
        if (!imageDisplayed) return@LaunchedEffect   // start-on-load
        var last = 0L
        while (imageElapsed < IMAGE_DURATION_MS) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val delta = if (last == 0L) 0L else (now - last) / 1_000_000L
            last = now
            imageElapsed += delta.coerceIn(0L, 64L)
        }
        // The timer ran out with the image on screen — genuinely watched, not
        // skipped. Recorded BEFORE advancing so the story id is still the one
        // that finished. The image-error auto-advance below deliberately does
        // NOT call this: bailing out of a broken image is not a completion.
        onStoryCompleted(story.storyId)
        if (index < stories.size - 1) index++ else onGroupFinished()
    }

    val currentProgress = if (isVideo) videoProgress
    else (imageElapsed.toFloat() / IMAGE_DURATION_MS).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        // Backdrop — fades out as the story is dismissed, revealing behind.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - dismissProgress))
        )
        // Story card — moves with the finger, shrinks + fades on dismiss.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value.coerceAtLeast(0f)
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = 1f - dismissProgress
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth.toFloat()

                // ── Media with a SHARED pinch-zoom transform ───────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = mediaZoom.value
                            scaleY = mediaZoom.value
                            translationX = mediaOffsetX.value
                            translationY = mediaOffsetY.value
                        }
                ) {
                    if (isVideo) {
                        VideoStoryPlayer(
                            url = story.mediaUrl,
                            posterUrl = story.thumbnailUrl,
                            isCurrentPage = isCurrentPage,
                            isPaused = isPaused || mediaZoomed || isDismissing || trayVisible,
                            isMuted = isMuted,
                            onProgress = { videoProgress = it },
                            onEnded = {
                                // Reached the last frame — a real completion,
                                // unlike a tap-forward. Recorded before the
                                // index moves.
                                onStoryCompleted(story.storyId)
                                if (index < stories.size - 1) index++ else onGroupFinished()
                            }
                        )
                    } else {
                        coil.compose.AsyncImage(
                            model = story.mediaUrl,
                            contentDescription = buildString {
                                append("Story from ${group.teacherName}")
                                if (story.caption.isNotBlank()) append(": ${story.caption}")
                            },
                            contentScale = ContentScale.Fit,
                            onSuccess = { imageDisplayed = true },
                            onError = { imageFailed = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (!isVideo && !imageDisplayed && !imageFailed) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(36.dp))
                    }
                }
                // Broken/expired media — surface an error instead of an eternal
                // spinner, and auto-advance (mirrors the video onEnded fallback).
                if (!isVideo && imageFailed) {
                    val failedCd = stringResource(R.string.story_could_not_load)
                    Box(
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = failedCd },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.story_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
                LaunchedEffect(imageFailed, isCurrentPage) {
                    if (imageFailed && isCurrentPage && !isVideo) {
                        kotlinx.coroutines.delay(2500)
                        if (index < stories.size - 1) index++ else onGroupFinished()
                    }
                }

                // ── SHARED gesture overlay for ALL media ───────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Pinch-zoom FIRST (only acts on 2+ pointers).
                        .pointerInput(index, group.teacherId) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var didZoom = false
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        didZoom = true
                                        val newScale = (mediaZoom.value * event.calculateZoom()).coerceIn(MIN_ZOOM_OUT, 4f)
                                        val pan = event.calculatePan()
                                        val maxX = (widthPx * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                        val maxY = (constraints.maxHeight.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                        scope.launch { mediaZoom.snapTo(newScale) }
                                        scope.launch { mediaOffsetX.snapTo((mediaOffsetX.value + pan.x).coerceIn(-maxX, maxX)) }
                                        scope.launch { mediaOffsetY.snapTo((mediaOffsetY.value + pan.y).coerceIn(-maxY, maxY)) }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (didZoom) {
                                    if (mediaZoom.value < DISMISS_ZOOM_OUT) {
                                        onClose()
                                    } else {
                                        scope.launch { mediaZoom.animateTo(1f, tween(200)) }
                                        scope.launch { mediaOffsetX.animateTo(0f, tween(200)) }
                                        scope.launch { mediaOffsetY.animateTo(0f, tween(200)) }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dy ->
                                    reactDrag += dy   // track up-swipe (negative) for the reaction tray
                                    scope.launch { dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f)) }
                                },
                                onDragEnd = {
                                    if (dragOffset.value > DISMISS_THRESHOLD_PX) onClose()
                                    else {
                                        // A net upward swipe opens the reaction tray (WhatsApp).
                                        if (reactDrag < -SWIPE_UP_THRESHOLD_PX) trayVisible = true
                                        scope.launch { dragOffset.animateTo(0f, tween(180)) }
                                    }
                                    reactDrag = 0f
                                },
                                onDragCancel = {
                                    scope.launch { dragOffset.animateTo(0f, tween(180)) }
                                    reactDrag = 0f
                                }
                            )
                        }
                        // Tap zones + hold-to-pause WITHOUT consuming events, so
                        // a swipe-down that begins during/after a hold still
                        // reaches the drag detector, and horizontal swipes reach
                        // the pager. No navigation on a plain hold-release.
                        .pointerInput(index, stories.size) {
                            val longPressMs = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isPaused = true
                                val up = withTimeoutOrNull(longPressMs) { waitForUpOrCancellation() }
                                if (up != null) {
                                    isPaused = false
                                    if (down.position.x < widthPx / 3f) {
                                        if (index > 0) index--
                                    } else {
                                        if (index < stories.size - 1) index++ else onGroupFinished()
                                    }
                                } else {
                                    waitForUpOrCancellation()
                                    isPaused = false
                                }
                            }
                        }
                )
            }

            // ── Chrome — hidden immediately while zooming/dismissing ───
            AnimatedVisibility(
                visible = chromeVisible && !isDismissing,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopChrome(
                    storyCount = stories.size,
                    currentIndex = index,
                    currentProgress = currentProgress,
                    isCurrentPage = isCurrentPage,
                    group = group,
                    createdAt = story.createdAt,
                    isVideo = isVideo,
                    isMuted = isMuted,
                    onToggleMute = { isMuted = !isMuted },
                    onClose = onClose
                )
            }

            // ── Bottom: centered caption + "swipe up to react" hint ────
            // WhatsApp-style: caption centered just above the bottom edge;
            // a subtle chevron invites the swipe-up reaction tray. Hidden
            // while the tray is open (the tray takes its place).
            AnimatedVisibility(
                visible = chromeVisible && !isDismissing && !trayVisible,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (story.caption.isNotBlank()) {
                            Text(
                                text = story.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp)
                            )
                        }
                        // Reaction affordance — a single upward chevron (tap or
                        // swipe up opens the tray). No label text; once the
                        // parent has reacted it shows their chosen emoji instead.
                        // Sits at the very bottom edge (small bottom padding).
                        val myEmoji = myReactions[story.storyId]
                        // Hoisted: Modifier.semantics {} is not a composable scope.
                        val reactCd = stringResource(R.string.story_react)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { trayVisible = true }
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .padding(8.dp)
                                .semantics {
                                    contentDescription =
                                        if (myEmoji != null) "You reacted $myEmoji. Tap to change your reaction"
                                        else reactCd
                                }
                        ) {
                            if (myEmoji != null) {
                                Text(text = myEmoji, style = MaterialTheme.typography.titleMedium)
                            } else {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Reaction tray (WhatsApp) — tap-scrim + slide-up emoji row ─
            AnimatedVisibility(
                visible = trayVisible,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { trayVisible = false }
                )
            }
            AnimatedVisibility(
                visible = trayVisible,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReactionTray(
                    selected = myReactions[story.storyId],
                    onReact = { emoji ->
                        onReact(story.storyId, emoji)
                        poppedEmoji = emoji
                        trayVisible = false
                    }
                )
            }

            // ── Floating reaction pop (plays after a tap) ──────────────
            poppedEmoji?.let { emoji ->
                FloatingReactionPop(emoji = emoji, onDone = { poppedEmoji = null })
            }
        } // story card
    } // page root
}

/**
 * WhatsApp-style reaction tray — a rounded translucent pill of emojis
 * that slides up from the bottom. Centered via the caller's BottomCenter
 * alignment. The currently-selected emoji is highlighted + scaled up.
 */
@Composable
private fun ReactionTray(selected: String?, onReact: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 64.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(30.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StorySharedConfig.ALLOWED_REACTIONS.forEach { emoji ->
            val isSel = selected == emoji
            val scale by animateFloatAsState(targetValue = if (isSel) 1.3f else 1f, label = "reactScale")
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSel) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onReact(emoji) }
                    .semantics {
                        contentDescription = if (isSel) "Reacted with $emoji" else "React with $emoji"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 26.sp,
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
        }
    }
}

/**
 * The chosen emoji flies up and fades out once, WhatsApp-style, then
 * signals completion so the caller can clear it.
 */
@Composable
private fun FloatingReactionPop(emoji: String, onDone: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    val transY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.7f, tween(600)) }
        launch { transY.animateTo(-180f, tween(750)) }
        alpha.animateTo(0f, tween(750))
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = emoji,
            fontSize = 72.sp,
            modifier = Modifier
                .padding(bottom = 130.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationY = transY.value
                    this.alpha = alpha.value
                }
        )
    }
}

@Composable
private fun TopChrome(
    storyCount: Int,
    currentIndex: Int,
    currentProgress: Float,
    isCurrentPage: Boolean,
    group: TeacherStoryGroup,
    createdAt: Long,
    isVideo: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onClose: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(storyCount) { i ->
                    val barProgress = when {
                        i < currentIndex -> 1f
                        i == currentIndex && isCurrentPage -> currentProgress
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { barProgress },
                        modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (group.teacherPic.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = group.teacherPic,
                            contentDescription = stringResource(R.string.stories_profile_photo_fmt, group.teacherName),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                        )
                    } else {
                        val initials = group.teacherName.split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                            .joinToString("").ifBlank { "T" }
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(group.teacherName, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatStoryTime(LocalContext.current, createdAt), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
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
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoStoryPlayer(
    url: String,
    posterUrl: String,
    isCurrentPage: Boolean,
    isPaused: Boolean,
    isMuted: Boolean,
    onProgress: (Float) -> Unit,
    onEnded: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(url) {
        val source = ProgressiveMediaSource.Factory(StoryVideoCache.dataSourceFactory(context))
            .createMediaSource(MediaItem.fromUri(url))
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 10000, 1000, 1000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaSource(source)
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    // Buffering / failure are surfaced to the user. Previously a video that was
    // slow or broken showed a plain black screen with a 0% progress bar and no
    // timeout — indistinguishable from a hang, and the usual reason a parent
    // reports "the video doesn't play". PlayerView can't help here: media3
    // defaults to SHOW_BUFFERING_NEVER and useController is false.
    var isBuffering by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage, isPaused, player) {
        player.playWhenReady = isCurrentPage && !isPaused
    }
    LaunchedEffect(isMuted, player) {
        player.volume = if (isMuted) 0f else 1f
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) onEnded()
            }
            // Never leave the viewer stuck on a black frame. This used to call
            // onEnded() bare — no log, no message — so a group of broken videos
            // flashed past and closed itself, which reads as "videos don't play".
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e(
                    "StoryViewer",
                    "Video playback failed (code=${error.errorCodeName}) url=$url",
                    error
                )
                failed = true
                isBuffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause when the app leaves the foreground. Without this the story keeps
    // playing (and audibly so) behind the lock screen or another app, and the
    // progress bar drifts out of sync with the real playback position.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Watchdog: a video stuck in STATE_BUFFERING never emits an error, so
    // without this the viewer waits forever on a black frame.
    LaunchedEffect(url, isBuffering, isCurrentPage, isPaused) {
        if (!isBuffering || !isCurrentPage || isPaused) return@LaunchedEffect
        kotlinx.coroutines.delay(VIDEO_LOAD_TIMEOUT_MS)
        if (isBuffering) {
            android.util.Log.w("StoryViewer", "Video load timed out after ${VIDEO_LOAD_TIMEOUT_MS}ms url=$url")
            failed = true
        }
    }

    // Advance past a failed video the way the image path does, after letting the
    // message be read, rather than skipping instantly with no explanation.
    LaunchedEffect(failed) {
        if (failed) {
            kotlinx.coroutines.delay(2500)
            onEnded()
        }
    }
    // Poll playback position ONLY while actually playing (current page and not
    // paused). Gating stops the 50ms loop when the story is paused, off-screen,
    // or the reaction tray/zoom pauses it — no needless wakeups per frame.
    LaunchedEffect(player, isCurrentPage, isPaused) {
        if (isPaused || !isCurrentPage) return@LaunchedEffect
        while (true) {
            val dur = player.duration
            if (dur > 0) onProgress((player.currentPosition.toFloat() / dur).coerceIn(0f, 1f))
            kotlinx.coroutines.delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            // CRITICAL: without this, advancing video -> video plays nothing.
            // `remember(url)` builds a NEW ExoPlayer and DisposableEffect releases
            // the OLD one, but the composition slot is reused so `factory` is not
            // re-invoked — PlayerView would keep pointing at the RELEASED player
            // and the new one would never get a surface. Symptom: black frame,
            // audio only, progress bar still advancing (it polls the new player).
            update = { view ->
                if (view.player !== player) view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        // Poster OVERLAY (drawn after the PlayerView, which is opaque) — shown
        // only until the first frame decodes, so a buffering video presents the
        // real still instead of a black rectangle. The Teacher app has always
        // uploaded this poster; the Parent model simply dropped the field.
        if (posterUrl.isNotBlank() && (isBuffering || failed)) {
            coil.compose.AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (failed) {
            Text(
                text = stringResource(R.string.story_load_failed),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** Top-level helper — no composition here, so the Context is passed in. */
private fun formatStoryTime(ctx: android.content.Context, timestamp: Long): String {
    if (timestamp <= 0) return ""
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    return when {
        minutes < 1 -> ctx.getString(R.string.generic_just_now)
        minutes < 60 -> ctx.getString(R.string.story_mins_ago, minutes.toInt())
        hours < 24 -> ctx.getString(R.string.story_hours_ago, hours.toInt())
        // Display formatter: localized month name, Latin digits pinned.
        else -> try { com.schoolsync.parent.util.DisplayFormat.dayMonth(Date(timestamp)) } catch (_: Exception) { "" }
    }
}
