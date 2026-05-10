package com.schoolsync.parent.ui.attendance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.AttendanceData
import com.schoolsync.parent.data.model.AttendanceStatus
import com.schoolsync.parent.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    onNavigateToLeave: () -> Unit = {},
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current

    // Refresh on background → foreground only. The viewmodel's init {}
    // already performs the initial load, and the lifecycle observer below
    // would otherwise fire ON_RESUME synchronously on first registration
    // (the lifecycle is already RESUMED when this composition runs),
    // causing a triple-load + UI flicker on screen open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var skippedFirstResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!skippedFirstResume) {
                    skippedFirstResume = true
                    return@LifecycleEventObserver
                }
                viewModel.loadAttendance()
                viewModel.refreshExtras()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
    ) {
        // 1. Header
        AttendanceHeader(
            studentName = uiState.user.name.ifBlank { "Student" },
            className = uiState.user.className,
            section = uiState.user.section,
            onBack = onBack,
            colors = colors
        )

        // weight(1f) so the pull-refresh gesture surface fills exactly the
        // remaining column height. Without it the inner fillMaxSize Box can
        // overlap the header and the indicator drifts off-screen.
        com.schoolsync.parent.ui.common.PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.pullRefresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
        if (uiState.isLoading && uiState.attendanceData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 2. Today's Status Banner
                item {
                    TodayStatusBanner(
                        todayStatus = uiState.todayStatus,
                        dayOfYear = uiState.dayOfYear,
                        totalSchoolDays = uiState.totalSchoolDays,
                        colors = colors
                    )
                }

                // 3. Stats Ring + Counts
                item {
                    StatsCard(
                        stats = uiState.stats,
                        currentStreak = uiState.currentStreak,
                        bestStreak = uiState.bestStreak,
                        colors = colors
                    )
                }

                // 4. Calendar
                item {
                    val selectedMonth = uiState.months.getOrNull(uiState.selectedMonthIndex)
                    if (selectedMonth != null) {
                        CalendarCard(
                            yearMonth = selectedMonth.yearMonth,
                            attendanceData = uiState.attendanceData,
                            onPreviousMonth = {
                                // Go to older month (higher index, since index 0 = current month)
                                val olderIdx = uiState.selectedMonthIndex + 1
                                if (olderIdx < uiState.months.size) {
                                    viewModel.selectMonth(olderIdx)
                                }
                            },
                            onNextMonth = {
                                // Go to newer month (lower index)
                                val newerIdx = uiState.selectedMonthIndex - 1
                                if (newerIdx >= 0) {
                                    viewModel.selectMonth(newerIdx)
                                }
                            },
                            canGoBack = uiState.selectedMonthIndex < uiState.months.size - 1,
                            canGoForward = uiState.selectedMonthIndex > 0,
                            colors = colors
                        )
                    }
                }

                // 5. Monthly Overview
                if (uiState.monthlyComparison.isNotEmpty()) {
                    item {
                        MonthlyOverviewCard(
                            months = uiState.monthlyComparison,
                            selectedMonthIndex = uiState.selectedMonthIndex,
                            colors = colors
                        )
                    }
                }

                // 6. Recent Days List
                if (uiState.recentDays.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent days",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    uiState.recentDays.forEach { day ->
                        item(key = "recent_${day.dayOfMonth}") {
                            RecentDayCard(day = day, colors = colors)
                        }
                    }
                }

                // 7. Apply for Leave button
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onNavigateToLeave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = if (colors.isDark) Color(0xFF0E1822) else Color.White
                        )
                    ) {
                        Text(
                            text = "Apply for leave",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Error
                uiState.errorMessage?.let { error ->
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.errorBg)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = error,
                                color = colors.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
        }
    }
}

// ─── 1. Header ──────────────────────────────────────────────────────────────

