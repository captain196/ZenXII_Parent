package com.schoolsync.parent.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Grading
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.ui.stories.StoriesRow
import com.schoolsync.parent.ui.theme.*

// ---------------------------------------------------------------------------
// Data classes for static content
// ---------------------------------------------------------------------------

private data class HighlightCard(
    val tag: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val gradStart: Color,
    val gradEnd: Color,
    val imageUrl: String? = null
)

private data class QuickAction(
    val emoji: String,
    val label: String,
    val badge: Int = 0,
    val onClick: () -> Unit
)

private data class SubjectCard(
    val emoji: String,
    val name: String,
    val grade: String,
    val score: Int,
    val total: Int,
    val barColor: Color
)

// ---------------------------------------------------------------------------
// Main DashboardScreen
// ---------------------------------------------------------------------------

@Composable
fun DashboardScreen(
    onNavigateToAttendance: () -> Unit,
    onNavigateToResults: () -> Unit,
    onNavigateToFees: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToHomework: () -> Unit = {},
    onNavigateToNotices: () -> Unit,
    onNavigateToLeave: () -> Unit = {},
    onNavigateToEvents: () -> Unit = {},
    onNavigateToEventDetail: (String) -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToRedFlags: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToStoryViewer: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                userName = uiState.user?.name ?: "Student",
                schoolName = uiState.user?.schoolDisplayName ?: "",
                onClose = { scope.launch { drawerState.close() } },
                onAttendance = { scope.launch { drawerState.close() }; onNavigateToAttendance() },
                onResults = { scope.launch { drawerState.close() }; onNavigateToResults() },
                onFees = { scope.launch { drawerState.close() }; onNavigateToFees() },
                onTimetable = { scope.launch { drawerState.close() }; onNavigateToTimetable() },
                onHomework = { scope.launch { drawerState.close() }; onNavigateToHomework() },
                onNotices = { scope.launch { drawerState.close() }; onNavigateToNotices() },
                onEvents = { scope.launch { drawerState.close() }; onNavigateToEvents() },
                onGallery = { scope.launch { drawerState.close() }; onNavigateToGallery() },
                onRedFlags = { scope.launch { drawerState.close() }; onNavigateToRedFlags() },
                onLibrary = { scope.launch { drawerState.close() }; onNavigateToLibrary() },
                onProfile = { scope.launch { drawerState.close() }; onNavigateToProfile() }
            )
        }
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
    ) {
        if (uiState.isLoading && uiState.user == null) {
            DashboardShimmer()
        } else {
            val user = uiState.user
            val firstName = (user?.name ?: "Student").split(" ").firstOrNull() ?: "Student"
            val initials = buildInitials(user?.name ?: "S")
            val attendancePct = uiState.attendancePercentage

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // ---- Top Bar (non-scrollable) ----
                TopBar(
                    firstName = firstName,
                    initials = initials,
                    onNotificationClick = onNavigateToNotices,
                    onProfileClick = onNavigateToProfile,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )

                // ---- Scrollable content ----
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // ---- Search Bar ----
                    SearchBarRow()

                    // ---- Stories Row ----
                    if (uiState.storyGroups.isNotEmpty()) {
                        StoriesRow(
                            storyGroups = uiState.storyGroups,
                            onTeacherClick = onNavigateToStoryViewer
                        )
                    }

                    // ---- Red Flags Badge ----
                    if (uiState.activeFlagCount > 0) {
                        RedFlagBanner(
                            count = uiState.activeFlagCount,
                            onClick = onNavigateToRedFlags
                        )
                    }

                    // ---- Highlights ----
                    HighlightsSection(
                        events = uiState.upcomingEvents,
                        onEventClick = onNavigateToEventDetail,
                        onViewAll = onNavigateToEvents
                    )

                    // ---- Overview (Bento Grid) ----
                    OverviewSection(
                        attendancePercent = attendancePct,
                        attendanceChange = uiState.attendanceChange,
                        onAttendanceClick = onNavigateToAttendance
                    )

                    // ---- Quick Actions ----
                    QuickActionsSection(
                        onHomework = onNavigateToHomework,
                        onFees = onNavigateToFees,
                        onResults = onNavigateToResults,
                        onTimetable = onNavigateToTimetable,
                        homeworkBadge = uiState.pendingHomeworkCount
                    )

                    // ---- Subjects ----
                    SubjectsSection(onViewAll = onNavigateToResults)

                    // Bottom padding for nav bar clearance
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Error toast
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.errorBg)
                    .padding(12.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(
                    text = error,
                    color = c.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    } // ModalNavigationDrawer
}

// ---------------------------------------------------------------------------
// Top Bar
// ---------------------------------------------------------------------------

@Composable
private fun TopBar(
    firstName: String,
    initials: String,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: hamburger + greeting
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Menu",
                tint = c.textPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onMenuClick)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Good morning",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = c.textSecondary,
                        letterSpacing = 0.3.sp
                    )
                )
                Text(
                    text = firstName,
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                )
            }
        }

        // Right side: notification bell + avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Bell with red dot
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.glass)
                    .clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = c.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
                // Red dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c.error)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                )
            }

            // Avatar circle with initials — clickable to profile
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(c.accent, c.accentSecondary)
                        )
                    )
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Red Flag Banner
// ---------------------------------------------------------------------------

