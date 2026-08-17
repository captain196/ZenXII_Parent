package com.schoolsync.parent.ui.stories

import androidx.compose.ui.res.stringResource
import com.schoolsync.parent.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.schoolsync.parent.data.model.TeacherStoryGroup
import com.schoolsync.parent.ui.theme.LocalAppColors

/**
 * The parent-side story tray.
 *
 * Mirrors the staff app's tray ordering and segmented rings so both apps read
 * as one product — MINUS the authoring half: parents consume stories, they
 * never post, so there is no "Your story" tile and no `+` affordance here.
 *
 * Unseen stories are grouped ahead of seen ones (the VM's sort already does
 * this), which is the same Recent/Viewed split WhatsApp shows — surfaced here
 * as ordering rather than as section headers, because a horizontal tray can't
 * carry headers without becoming two rows.
 */
@Composable
fun StoriesRow(
    storyGroups: List<TeacherStoryGroup>,
    onTeacherClick: (String) -> Unit
) {
    val c = LocalAppColors.current

    if (storyGroups.isEmpty()) return

    val unseenCount = storyGroups.count { it.hasUnviewed }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_stories),
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            // Tells a parent at a glance whether there is anything NEW, which
            // the rings alone only answer after scanning them.
            if (unseenCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.accent)
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$unseenCount new",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            storyGroups.forEach { group ->
                StoryAvatar(
                    group = group,
                    onClick = { onTeacherClick(group.teacherId) }
                )
            }
        }
    }
}

@Composable
private fun StoryAvatar(
    group: TeacherStoryGroup,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current

    // Merge the ring + avatar + name into ONE semantics node so a screen
    // reader announces e.g. "Ms. Rao's story, unseen" and activates on tap,
    // instead of reading the decorative ring/initials separately.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                // Announce HOW MANY are unseen, matching what the segmented
                // ring now shows — "unseen" alone lost that the moment the
                // ring started distinguishing 1-of-3 from 3-of-3.
                val unseen = group.stories.count { !it.isViewed }
                contentDescription = when {
                    unseen == 0 -> "${group.teacherName}'s stories, all seen"
                    unseen == 1 -> "${group.teacherName}'s story, 1 unseen"
                    else -> "${group.teacherName}'s stories, $unseen unseen"
                }
            }
    ) {
        Box(
            modifier = Modifier.size(62.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ring — Phase C: admin posts get a red→gold gradient so
            // school-wide announcements stand out against teacher
            // avatars. Teacher posts use the existing teal gradient.
            val isAdmin = group.authorType == "admin"
            val isHighPri = isAdmin && group.priority == "high"
            val ringBrush = when {
                isHighPri -> Brush.linearGradient(
                    colors = listOf(Color(0xFFE53935), Color(0xFFFFC107))   // bold red→gold for pinned
                )
                isAdmin -> Brush.linearGradient(
                    colors = listOf(Color(0xFFE53935), Color(0xFFFF8F00))   // softer red→amber
                )
                else -> Brush.linearGradient(
                    colors = listOf(c.accent, c.accentSecondary)            // teal teacher gradient
                )
            }
            // WhatsApp-style SEGMENTED ring: one arc per story, each coloured
            // on its OWN seen-state. A single `hasUnviewed` boolean used to
            // collapse a teacher's whole group into one unbroken circle, so
            // three stories looked identical to one and watching two of them
            // changed nothing. Order matches the viewer's playback order
            // (oldest first), so the arc that greys out is the one just seen.
            SegmentedStoryRing(
                segmentSeen = group.stories.map { it.isViewed },
                unseenBrush = ringBrush,
                seenColor = c.glassBorder,
                diameter = 62.dp,
                strokeWidth = if (isHighPri) 3.dp else 2.5.dp
            )

            // The circle previews the STORY MEDIA, not the teacher's photo —
            // the same still a parent is about to watch. One thumbnail even
            // when the teacher posted three; the arcs communicate the count.
            StoryCircleContent(
                name = group.teacherName,
                pic = group.teacherPic,
                mediaUrl = group.previewMediaUrl(),
                accent = c.accent,
                accentSecondary = c.accentSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = group.teacherName.split(" ").firstOrNull() ?: "Teacher",
            style = MaterialTheme.typography.labelSmall,
            color = if (group.hasUnviewed) c.textPrimary else c.textSecondary,
            fontWeight = if (group.hasUnviewed) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The ONE media still that represents a teacher's stories in the tray.
 *
 * Mirrors the staff app's `StoryGroup.previewMediaUrl()`. Picks the story the
 * tap will actually open — first unseen, falling back to the most recent — so
 * the circle previews what you're about to watch. Video stories preview from
 * their POSTER, never the .mp4: Coil's image decoder can't decode video, and
 * VideoFrameDecoder needs a local file so it would pull the whole clip down to
 * fill a 54dp circle.
 */
private fun TeacherStoryGroup.previewMediaUrl(): String {
    // MUST mirror the viewer's opening index (StoryPage): first unseen,
    // otherwise the FIRST story. Falling back to last() instead made the tile
    // preview a different story from the one tapping it opens — once a parent
    // had seen a teacher's whole group, the tile showed the newest story but
    // landed on the oldest.
    val s = stories.firstOrNull { !it.isViewed } ?: stories.firstOrNull() ?: return ""
    return if (s.type.equals("video", ignoreCase = true)) s.thumbnailUrl else s.mediaUrl
}

/**
 * Circle content: story media still → teacher's photo → initials. Every step
 * can fail (missing poster, dead URL, no avatar), so all three are wired
 * rather than assumed.
 */
@Composable
private fun StoryCircleContent(
    name: String,
    pic: String,
    mediaUrl: String,
    accent: Color,
    accentSecondary: Color
) {
    var mediaFailed by remember(mediaUrl) { mutableStateOf(false) }
    var picFailed by remember(pic) { mutableStateOf(false) }

    when {
        mediaUrl.isNotBlank() && !mediaFailed -> AsyncImage(
            model = mediaUrl,
            // Decorative — the merged parent node carries the spoken description.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { mediaFailed = true },
            modifier = Modifier.size(54.dp).clip(CircleShape)
        )
        pic.isNotBlank() && !picFailed -> AsyncImage(
            model = pic,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { picFailed = true },
            modifier = Modifier.size(54.dp).clip(CircleShape)
        )
        else -> {
            val initials = name.split(" ").take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("").ifBlank { "T" }
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent, accentSecondary))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