@Composable
private fun AttendanceHeader(
    studentName: String,
    className: String,
    section: String,
    onBack: () -> Unit,
    colors: AppColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glass back button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.glass)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(11.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Attendance",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            if (className.isNotBlank()) {
                Text(
                    text = "$studentName  ·  $className - $section",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}

// ─── 2. Today's Status Banner ───────────────────────────────────────────────

@Composable
private fun TodayStatusBanner(
    todayStatus: AttendanceStatus?,
    dayOfYear: Int,
    totalSchoolDays: Int,
    colors: AppColors
) {
    val statusColor = when (todayStatus) {
        AttendanceStatus.PRESENT -> colors.success
        AttendanceStatus.ABSENT -> colors.error
        AttendanceStatus.LEAVE -> colors.warning
        AttendanceStatus.HOLIDAY -> colors.attHoliday
        AttendanceStatus.TRIP, AttendanceStatus.VACATION -> colors.attVacation
        null -> colors.textTertiary
    }

    val statusText = when (todayStatus) {
        AttendanceStatus.PRESENT -> "Present today"
        AttendanceStatus.ABSENT -> "Absent today"
        AttendanceStatus.LEAVE -> "On leave today"
        AttendanceStatus.HOLIDAY -> "Holiday today"
        AttendanceStatus.TRIP -> "Late arrival today"
        AttendanceStatus.VACATION -> "Vacation"
        null -> "No status yet"
    }

    val subtitle = when (todayStatus) {
        AttendanceStatus.PRESENT -> "Keep up the good work!"
        AttendanceStatus.ABSENT -> "Make sure to catch up"
        AttendanceStatus.LEAVE -> "Approved leave"
        AttendanceStatus.HOLIDAY -> "Enjoy your day off"
        else -> "Status will be updated soon"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot with glow
        Box(contentAlignment = Alignment.Center) {
            // Glow shadow
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .shadow(8.dp, CircleShape, ambientColor = statusColor, spotColor = statusColor)
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }

        if (totalSchoolDays > 0) {
            Text(
                text = "Day $dayOfYear of $totalSchoolDays",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = colors.textTertiary
            )
        }
    }
}

// ─── 3. Stats Ring + Counts ─────────────────────────────────────────────────

@Composable
private fun AttendanceRing(
    percentage: Float,
    size: Dp,
    strokeWidth: Dp,
    color: Color,
    trackColor: Color
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (percentage / 100f) * 360f,
        animationSpec = tween(durationMillis = 800),
        label = "ring"
    )

    Canvas(modifier = Modifier.size(size)) {
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = animatedSweep,
            useCenter = false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun StatsCard(
    stats: AttendanceStats,
    currentStreak: Int,
    bestStreak: Int,
    colors: AppColors
) {
    val ringColor = when {
        stats.percentage >= 75 -> colors.success
        stats.percentage >= 50 -> colors.warning
        else -> colors.error
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Ring
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                AttendanceRing(
                    percentage = stats.percentage,
                    size = 100.dp,
                    strokeWidth = 8.dp,
                    color = ringColor,
                    trackColor = colors.glassBorder
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${stats.percentage.toInt()}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "percent",
                        fontSize = 10.sp,
                        color = colors.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Right: 2x2 grid
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatGridItem(
                        count = stats.present,
                        label = "Present",
                        dotColor = colors.attPresent,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    StatGridItem(
                        count = stats.absent,
                        label = "Absent",
                        dotColor = colors.attAbsent,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    StatGridItem(
                        count = stats.tardy,
                        label = "Tardy",
                        dotColor = colors.attVacation,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatGridItem(
                        count = stats.leave,
                        label = "Leave",
                        dotColor = colors.warning,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    StatGridItem(
                        count = stats.holiday,
                        label = "Holiday",
                        dotColor = colors.textTertiary,
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                    // Spacer to keep the 3-column grid aligned with row 1.
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Streak bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.successBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\uD83D\uDD25", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$currentStreak day streak",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.success
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Best: $bestStreak days",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun StatGridItem(
    count: Int,
    label: String,
    dotColor: Color,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = "$count",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = colors.textPrimary
            )
            Text(
                text = label,
                fontSize = 9.sp,
                color = colors.textTertiary
            )
        }
    }
}

// ─── 4. Calendar ────────────────────────────────────────────────────────────

@Composable
private fun CalendarCard(
    yearMonth: YearMonth,
    attendanceData: AttendanceData?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    colors: AppColors
) {
    val today = remember { LocalDate.now() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(12.dp)
    ) {
        // Month navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left arrow
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canGoBack) colors.glass else Color.Transparent)
                    .then(
                        if (canGoBack)
                            Modifier.clickable(onClick = onPreviousMonth)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = if (canGoBack) colors.textPrimary else colors.textTertiary.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            // Right arrow
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canGoForward) colors.glass else Color.Transparent)
                    .then(
                        if (canGoForward)
                            Modifier.clickable(onClick = onNextMonth)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = if (canGoForward) colors.textPrimary else colors.textTertiary.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Day headers (Monday start)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    text = day,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Calendar grid (Monday start)
        val firstDayOfMonth = yearMonth.atDay(1).dayOfWeek
        // Monday=0, Tuesday=1, ..., Sunday=6
        val startOffset = (firstDayOfMonth.value - 1) % 7
        val daysInMonth = yearMonth.lengthOfMonth()
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        val isCurrentMonth = yearMonth == YearMonth.from(today)

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - startOffset + 1

                    if (day in 1..daysInMonth) {
                        val status = attendanceData?.statusForDay(day)
                        val isToday = isCurrentMonth && day == today.dayOfMonth
                        val isFuture = isCurrentMonth && day > today.dayOfMonth

                        CalendarDayCell(
                            day = day,
                            status = status,
                            isToday = isToday,
                            isFuture = isFuture,
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = colors.attPresent, label = "Present", colors = colors)
            LegendDot(color = colors.attAbsent, label = "Absent", colors = colors)
            LegendDot(color = colors.warning, label = "Leave", colors = colors)
            LegendDot(color = colors.textTertiary, label = "Holiday", colors = colors)
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    status: AttendanceStatus?,
    isToday: Boolean,
    isFuture: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    // Phase 10: future days still show Leave (L) and Holiday (H)
    // dots so approved leaves and holidays are visible on the
    // calendar even before the date arrives. Only Vacant (V) and
    // unmarked (null) are suppressed on future days.
    val hasPresetStatus = status == AttendanceStatus.LEAVE || status == AttendanceStatus.HOLIDAY
    val showStatus = !isFuture || hasPresetStatus

    val bgColor = when {
        !showStatus -> colors.glass.copy(alpha = 0.1f)
        else -> when (status) {
            AttendanceStatus.PRESENT -> colors.attPresent.copy(alpha = 0.2f)
            AttendanceStatus.ABSENT -> colors.attAbsent.copy(alpha = 0.2f)
            AttendanceStatus.LEAVE -> colors.warning.copy(alpha = 0.2f)
            AttendanceStatus.HOLIDAY -> colors.textTertiary.copy(alpha = 0.1f)
            AttendanceStatus.TRIP -> colors.attVacation.copy(alpha = 0.2f)
            AttendanceStatus.VACATION, null -> Color.Transparent  // V = unrecorded, no bg
        }
    }

    val dotColor = when {
        !showStatus -> null
        else -> when (status) {
            AttendanceStatus.PRESENT -> colors.attPresent
            AttendanceStatus.ABSENT -> colors.attAbsent
            AttendanceStatus.LEAVE -> colors.warning
            AttendanceStatus.HOLIDAY -> colors.textTertiary
            AttendanceStatus.TRIP -> colors.attVacation
            AttendanceStatus.VACATION, null -> null  // V = unrecorded, no dot
        }
    }

    val textColor = when {
        isFuture -> colors.textTertiary
        isToday -> colors.textPrimary
        status != null -> colors.textPrimary
        else -> colors.textSecondary
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.5.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (isToday)
                    Modifier.border(1.5.dp, colors.accent, RoundedCornerShape(10.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$day",
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (dotColor != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
    colors: AppColors
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = colors.textSecondary
        )
    }
}

// ─── 5. Monthly Overview ────────────────────────────────────────────────────

@Composable
private fun MonthlyOverviewCard(
    months: List<MonthlyComparison>,
    selectedMonthIndex: Int,
    colors: AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .padding(16.dp)
    ) {
        Text(
            text = "MONTHLY OVERVIEW",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textTertiary,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            months.forEachIndexed { index, month ->
                val isActive = index == months.lastIndex - selectedMonthIndex
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Bar
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.glassBorder.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val fillFraction = (month.percentage / 100f).coerceIn(0f, 1f)
                        val animatedFill by animateFloatAsState(
                            targetValue = fillFraction,
                            animationSpec = tween(600),
                            label = "bar"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animatedFill)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    colors.accent.copy(
                                        alpha = if (isActive) 1f else 0.4f
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${month.percentage.toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActive) colors.accent else colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = month.monthAbbrev,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

// ─── 6. Recent Days List ────────────────────────────────────────────────────

@Composable
private fun RecentDayCard(
    day: RecentDay,
    colors: AppColors
) {
    val statusColor = when (day.status) {
        AttendanceStatus.PRESENT -> colors.attPresent
        AttendanceStatus.ABSENT -> colors.attAbsent
        AttendanceStatus.LEAVE -> colors.warning
        AttendanceStatus.HOLIDAY -> colors.attHoliday
        AttendanceStatus.TRIP, AttendanceStatus.VACATION -> colors.attVacation
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Day info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = day.dayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = day.dateStr,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textTertiary
                )
            }

            // Status label, plus a "+N min late" suffix on tardy days when
            // we have a recorded arrival time.
            val lateMinutes = day.arrivalTime?.let { computeLateMinutes(it) }
            val statusLabel = when {
                day.status == AttendanceStatus.TRIP && lateMinutes != null && lateMinutes > 0 ->
                    "Tardy · +${lateMinutes} min"
                else -> day.status.label
            }
            Text(
                text = statusLabel,
                fontSize = 10.sp,
                color = statusColor
            )
        }

        // Arrival time (real, when admin recorded it for tardy days).
        // Falls back to a simple placeholder for non-tardy days so the
        // layout doesn't shift.
        Column(horizontalAlignment = Alignment.End) {
            val arrivalLabel = day.arrivalTime ?: when (day.status) {
                AttendanceStatus.PRESENT -> "On time"
                AttendanceStatus.ABSENT -> "—"
                AttendanceStatus.LEAVE -> "Leave"
                AttendanceStatus.HOLIDAY -> "Holiday"
                AttendanceStatus.VACATION -> "Vacation"
                AttendanceStatus.TRIP -> "Tardy"
            }
            Text(
                text = arrivalLabel,
                fontSize = 10.sp,
                fontFamily = if (day.arrivalTime != null) FontFamily.Monospace else FontFamily.Default,
                color = if (day.arrivalTime != null) statusColor else colors.textSecondary
            )
        }
    }
}

/**
 * Compute minutes late vs. the school's late threshold.
 * Phase 10f: threshold comes from [lateThreshold] param (default "08:30"),
 * read from school config via the ViewModel.
 */
private fun computeLateMinutes(arrivalTime: String, lateThreshold: String = "08:30"): Int? {
    val parts = arrivalTime.split(":")
    if (parts.size != 2) return null
    val hh = parts[0].toIntOrNull() ?: return null
    val mm = parts[1].toIntOrNull() ?: return null
    val arrivalMin = hh * 60 + mm

    val cutoffParts = lateThreshold.split(":")
    val cutoffH = cutoffParts.getOrNull(0)?.toIntOrNull() ?: 8
    val cutoffM = cutoffParts.getOrNull(1)?.toIntOrNull() ?: 30
    val cutoffMin = cutoffH * 60 + cutoffM

    val diff = arrivalMin - cutoffMin
    return if (diff > 0) diff else null
}
