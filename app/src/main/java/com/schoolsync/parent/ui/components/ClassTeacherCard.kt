package com.schoolsync.parent.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard

/**
 * Class-teacher contact strip — minimalist glass card that matches the rest
 * of the Profile surface. Avatar + role label + name + class/subjects, with
 * a subtle Call button and a filled Message button. All colors are pulled
 * from [LocalAppColors] so the card adapts to light/dark themes.
 */
@Composable
fun ClassTeacherCard(
    entry: MyTeachersFirestoreRepository.TeacherEntry,
    subjects: List<String>,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val staff = entry.staff
    val assignment = entry.assignment

    // Display name prefers the live staff doc; assignment.teacherName is
    // a snapshot fallback for the rare case the staff lookup failed.
    val displayName = staff?.name?.takeIf { it.isNotBlank() }
        ?: assignment.teacherName.ifBlank { "Class Teacher" }
    val photo = staff?.profilePic.orEmpty()
    val phoneRaw = staff?.phone.orEmpty().trim()

    // Two-letter initials (first + last word) — e.g. "Vipul Tiwari" → "VT".
    val initials = run {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    val classSection = listOf(assignment.className, assignment.section)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val subjectsLine = subjects.joinToString(" • ")
    val subtitle = listOfNotNull(
        classSection.takeIf { it.isNotBlank() },
        subjectsLine.takeIf { it.isNotBlank() }
    ).joinToString("  ·  ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — rounded-square with the theme accent gradient.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(c.accent, c.accentSecondary))),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.isNotBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Text(
                    text = initials,
                    color = c.pillText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CLASS TEACHER",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = c.accent,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        if (phoneRaw.isNotBlank()) {
            ClassTeacherIconAction(
                icon = Icons.Filled.Call,
                filled = false,
                label = "Call",
                onClick = {
                    val isActive = staff?.status?.equals("Active", ignoreCase = true) == true
                    if (!isActive) {
                        Toast.makeText(context, "Class teacher is not currently active.", Toast.LENGTH_SHORT).show()
                        return@ClassTeacherIconAction
                    }
                    try {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneRaw))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open dialer.", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
        }
        ClassTeacherIconAction(
            icon = Icons.Filled.Chat,
            filled = true,
            label = "Message",
            onClick = {
                val isActive = staff?.status?.equals("Active", ignoreCase = true) == true
                if (isActive) {
                    onMessage()
                } else {
                    Toast.makeText(context, "Class teacher is not currently active.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

/**
 * Compact circular icon button. [filled] renders the accent-filled primary
 * action; otherwise a subtle accent-tinted secondary action. Theme-driven.
 */
@Composable
private fun ClassTeacherIconAction(
    icon: ImageVector,
    filled: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (filled) c.accent else c.accentBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (filled) c.pillText else c.accent,
            modifier = Modifier.size(16.dp),
        )
    }
}
