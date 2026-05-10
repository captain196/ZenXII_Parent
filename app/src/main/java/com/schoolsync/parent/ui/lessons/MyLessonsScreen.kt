package com.schoolsync.parent.ui.lessons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.firestore.LessonPlanDoc
import com.schoolsync.parent.data.model.firestore.SubjectPlanProgressDoc
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Phase 8B parent visibility — lesson plans + per-subject completion.
 *
 * Reads only — admin and teachers are the writers (Phase 6/7).
 * Layout:
 *   • Top bar with back arrow + refresh
 *   • Date scrubber (prev / current / next) — defaults to today
 *   • Subject progress strip (horizontal — % completion bars per subject)
 *   • Today's lesson list (subject · teacher · topic · status pill · notes)
 */
@Composable
fun MyLessonsScreen(
    onBack: () -> Unit,
    viewModel: MyLessonsViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    Column(modifier = Modifier.fillMaxSize().gradientBackground().statusBarsPadding()) {
        // ── Top bar ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("My Child's Lessons", color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                if (state.className.isNotBlank()) {
                    Text("${state.className} · ${state.section}",
                        color = c.textSecondary, fontSize = 12.sp)
                }
            }
            IconButton(onClick = { viewModel.load() }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = c.textSecondary)
            }
        }

        // ── Date scrubber ──
        DateScrubber(
            isoDate = state.date,
            dayLabel = state.dayLabel,
            onPrev = { viewModel.setDate(shiftDate(state.date, -1)) },
            onNext = { viewModel.setDate(shiftDate(state.date, +1)) }
        )

        // ── Subject progress strip ──
        if (state.isLoadingProgress) {
            Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                    color = c.accent)
            }
        } else if (state.progress.isNotEmpty()) {
            SubjectProgressStrip(state.progress)
        }

        // ── Lesson list ──
        when {
            state.isLoadingLessons -> Centered { CircularProgressIndicator(color = c.accent) }
            state.error != null -> Centered {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            state.lessons.isEmpty() -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.EventNote, null, tint = c.textTertiary,
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No lessons recorded for this day yet",
                        color = c.textSecondary, fontSize = 13.sp)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.lessons, key = { it.planId.ifBlank { it.id } }) { lesson ->
                    LessonCard(lesson)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Date scrubber
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun DateScrubber(
    isoDate: String,
    dayLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassCard(14.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, "Previous day", tint = c.textPrimary)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayLabel, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(isoDate, color = c.textTertiary, fontSize = 10.sp)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, "Next day", tint = c.textPrimary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Subject progress strip
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun SubjectProgressStrip(rows: List<SubjectPlanProgressDoc>) {
    val c = LocalAppColors.current
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (r in rows) {
            ProgressChip(
                subject = r.subject,
                completed = r.completedCount.toInt(),
                total = r.totalPlans.toInt(),
                percent = r.percentComplete
            )
        }
    }
}

@Composable
private fun ProgressChip(subject: String, completed: Int, total: Int, percent: Double) {
    val c = LocalAppColors.current
    val pctClamped = percent.toFloat().coerceIn(0f, 100f) / 100f
    val color = when {
        percent >= 75 -> c.success
        percent >= 40 -> c.warning
        else          -> c.coral
    }
    Column(
        modifier = Modifier
            .width(140.dp)
            .glassCard(12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            subject.ifBlank { "—" },
            color = c.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { pctClamped },
            color = color,
            trackColor = c.textTertiary.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
        )
        Spacer(Modifier.height(4.dp))
        Row {
            Text("${"%.1f".format(percent)}%", color = color, fontSize = 11.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("$completed / $total", color = c.textTertiary, fontSize = 10.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  Lesson card
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun LessonCard(lesson: LessonPlanDoc) {
    val c = LocalAppColors.current
    val (statusColor, statusLabel) = statusVisuals(lesson.status, c)
    Column(
        modifier = Modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("P${lesson.periodNumber}", color = c.textTertiary,
                fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            StatusPill(statusLabel, statusColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            lesson.subject.ifBlank { "(no subject)" },
            color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        if (lesson.teacherName.isNotBlank()) {
            Text("by ${lesson.teacherName}", color = c.textSecondary, fontSize = 12.sp)
        }
        if (lesson.topicTitle.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(lesson.topicTitle, color = c.textPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (lesson.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(lesson.notes, color = c.textSecondary, fontSize = 12.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (lesson.status == "rescheduled" && lesson.rescheduledTo.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Rescheduled to ${formatRescheduleTarget(lesson.rescheduledTo)}",
                color = c.warning, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun statusVisuals(status: String, c: com.schoolsync.parent.ui.theme.AppColors): Pair<Color, String> = when (status) {
    "completed"   -> c.success to "Completed"
    "skipped"     -> c.coral   to "Skipped"
    "rescheduled" -> c.warning to "Rescheduled"
    else          -> c.accent  to "Planned"
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

// ─────────────────────────────────────────────────────────────────────
//  Date helpers
// ─────────────────────────────────────────────────────────────────────

private fun shiftDate(iso: String, days: Int): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            time = sdf.parse(iso) ?: Date()
            add(Calendar.DATE, days)
        }
        sdf.format(cal.time)
    } catch (_: Exception) { iso }
}

private fun formatRescheduleTarget(s: String): String {
    val parts = s.split("_P")
    return if (parts.size == 2) "${parts[0]} · Period ${parts[1]}" else s
}