@Composable
private fun RedFlagBanner(count: Int, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.errorBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(c.error)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "$count active alert${if (count > 1) "s" else ""} for your child",
                style = MaterialTheme.typography.labelMedium,
                color = c.error,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "View alerts",
            tint = c.error,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Search Bar Row
// ---------------------------------------------------------------------------

@Composable
private fun SearchBarRow() {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search field
        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .glassCard(14.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Search...",
                style = TextStyle(
                    fontSize = 14.sp,
                    color = c.textTertiary
                )
            )
        }

        // Grid button
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.GridView,
                contentDescription = "Grid",
                tint = c.pillText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Highlights Section — shows real Firebase events
// ---------------------------------------------------------------------------

private fun eventGradient(category: String, c: AppColors): Pair<Color, Color> {
    return when (category.lowercase()) {
        "cultural" -> c.banner1Start to c.banner1End
        "sports" -> c.banner2Start to c.banner2End
        "academic" -> c.banner3Start to c.banner3End
        "exam" -> Pair(c.error.copy(alpha = 0.6f), c.error.copy(alpha = 0.3f))
        "holiday" -> Pair(c.info.copy(alpha = 0.5f), c.info.copy(alpha = 0.25f))
        else -> c.banner1Start to c.banner1End
    }
}

private fun eventEmoji(category: String): String {
    return when (category.lowercase()) {
        "cultural" -> "\uD83C\uDFAD"
        "sports" -> "\u26BD"
        "academic" -> "\uD83D\uDCDA"
        "exam" -> "\uD83D\uDCDD"
        "holiday" -> "\uD83C\uDF89"
        else -> "\uD83D\uDCC5"
    }
}

private fun formatDateRange(startDate: String, endDate: String): String {
    if (startDate.isBlank()) return ""
    if (endDate.isBlank() || endDate == startDate) return startDate
    return "$startDate - $endDate"
}

@Composable
private fun HighlightsSection(
    events: List<Event>,
    onEventClick: (String) -> Unit,
    onViewAll: () -> Unit
) {
    val c = LocalAppColors.current
    var expanded by remember { mutableStateOf(true) }

    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Highlights")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (events.isNotEmpty()) {
                    Text(
                        text = "All",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.accent
                        ),
                        modifier = Modifier.clickable(onClick = onViewAll)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = c.textTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300))
        ) {
            if (events.isEmpty()) {
                // Fallback when no events loaded
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .glassCard(16.dp)
                        .clickable(onClick = onViewAll),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.EventNote,
                            contentDescription = null,
                            tint = c.textTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No upcoming events",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = c.textSecondary
                            )
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    events.take(5).forEach { event ->
                        val (gradStart, gradEnd) = eventGradient(event.category, c)
                        val firstImage = event.mediaUrls.firstOrNull { it.type == "image" }?.url
                        HighlightBannerCard(
                            card = HighlightCard(
                                tag = event.category.replaceFirstChar { it.uppercase() },
                                emoji = eventEmoji(event.category),
                                title = event.title,
                                subtitle = buildString {
                                    append(formatDateRange(event.startDate, event.endDate))
                                    if (event.location.isNotBlank()) {
                                        if (isNotBlank()) append(" \u00B7 ")
                                        append(event.location)
                                    }
                                },
                                gradStart = gradStart,
                                gradEnd = gradEnd,
                                imageUrl = firstImage
                            ),
                            onClick = { onEventClick(event.eventId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightBannerCard(card: HighlightCard, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(card.gradStart, card.gradEnd)
                )
            )
            .clickable(onClick = onClick)
    ) {
        // Event image background
        if (card.imageUrl != null) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dark scrim over image for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Tag pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = card.tag,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Emoji icon
                Text(
                    text = card.emoji,
                    fontSize = 28.sp
                )
            }

            Column {
                Text(
                    text = card.title,
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.65f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Overview Section (Bento Grid)
// ---------------------------------------------------------------------------

@Composable
private fun OverviewSection(
    attendancePercent: Float,
    attendanceChange: Float?,
    onAttendanceClick: () -> Unit
) {
    Column {
        SectionHeader(title = "Overview")
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left tall card: Attendance
            AttendanceBentoCard(
                percentage = attendancePercent,
                change = attendanceChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onAttendanceClick)
            )

            // Right column: 2 stacked cards
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Class Rank card (gradient)
                ClassRankCard(
                    rank = 4,
                    totalStudents = 45,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // Current class card
                CurrentClassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AttendanceBentoCard(
    percentage: Float,
    change: Float? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val whole = percentage.toInt()
    val decimal = ".${((percentage - whole) * 10).toInt()}%"

    val animatedSweep by animateFloatAsState(
        targetValue = percentage / 100f * 360f,
        animationSpec = tween(durationMillis = 1000),
        label = "attendanceSweep"
    )

    Column(
        modifier = modifier
            .glassCard(20.dp)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        Text(
            text = "ATTENDANCE",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textTertiary,
                letterSpacing = 1.5.sp
            )
        )

        // Ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // Background track
                drawArc(
                    color = c.glassBorder,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Foreground arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(c.accent, c.success, c.accent)
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Center text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$whole",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = c.textPrimary
                    )
                )
                Text(
                    text = decimal,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = c.textSecondary
                    )
                )
            }
        }

        // Bottom stat — attendance change
        if (change != null) {
            val isRise = change >= 0f
            val changeColor = if (isRise) c.success else c.error
            val arrow = if (isRise) "\u2191" else "\u2193"
            val sign = if (isRise) "+" else ""
            val changeText = "$sign${"%.1f".format(change)}% this month"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = arrow,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = changeColor
                    )
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = changeText,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = changeColor
                    )
                )
            }
        } else {
            Text(
                text = "This month",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.textTertiary
                )
            )
        }
    }
}

