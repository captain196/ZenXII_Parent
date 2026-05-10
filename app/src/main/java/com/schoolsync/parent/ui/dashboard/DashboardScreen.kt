package com.schoolsync.parent.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Grading
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.schoolsync.parent.R
import android.widget.Toast
import com.schoolsync.parent.data.model.Event
import com.schoolsync.parent.data.model.Notice
import com.schoolsync.parent.data.repository.firestore.MyTeachersFirestoreRepository
import com.schoolsync.parent.ui.components.bouncyClickable
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.MetricLarge
import com.schoolsync.parent.ui.theme.MetricSmall
import com.schoolsync.parent.ui.theme.Motion
import com.schoolsync.parent.ui.theme.OverlineLabel
import com.schoolsync.parent.ui.theme.glassCard
import com.schoolsync.parent.ui.theme.gradientBackground
import kotlinx.coroutines.launch
import java.util.Calendar

// ───────────────────────────────────────────────────────────────────────────
// Main Dashboard
// ───────────────────────────────────────────────────────────────────────────

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
    onNavigateToPtm: (String) -> Unit = {},
    onNavigateToPtmList: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onNavigateToRedFlags: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToMyTeachers: () -> Unit = {},
    onNavigateToStoryViewer: (String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val c = LocalAppColors.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val defaultStudentName = stringResource(R.string.dashboard_default_student_name)

    // Only refresh on genuine background → foreground transitions.
    // The initial load is kicked off from init{} in the ViewModel,
    // and the VM is preserved across bottom-tab switches via
    // saveState/restoreState in NavGraph — so we don't want to refetch
    // on every tab re-entry (that's what was making the screen stutter).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                userName = uiState.user?.name ?: defaultStudentName,
                schoolName = uiState.schoolName.ifBlank { uiState.user?.schoolDisplayName ?: "" },
                onClose = { scope.launch { drawerState.close() } },
                onAttendance = { scope.launch { drawerState.close() }; onNavigateToAttendance() },
                onResults    = { scope.launch { drawerState.close() }; onNavigateToResults() },
                onFees       = { scope.launch { drawerState.close() }; onNavigateToFees() },
                onTimetable  = { scope.launch { drawerState.close() }; onNavigateToTimetable() },
                onHomework   = { scope.launch { drawerState.close() }; onNavigateToHomework() },
                onNotices    = { scope.launch { drawerState.close() }; onNavigateToNotices() },
                onLeave      = { scope.launch { drawerState.close() }; onNavigateToLeave() },
                onEvents     = { scope.launch { drawerState.close() }; onNavigateToEvents() },
                onGallery    = { scope.launch { drawerState.close() }; onNavigateToGallery() },
                onLibrary    = { scope.launch { drawerState.close() }; onNavigateToLibrary() },
                onMyTeachers = { scope.launch { drawerState.close() }; onNavigateToMyTeachers() },
                onRedFlags   = { scope.launch { drawerState.close() }; onNavigateToRedFlags() },
                onProfile    = { scope.launch { drawerState.close() }; onNavigateToProfile() }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().gradientBackground()) {
            if (uiState.isLoading && uiState.user == null) {
                DashboardShimmer()
            } else {
                val user = uiState.user
                val displayName = user?.name ?: defaultStudentName
                val firstName = displayName.split(" ").firstOrNull() ?: defaultStudentName
                val initials = buildInitials(displayName)
                // className in canonical shape is "Class 10th" (with prefix);
                // RTDB fallbacks may write bare "10th". Strip a leading
                // "Class " so we never render "Class Class 10th" when the
                // source already has the prefix. Same for "Section " prefix.
                val classLabel = listOfNotNull(
                    user?.className?.takeIf { it.isNotBlank() }?.let { raw ->
                        val bare = raw.trim().removePrefix("Class").removePrefix("class").trim()
                        "Class ${bare.ifBlank { raw }}"
                    },
                    user?.section?.takeIf { it.isNotBlank() }?.let { raw ->
                        val bare = raw.trim().removePrefix("Section").removePrefix("section").trim()
                        "Sec ${bare.ifBlank { raw }}"
                    },
                    user?.rollNo?.takeIf { it.isNotBlank() }?.let { "Roll #$it" }
                ).joinToString(" · ")

                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    TopBar(
                        firstName = firstName,
                        initials = initials,
                        classLabel = classLabel,
                        onNotificationClick = onNavigateToNotices,
                        onProfileClick = onNavigateToProfile,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )

                    DashboardPullToRefresh(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.pullRefresh() }
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 🎂 Birthday banner — only appears when the selected
                        // ward's DOB month-day matches today. Shown above
                        // everything else so the parent can't miss it.
                        val __isBday = isWardBirthdayToday(user?.dob)
                        android.util.Log.d("BirthdayBanner",
                            "dob='${user?.dob}' today='${java.text.SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date())}' isBday=$__isBday"
                        )
                        if (__isBday) {
                            item("birthday_banner") {
                                BirthdayBanner(wardName = displayName)
                            }
                        }

                        // Sibling switcher — only rendered when this parent
                        // has more than one child enrolled in the same school.
                        if (uiState.siblings.isNotEmpty()) {
                            item("sibling_switcher") {
                                SiblingSwitcher(
                                    currentStudentId = user?.userId.orEmpty(),
                                    currentName = displayName,
                                    siblings = uiState.siblings,
                                    onSelect = { viewModel.switchToSibling(it) }
                                )
                            }
                        }

                        // Today's Pulse — contextual nudges (fees due, attendance
                        // trend, next exam, homework). This is the hero anchor
                        // below the TopBar: the parent's eye lands here first.
                        item("pulse_strip") {
                            val pulses = buildPulses(
                                pendingFeeAmount = uiState.pendingFeeAmount,
                                feesLoadFailed = uiState.feesLoadFailed,
                                attendancePct = uiState.attendancePercentage,
                                attendanceChange = uiState.attendanceChange,
                                pendingHomework = uiState.pendingHomeworkCount,
                                noticeCount = uiState.recentNotices.size,
                                nextEvent = uiState.upcomingEvents.firstOrNull(),
                                nextPtm = uiState.nextPtm,
                                onFees = onNavigateToFees,
                                onAttendance = onNavigateToAttendance,
                                onHomework = onNavigateToHomework,
                                onNotices = onNavigateToNotices,
                                onEvents = onNavigateToEvents,
                                onEventDetail = onNavigateToEventDetail,
                                onPtm = onNavigateToPtm
                            )
                            PulseStrip(pulses = pulses)
                        }

                        // Class Teacher chip — compact contact card placed
                        // alongside the contextual hero strips so the parent
                        // sees their primary point of contact without scrolling
                        // past KPIs and lists. Hidden entirely when no Active
                        // class teacher is assigned (loader returns null).
                        uiState.classTeacher?.let { entry ->
                            item("class_teacher_card") {
                                ClassTeacherCard(
                                    entry = entry,
                                    subjects = uiState.classTeacherSubjects,
                                    onMessage = onNavigateToMyTeachers
                                )
                            }
                        }

                        // What's Now — shows the current period live so the
                        // parent knows what the child is doing right now.
                        // Hides outside school hours / on holidays.
                        uiState.todaySchedule?.let { day ->
                            if (day.slots.any { !it.isBreak }) {
                                item("whats_now") {
                                    WhatsNowCard(
                                        day = day,
                                        onClick = onNavigateToTimetable
                                    )
                                }
                            }
                        }

                        // Upcoming Events list (with View All). Only renders
                        // when there are events.
                        if (uiState.upcomingEvents.isNotEmpty()) {
                            item("upcoming_events") {
                                UpcomingEventsSection(
                                    events = uiState.upcomingEvents,
                                    onEventClick = onNavigateToEventDetail,
                                    onPtmClick   = onNavigateToPtm,
                                    onViewAll = onNavigateToEvents
                                )
                            }
                        }

                        item("kpi_grid") {
                            KpiGrid(
                                attendancePct = uiState.attendancePercentage,
                                attendanceChange = uiState.attendanceChange,
                                pendingFeeAmount = uiState.pendingFeeAmount,
                                feesLoadFailed = uiState.feesLoadFailed,
                                homeworkCount = uiState.pendingHomeworkCount,
                                noticeCount = uiState.recentNotices.size,
                                onAttendance = onNavigateToAttendance,
                                onFees = onNavigateToFees,
                                onHomework = onNavigateToHomework,
                                onNotices = onNavigateToNotices
                            )
                        }

                        // Today's Homework preview list — actionable items
                        // (subject, title, due) instead of just a count.
                        if (uiState.homeworkPreview.isNotEmpty()) {
                            item("homework_preview") {
                                HomeworkPreviewSection(
                                    items = uiState.homeworkPreview,
                                    onViewAll = onNavigateToHomework
                                )
                            }
                        }

                        // Latest result card — surfaces the most recent
                        // published exam result so parents don't need to dig.
                        uiState.latestResult?.let { result ->
                            item("latest_result") {
                                LatestResultCard(
                                    result = result,
                                    onClick = onNavigateToResults
                                )
                            }
                        }

                        // Shortcuts bento — merges the old Quick Actions +
                        // More rows into one unified 4-col × 2-row grid so
                        // there's a single place to jump from, no artificial
                        // split between "quick" and "more".
                        item("shortcuts_bento") {
                            ShortcutsBento(
                                onFees = onNavigateToFees,
                                onAttendance = onNavigateToAttendance,
                                onResults = onNavigateToResults,
                                onTimetable = onNavigateToTimetable,
                                onLeave = onNavigateToLeave,
                                onEvents = onNavigateToEvents,
                                onLibrary = onNavigateToLibrary,
                                onGallery = onNavigateToGallery,
                                onPtm = onNavigateToPtmList,
                                onRedFlags = onNavigateToRedFlags
                            )
                        }

                        item("footer_spacer") { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    } // DashboardPullToRefresh
                }
            }

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
                    Text(error, color = c.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Pull-to-refresh wrapper for the Dashboard list
// ───────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
private fun DashboardPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = androidx.compose.material.pullrefresh.rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = onRefresh
    )
    val c = LocalAppColors.current
    Box(modifier = Modifier.fillMaxSize().pullRefresh(state)) {
        content()
        androidx.compose.material.pullrefresh.PullRefreshIndicator(
            refreshing = isRefreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = c.bgStart,
            contentColor = c.accent
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Top Bar
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    firstName: String,
    initials: String,
    classLabel: String = "",
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val c = LocalAppColors.current
    val greetingRes = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.dashboard_greeting_morning
        in 12..16 -> R.string.dashboard_greeting_afternoon
        else -> R.string.dashboard_greeting_evening
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center
            ) {
                Pop3DIcon(
                    icon = Icons.Filled.Menu,
                    color = c.accent,
                    bgSize = 40.dp,
                    iconSize = 22.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(stringResource(greetingRes), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Text(firstName, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, fontWeight = FontWeight.Bold)
                if (classLabel.isNotBlank()) {
                    Text(
                        text = classLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(c.glass),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Notifications, stringResource(R.string.cd_notifications), tint = c.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(
                        Brush.linearGradient(listOf(c.accent, c.accentSecondary))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, style = MaterialTheme.typography.labelLarge, color = c.onBanner, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Student ID / Welcome Card
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun StudentIdCard(
    name: String,
    classLabel: String,
    schoolName: String,
    todayStatus: String?
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(c.accent, c.accentSecondary)))
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, null, tint = c.onBanner.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    schoolName.ifBlank { "SchoolSync" },
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onBannerMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                color = c.onBanner,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (classLabel.isNotBlank()) {
                Text(
                    classLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.onBannerMuted,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!todayStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.onBanner.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.EventAvailable, null, tint = c.onBanner, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Today: $todayStatus",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.onBanner,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// KPI Grid (2×2 bento)
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun KpiGrid(
    attendancePct: Float,
    attendanceChange: Float?,
    pendingFeeAmount: Double,
    feesLoadFailed: Boolean,
    homeworkCount: Int,
    noticeCount: Int,
    onAttendance: () -> Unit,
    onFees: () -> Unit,
    onHomework: () -> Unit,
    onNotices: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Overview")
        Row(
            modifier = Modifier.fillMaxWidth().height(170.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AttendanceRingCard(
                percentage = attendancePct,
                change = attendanceChange,
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .bouncyClickable(onClick = onAttendance)
            )
            FeesDueCard(
                amount = pendingFeeAmount,
                loadFailed = feesLoadFailed,
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .bouncyClickable(onClick = onFees)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CountTileCard(
                icon = Icons.AutoMirrored.Filled.Assignment,
                iconColor = Color(0xFFE65100),
                label = "Homework",
                value = homeworkCount.toString(),
                sublabel = if (homeworkCount == 1) "task pending" else "tasks pending",
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .bouncyClickable(onClick = onHomework)
            )
            CountTileCard(
                icon = Icons.Filled.Campaign,
                iconColor = Color(0xFF6A1B9A),
                label = "Notices",
                value = noticeCount.toString(),
                sublabel = if (noticeCount == 1) "new circular" else "new circulars",
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .bouncyClickable(onClick = onNotices)
            )
        }
    }
}

@Composable
private fun ClassTeacherCard(
    entry: MyTeachersFirestoreRepository.TeacherEntry,
    subjects: List<String>,
    onMessage: () -> Unit,
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

    // Two-letter initials (first + last word) — feels more "named" than
    // a single letter, and works for "Vipul Tiwari" → "VT".
    val initials = run {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    // Class/section pulled from assignment, not staff profile.
    val classSection = listOf(assignment.className, assignment.section)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val subjectsLine = subjects.joinToString(" • ")

    // Wide-shallow rectangle: avatar (left) | info column (centre, weight=1) |
    // action icons (right). Outer horizontal padding tightened from 16dp →
    // 10dp so the card stretches further across the screen and reads
    // distinctly as a horizontal strip rather than a square chip.
    //
    // Coloring: indigo palette (was teal accent). Card-local constants so
    // the rest of the dashboard's accent system stays unchanged — only
    // this card uses the indigo treatment so the class-teacher tile reads
    // as visually pinned and distinct from the surrounding tiles.
    val cardPrimary   = Color(0xFF4F46E5)   // indigo-600
    val cardSecondary = Color(0xFF7C3AED)   // violet-600
    val cardShape = RoundedCornerShape(18.dp)
    val cardBrush = Brush.horizontalGradient(
        colors = listOf(
            cardPrimary.copy(alpha = 0.22f),
            cardSecondary.copy(alpha = 0.10f),
            cardPrimary.copy(alpha = 0.04f),
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(cardShape)
            .background(cardBrush, cardShape)
            .border(1.dp, cardPrimary.copy(alpha = 0.28f), cardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar — solid indigo fill so the colored circle anchors the card
        // visually. White initials sit on top for high contrast.
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(cardPrimary),
            contentAlignment = Alignment.Center,
        ) {
            if (photo.isNotBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        Spacer(Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Solid indigo badge — clearer signal of role.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(cardPrimary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "CLASS TEACHER",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.6.sp,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Class / section row — School icon (indigo) + class label.
            if (classSection.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        tint = cardPrimary,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = classSection,
                        fontSize = 11.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Dedicated subjects row — MenuBook icon (violet) + violet
            // "Teaches:" lead-in.
            if (subjectsLine.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = cardSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Teaches: ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = cardSecondary,
                    )
                    Text(
                        text = subjectsLine,
                        fontSize = 11.sp,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Right-side icon-only actions — solid-fill accent circles so they
        // pop against the gradient backdrop. Call only renders when the
        // staff doc has a phone; both honour the runtime active check
        // (loader filters Inactive staff but a deactivation can race with
        // a tap).
        Spacer(Modifier.width(8.dp))
        if (phoneRaw.isNotBlank()) {
            ClassTeacherIconAction(
                icon = Icons.Filled.Call,
                accentBg = cardPrimary,
                iconTint = Color.White,
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
            Spacer(Modifier.width(6.dp))
        }
        ClassTeacherIconAction(
            icon = Icons.Filled.Chat,
            accentBg = cardSecondary,
            iconTint = Color.White,
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

/** Compact filled-circle icon button for the class-teacher card. */
@Composable
private fun ClassTeacherIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentBg: Color,
    iconTint: Color,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(accentBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AttendanceRingCard(
    percentage: Float,
    change: Float?,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val whole = percentage.toInt()
    val decimal = ".${((percentage - whole) * 10).toInt().coerceAtLeast(0)}%"
    val animatedSweep by animateFloatAsState(
        targetValue = percentage / 100f * 360f,
        animationSpec = Motion.slow(),
        label = "attendanceSweep"
    )
    Column(
        modifier = modifier.glassCard(20.dp).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("ATTENDANCE", style = OverlineLabel, color = c.textTertiary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    drawArc(
                        color = c.glassBorder,
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(c.accent, c.success, c.accent)),
                        startAngle = -90f, sweepAngle = animatedSweep, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "$whole%",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = c.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("This month", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                Text("$whole$decimal", style = MetricSmall, color = c.textPrimary)
                if (change != null) {
                    val isRise = change >= 0f
                    val arrow = if (isRise) "▲" else "▼"
                    val color = if (isRise) c.success else c.error
                    Text(
                        "$arrow ${"%.1f".format(kotlin.math.abs(change))}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeesDueCard(
    amount: Double,
    loadFailed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    val isOverdue = amount > 0.0
    // When the fetch fails we render a warning state instead of the
    // misleading green "All cleared" — parents deserve to know the
    // number never actually loaded.
    val accent = when {
        loadFailed -> c.warning
        isOverdue -> c.error
        else -> c.success
    }
    Column(
        modifier = modifier.glassCard(20.dp).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CreditCard, null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                when {
                    loadFailed -> "FEES"
                    isOverdue -> "FEES DUE"
                    else -> "FEES"
                },
                style = OverlineLabel,
                color = c.textTertiary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (loadFailed) {
            Text(
                "Couldn't load",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "Rs. ",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "%,.0f".format(amount),
                    style = MetricLarge.copy(fontSize = 26.sp),
                    color = c.textPrimary
                )
            }
        }
        Text(
            when {
                loadFailed -> "Tap to retry"
                isOverdue -> "Tap to pay"
                else -> "All cleared"
            },
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CountTileCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    val c = LocalAppColors.current
    Row(
        modifier = modifier.glassCard(16.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pop3DIcon(
            icon = icon,
            color = iconColor,
            bgSize = 44.dp,
            iconSize = 22.dp,
            shape = RoundedCornerShape(13.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label.uppercase(), style = OverlineLabel, color = c.textTertiary)
            Text(value, style = MetricLarge, color = c.textPrimary)
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Quick Actions
// ───────────────────────────────────────────────────────────────────────────

private data class QuickAction(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun ShortcutsBento(
    onFees: () -> Unit,
    onAttendance: () -> Unit,
    onResults: () -> Unit,
    onTimetable: () -> Unit,
    onLeave: () -> Unit,
    onEvents: () -> Unit,
    onLibrary: () -> Unit,
    onGallery: () -> Unit,
    onPtm: () -> Unit = {},
    onRedFlags: () -> Unit = {}
) {
    val c = LocalAppColors.current
    // Ordered by parent-value: daily → weekly → occasional.
    // Red Flags is placed up-front (red icon) since it surfaces urgent
    // teacher-raised concerns parents need to act on quickly.
    val actions = listOf(
        QuickAction(Icons.Filled.Flag,                                Color(0xFFD32F2F),  "Red Flags",   onRedFlags),
        QuickAction(Icons.Filled.Payment,                             c.accent,           "Pay Fees",    onFees),
        QuickAction(Icons.Filled.CalendarMonth,                       Color(0xFF1565C0),  "Attendance",  onAttendance),
        QuickAction(Icons.AutoMirrored.Filled.Grading,                Color(0xFF2E7D32),  "Results",     onResults),
        QuickAction(Icons.Filled.Schedule,                            Color(0xFFC62828),  "Timetable",   onTimetable),
        QuickAction(Icons.AutoMirrored.Filled.EventNote,              Color(0xFF00838F),  "Leave",       onLeave),
        QuickAction(Icons.Filled.Event,                               Color(0xFFAD1457),  "Events",      onEvents),
        QuickAction(Icons.Filled.EventAvailable,                      Color(0xFF6A1B9A),  "PTM",         onPtm),
        QuickAction(Icons.AutoMirrored.Filled.MenuBook,               Color(0xFF4527A0),  "Library",     onLibrary),
        QuickAction(Icons.Filled.Collections,                         Color(0xFFEF6C00),  "Gallery",     onGallery)
    )
    Column {
        SectionHeader(title = "Shortcuts")
        Spacer(modifier = Modifier.height(12.dp))
        // 4 × 2 bento — one unified tile size so nothing feels secondary.
        actions.chunked(4).forEachIndexed { rowIdx, row ->
            if (rowIdx > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { action ->
                    QuickActionTile(action, modifier = Modifier.weight(1f))
                }
                // Pad incomplete final row so tiles keep their width.
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: QuickAction, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .glassCard(14.dp)
            .clickable(onClick = action.onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Pop3DIcon(
            icon = action.icon,
            color = action.color,
            bgSize = 48.dp,
            iconSize = 24.dp,
            shape = RoundedCornerShape(15.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * PhonePe-style 3D icon tile: gradient squircle background, drop shadow
 * for depth, soft inner highlight, white icon. Shared by QuickActionTile
 * and ShortcutTile so both rows feel like part of the same icon system.
 */
@Composable
private fun Pop3DIcon(
    icon: ImageVector,
    color: Color,
    bgSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(14.dp)
) {
    val top = Color(
        red = (color.red + (1f - color.red) * 0.25f).coerceIn(0f, 1f),
        green = (color.green + (1f - color.green) * 0.25f).coerceIn(0f, 1f),
        blue = (color.blue + (1f - color.blue) * 0.25f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val bottom = Color(
        red = (color.red * 0.78f).coerceIn(0f, 1f),
        green = (color.green * 0.78f).coerceIn(0f, 1f),
        blue = (color.blue * 0.78f).coerceIn(0f, 1f),
        alpha = 1f
    )
    Box(
        modifier = Modifier
            .size(bgSize)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = color,
                spotColor = color
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        // Subtle top highlight — mimics the glossy PhonePe sheen.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        endY = bgSize.value * 1.3f
                    )
                )
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Recent Notices
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentNoticesSection(
    notices: List<Notice>,
    onViewAll: () -> Unit
) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Recent Notices")
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View All", style = MaterialTheme.typography.labelLarge, color = c.accent, fontWeight = FontWeight.Medium)
                Icon(Icons.Filled.ChevronRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (notices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).glassCard(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Campaign, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("No recent notices", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                notices.take(3).forEach { notice ->
                    NoticeRow(notice = notice, onClick = onViewAll)
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: Notice, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val accent = when (notice.priority.lowercase()) {
        "high", "urgent" -> c.error
        "medium" -> c.warning
        else -> c.accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Campaign, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                notice.title.ifBlank { "(Untitled)" },
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = listOfNotNull(
                notice.author.takeIf { it.isNotBlank() },
                notice.date.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Upcoming Events (horizontal scroller)
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun UpcomingEventsSection(
    events: List<Event>,
    onEventClick: (String) -> Unit,
    onPtmClick: (String) -> Unit = onEventClick,
    onViewAll: () -> Unit
) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Upcoming Events")
            if (events.isNotEmpty()) {
                Row(
                    modifier = Modifier.clickable(onClick = onViewAll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("View All", style = MaterialTheme.typography.labelLarge, color = c.accent, fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.ChevronRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).glassCard(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Event, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("No upcoming events", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                events.take(5).forEach { event ->
                    val isPtm = event.category.equals("ptm", ignoreCase = true)
                    EventBannerCard(
                        event = event,
                        onClick = {
                            if (isPtm) onPtmClick(event.eventId)
                            else        onEventClick(event.eventId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventBannerCard(event: Event, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val category = event.category.lowercase()
    val (gradStart, gradEnd) = when (category) {
        "cultural" -> c.banner1Start to c.banner1End
        "sports" -> c.banner2Start to c.banner2End
        "academic" -> c.banner3Start to c.banner3End
        "exam" -> c.error.copy(alpha = 0.8f) to c.error.copy(alpha = 0.5f)
        "holiday" -> c.info.copy(alpha = 0.7f) to c.info.copy(alpha = 0.4f)
        // PTM rows surface here from synthesised events. Dark-blue
        // gradient distinguishes them visually from school events.
        "ptm" -> Color(0xFF1565C0) to Color(0xFF1565C0).copy(alpha = 0.6f)
        else -> c.accent to c.accentSecondary
    }
    val emoji = when (category) {
        "cultural" -> "\uD83C\uDFAD"; "sports" -> "\u26BD"
        "academic" -> "\uD83D\uDCDA"; "exam" -> "\uD83D\uDCDD"
        "holiday" -> "\uD83C\uDF89"
        "ptm"     -> "\uD83D\uDC65"; else -> "\uD83D\uDCC5"
    }
    val firstImage = event.mediaUrls.firstOrNull { it.type == "image" }?.url
    val daysUntil = computeDaysUntil(event.startDate)
    val dateLabel = formatEventDate(event.startDate).ifBlank { event.startDate }

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(140.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = gradStart,
                spotColor = gradStart
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(gradStart, gradEnd)))
            .clickable(onClick = onClick)
    ) {
        if (firstImage != null) {
            AsyncImage(
                model = firstImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.75f)))
                )
            )
        }
        // Soft top-left highlight — adds depth, same as hero card.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        endY = 140f
                    )
                )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.onBanner.copy(alpha = 0.24f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        event.category.ifBlank { "Event" }.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onBanner,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (daysUntil != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.92f))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            when {
                                daysUntil == 0L -> "Today"
                                daysUntil == 1L -> "Tomorrow"
                                daysUntil < 0L -> "Ongoing"
                                else -> "In $daysUntil days"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = gradStart,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(emoji, fontSize = 20.sp)
                }
            }
            Column {
                Text(
                    event.title.ifBlank { "School Event" },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onBanner,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = c.onBannerMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onBannerMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.location.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = c.onBannerMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            event.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = c.onBannerMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

/** Days between today and the given ISO date (yyyy-MM-dd). Null on parse failure. */
private fun computeDaysUntil(iso: String): Long? {
    if (iso.isBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        parser.isLenient = false
        val target = parser.parse(iso) ?: return null
        val targetMillis = target.time
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        (targetMillis - now) / (1000L * 60L * 60L * 24L)
    } catch (_: Exception) { null }
}

// ───────────────────────────────────────────────────────────────────────────
// Today's Pulse — contextual intelligence hero strip
// ───────────────────────────────────────────────────────────────────────────

/**
 * One contextual nudge the parent should see right now. Built from current
 * UI state (fees, attendance, homework, next event) and navigates to the
 * relevant screen on tap. Priority is assigned in [buildPulses].
 */
private data class Pulse(
    val kind: PulseKind,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

private enum class PulseKind { FEES, PTM, ATTENDANCE, HOMEWORK, EVENT, NOTICE, CELEBRATE }

@Composable
private fun buildPulses(
    pendingFeeAmount: Double,
    feesLoadFailed: Boolean,
    attendancePct: Float,
    attendanceChange: Float?,
    pendingHomework: Int,
    noticeCount: Int,
    nextEvent: Event?,
    nextPtm: com.schoolsync.parent.data.model.firestore.PtmEventDoc? = null,
    onFees: () -> Unit = {},
    onAttendance: () -> Unit = {},
    onHomework: () -> Unit = {},
    onNotices: () -> Unit = {},
    onEvents: () -> Unit = {},
    onEventDetail: (String) -> Unit = {},
    onPtm: (String) -> Unit = {}
): List<Pulse> {
    val c = LocalAppColors.current
    val out = mutableListOf<Pulse>()

    // 1. Fees — highest priority when any amount is due.
    if (!feesLoadFailed && pendingFeeAmount > 0.0) {
        val amt = formatRupees(pendingFeeAmount)
        out += Pulse(
            kind = PulseKind.FEES,
            title = "Fees due: $amt",
            subtitle = "Tap to pay securely",
            icon = Icons.Filled.CreditCard,
            tint = Color(0xFFD84315),
            onClick = onFees
        )
    }

    // 2. PTM — show whenever an upcoming PTM is found, since RSVPs need
    //    parent action regardless of date proximity.
    if (nextPtm != null) {
        val days = daysUntilIso(nextPtm.date)
        val timing = when {
            days == null -> nextPtm.date
            days <= 0L   -> "Today"
            days == 1L   -> "Tomorrow"
            days <= 7L   -> "In $days days"
            else         -> formatPtmShortDate(nextPtm.date)
        }
        out += Pulse(
            kind = PulseKind.PTM,
            title = "PTM: $timing",
            subtitle = "Tap to RSVP \u00B7 ${nextPtm.title.ifBlank { "Parent-Teacher Meeting" }}",
            icon = Icons.Filled.EventAvailable,
            tint = Color(0xFF1565C0),
            onClick = { onPtm(nextPtm.ptmEventId.ifBlank { nextPtm.id }) }
        )
    }

    // 3. Next event — show when it's in the next 3 days or ongoing.
    if (nextEvent != null) {
        val days = daysUntilIso(nextEvent.startDate)
        if (days != null && days <= 3L) {
            val when_ = when {
                days <= 0L -> "Today"
                days == 1L -> "Tomorrow"
                else -> "In $days days"
            }
            out += Pulse(
                kind = PulseKind.EVENT,
                title = "${nextEvent.title.ifBlank { "School event" }} · $when_",
                subtitle = nextEvent.location.ifBlank { "Tap for details" },
                icon = Icons.Filled.Event,
                tint = Color(0xFFAD1457),
                onClick = {
                    if (nextEvent.eventId.isNotBlank()) onEventDetail(nextEvent.eventId)
                    else onEvents()
                }
            )
        }
    }

    // 3. Attendance — warn if below 75%, celebrate if ≥ 92%.
    if (attendancePct > 0f) {
        when {
            attendancePct < 75f -> out += Pulse(
                kind = PulseKind.ATTENDANCE,
                title = "Attendance low: ${attendancePct.toInt()}%",
                subtitle = "Below 75% — schools often require 75%+",
                icon = Icons.Filled.Warning,
                tint = Color(0xFFEF6C00),
                onClick = onAttendance
            )
            attendanceChange != null && attendanceChange < -3f -> out += Pulse(
                kind = PulseKind.ATTENDANCE,
                title = "Attendance dropping",
                subtitle = "Down ${"%.1f".format(kotlin.math.abs(attendanceChange))}% this month",
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                tint = Color(0xFFEF6C00),
                onClick = onAttendance
            )
            attendancePct >= 92f -> out += Pulse(
                kind = PulseKind.CELEBRATE,
                title = "Great attendance!",
                subtitle = "${attendancePct.toInt()}% this month · keep it up",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                tint = Color(0xFF2E7D32),
                onClick = onAttendance
            )
        }
    }

    // 4. Homework — prompt when there are pending tasks.
    if (pendingHomework > 0) {
        out += Pulse(
            kind = PulseKind.HOMEWORK,
            title = "$pendingHomework homework pending",
            subtitle = "Review with your child today",
            icon = Icons.AutoMirrored.Filled.Assignment,
            tint = Color(0xFFE65100),
            onClick = onHomework
        )
    }

    // 5. Notices — lowest priority, only if unread.
    if (noticeCount > 0) {
        out += Pulse(
            kind = PulseKind.NOTICE,
            title = "$noticeCount new notice${if (noticeCount == 1) "" else "s"}",
            subtitle = "From school · tap to read",
            icon = Icons.Filled.Campaign,
            tint = Color(0xFF6A1B9A),
            onClick = onNotices
        )
    }

    // Fallback — everything is calm.
    if (out.isEmpty()) {
        out += Pulse(
            kind = PulseKind.CELEBRATE,
            title = "All caught up",
            subtitle = "No pending items today",
            icon = Icons.Filled.Celebration,
            tint = c.success,
            onClick = {}
        )
    }
    return out
}

@Composable
private fun PulseStrip(pulses: List<Pulse>) {
    Column {
        SectionHeader(title = "Today's Pulse")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pulses.forEach { pulse ->
                PulseCard(pulse = pulse)
            }
        }
    }
}

@Composable
private fun PulseCard(pulse: Pulse) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier
            .width(260.dp)
            .glassCard(16.dp)
            .clickable(onClick = pulse.onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pop3DIcon(
            icon = pulse.icon,
            color = pulse.tint,
            bgSize = 42.dp,
            iconSize = 22.dp,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                pulse.title,
                style = MaterialTheme.typography.labelLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                pulse.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatPtmShortDate(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val out    = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault())
        parser.parse(iso)?.let { out.format(it) } ?: iso
    } catch (_: Exception) { iso }
}

/** "₹5,000" / "₹5,00,000" (Indian digit grouping). */
private fun formatRupees(amount: Double): String {
    val intAmt = amount.toLong()
    val nf = java.text.NumberFormat.getInstance(java.util.Locale("en", "IN"))
    return "\u20B9${nf.format(intAmt)}"
}

/** Days between today (local) and the given ISO yyyy-MM-dd. Null on parse failure. */
private fun daysUntilIso(iso: String): Long? {
    if (iso.isBlank()) return null
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        parser.isLenient = false
        val target = parser.parse(iso) ?: return null
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        (target.time - now) / (1000L * 60L * 60L * 24L)
    } catch (_: Exception) { null }
}

// ───────────────────────────────────────────────────────────────────────────
// What's Now — live current period
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun WhatsNowCard(
    day: com.schoolsync.parent.data.model.DayTimetable,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val nowMin = currentMinuteOfDay()
    val classSlots = day.slots.filter { !it.isBreak && it.subject.isNotBlank() }
    val current = classSlots.firstOrNull { slot ->
        val s = parseHHmm(slot.startTime); val e = parseHHmm(slot.endTime)
        s != null && e != null && nowMin in s until e
    }
    val next = classSlots.firstOrNull { slot ->
        val s = parseHHmm(slot.startTime)
        s != null && s > nowMin
    }
    val (label, slot, accent) = when {
        current != null -> Triple("NOW", current, c.success)
        next != null    -> Triple("UP NEXT", next, c.accent)
        else            -> return // school day done — no widget
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(18.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pulsing dot for "now playing" state.
            if (label == "NOW") {
                val infiniteTransition = rememberInfiniteTransition(label = "now-pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                    label = "pulse"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer(alpha = pulseAlpha)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                label,
                style = OverlineLabel,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (slot.time.isNotBlank()) {
                Text(slot.time, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pop3DIcon(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                color = accent,
                bgSize = 44.dp,
                iconSize = 22.dp,
                shape = RoundedCornerShape(13.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    slot.subject,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = listOfNotNull(
                    slot.teacher.takeIf { it.isNotBlank() },
                    slot.room.takeIf { it.isNotBlank() }?.let { "Room $it" }
                ).joinToString(" \u00B7 ")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun parseHHmm(s: String): Int? {
    val str = s.trim().ifBlank { return null }
    return try {
        // Accept "8:00", "08:00", "8:00 AM", "08:00am", "14:30"
        val cleaned = str.uppercase()
        val ampm = when {
            cleaned.endsWith("AM") -> "AM"
            cleaned.endsWith("PM") -> "PM"
            else -> ""
        }
        val core = cleaned.removeSuffix("AM").removeSuffix("PM").trim()
        val parts = core.split(":")
        if (parts.size < 2) return null
        var h = parts[0].trim().toInt()
        val m = parts[1].trim().toInt()
        if (ampm == "PM" && h < 12) h += 12
        if (ampm == "AM" && h == 12) h = 0
        h * 60 + m
    } catch (_: Exception) { null }
}

private fun currentMinuteOfDay(): Int {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
}

// ───────────────────────────────────────────────────────────────────────────
// Today's Homework — actionable preview list
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeworkPreviewSection(
    items: List<com.schoolsync.parent.data.model.firestore.HomeworkDoc>,
    onViewAll: () -> Unit
) {
    val c = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Today's Homework")
            Row(
                modifier = Modifier.clickable(onClick = onViewAll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("View All", style = MaterialTheme.typography.labelLarge, color = c.accent, fontWeight = FontWeight.Medium)
                Icon(Icons.Filled.ChevronRight, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.take(3).forEach { hw ->
                HomeworkRow(hw = hw, onClick = onViewAll)
            }
        }
    }
}

@Composable
private fun HomeworkRow(
    hw: com.schoolsync.parent.data.model.firestore.HomeworkDoc,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val tint = subjectColor(hw.subject)
    val due = formatHomeworkDue(hw.dueDate)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(12.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pop3DIcon(
            icon = Icons.AutoMirrored.Filled.Assignment,
            color = tint,
            bgSize = 38.dp,
            iconSize = 20.dp,
            shape = RoundedCornerShape(11.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hw.subject.isNotBlank()) {
                    Text(
                        hw.subject.uppercase(),
                        style = OverlineLabel,
                        color = tint,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (due.label.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(due.tint.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            due.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = due.tint,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                hw.title.ifBlank { "(Untitled)" },
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hw.teacherName.isNotBlank()) {
                Text(
                    hw.teacherName,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = c.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class DueLabel(val label: String, val tint: Color)

@Composable
private fun formatHomeworkDue(dueDate: String): DueLabel {
    val c = LocalAppColors.current
    if (dueDate.isBlank()) return DueLabel("", c.textTertiary)
    val days = daysUntilIso(dueDate) ?: return DueLabel("Due $dueDate", c.textTertiary)
    return when {
        days < 0L  -> DueLabel("Overdue", c.error)
        days == 0L -> DueLabel("Due today", c.error)
        days == 1L -> DueLabel("Due tomorrow", c.warning)
        days <= 7L -> DueLabel("Due in $days days", c.warning)
        else       -> DueLabel("Due in $days days", c.textTertiary)
    }
}

/** Stable subject → color mapping so the same subject always tints the same. */
@Composable
private fun subjectColor(subject: String): Color {
    val c = LocalAppColors.current
    val palette = listOf(
        Color(0xFF1565C0),  // blue
        Color(0xFF2E7D32),  // green
        Color(0xFFEF6C00),  // orange
        Color(0xFF6A1B9A),  // purple
        Color(0xFFC62828),  // red
        Color(0xFF00838F),  // teal
        Color(0xFFAD1457),  // pink
        Color(0xFF4527A0)   // indigo
    )
    if (subject.isBlank()) return c.accent
    val hash = subject.hashCode().let { if (it < 0) -it else it }
    return palette[hash % palette.size]
}

// ───────────────────────────────────────────────────────────────────────────
// Mini Attendance Calendar — current month grid
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun AttendanceCalendarStrip(
    summary: com.schoolsync.parent.data.model.firestore.AttendanceSummaryDoc,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val cal = java.util.Calendar.getInstance()
    val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val dayWise = summary.dayWise
    val monthLabel = summary.monthLabel.ifBlank { summary.month }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ATTENDANCE \u00B7 $monthLabel".uppercase(), style = OverlineLabel, color = c.textTertiary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${"%.0f".format(summary.percentage)}% \u00B7 ${summary.present} present, ${summary.absent} absent",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Week-stride 7-column mini-grid: each row holds 7 day cells.
        val cellsPerRow = 7
        for (rowStart in 1..daysInMonth step cellsPerRow) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (offset in 0 until cellsPerRow) {
                    val dayNum = rowStart + offset
                    if (dayNum > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val code = dayWise.getOrNull(dayNum - 1) ?: ' '
                        val (cellColor, _) = attendanceCellStyle(code, c)
                        val isToday = dayNum == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cellColor)
                                .then(
                                    if (isToday) Modifier.border(
                                        1.5.dp,
                                        c.accent,
                                        RoundedCornerShape(6.dp)
                                    ) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dayNum.toString(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = attendanceCellTextColor(code, c, isToday)
                            )
                        }
                    }
                }
            }
            if (rowStart + cellsPerRow <= daysInMonth) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CalendarLegend(color = c.success.copy(alpha = 0.55f), label = "Present")
            CalendarLegend(color = c.error.copy(alpha = 0.55f), label = "Absent")
            CalendarLegend(color = c.warning.copy(alpha = 0.55f), label = "Tardy")
            CalendarLegend(color = c.info.copy(alpha = 0.55f), label = "Leave")
        }
    }
}

@Composable
private fun CalendarLegend(color: Color, label: String) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
}

private fun attendanceCellStyle(code: Char, c: com.schoolsync.parent.ui.theme.AppColors): Pair<Color, Color> {
    val (bg, fg) = when (code) {
        'P' -> c.success.copy(alpha = 0.55f) to Color.White
        'A' -> c.error.copy(alpha = 0.55f) to Color.White
        'T' -> c.warning.copy(alpha = 0.55f) to Color.White
        'L' -> c.info.copy(alpha = 0.55f) to Color.White
        'H' -> c.glass to c.textTertiary
        'V' -> c.glass to c.textTertiary
        else -> c.glass.copy(alpha = 0.5f) to c.textTertiary
    }
    return bg to fg
}

@Composable
private fun attendanceCellTextColor(
    code: Char,
    c: com.schoolsync.parent.ui.theme.AppColors,
    isToday: Boolean
): Color {
    return when (code) {
        'P', 'A', 'T', 'L' -> Color.White
        else -> if (isToday) c.accent else c.textTertiary
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Latest Result — most recent published exam result
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun LatestResultCard(
    result: com.schoolsync.parent.data.model.firestore.ResultDoc,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    val pct = result.percentage
    val tint = when {
        pct >= 80 -> c.success
        pct >= 60 -> c.accent
        pct >= 40 -> c.warning
        else      -> c.error
    }
    val passed = result.passFail.equals("Pass", ignoreCase = true) || (pct >= 33 && result.passFail.isBlank())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LATEST RESULT", style = OverlineLabel, color = c.textTertiary)
            if (result.rank > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(c.accentBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "Rank #${result.rank}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Percentage chip
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = tint, spotColor = tint)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(
                                    red = (tint.red + (1f - tint.red) * 0.25f).coerceIn(0f, 1f),
                                    green = (tint.green + (1f - tint.green) * 0.25f).coerceIn(0f, 1f),
                                    blue = (tint.blue + (1f - tint.blue) * 0.25f).coerceIn(0f, 1f),
                                    alpha = 1f
                                ),
                                Color(
                                    red = (tint.red * 0.78f).coerceIn(0f, 1f),
                                    green = (tint.green * 0.78f).coerceIn(0f, 1f),
                                    blue = (tint.blue * 0.78f).coerceIn(0f, 1f),
                                    alpha = 1f
                                )
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${pct.toInt()}%",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.examName.ifBlank { "Recent Exam" },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subjectCount = result.subjects.size
                val parts = listOfNotNull(
                    "${result.totalMarks.toInt()}/${result.maxMarks.toInt()} marks",
                    if (result.grade.isNotBlank()) "Grade ${result.grade}" else null,
                    if (subjectCount > 0) "$subjectCount subjects" else null
                )
                Text(
                    parts.joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (passed) c.success.copy(alpha = 0.18f) else c.error.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (passed) "PASSED" else (result.passFail.ifBlank { "REVIEW" }).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (passed) c.success else c.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Shared Components
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    val c = LocalAppColors.current
    Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
}

// ───────────────────────────────────────────────────────────────────────────
// Shimmer
// ───────────────────────────────────────────────────────────────────────────

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
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(c.shimmerBase.copy(alpha = shimmerAlpha)))
        Row(modifier = Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(c.shimmerBase.copy(alpha = shimmerAlpha)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(c.shimmerBase.copy(alpha = shimmerAlpha)))
        }
        Row(modifier = Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.shimmerBase.copy(alpha = shimmerAlpha)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.shimmerBase.copy(alpha = shimmerAlpha)))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Box(modifier = Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp)).background(c.shimmerBase.copy(alpha = shimmerAlpha)))
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Drawer
// ───────────────────────────────────────────────────────────────────────────

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
    onLeave: () -> Unit,
    onEvents: () -> Unit,
    onGallery: () -> Unit,
    onLibrary: () -> Unit,
    onMyTeachers: () -> Unit,
    onRedFlags: () -> Unit,
    onProfile: () -> Unit
) {
    val c = LocalAppColors.current
    ModalDrawerSheet(
        modifier = Modifier.width(284.dp),
        drawerContainerColor = c.bgStart,
        drawerContentColor = c.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(c.accent, c.accentSecondary)))
                .padding(24.dp)
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(c.onBanner.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text(buildInitials(userName), style = MaterialTheme.typography.titleLarge, color = c.onBanner, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(userName, style = MaterialTheme.typography.titleLarge, color = c.onBanner, fontWeight = FontWeight.Bold)
            if (schoolName.isNotBlank()) {
                Text(schoolName, style = MaterialTheme.typography.labelMedium, color = c.onBannerMuted)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val items = listOf(
            Triple(Icons.Filled.CalendarMonth, stringResource(R.string.drawer_attendance), onAttendance),
            Triple(Icons.AutoMirrored.Filled.MenuBook,      stringResource(R.string.drawer_results),    onResults),
            Triple(Icons.Filled.CreditCard,    stringResource(R.string.drawer_fees),       onFees),
            Triple(Icons.Filled.Schedule,      stringResource(R.string.drawer_timetable),  onTimetable),
            Triple(Icons.AutoMirrored.Filled.Assignment,    stringResource(R.string.drawer_homework),   onHomework),
            Triple(Icons.Filled.Campaign,      stringResource(R.string.drawer_notices),    onNotices),
            Triple(Icons.AutoMirrored.Filled.EventNote,     "Apply Leave",                              onLeave),
            Triple(Icons.Filled.Event,         stringResource(R.string.drawer_events),     onEvents),
            Triple(Icons.Filled.PhotoLibrary,  stringResource(R.string.drawer_gallery),    onGallery),
            Triple(Icons.Filled.LocalLibrary,  stringResource(R.string.drawer_library),    onLibrary),
            Triple(Icons.Filled.School,        stringResource(R.string.drawer_my_teachers),onMyTeachers),
            Triple(Icons.Filled.Flag,          stringResource(R.string.drawer_alerts),     onRedFlags),
            Triple(Icons.Filled.Person,        stringResource(R.string.drawer_profile),    onProfile)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { (icon, label, onClick) ->
                NavigationDrawerItem(
                    icon = { Icon(icon, label, tint = c.textSecondary, modifier = Modifier.size(22.dp)) },
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
            Spacer(modifier = Modifier.height(12.dp))
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

// ═══════════════════════════════════════════════════════════════════════════
// Academics Hub Content (unchanged; consumed by NavGraph)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun AcademicsHubContent(
    onNavigateToAttendance: () -> Unit,
    onNavigateToResults: () -> Unit,
    onNavigateToHomework: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToGallery: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToPtmList: () -> Unit = {},
    onNavigateToLessons: () -> Unit = {}
) {
    // Wrap the menu items in a vertical scroll so the list always reaches
    // every entry on smaller phones (e.g. anything below 720dp tall) — adding
    // PTM pushed the bottom item off-screen on a few devices.
    val scrollState = rememberScrollState()
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .gradientBackground()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.academics_title),
            style = MaterialTheme.typography.headlineLarge,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AcademicsMenuItem(Icons.Filled.CalendarMonth, stringResource(R.string.drawer_attendance), stringResource(R.string.academics_attendance_subtitle), c.success, onNavigateToAttendance)
        AcademicsMenuItem(Icons.AutoMirrored.Filled.Grading,       stringResource(R.string.drawer_results),    stringResource(R.string.academics_results_subtitle),    c.info,    onNavigateToResults)
        AcademicsMenuItem(Icons.AutoMirrored.Filled.MenuBook,      stringResource(R.string.drawer_homework),   stringResource(R.string.academics_homework_subtitle),   c.warning, onNavigateToHomework)
        AcademicsMenuItem(Icons.Filled.Schedule,      stringResource(R.string.drawer_timetable),  stringResource(R.string.academics_timetable_subtitle),  c.accent,  onNavigateToTimetable)
        AcademicsMenuItem(
            Icons.AutoMirrored.Filled.EventNote,
            "Daily Lessons",
            "Today's topics, notes & subject progress",
            c.teal,
            onNavigateToLessons
        )
        AcademicsMenuItem(Icons.AutoMirrored.Filled.EventNote,     stringResource(R.string.drawer_events),     stringResource(R.string.academics_events_subtitle),     c.attVacation, onNavigateToEvents)
        AcademicsMenuItem(Icons.Filled.Collections,   stringResource(R.string.drawer_gallery),    stringResource(R.string.academics_gallery_subtitle),    c.coral,   onNavigateToGallery)
        AcademicsMenuItem(Icons.Filled.LocalLibrary,  stringResource(R.string.drawer_library),    stringResource(R.string.academics_library_subtitle),    c.purple,  onNavigateToLibrary)
        AcademicsMenuItem(
            Icons.Filled.EventAvailable,
            "Parent-Teacher Meetings",
            "View upcoming and past PTMs · RSVP",
            Color(0xFF1565C0),
            onNavigateToPtmList
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
        modifier = Modifier.fillMaxWidth().glassCard(14.dp).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = c.textTertiary)
    }
}

// ───────────────────────────────────────────────────────────────────────────
// Sibling switcher — chip row + dropdown, only visible when this parent
// has multiple children enrolled. Tapping a sibling swaps the active
// student; every screen reading tokenManager.user auto-updates.
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SiblingSwitcher(
    currentStudentId: String,
    currentName: String,
    siblings: List<SiblingSummary>,
    onSelect: (String) -> Unit
) {
    val c = LocalAppColors.current
    val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Box {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.glass)
                .clickable { expanded.value = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Viewing",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textTertiary
                )
                Text(
                    currentName,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(c.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${siblings.size + 1} kids",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.accent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    null,
                    tint = c.accent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            modifier = Modifier.background(c.bgStart)
        ) {
            // Current student (first, disabled state)
            androidx.compose.material3.DropdownMenuItem(
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            currentName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.accent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Currently viewing",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.accent.copy(alpha = 0.7f)
                        )
                    }
                },
                onClick = { expanded.value = false },
                leadingIcon = {
                    Icon(Icons.Filled.Person, null, tint = c.accent)
                }
            )
            siblings.forEach { sib ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(
                                sib.name.ifBlank { sib.studentId },
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            val classLine = listOfNotNull(
                                sib.className.takeIf { it.isNotBlank() }?.let { raw ->
                                    val bare = raw.trim().removePrefix("Class").removePrefix("class").trim()
                                    "Class ${bare.ifBlank { raw }}"
                                },
                                sib.section.takeIf { it.isNotBlank() }?.let { raw ->
                                    val bare = raw.trim().removePrefix("Section").removePrefix("section").trim()
                                    "Sec ${bare.ifBlank { raw }}"
                                }
                            ).joinToString(" · ")
                            if (classLine.isNotBlank()) {
                                Text(
                                    classLine,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.textTertiary
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded.value = false
                        if (sib.studentId != currentStudentId) onSelect(sib.studentId)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Person,
                            null,
                            tint = c.textTertiary
                        )
                    }
                )
            }
        }
    }
}

/**
 * Hero-styled upcoming-event card. Replaces the old Student ID card in the
 * top slot — same visual weight (brand gradient, tall card, prominent title)
 * but advertises the next school event instead of the ward's basic info.
 * Tapping opens the event detail screen.
 */
@Composable
private fun UpcomingEventHeroCard(
    event: com.schoolsync.parent.data.model.Event,
    onClick: () -> Unit
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(c.accent, c.accentSecondary)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Event,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "UPCOMING EVENT",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.title.ifBlank { "School Event" },
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatEventDate(event.startDate),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp
                )
                if (event.location.isNotBlank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.location,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** "2026-04-27" → "Mon, 27 Apr". Returns the raw input on parse failure. */
private fun formatEventDate(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val parser = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val formatter = java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault())
        parser.parse(iso)?.let { formatter.format(it) } ?: iso
    } catch (e: Exception) {
        iso
    }
}

/**
 * Returns true when the given DOB string's month-day matches today (device TZ).
 * Accepts `YYYY-MM-DD`, `DD/MM/YYYY`, `DD-MM-YYYY`, or ISO timestamp prefix.
 */
private fun isWardBirthdayToday(dob: String?): Boolean {
    if (dob.isNullOrBlank()) return false
    val cal = java.util.Calendar.getInstance()
    val todayMd = String.format(
        "%02d-%02d",
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
    // YYYY-MM-DD (ISO) or ISO timestamp starting that way
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(dob)?.let {
        val (_, mm, dd) = it.destructured
        return "$mm-$dd" == todayMd
    }
    // DD/MM/YYYY or DD-MM-YYYY
    Regex("^(\\d{2})[/\\-](\\d{2})[/\\-](\\d{4})").find(dob)?.let {
        val (dd, mm, _) = it.destructured
        return "$mm-$dd" == todayMd
    }
    return false
}

@Composable
private fun BirthdayBanner(wardName: String) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFFE11D74),
                        androidx.compose.ui.graphics.Color(0xFFF59E0B)
                    )
                )
            )
    ) {
        // Floating balloons rising behind the text — purely decorative,
        // stays clipped inside the banner so it doesn't disturb scroll.
        BirthdayBalloons()

        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "🎂",
                fontSize = 34.sp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Happy Birthday, ${wardName.ifBlank { "Champ" }}!",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Wishing you a wonderful year ahead \uD83C\uDF89",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Continuous balloon-rising animation. Each balloon loops on its own
 * schedule — staggered start, different horizontal position, different
 * duration — to avoid a rhythmic "all balloons move together" look.
 */
@Composable
private fun BirthdayBalloons() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "balloons")

    // (emoji, xPercent, durationMs, startDelayMs)
    val balloons = listOf(
        Triple("\uD83C\uDF88", 0.08f, 4200),   // 🎈 red
        Triple("\uD83C\uDF88", 0.22f, 5400),
        Triple("\uD83C\uDF88", 0.38f, 4700),
        Triple("\uD83C\uDF88", 0.55f, 5100),
        Triple("\uD83C\uDF88", 0.72f, 4500),
        Triple("\uD83C\uDF88", 0.88f, 4900),
    )
    val delays = listOf(0, 800, 1600, 400, 2200, 1200)

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        val widthPx  = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }

        balloons.forEachIndexed { idx, (emoji, xPct, durationMs) ->
            val progress by transition.animateFloat(
                initialValue = 1f,  // bottom
                targetValue = 0f,   // top
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        durationMillis = durationMs,
                        delayMillis = delays[idx % delays.size],
                        easing = androidx.compose.animation.core.LinearEasing
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "balloon_$idx"
            )
            // Fade in for first 15% of rise, fade out in last 20%.
            val alpha = when {
                progress > 0.85f -> ((1f - progress) / 0.15f).coerceIn(0f, 1f)
                progress < 0.20f -> (progress / 0.20f).coerceIn(0f, 1f)
                else -> 1f
            }
            Text(
                text = emoji,
                fontSize = 20.sp,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = widthPx * xPct - 20f
                        translationY = heightPx * progress
                        this.alpha = alpha
                    }
            )
        }
    }
}
