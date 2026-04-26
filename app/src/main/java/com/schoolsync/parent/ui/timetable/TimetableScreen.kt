package com.schoolsync.parent.ui.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.DayTimetable
import com.schoolsync.parent.data.model.TimetableSlot
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground

// ═══════════════════════════════════════════════════════════════════════════════
//  Subject → Color mapping
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun subjectColor(subject: String): Color {
    val c = LocalAppColors.current
    return when (subject.lowercase().trim()) {
        "maths", "mathematics", "math" -> c.accent
        "science", "physics", "chemistry", "biology" -> c.success
        "english" -> c.purple
        "hindi" -> c.coral
        "geography", "history", "social science", "sst" -> c.teal
        "pt", "physical education", "sports" -> c.info
        "art", "drawing", "craft" -> c.warning
        "computer", "computers", "computer science", "it" -> c.accentSecondary
        "sanskrit" -> c.coral
        "evs", "environmental science" -> c.success
        "gk", "general knowledge" -> c.info
        "moral science" -> c.purple
        else -> c.accent
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Main Entry
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TimetableScreen(
    onBack: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val c = LocalAppColors.current

    // Handle back from detail page
    BackHandler(enabled = uiState.selectedDetail != null) {
        viewModel.closeDetail()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // Main list
        AnimatedVisibility(
            visible = uiState.selectedDetail == null,
            enter = fadeIn() + slideInHorizontally { -it / 3 },
            exit = fadeOut() + slideOutHorizontally { -it / 3 }
        ) {
            TimetableListPage(
                uiState = uiState,
                onBack = onBack,
                onSelectDay = viewModel::selectDay,
                onSetViewMode = viewModel::setViewMode,
                onSlotClick = viewModel::openDetail,
                onWeekCellClick = viewModel::switchToDay,
                onPullRefresh = { viewModel.pullRefresh() }
            )
        }

        // Detail page
        AnimatedVisibility(
            visible = uiState.selectedDetail != null,
            enter = fadeIn() + slideInHorizontally { it / 3 },
            exit = fadeOut() + slideOutHorizontally { it / 3 }
        ) {
            uiState.selectedDetail?.let { slot ->
                TimetableDetailPage(
                    slot = slot,
                    dayName = uiState.selectedDetailDay,
                    isLive = uiState.currentSlotIndex >= 0 &&
                            uiState.slots.getOrNull(uiState.currentSlotIndex) == slot,
                    onBack = viewModel::closeDetail
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  List Page
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimetableListPage(
    uiState: TimetableUiState,
    onBack: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onSetViewMode: (TimetableViewMode) -> Unit,
    onSlotClick: (TimetableSlot) -> Unit,
    onWeekCellClick: (String) -> Unit,
    onPullRefresh: () -> Unit = {}
) {
    val c = LocalAppColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(11.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Timetable",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
        }

        // ── Day / Week toggle ────────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        ViewModeToggle(
            currentMode = uiState.viewMode,
            onModeChange = onSetViewMode
        )

        // ── Content based on view mode ───────────────────────────────────
        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onPullRefresh
        ) {
        if (uiState.viewMode == TimetableViewMode.DAY) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Day pills row
            Spacer(modifier = Modifier.height(12.dp))
            DayPillsRow(
                pills = uiState.dayPills,
                selectedIndex = uiState.selectedDayIndex,
                onSelect = onSelectDay
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = c.accent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
            } else if (uiState.slots.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Now indicator
                    if (uiState.isSelectedDayToday && uiState.currentSlot != null) {
                        item(key = "now_indicator") {
                            NowIndicator(slot = uiState.currentSlot!!)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Progress bar
                    if (uiState.isSelectedDayToday && uiState.totalClassCount > 0) {
                        item(key = "progress") {
                            ProgressBar(
                                completed = uiState.completedCount,
                                total = uiState.totalClassCount,
                                fraction = uiState.progressFraction
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Slot rows
                    itemsIndexed(
                        items = uiState.slots,
                        key = { _, slot -> slot.periodKey }
                    ) { index, slot ->
                        val isCurrent = index == uiState.currentSlotIndex
                        val isNext = index == uiState.nextSlotIndex
                        val isDone = uiState.isSelectedDayToday &&
                                !slot.isBreak &&
                                uiState.currentSlotIndex >= 0 &&
                                index < uiState.currentSlotIndex
                        val isDoneByCompletion = uiState.isSelectedDayToday &&
                                !slot.isBreak &&
                                uiState.currentSlotIndex < 0 &&
                                uiState.completedCount > 0 &&
                                uiState.slots.filter { !it.isBreak }
                                    .take(uiState.completedCount)
                                    .contains(slot)

                        if (slot.isBreak) {
                            BreakRow(slot = slot)
                        } else {
                            SlotRow(
                                slot = slot,
                                isCurrent = isCurrent,
                                isNext = isNext,
                                isDone = isDone || isDoneByCompletion,
                                onClick = { onSlotClick(slot) }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
            }  // close DAY-view Column
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Week view
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = c.accent,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                } else {
                    WeekGridView(
                        weekData = uiState.weekData,
                        dayPills = uiState.dayPills,
                        currentSlotIndex = uiState.currentSlotIndex,
                        currentDaySlots = uiState.slots,
                        onCellClick = onWeekCellClick
                    )
                }
            }
        }
        }  // close PullToRefreshBox
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  View Mode Toggle (segmented control)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ViewModeToggle(
    currentMode: TimetableViewMode,
    onModeChange: (TimetableViewMode) -> Unit
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimetableViewMode.entries.forEach { mode ->
            val isActive = mode == currentMode
            val label = if (mode == TimetableViewMode.DAY) "Day" else "Week"

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isActive) Modifier.background(c.accent)
                        else Modifier
                    )
                    .clickable { onModeChange(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) {
                        if (c.isDark) Color(0xFF0E1822) else Color.White
                    } else c.textSecondary
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Day Pills Row
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DayPillsRow(
    pills: List<DayPill>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        pills.forEachIndexed { index, pill ->
            val isSelected = index == selectedIndex

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = c.accent.copy(alpha = 0.3f))
                                .background(c.accent)
                        } else {
                            Modifier.background(Color.Transparent)
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = pill.abbreviation,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        if (c.isDark) Color(0xFF0E1822) else Color.White
                    } else c.textSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pill.dateNumber.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) {
                        if (c.isDark) Color(0xFF0E1822) else Color.White
                    } else c.textPrimary
                )

                // Today dot (when not selected)
                if (pill.isToday && !isSelected) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                    )
                } else {
                    Spacer(modifier = Modifier.height(7.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Now Indicator
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NowIndicator(slot: TimetableSlot) {
    val c = LocalAppColors.current
    val pulse = rememberPulseAlpha()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.glass)
            .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing green dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = pulse }
                .clip(CircleShape)
                .background(c.success)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Now: ",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = c.success
        )
        Text(
            text = slot.subject,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary
        )
        if (slot.teacher.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = slot.teacher,
                fontSize = 11.sp,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = slot.time,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = c.textTertiary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Progress Bar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProgressBar(
    completed: Int,
    total: Int,
    fraction: Float
) {
    val c = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.glass)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$completed/$total",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = c.textSecondary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Slot Row (class period)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SlotRow(
    slot: TimetableSlot,
    isCurrent: Boolean,
    isNext: Boolean,
    isDone: Boolean,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val subjColor = subjectColor(slot.subject)
    // Softer fade for completed classes — 0.4 was too faint to read at a glance.
    val contentAlpha = if (isDone) 0.62f else 1f
    val pulse = rememberPulseAlpha()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(contentAlpha)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isCurrent) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(c.success.copy(alpha = 0.1f), c.glass)
                            )
                        )
                        .border(1.dp, c.success.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                        .background(c.glass)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                }
            )
            .clickable(enabled = !isDone) { onClick() }
            .padding(start = 0.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time column (44dp)
        Column(
            modifier = Modifier
                .width(50.dp)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = slot.startTime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isCurrent) c.success else c.textPrimary,
                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
            )
        }

        // Color bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(subjColor)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing dot for current
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { alpha = pulse }
                            .clip(CircleShape)
                            .background(c.success)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = slot.subject,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )

                // NEXT badge
                if (isNext) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.accent.copy(alpha = 0.15f))
                            .border(1.dp, c.accent.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEXT",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.accent,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (slot.teacher.isNotBlank() || slot.room.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (slot.teacher.isNotBlank()) append(slot.teacher)
                        if (slot.teacher.isNotBlank() && slot.room.isNotBlank()) append("  \u2022  ")
                        if (slot.room.isNotBlank()) append(slot.room)
                    },
                    fontSize = 11.sp,
                    color = c.textTertiary,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                )
            }
        }

        // Right side: end time or checkmark
        if (isDone) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Done",
                tint = c.success,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = slot.endTime,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = c.textTertiary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Break Row
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BreakRow(slot: TimetableSlot) {
    val c = LocalAppColors.current
    val isLunch = slot.periodKey.equals("Lunch", ignoreCase = true) ||
            slot.breakLabel.equals("Lunch", ignoreCase = true) ||
            slot.subject.equals("Lunch", ignoreCase = true)
    val emoji = if (isLunch) "\uD83C\uDF71" else "\u2615"  // lunch box or coffee
    val label = if (isLunch) "Lunch" else slot.breakLabel.ifBlank { "Break" }
    // Warm amber tint — distinct from subject colors but not loud.
    val breakTint = c.warning
    val bgTint = breakTint.copy(alpha = 0.10f)
    val borderTint = breakTint.copy(alpha = 0.28f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(bgTint)
            .border(1.dp, borderTint, RoundedCornerShape(12.dp))
            .padding(start = 0.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time column — same width as SlotRow so list stays aligned.
        Column(
            modifier = Modifier
                .width(50.dp)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = slot.startTime.ifBlank { "—" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = breakTint
            )
        }

        // Accent bar — matches SlotRow structure.
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(breakTint)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(text = emoji, fontSize = 18.sp)

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            if (slot.time.isNotBlank()) {
                Text(
                    text = slot.time,
                    fontSize = 11.sp,
                    color = c.textSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Week Grid View
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WeekGridView(
    weekData: Map<String, DayTimetable>,
    dayPills: List<DayPill>,
    currentSlotIndex: Int,
    currentDaySlots: List<TimetableSlot>,
    onCellClick: (String) -> Unit
) {
    val c = LocalAppColors.current
    val days = DayTimetable.DAYS_OF_WEEK
    val abbrevs = DayTimetable.DAY_ABBREVIATIONS

    // Collect all unique times across all days for the left time column
    val allSlots = days.flatMap { weekData[it]?.slots ?: emptyList() }
    val uniqueTimes = allSlots
        .map { it.time }
        .distinct()
        .sortedBy { time ->
            try {
                val parts = time.split("-")[0].trim().split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (_: Exception) { 999 }
        }

    // Find today
    val todayName = dayPills.firstOrNull { it.isToday }?.dayName
    val todayCurrentSlot = if (todayName != null && currentSlotIndex >= 0) {
        currentDaySlots.getOrNull(currentSlotIndex)
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        // Header row: empty + day abbreviations
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Time label column
            Spacer(modifier = Modifier.width(48.dp))

            days.forEach { day ->
                val isToday = day == todayName
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = abbrevs[day] ?: day.take(3),
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) c.accent else c.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Grid rows
        uniqueTimes.forEach { timeStr ->
            val breakSlots = allSlots.filter { it.time == timeStr && it.isBreak }
            val isBreakTime = breakSlots.isNotEmpty()

            if (isBreakTime) {
                // Unified break row spanning full grid width — amber pill with
                // lunch/coffee icon + label so it reads as a structured row,
                // not a section separator.
                val isLunch = breakSlots.any {
                    it.periodKey.equals("Lunch", ignoreCase = true) ||
                        it.breakLabel.equals("Lunch", ignoreCase = true) ||
                        it.subject.equals("Lunch", ignoreCase = true)
                }
                val emoji = if (isLunch) "\uD83C\uDF71" else "\u2615"
                val label = if (isLunch) "Lunch" else "Break"
                val breakTint = c.warning
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(breakTint.copy(alpha = 0.10f))
                        .border(1.dp, breakTint.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = breakTint,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.textSecondary
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time label
                    val startLabel = timeStr.split("-").firstOrNull()?.trim() ?: ""
                    Text(
                        text = startLabel,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = c.textTertiary,
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.Center
                    )

                    days.forEach { day ->
                        val dayData = weekData[day]
                        val slot = dayData?.slots?.firstOrNull {
                            it.time == timeStr && !it.isBreak
                        }
                        val isToday = day == todayName
                        val isCurrent = isToday && todayCurrentSlot != null &&
                                slot?.time == todayCurrentSlot.time &&
                                slot?.subject == todayCurrentSlot.subject

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(
                                    when {
                                        isCurrent -> Modifier
                                            .background(c.accent.copy(alpha = 0.2f))
                                            .border(
                                                1.dp,
                                                c.accent.copy(alpha = 0.4f),
                                                RoundedCornerShape(6.dp)
                                            )
                                        isToday -> Modifier
                                            .background(c.glass.copy(alpha = 0.8f))
                                            .border(
                                                1.dp,
                                                c.accent.copy(alpha = 0.15f),
                                                RoundedCornerShape(6.dp)
                                            )
                                        else -> Modifier.background(c.glass.copy(alpha = 0.4f))
                                    }
                                )
                                .clickable { onCellClick(day) }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (slot != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(subjectColor(slot.subject))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = slot.subject.take(4),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCurrent) c.accent else c.textPrimary,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Detail Page
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimetableDetailPage(
    slot: TimetableSlot,
    dayName: String,
    isLive: Boolean,
    onBack: () -> Unit
) {
    val c = LocalAppColors.current
    val subjColor = subjectColor(slot.subject)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.glass)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(11.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            // Status pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLive) c.success.copy(alpha = 0.15f)
                        else c.glass
                    )
                    .border(
                        1.dp,
                        if (isLive) c.success.copy(alpha = 0.3f) else c.glassBorder,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        val pulse = rememberPulseAlpha()
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .graphicsLayer { alpha = pulse }
                                .clip(CircleShape)
                                .background(c.success)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isLive) "Live" else "Completed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLive) c.success else c.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Subject name with color bar
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(subjColor)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = slot.subject,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
        }

        // Chapter/topic (placeholder since data doesn't have it)
        Text(
            text = "Period ${slot.periodKey}",
            fontSize = 13.sp,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 38.dp, top = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Info card
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .glassCard(16.dp)
                .padding(16.dp)
        ) {
            DetailInfoRow(
                icon = Icons.Filled.AccessTime,
                label = "Time",
                value = slot.time,
                iconTint = c.accent
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = c.divider
            )
            DetailInfoRow(
                icon = Icons.Filled.Person,
                label = "Teacher",
                value = slot.teacher.ifBlank { "--" },
                iconTint = c.accent
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = c.divider
            )
            DetailInfoRow(
                icon = Icons.Filled.LocationOn,
                label = "Room",
                value = slot.room.ifBlank { "--" },
                iconTint = c.accent
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = c.divider
            )
            DetailInfoRow(
                icon = Icons.Filled.Today,
                label = "Day",
                value = dayName,
                iconTint = c.accent
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Carry section (subject-based suggestions)
        val carryItems = remember(slot.subject) { getCarryItems(slot.subject) }
        if (carryItems.isNotEmpty()) {
            Text(
                text = "Things to carry",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                carryItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.glass)
                            .border(1.dp, c.glassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = item,
                            fontSize = 12.sp,
                            color = c.textPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Teacher note (placeholder)
        Text(
            text = "\"Come prepared with your assignments.\"",
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
            color = c.textTertiary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color
) {
    val c = LocalAppColors.current

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = c.textTertiary,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Empty State
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState() {
    com.schoolsync.parent.ui.components.EmptyStatePro(
        icon = Icons.Filled.Schedule,
        title = "No Timetable",
        description = "Timetable for this day is not available yet.",
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helpers
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun rememberPulseAlpha(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    return alpha
}

private fun getCarryItems(subject: String): List<String> {
    return when (subject.lowercase().trim()) {
        "maths", "mathematics", "math" -> listOf("Textbook", "Notebook", "Geometry Box", "Calculator")
        "science", "physics", "chemistry", "biology" -> listOf("Textbook", "Lab Coat", "Notebook")
        "english" -> listOf("Textbook", "Notebook", "Dictionary")
        "hindi", "sanskrit" -> listOf("Textbook", "Notebook")
        "computer", "computers", "computer science", "it" -> listOf("Notebook", "USB Drive")
        "pt", "physical education", "sports" -> listOf("Sports Kit", "Water Bottle", "Shoes")
        "art", "drawing", "craft" -> listOf("Drawing Book", "Colors", "Pencils", "Eraser")
        "geography", "history", "social science", "sst" -> listOf("Textbook", "Notebook", "Atlas")
        else -> listOf("Textbook", "Notebook")
    }
}