@Composable
private fun ClassRankCard(
    rank: Int,
    totalStudents: Int,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(c.accent, c.accentSecondary)
                )
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CLASS RANK",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.5.sp
                )
            )

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "#$rank",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                )
                Text(
                    text = "/$totalStudents",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Text(
                text = "Top 10%",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            )
        }
    }
}

@Composable
private fun CurrentClassCard(
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .glassCard(20.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "NOW",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textTertiary,
                letterSpacing = 1.5.sp
            )
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Mathematics",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                )
                Text(
                    text = "Room 204",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = c.textSecondary
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(c.glass)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Next: Hindi \u00B7 11:15",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = c.textSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Quick Actions
// ---------------------------------------------------------------------------

@Composable
private fun QuickActionsSection(
    onHomework: () -> Unit,
    onFees: () -> Unit,
    onResults: () -> Unit,
    onTimetable: () -> Unit,
    homeworkBadge: Int = 0
) {
    Column {
        SectionHeader(title = "Quick actions")
        Spacer(modifier = Modifier.height(10.dp))

        val actions = remember(onHomework, onFees, onResults, onTimetable, homeworkBadge) {
            listOf(
                QuickAction(emoji = "\uD83D\uDCDD", label = "Homework", badge = homeworkBadge, onClick = onHomework),
                QuickAction(emoji = "\uD83D\uDCB3", label = "Pay fees", onClick = onFees),
                QuickAction(emoji = "\uD83D\uDCCA", label = "Results", onClick = onResults),
                QuickAction(emoji = "\uD83D\uDCC5", label = "Timetable", onClick = onTimetable)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            actions.forEach { action ->
                QuickActionItem(action = action)
            }
        }
    }
}

@Composable
private fun QuickActionItem(action: QuickAction) {
    val c = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = action.onClick)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .glassCard(27.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = action.emoji,
                    fontSize = 22.sp
                )
            }

            // Badge
            if (action.badge > 0) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(c.error)
                        .offset(x = 2.dp, y = (-2).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${action.badge}",
                        style = TextStyle(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = action.label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = c.textSecondary
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Subjects Section
// ---------------------------------------------------------------------------

@Composable
private fun SubjectsSection(onViewAll: () -> Unit) {
    val c = LocalAppColors.current
    val subjects = listOf(
        SubjectCard("\uD83D\uDCCF", "Maths", "A+", 92, 100, c.accent),
        SubjectCard("\uD83E\uDDEA", "Science", "A", 88, 100, c.success),
        SubjectCard("\uD83D\uDCDA", "English", "B+", 79, 100, c.warning),
        SubjectCard("\uD83C\uDF0D", "SST", "A", 85, 100, c.purple),
        SubjectCard("\uD83D\uDCBB", "Computer", "A+", 95, 100, c.teal)
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Subjects")
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.accent
                    )
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            subjects.forEach { subject ->
                SubjectCardItem(subject)
            }
        }
    }
}

@Composable
private fun SubjectCardItem(subject: SubjectCard) {
    val c = LocalAppColors.current
    val progress = subject.score.toFloat() / subject.total.toFloat()

    Column(
        modifier = Modifier
            .width(100.dp)
            .glassCard(16.dp)
            .padding(bottom = 0.dp)
    ) {
        // Top color bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(subject.barColor)
        )

        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Emoji + Grade badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subject.emoji,
                    fontSize = 20.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(subject.barColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = subject.grade,
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = subject.barColor
                        )
                    )
                }
            }

            // Subject name
            Text(
                text = subject.name,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Score
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${subject.score}",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = c.textPrimary
                    )
                )
                Text(
                    text = "/${subject.total}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = c.textSecondary
                    ),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }

        // Progress bar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(c.glassBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(3.dp)
                    .background(subject.barColor)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared Components
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    val c = LocalAppColors.current
    Text(
        text = title,
        style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary
        )
    )
}

