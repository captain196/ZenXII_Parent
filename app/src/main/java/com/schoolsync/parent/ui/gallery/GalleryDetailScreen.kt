@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.schoolsync.parent.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.layout.ContentScale
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
import com.schoolsync.parent.ui.common.FullscreenMediaItem
import com.schoolsync.parent.ui.common.FullscreenMediaViewer
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
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    // rememberSaveable so an open fullscreen viewer survives config change /
    // process death.
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }

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

                // Real load failure vs. genuinely-missing album: give the
                // failure a Retry instead of the identical "not found" state.
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
                                text = "Couldn't load this album",
                                style = MaterialTheme.typography.titleLarge,
                                color = c.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Check your connection and try again.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textTertiary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(c.accent.copy(alpha = 0.15f))
                                    .clickable { viewModel.loadAlbumDetail(albumId) }
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
                                    text = "Retry",
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
                                // Images and videos both open the shared fullscreen
                                // viewer — videos now play inline (no external launch).
                                onClick = { viewerIndex = index }
                            )
                        }

                        item(span = { GridItemSpan(3) }) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }

        // Fullscreen viewer (shared with the Events screen). Videos play inline.
        val album = detailState.album
        val allMedia = album?.media ?: emptyList()

        AnimatedVisibility(
            visible = viewerIndex >= 0 && viewerIndex < allMedia.size,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (viewerIndex >= 0 && allMedia.isNotEmpty() && viewerIndex < allMedia.size) {
                val viewerItems = remember(allMedia) {
                    allMedia.map { m ->
                        FullscreenMediaItem(
                            url = m.url,
                            isVideo = m.type == "video",
                            // Video-decode guard: never feed a raw video URL as a poster.
                            posterUrl = when {
                                m.type != "video" -> m.url
                                !m.thumbnail.isNullOrBlank() -> m.thumbnail
                                else -> null
                            },
                            caption = m.caption
                        )
                    }
                }
                FullscreenMediaViewer(
                    items = viewerItems,
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
        // Poster = photo url, or a video's poster frame when it has one. We do
        // NOT feed the raw .mp4 to Coil for a grid tile (decoding a frame off a
        // remote video downloads the whole file — slow + often blank). A
        // poster-less video shows the play overlay below on the glass tile.
        val posterUrl = when {
            media.type != "video" -> media.url
            !media.thumbnail.isNullOrBlank() -> media.thumbnail
            else -> null
        }

        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = media.caption.ifBlank { "Gallery photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (media.type != "video") {
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
