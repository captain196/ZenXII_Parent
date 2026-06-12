package com.schoolsync.parent.ui.exam

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.ui.theme.*

@Composable
fun ExamScheduleScreen(
    onBack: () -> Unit,
    viewModel: ExamScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                    tint = TextPrimary
                )
            }
            Column {
                Text(
                    text = "Exam Schedule",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.className.isNotBlank()) {
                    Text(
                        text = "${uiState.className} · ${uiState.section}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Exam selector
        if (uiState.examIds.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(12.dp)
                        .clickable { viewModel.toggleExamSelector() }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.examNames.getOrNull(uiState.selectedExamIndex)
                            ?: "Select Exam",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Select Exam",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = uiState.examSelectorExpanded,
                    onDismissRequest = { viewModel.dismissExamSelector() },
                    modifier = Modifier.background(SurfaceElevated)
                ) {
                    uiState.examIds.forEachIndexed { index, _ ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = uiState.examNames.getOrNull(index)
                                        ?: uiState.examIds[index],
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            onClick = { viewModel.selectExam(index) }
                        )
                    }
                }
            }
        }

        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() }
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Teal, modifier = Modifier.size(40.dp))
                }
            } else if (uiState.examIds.isEmpty() || uiState.entries.isEmpty()) {
                EmptyScheduleState(
                    message = when {
                        uiState.examIds.isEmpty() -> "No exams have been scheduled yet."
                        else -> "No datesheet published for this exam yet."
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Datesheet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    itemsIndexed(uiState.entries) { _, entry ->
                        ExamScheduleCard(entry = entry)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExamScheduleCard(entry: ExamScheduleEntry) {
    val d = parseExamDate(entry.date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date chip — day over month
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Teal.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = d.day,
                style = MaterialTheme.typography.titleLarge,
                color = Teal,
                fontWeight = FontWeight.Bold
            )
            if (d.mon.isNotEmpty()) {
                Text(
                    text = d.mon,
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.subjectName.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(3.dp))
            val dateLine = listOfNotNull(
                d.weekday.ifBlank { null },
                d.full.ifBlank { null }
            ).joinToString(" · ")
            if (dateLine.isNotBlank()) {
                IconLabel(Icons.Filled.CalendarMonth, dateLine)
            }
            if (entry.startTime.isNotBlank() || entry.endTime.isNotBlank()) {
                IconLabel(Icons.Filled.Schedule, timeRange(entry.startTime, entry.endTime))
            }
            if (entry.room.isNotBlank()) {
                IconLabel(Icons.Filled.MeetingRoom, "Room ${entry.room}")
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Max marks
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.maxTotal.toInt().toString(),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Max Marks",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun EmptyScheduleState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EventBusy,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class ParsedExamDate(
    val day: String,
    val mon: String,
    val full: String,
    val weekday: String
)

/**
 * Parses a datesheet date. The admin stores "DD-MM-YYYY" (e.g. "12-06-2026");
 * this also tolerates ISO "YYYY-MM-DD" and "/" separators, falling back to the
 * raw string if it can't be understood.
 */
private fun parseExamDate(raw: String): ParsedExamDate {
    val parts = raw.split("-", "/").map { it.trim() }
    if (parts.size == 3) {
        val (dStr, mStr, yStr) = if (parts[0].length == 4) {
            Triple(parts[2], parts[1], parts[0]) // ISO YYYY-MM-DD
        } else {
            Triple(parts[0], parts[1], parts[2]) // DD-MM-YYYY
        }
        val day = dStr.toIntOrNull()
        val mon = mStr.toIntOrNull()
        val year = yStr.toIntOrNull()
        if (day != null && mon in 1..12 && year != null && day in 1..31) {
            return ParsedExamDate(
                day = day.toString().padStart(2, '0'),
                mon = MONTHS_SHORT[mon!! - 1],
                full = "$day ${MONTHS_TITLE[mon - 1]} $year",
                weekday = weekdayName(year, mon, day)
            )
        }
    }
    return ParsedExamDate(day = raw.takeIf { it.isNotBlank() } ?: "—", mon = "", full = raw, weekday = "")
}

private val MONTHS_SHORT = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
)
private val MONTHS_TITLE = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** Zeller's congruence (Gregorian) — no java.time dependency, safe on every API level. */
private fun weekdayName(y: Int, m: Int, d: Int): String {
    var mm = m
    var yy = y
    if (mm < 3) { mm += 12; yy -= 1 }
    val k = yy % 100
    val j = yy / 100
    val h = (d + (13 * (mm + 1)) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
    // h: 0=Sat, 1=Sun, 2=Mon, … 6=Fri
    return listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri").getOrElse(h) { "" }
}

private fun timeRange(start: String, end: String): String {
    val s = normalizeTime(start)
    val e = normalizeTime(end)
    return when {
        s.isNotBlank() && e.isNotBlank() -> "$s – $e"
        s.isNotBlank() -> s
        else -> e
    }
}

/** "09:00AM" → "09:00 AM" for readability. */
private fun normalizeTime(t: String): String =
    t.trim().replace(Regex("(?i)([0-9])\\s*(am|pm)$")) { mr ->
        mr.groupValues[1] + " " + mr.groupValues[2].uppercase()
    }