// ---------------------------------------------------------------------------
// Shimmer / Loading
// ---------------------------------------------------------------------------

@Composable
private fun DashboardShimmer() {
    val c = LocalAppColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
            }
        }

        // Search bar shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.shimmerBase.copy(alpha = shimmerAlpha))
        )

        // Highlights shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
            }
        }

        // Overview shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.shimmerBase.copy(alpha = shimmerAlpha))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                )
            }
        }

        // Quick actions shimmer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.shimmerBase.copy(alpha = shimmerAlpha))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Drawer Menu
// ---------------------------------------------------------------------------

@Composable
private fun DrawerContent(
    userName: String,
    schoolName: String,
    onClose: () -> Unit,
    onAttendance: () -> Unit,
    onResults: () -> Unit,
    onFees: () -> Unit,
    onTimetable: () -> Unit,
    onHomework: () -> Unit,
    onNotices: () -> Unit,
    onEvents: () -> Unit,
    onGallery: () -> Unit,
    onRedFlags: () -> Unit,
    onLibrary: () -> Unit,
    onProfile: () -> Unit
) {
    val c = LocalAppColors.current

    ModalDrawerSheet(
        drawerContainerColor = c.bgStart,
        drawerContentColor = c.textPrimary
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(c.accent, c.accentSecondary))
                )
                .padding(24.dp)
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buildInitials(userName),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = userName,
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            )
            if (schoolName.isNotBlank()) {
                Text(
                    text = schoolName,
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Menu items
        val items = listOf(
            Triple(Icons.Filled.CalendarMonth, "Attendance", onAttendance),
            Triple(Icons.Filled.MenuBook, "Results", onResults),
            Triple(Icons.Filled.CreditCard, "Fees", onFees),
            Triple(Icons.Filled.Schedule, "Timetable", onTimetable),
            Triple(Icons.Filled.Assignment, "Homework", onHomework),
            Triple(Icons.Filled.Campaign, "Notices", onNotices),
            Triple(Icons.Filled.Event, "Events", onEvents),
            Triple(Icons.Filled.PhotoLibrary, "Gallery", onGallery),
            Triple(Icons.Filled.LocalLibrary, "Library", onLibrary),
            Triple(Icons.Filled.Flag, "Alerts", onRedFlags),
            Triple(Icons.Filled.Person, "Profile", onProfile)
        )

        items.forEach { (icon, label, onClick) ->
            NavigationDrawerItem(
                icon = { Icon(icon, contentDescription = label, tint = c.textSecondary, modifier = Modifier.size(22.dp)) },
                label = { Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)) },
                selected = false,
                onClick = onClick,
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = c.textPrimary,
                    unselectedIconColor = c.textSecondary
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

private fun buildInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex())
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts.isNotEmpty() && parts[0].isNotEmpty() -> "${parts[0].first().uppercaseChar()}"
        else -> "S"
    }
}

