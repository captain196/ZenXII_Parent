package com.schoolsync.parent.ui.ptm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.schoolsync.parent.data.model.firestore.PtmEventDoc
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Permanent entry into the parent's PTM history. Reachable from the
 * Academics hub. Shows two sections — Upcoming and Past — each row
 * tappable into the existing [PtmDetailScreen] (via the same `ptm/{id}`
 * route used by the dashboard pulse tile).
 */
@Composable
fun PtmListScreen(
    onBack: () -> Unit,
    onOpenPtm: (String) -> Unit,
    viewModel: PtmListViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = c.textPrimary
                )
            }
            Text(
                "Parent-Teacher Meetings",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.accent)
            }
            state.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    state.errorMessage ?: "Failed to load.",
                    color = c.error,
                    modifier = Modifier.padding(24.dp)
                )
            }
            state.upcoming.isEmpty() && state.past.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.EventAvailable,
                        contentDescription = null,
                        tint = c.textTertiary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No Parent-Teacher Meetings yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "When your school schedules a PTM, it will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                if (state.upcoming.isNotEmpty()) {
                    item { SectionHeader("Upcoming", state.upcoming.size) }
                    items(state.upcoming, key = { it.ptm.id.ifBlank { it.ptm.ptmEventId } }) { row ->
                        PtmRow(row, onOpenPtm)
                    }
                }
                if (state.past.isNotEmpty()) {
                    item { Spacer(Modifier.height(12.dp)) }
                    item { SectionHeader("Past", state.past.size) }
                    items(state.past, key = { "past-" + (it.ptm.id.ifBlank { it.ptm.ptmEventId }) }) { row ->
                        PtmRow(row, onOpenPtm)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = c.textTertiary
        )
    }
}

@Composable
private fun PtmRow(row: PtmListRow, onOpenPtm: (String) -> Unit) {
    val c = LocalAppColors.current
    val ptm = row.ptm
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable { onOpenPtm(ptm.ptmEventId.ifBlank { ptm.id }) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: stylised calendar tile — month banner on top, day number
        // below. The whole tile reads as a mini calendar icon.
        val (day, month) = formatDateChip(ptm.date)
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (row.isUpcoming) c.accent.copy(alpha = 0.10f)
                    else c.glass.copy(alpha = 0.5f)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top "binding" strip — gives the tile its calendar look.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(if (row.isUpcoming) c.accent else c.textTertiary.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        month.uppercase(Locale.getDefault()).ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (row.isUpcoming) {
                            if (c.isDark) c.bgStart else androidx.compose.ui.graphics.Color.White
                        } else c.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(2.dp))
                // Calendar icon + day number — icon ghosts behind the
                // big number for visual texture without competing with it.
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = (if (row.isUpcoming) c.accent else c.textTertiary).copy(alpha = 0.10f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        day,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (row.isUpcoming) c.accent else c.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        // Middle: title + meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ptm.title.ifBlank { "Parent-Teacher Meeting" },
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            // Time window — "10:00 – 11:30" (first slot's start to last slot's end).
            val timeLine = slotTimeWindow(ptm)
            if (timeLine.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = c.textTertiary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        timeLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
            if (ptm.location.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = c.textTertiary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        ptm.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // Right: status badge
        StatusBadge(rsvpStatus = row.rsvpStatus, ptmStatus = ptm.status, isUpcoming = row.isUpcoming)
    }
}

@Composable
private fun StatusBadge(rsvpStatus: String, ptmStatus: String, isUpcoming: Boolean) {
    val c = LocalAppColors.current
    // Phase-A vocabulary in `rsvpStatus`: applied / delivered / no-show /
    // declined / none. Cancellation at the PTM level overrides any RSVP
    // status; "delivered" / "no-show" are teacher-finalised states that
    // should still show even on a completed PTM, so they're checked
    // before the ptm.status==completed fallthrough.
    val (label, bg, fg) = when {
        ptmStatus.equals("cancelled", ignoreCase = true) ->
            Triple("CANCELLED", c.errorBg, c.error)
        rsvpStatus == "delivered" ->
            Triple("DELIVERED", Color(0x3322C55E), Color(0xFF22C55E))
        rsvpStatus == "no-show" ->
            Triple("NO SHOW", c.errorBg, c.error)
        ptmStatus.equals("completed", ignoreCase = true) && rsvpStatus == "none" ->
            Triple("MISSED", c.errorBg, c.error)
        rsvpStatus == "applied" ->
            Triple("APPLIED", Color(0x333B82F6), Color(0xFF3B82F6))
        rsvpStatus == "declined" ->
            Triple("DECLINED", c.errorBg, c.error)
        rsvpStatus == "none" && isUpcoming ->
            Triple("RSVP NOW", Color(0x33D4AF37), c.accent)
        else ->
            Triple("PENDING", Color(0x33A0A0A0), c.textTertiary)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = fg
        )
    }
}

private fun slotTimeWindow(ptm: PtmEventDoc): String {
    val slots = ptm.slots
    if (slots.isEmpty()) return ""
    val first = slots.first().startTime
    val last  = slots.last().endTime
    if (first.isBlank() || last.isBlank()) return ""
    return "${to12h(first)} – ${to12h(last)}"
}

/**
 * "HH:MM" (24h, what's stored) → "h:mm AM/PM" for display in IST. Pure
 * formatting — values are wall-clock strings so no timezone conversion.
 */
private fun to12h(hm: String): String {
    val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(hm.trim()) ?: return hm
    val h24 = m.groupValues[1].toIntOrNull() ?: return hm
    val ap  = if (h24 >= 12) "PM" else "AM"
    val h12 = (h24 % 12).let { if (it == 0) 12 else it }
    return "$h12:${m.groupValues[2]} $ap"
}

private fun formatDateChip(iso: String): Pair<String, String> {
    if (iso.isBlank()) return "—" to ""
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso) ?: return iso to ""
        val day = SimpleDateFormat("d", Locale.getDefault()).format(d)
        val mon = SimpleDateFormat("MMM", Locale.getDefault()).format(d)
        day to mon
    } catch (_: Exception) {
        iso to ""
    }
}

