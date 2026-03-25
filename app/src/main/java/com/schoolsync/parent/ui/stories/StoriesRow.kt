package com.schoolsync.parent.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.schoolsync.parent.data.model.TeacherStoryGroup
import com.schoolsync.parent.ui.theme.LocalAppColors

@Composable
fun StoriesRow(
    storyGroups: List<TeacherStoryGroup>,
    onTeacherClick: (String) -> Unit
) {
    val c = LocalAppColors.current

    if (storyGroups.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Stories",
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(62.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ring
            val ringModifier = if (group.hasUnviewed) {
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(c.accent, c.accentSecondary)
                        ),
                        shape = CircleShape
                    )
            } else {
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = c.glassBorder,
                        shape = CircleShape
                    )
            }

            Box(modifier = ringModifier)

            // Avatar
            if (group.teacherPic.isNotBlank()) {
                AsyncImage(
                    model = group.teacherPic,
                    contentDescription = group.teacherName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                )
            } else {
                // Initials fallback
                val initials = group.teacherName
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                    .joinToString("")
                    .ifBlank { "T" }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(c.accent, c.accentSecondary)
                            )
                        ),
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