// ===========================================================================
// Academics Hub Content (used by NavGraph)
// ===========================================================================

@Composable
fun AcademicsHubContent(
    onNavigateToAttendance: () -> Unit,
    onNavigateToResults: () -> Unit,
    onNavigateToHomework: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToGallery: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {}
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Academics",
            style = MaterialTheme.typography.headlineLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AcademicsMenuItem(
            icon = Icons.Filled.CalendarMonth,
            title = "Attendance",
            subtitle = "View daily and monthly attendance records",
            color = c.success,
            onClick = onNavigateToAttendance
        )

        AcademicsMenuItem(
            icon = Icons.Filled.Grading,
            title = "Results",
            subtitle = "View exam results and report cards",
            color = c.info,
            onClick = onNavigateToResults
        )

        AcademicsMenuItem(
            icon = Icons.Filled.MenuBook,
            title = "Homework",
            subtitle = "View study material and assignments",
            color = c.warning,
            onClick = onNavigateToHomework
        )

        AcademicsMenuItem(
            icon = Icons.Filled.Schedule,
            title = "Timetable",
            subtitle = "View class timetable and schedule",
            color = c.accent,
            onClick = onNavigateToTimetable
        )

        AcademicsMenuItem(
            icon = Icons.Filled.EventNote,
            title = "Events",
            subtitle = "Upcoming school events and holidays",
            color = c.attVacation,
            onClick = onNavigateToEvents
        )

        AcademicsMenuItem(
            icon = Icons.Filled.Collections,
            title = "Gallery",
            subtitle = "School photos and event galleries",
            color = c.coral,
            onClick = onNavigateToGallery
        )

        AcademicsMenuItem(
            icon = Icons.Filled.LocalLibrary,
            title = "Library",
            subtitle = "Borrowed books, fines, and catalogue",
            color = c.purple,
            onClick = onNavigateToLibrary
        )
    }
}

@Composable
private fun AcademicsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = c.textTertiary
        )
    }
}
