package com.schoolsync.parent.ui.stories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.schoolsync.parent.data.model.TeacherStoryGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val STORY_DURATION_MS = 5000L

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
                onNextTeacher = {
                    // Will be handled by pager swipe
                },
                onPrevTeacher = {
                    // Will be handled by pager swipe
                }
            )
        }
    }
}

@Composable
private fun TeacherStoryPage(
    group: TeacherStoryGroup,
    isCurrentPage: Boolean,
    onClose: () -> Unit,
    onStoryViewed: (String) -> Unit,
    onNextTeacher: () -> Unit,
    onPrevTeacher: () -> Unit
) {
    val stories = group.stories
    if (stories.isEmpty()) return

    var currentStoryIndex by remember(group.teacherId) { mutableIntStateOf(0) }
    val currentStory = stories.getOrNull(currentStoryIndex) ?: return
    val scope = rememberCoroutineScope()

    // Mark story as viewed
    LaunchedEffect(currentStory.storyId, isCurrentPage) {
        if (isCurrentPage) {
            onStoryViewed(currentStory.storyId)
        }
    }

    // Auto-advance timer
    val progress by animateFloatAsState(
        targetValue = if (isCurrentPage) 1f else 0f,
        animationSpec = tween(durationMillis = STORY_DURATION_MS.toInt()),
        label = "storyProgress"
    )

    LaunchedEffect(currentStoryIndex, isCurrentPage) {
        if (isCurrentPage) {
            delay(STORY_DURATION_MS)
            if (currentStoryIndex < stories.size - 1) {
                currentStoryIndex++
            } else {
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(currentStoryIndex, stories.size) {
                detectTapGestures { offset ->
                    val screenWidth = size.width
                    if (offset.x < screenWidth / 3f) {
                        // Tap left: previous story
                        if (currentStoryIndex > 0) {
                            currentStoryIndex--
                        }
                    } else {
                        // Tap right: next story
                        if (currentStoryIndex < stories.size - 1) {
                            currentStoryIndex++
                        } else {
                            onClose()
                        }
                    }
                }
            }
    ) {
        // Story media
        AsyncImage(
            model = currentStory.mediaUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom gradient overlay for caption
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // Top content: progress bars + teacher info + close
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
        ) {
            // Progress bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                stories.forEachIndexed { index, _ ->
                    val barProgress = when {
                        index < currentStoryIndex -> 1f
                        index == currentStoryIndex && isCurrentPage -> progress
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
                    // Teacher avatar
                    if (group.teacherPic.isNotBlank()) {
                        AsyncImage(
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
                            text = formatStoryTime(currentStory.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Close button
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

        // Bottom: caption
        if (currentStory.caption.isNotBlank()) {
            Text(
                text = currentStory.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
        else -> {
            try {
                SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
            } catch (_: Exception) {
                ""
            }
        }
    }
}
