@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.parent.ui.gallery

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.schoolsync.parent.data.model.GalleryMedia
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

@Composable
fun GalleryDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    var viewerIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(albumId) {
        viewModel.loadAlbumDetail(albumId)
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
                    text = detailState.album?.title ?: "Album",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

                detailState.album == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Collections,
                                contentDescription = null,
                                tint = c.textTertiary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Album not found",
                                style = MaterialTheme.typography.titleLarge,
                                color = c.textSecondary
                            )
                        }
                    }
                }

                else -> {
                    val album = detailState.album!!
                    val media = album.media

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Album info header
                        item(span = { GridItemSpan(3) }) {
                            AlbumHeader(album = album)
                        }

                        // Media count
                        item(span = { GridItemSpan(3) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    tint = c.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${media.size} item${if (media.size != 1) "s" else ""}",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = c.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // Media grid
                        itemsIndexed(
                            items = media,
                            key = { _, m -> m.mediaId }
                        ) { index, mediaItem ->
                            MediaThumbnail(
                                media = mediaItem,
                                onClick = {
                                    if (mediaItem.type == "video") {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(mediaItem.url), "video/*")
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        viewerIndex = index
                                    }
                                }
                            )
                        }

                        item(span = { GridItemSpan(3) }) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        // Fullscreen image viewer
        val album = detailState.album
        val allMedia = album?.media ?: emptyList()
        val imageMedia = allMedia.filter { it.type == "image" }

        AnimatedVisibility(
            visible = viewerIndex >= 0 && viewerIndex < allMedia.size,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (viewerIndex >= 0 && allMedia.isNotEmpty() && viewerIndex < allMedia.size) {
                FullscreenGalleryViewer(
                    media = allMedia,
                    initialIndex = viewerIndex,
                    onDismiss = { viewerIndex = -1 },
                    onIndexChange = { viewerIndex = it }
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: com.schoolsync.parent.data.model.GalleryAlbum) {
    val c = LocalAppColors.current

    if (album.description.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .glassCard(14.dp)
                .padding(14.dp)
        ) {
            Text(
                text = album.description,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = c.textSecondary,
                    lineHeight = 20.sp
                )
            )
            if (album.createdAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = album.createdAt,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = c.textTertiary
                    )
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    media: GalleryMedia,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(c.glass)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val imageUrl = if (media.type == "video" && !media.thumbnail.isNullOrBlank()) {
            media.thumbnail
        } else {
            media.url
        }

        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = media.caption.ifBlank { "Gallery photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Video overlay
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
                    modifier = Modifier.size(32.dp)
                )
            }

            // Duration
            if (!media.duration.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = media.duration,
                        style = TextStyle(fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        // Caption indicator
        if (media.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    text = media.caption,
                    style = TextStyle(fontSize = 9.sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FullscreenGalleryViewer(
    media: List<GalleryMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIndexChange: (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { media.size })
    var showControls by remember { mutableStateOf(true) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = tween(200), label = "controls"
    )

    LaunchedEffect(pagerState.currentPage) {
        onIndexChange(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = media[page]
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = {
                                scale = if (scale > 1.5f) 1f else 2.5f
                                offsetX = 0f
                                offsetY = 0f
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = if (item.type == "video" && !item.thumbnail.isNullOrBlank()) {
                    item.thumbnail
                } else {
                    item.url
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.caption.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
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
                    text = "${pagerState.currentPage + 1} of ${media.size}",
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
        val currentMedia = media.getOrNull(pagerState.currentPage)
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
        if (media.size > 1 && media.size <= 20) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer(alpha = controlsAlpha)
                    .padding(bottom = if (currentMedia?.caption?.isNotBlank() == true) 60.dp else 40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(media.size) { i ->
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
