package com.schoolsync.parent.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.schoolsync.parent.ui.attendance.AttendanceScreen
import com.schoolsync.parent.ui.search.SearchScreen
import com.schoolsync.parent.ui.dashboard.DashboardScreen
import com.schoolsync.parent.ui.events.EventDetailScreen
import com.schoolsync.parent.ui.events.EventsScreen
import com.schoolsync.parent.util.DeepLinkBridge
import com.schoolsync.parent.ui.gallery.GalleryDetailScreen
import com.schoolsync.parent.ui.gallery.GalleryScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.schoolsync.parent.ui.fees.FeesScreen
import com.schoolsync.parent.ui.fees.ReceiptDetailScreen
import com.schoolsync.parent.ui.leave.LeaveScreen
import com.schoolsync.parent.ui.homework.HomeworkScreen
import com.schoolsync.parent.ui.messages.MessagesScreen
import com.schoolsync.parent.ui.notices.NoticesScreen
import com.schoolsync.parent.ui.profile.ProfileScreen
import com.schoolsync.parent.ui.results.ResultsScreen
import com.schoolsync.parent.ui.timetable.TimetableScreen
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.schoolsync.parent.ui.auth.LoginScreen
import com.schoolsync.parent.ui.library.LibraryScreen
import com.schoolsync.parent.ui.redflags.RedFlagScreen
import com.schoolsync.parent.ui.splash.SplashScreen
import com.schoolsync.parent.ui.teachers.MyTeachersScreen
import com.schoolsync.parent.ui.splash.SplashViewModel
import com.schoolsync.parent.ui.splash.WalkthroughScreen
import com.schoolsync.parent.ui.stories.StoryViewer
import com.schoolsync.parent.ui.stories.StoryViewModel
import com.schoolsync.parent.ui.theme.LocalAppColors

// --- Route definitions ---

sealed class Route(val route: String) {
    data object Splash : Route("splash")
    data object Walkthrough : Route("walkthrough")
    data object Login : Route("login")
    /** Phase A — gate before Main when `mustChangePassword` is true. */
    data object ForceChangePassword : Route("force_change_password")
    data object Main : Route("main")

    // Bottom nav destinations
    data object Dashboard : Route("dashboard")
    data object Academics : Route("academics")
    data object Fees : Route("fees")
    data object Messages : Route("messages")
    data object Profile : Route("profile")
    data object Search : Route("search")

    // Academics sub-screens
    data object Attendance : Route("attendance")
    data object Results : Route("results") {
        // Optional deep-link arg: preselect a specific exam's result (e.g. from
        // a `result_published` push). Plain navigation to "results" still
        // matches and lands on the first exam, since examId defaults to "".
        const val ARG_EXAM_ID = "examId"
        val routeWithArgs = "results?$ARG_EXAM_ID={$ARG_EXAM_ID}"
        fun createRoute(examId: String = ""): String =
            if (examId.isBlank()) "results" else "results?$ARG_EXAM_ID=$examId"
    }
    data object Homework : Route("homework") {
        // Optional deep-link arg: open straight into a specific homework's
        // detail page (e.g. tapping a row in the dashboard "Today's Homework"
        // preview). Plain navigation to "homework" still matches this pattern
        // and lands on the list, since hwId defaults to "".
        const val ARG_HW_ID = "hwId"
        const val ARG_TAB = "tab"   // optional: preselect a list tab ("all", "pending", …)
        val routeWithArgs = "homework?$ARG_HW_ID={$ARG_HW_ID}&$ARG_TAB={$ARG_TAB}"
        fun createRoute(hwId: String = "", tab: String = ""): String {
            val query = buildList {
                if (hwId.isNotBlank()) add("$ARG_HW_ID=$hwId")
                if (tab.isNotBlank()) add("$ARG_TAB=$tab")
            }
            return if (query.isEmpty()) "homework" else "homework?" + query.joinToString("&")
        }
    }
    data object Timetable : Route("timetable")
    data object Exams : Route("exams") {
        // Optional deep-link arg: open a specific exam's schedule (e.g. from an
        // `exam_scheduled` push). Plain navigation to "exams" still matches and
        // falls back to the nearest available exam, since examId defaults to "".
        const val ARG_EXAM_ID = "examId"
        val routeWithArgs = "exams?$ARG_EXAM_ID={$ARG_EXAM_ID}"
        fun createRoute(examId: String = ""): String =
            if (examId.isBlank()) "exams" else "exams?$ARG_EXAM_ID=$examId"
    }

    // Other screens
    data object Notices : Route("notices")
    data object Leave : Route("leave")
    data object Events : Route("events")
    data object EventDetail : Route("event_detail/{eventId}") {
        fun createRoute(eventId: String) = "event_detail/$eventId"
    }
    data object Gallery : Route("gallery")
    data object GalleryDetail : Route("gallery_detail/{albumId}") {
        fun createRoute(albumId: String) = "gallery_detail/$albumId"
    }
    data object Library : Route("library")
    data object Ptm : Route("ptm/{ptmEventId}") {
        fun createRoute(ptmEventId: String) = "ptm/$ptmEventId"
    }
    /** Permanent entry: list all PTMs (upcoming + past) for the parent's child. */
    data object PtmList : Route("ptm_list")
    data object RedFlags : Route("red_flags")
    data object MyTeachers : Route("my_teachers")
    data object MyLessons : Route("my_lessons")
    data object StoryViewer : Route("story_viewer/{teacherId}") {
        fun createRoute(teacherId: String) = "story_viewer/$teacherId"
    }
    data object ReceiptDetail : Route("receipt_detail/{receiptId}") {
        fun createRoute(receiptId: String) = "receipt_detail/$receiptId"
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Route.Dashboard.route,
        label = "Home",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    ),
    BottomNavItem(
        route = Route.Academics.route,
        label = "Categories",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School
    ),
    BottomNavItem(
        route = Route.Fees.route,
        label = "Fees",
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet
    ),
    BottomNavItem(
        route = Route.Notices.route,
        label = "Notices",
        selectedIcon = Icons.Filled.Campaign,
        unselectedIcon = Icons.Outlined.Campaign
    ),
    BottomNavItem(
        route = Route.Profile.route,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Route.Splash.route
) {
    // ── Mid-session credential enforcement ──
    // Before this, `mustChangePassword` was only read on a cold start, so an
    // admin resetting a password had no effect on a running app — the ID token
    // stays valid for up to an hour and nothing re-read the flag. The guard
    // re-checks whenever the app comes to the foreground, and also reacts to
    // Firebase dropping the user (revoked token / disabled account).
    val sessionGuard: com.schoolsync.parent.ui.session.SessionGuardViewModel = hiltViewModel()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sessionGuard.recheck()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val guardContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        sessionGuard.sessionEnded.collect { message ->
            android.widget.Toast.makeText(guardContext, message, android.widget.Toast.LENGTH_LONG).show()
            navController.navigate(Route.Login.route) {
                // Drop the whole back stack: the session is gone, so nothing
                // behind us is still authorised to render. launchSingleTop stops
                // a second trigger (auth-state AND foreground re-check can both
                // fire) from stacking two Login screens.
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable(Route.Splash.route) {
            val viewModel: SplashViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(LocalAppColors.current.bgStart))
            } else {
                SplashScreen(
                    onNavigateToWalkthrough = {
                        navController.navigate(Route.Walkthrough.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(Route.Login.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToMain = {
                        // Phase A — if the cached user still has the
                        // mustChangePassword flag (e.g. user killed the
                        // app mid-force-change), route to the gate
                        // instead of Main so the requirement isn't
                        // bypassable by a cold restart.
                        val dest = if (state.mustChangePassword)
                            Route.ForceChangePassword.route
                        else
                            Route.Main.route
                        navController.navigate(dest) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    isLoggedIn = state.isLoggedIn,
                    hasSeenOnboarding = state.hasSeenOnboarding
                )
            }
        }

        composable(Route.Walkthrough.route) {
            val viewModel: SplashViewModel = hiltViewModel()

            WalkthroughScreen(
                onFinished = {
                    viewModel.markOnboardingSeen()
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.Walkthrough.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Login.route) {
            LoginScreen(
                onLoginSuccess = { mustChangePassword ->
                    if (mustChangePassword) {
                        navController.navigate(Route.ForceChangePassword.route) {
                            popUpTo(Route.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Route.Main.route) {
                            popUpTo(Route.Login.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Route.ForceChangePassword.route) {
            com.schoolsync.parent.ui.auth.ForceChangePasswordScreen(
                onPasswordChanged = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.ForceChangePassword.route) { inclusive = true }
                    }
                },
                onLogout = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Main.route) {
            MainScreen(
                onLogout = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

// Routes that show the bottom bar (main tabs only)
private val bottomBarRoutes = setOf(
    Route.Dashboard.route,
    Route.Academics.route,
    Route.Fees.route,
    Route.Notices.route,
    Route.Profile.route
)

@Composable
fun MainScreen(
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val badgeViewModel: BadgeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val badgeCounts by badgeViewModel.counts.collectAsState()

    // ── Shared Stories VM (hoisted to MainScreen scope) ─────────────────────
    // One warm StoryViewModel backs BOTH the dashboard ring row AND the
    // full-screen overlay. Previously each spun up its OWN cold hiltViewModel
    // (dashboard-scoped vs Main-scoped), so opening the overlay started an
    // EMPTY VM whose Firestore listener emitted an onStart empty placeholder —
    // the viewer flash-closed on the first tap. Sharing one instance means the
    // overlay reuses the already-loaded storyGroups, so it never sees a
    // transient empty set and only closes on a genuinely empty result.
    val storyViewModel: StoryViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val storyState by storyViewModel.uiState.collectAsState()

    // Track whether the Messages screen is currently inside an open chat —
    // when true we hide the bottom bar so the chat input isn't covered.
    var inChatView by remember { mutableStateOf(false) }

    // Phase 8: consume FCM deep-link intents. MainActivity publishes the
    // target route onto DeepLinkBridge when the app is launched (or
    // foregrounded) by a notification tap; we navigate once the main
    // scaffold is up. Calling consume() clears the flag so a tab switch
    // later doesn't re-route.
    // Carries a deep-linked notice id (from a tapped notice push) to the Notices
    // screen so it can auto-expand + scroll to that notice.
    var pendingNoticeId by remember { mutableStateOf<String?>(null) }
    val pendingDeepLink by DeepLinkBridge.pending.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val dl = pendingDeepLink ?: return@LaunchedEffect
        val target = dl.route
        // Allow-listed main-tab routes, plus the events + event_detail routes
        // for push-notification tap deep-links. Unknown targets dropped silently.
        // Includes "red_flags" so a flag-alert push actually opens the Red
        // Flags screen (the MainActivity type→route mapping publishes it; this
        // allow-list is the second gate that was silently dropping it).
        val allowedTabs     = listOf("fees", "messages", "dashboard", "profile", "events", "notices", "red_flags")
        val isEventDetail   = target.startsWith("event_detail/")
        // "homework" and "homework?hwId=..." both resolve to the Homework
        // destination (its route args are optional), so allow the whole prefix.
        val isHomework      = target == "homework" || target.startsWith("homework?")
        // result_published / exam_scheduled deep links (optional examId arg).
        val isResults       = target == "results" || target.startsWith("results?")
        val isExams         = target == "exams" || target.startsWith("exams?")
        if (target in allowedTabs || isEventDetail || isHomework || isResults || isExams) {
            // Stash the notice id (if any) so the Notices screen auto-opens it.
            if (target == "notices") pendingNoticeId = dl.arg
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        DeepLinkBridge.consume()
    }

    val showBottomBar = currentRoute in bottomBarRoutes && !inChatView
    val c = LocalAppColors.current

    // Transition specs
    val slideDuration = 300
    val fadeDuration = 250

    // Animate bottom padding so content clears the nav bar
    val navBarPadding by animateDpAsState(
        targetValue = if (showBottomBar) 100.dp else 0.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "navPadding"
    )

    // Story viewer is rendered as a FULL-SCREEN OVERLAY above the NavHost
    // (not a separate route) so the dashboard it was opened from stays
    // composed behind it — a swipe-down / pinch-out dismiss fades to reveal
    // the dashboard, matching the teacher app. It is backed by the SAME
    // hoisted `storyViewModel` above as the dashboard ring, so the overlay
    // opens onto already-loaded data (no cold-start flash).
    var storyViewerTeacherId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bgStart)
    ) {
        NavHost(
            navController = navController,
            startDestination = Route.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = navBarPadding),
            // Default: tab switches use crossfade
            enterTransition = { fadeIn(tween(fadeDuration, easing = FastOutSlowInEasing)) },
            exitTransition = { fadeOut(tween(200, easing = FastOutSlowInEasing)) },
            popEnterTransition = { fadeIn(tween(fadeDuration, easing = FastOutSlowInEasing)) },
            popExitTransition = { fadeOut(tween(200, easing = FastOutSlowInEasing)) }
        ) {
            // ── Main Tabs (bottom bar visible) ──────────────────────────────

            composable(Route.Dashboard.route) {
                // Stories ring row data — the SAME hoisted StoryViewModel that
                // backs the full-screen viewer, so the dashboard ring and the
                // viewer stay in sync (viewed state, reactions, live updates)
                // AND the overlay opens onto warm data.
                DashboardScreen(
                    storyGroups = storyState.storyGroups,
                    storiesLoading = storyState.isLoading,
                    onNavigateToAttendance = { navController.navigate(Route.Attendance.route) },
                    onNavigateToResults = { navController.navigate(Route.Results.route) },
                    onNavigateToFees = { navController.navigate(Route.Fees.route) },
                    onNavigateToTimetable = { navController.navigate(Route.Timetable.route) },
                    onNavigateToHomework = { navController.navigate(Route.Homework.route) },
                    onOpenHomework = { hwId -> navController.navigate(Route.Homework.createRoute(hwId)) },
                    // Dashboard search-row grid icon → Categories tab (same
                    // save/restore behaviour as tapping it in the bottom bar).
                    onNavigateToAcademics = {
                        navController.navigate(Route.Academics.route) {
                            popUpTo(Route.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSearch = { navController.navigate(Route.Search.route) },
                    onNavigateToNotices = { navController.navigate(Route.Notices.route) },
                    onNavigateToLeave = { navController.navigate(Route.Leave.route) },
                    onNavigateToEvents = { navController.navigate(Route.Events.route) },
                    onNavigateToEventDetail = { eventId ->
                        navController.navigate(Route.EventDetail.createRoute(eventId))
                    },
                    onNavigateToPtm = { ptmEventId ->
                        navController.navigate(Route.Ptm.createRoute(ptmEventId))
                    },
                    onNavigateToPtmList = { navController.navigate(Route.PtmList.route) },
                    onNavigateToGallery = { navController.navigate(Route.Gallery.route) },
                    onNavigateToRedFlags = { navController.navigate(Route.RedFlags.route) },
                    onNavigateToLibrary = { navController.navigate(Route.Library.route) },
                    onNavigateToMyTeachers = { navController.navigate(Route.MyTeachers.route) },
                    onNavigateToStoryViewer = { teacherId -> storyViewerTeacherId = teacherId },
                    onNavigateToProfile = {
                        navController.navigate(Route.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Route.Academics.route) {
                AcademicsHubScreen(
                    onNavigateToAttendance = { navController.navigate(Route.Attendance.route) },
                    onNavigateToResults = { navController.navigate(Route.Results.route) },
                    onNavigateToHomework = { navController.navigate(Route.Homework.route) },
                    onNavigateToTimetable = { navController.navigate(Route.Timetable.route) },
                    onNavigateToEvents = { navController.navigate(Route.Events.route) },
                    onNavigateToGallery = { navController.navigate(Route.Gallery.route) },
                    onNavigateToLibrary = { navController.navigate(Route.Library.route) },
                    onNavigateToPtmList = { navController.navigate(Route.PtmList.route) },
                    onNavigateToLessons = { navController.navigate(Route.MyLessons.route) }
                )
            }

            composable(Route.Fees.route) {
                FeesScreen(
                    onOpenReceipt = { id ->
                        navController.navigate(Route.ReceiptDetail.createRoute(id))
                    }
                )
            }

            composable(
                route = Route.ReceiptDetail.route,
                arguments = listOf(navArgument("receiptId") { type = NavType.StringType }),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                ReceiptDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Route.Messages.route) {
                MessagesScreen(onChatViewChange = { inChatView = it })
            }

            composable(Route.Profile.route) {
                ProfileScreen(
                    onLogout = onLogout,
                    onNavigateToMyTeachers = { navController.navigate(Route.MyTeachers.route) },
                    onOpenHomework = { tab -> navController.navigate(Route.Homework.createRoute(tab = tab)) }
                )
            }

            // ── Sub-screens (bottom bar hidden, slide transitions) ──────────

            composable(
                Route.Attendance.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                AttendanceScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToLeave = { navController.navigate(Route.Leave.route) }
                )
            }

            composable(
                Route.Results.routeWithArgs,
                arguments = listOf(
                    navArgument(Route.Results.ARG_EXAM_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                ResultsScreen(
                    onBack = { navController.popBackStack() },
                    onPayFees = {
                        navController.navigate(Route.Fees.route) {
                            popUpTo(Route.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // MED-6: Exam Schedule — previously orphaned (no composable
            // registration). Wired here so the `exam_scheduled` push has a real
            // destination instead of misrouting to Results.
            composable(
                Route.Exams.routeWithArgs,
                arguments = listOf(
                    navArgument(Route.Exams.ARG_EXAM_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                com.schoolsync.parent.ui.exams.ExamScheduleScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Route.Search.route,
                enterTransition = { fadeIn(tween(fadeDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    // Result tap → navigate to its route (feature, homework
                    // deep-link, event detail, notices, etc.). Drop Search from
                    // the back stack so Back from the target lands on Dashboard.
                    onNavigateRoute = { route ->
                        navController.navigate(route) {
                            popUpTo(Route.Search.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                Route.Homework.routeWithArgs,
                arguments = listOf(
                    navArgument(Route.Homework.ARG_HW_ID) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Route.Homework.ARG_TAB) {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) { backStackEntry ->
                HomeworkScreen(
                    onBack = { navController.popBackStack() },
                    initialHomeworkId = backStackEntry.arguments?.getString(Route.Homework.ARG_HW_ID).orEmpty(),
                    initialTab = backStackEntry.arguments?.getString(Route.Homework.ARG_TAB).orEmpty()
                )
            }

            composable(
                Route.Timetable.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                TimetableScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Route.MyLessons.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                com.schoolsync.parent.ui.lessons.MyLessonsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Route.Notices.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                NoticesScreen(
                    onBack = { navController.popBackStack() },
                    deepLinkNoticeId = pendingNoticeId,
                    onDeepLinkConsumed = { pendingNoticeId = null }
                )
            }

            composable(
                Route.Leave.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                LeaveScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Route.Events.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                EventsScreen(
                    onBack = { navController.popBackStack() },
                    onEventClick = { eventId ->
                        navController.navigate(Route.EventDetail.createRoute(eventId))
                    },
                    onPtmClick = { ptmEventId ->
                        navController.navigate(Route.Ptm.createRoute(ptmEventId))
                    }
                )
            }

            composable(
                route = Route.EventDetail.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                EventDetailScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                    onViewPhotos = { albumId ->
                        navController.navigate(Route.GalleryDetail.createRoute(albumId))
                    }
                )
            }

            composable(
                route = Route.Ptm.route,
                arguments = listOf(navArgument("ptmEventId") { type = NavType.StringType }),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) { backStackEntry ->
                val ptmEventId = backStackEntry.arguments?.getString("ptmEventId") ?: ""
                com.schoolsync.parent.ui.ptm.PtmDetailScreen(
                    ptmEventId = ptmEventId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Permanent PTM list — reachable from the Academics hub.
            composable(
                Route.PtmList.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                com.schoolsync.parent.ui.ptm.PtmListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPtm = { id -> navController.navigate(Route.Ptm.createRoute(id)) }
                )
            }

            composable(
                Route.Gallery.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                GalleryScreen(
                    onBack = { navController.popBackStack() },
                    onAlbumClick = { albumId ->
                        navController.navigate(Route.GalleryDetail.createRoute(albumId))
                    }
                )
            }

            composable(
                route = Route.GalleryDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
                GalleryDetailScreen(
                    albumId = albumId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                Route.RedFlags.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                RedFlagScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Route.Library.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                LibraryScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Route.MyTeachers.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                MyTeachersScreen(
                    onBack = { navController.popBackStack() },
                    onMessageTeacher = {
                        // ChatLauncher already received the request from
                        // the ViewModel; just switch to the Messages tab
                        // and clear back stack to Dashboard so the bottom
                        // bar stays in sync.
                        navController.navigate(Route.Messages.route) {
                            popUpTo(Route.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

        }

        // ── Bottom bar overlay (floats above content) ───────────────────
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180))
        ) {
            SmoothBottomBar(
                navController = navController,
                currentRoute = currentRoute,
                badges = badgeCounts,
            )
        }

        // ── Full-screen story overlay — dashboard stays composed behind, so
        //    it shows through as the viewer fades on a swipe-down / pinch-out.
        storyViewerTeacherId?.let { teacherId ->
            StoryViewer(
                storyGroups = storyState.storyGroups,
                isLoading = storyState.isLoading,
                initialTeacherId = teacherId,
                myReactions = storyState.myReactions,
                onClose = { storyViewerTeacherId = null },
                onStoryViewed = { storyId -> storyViewModel.markStoryViewed(storyId) },
                onReact = { storyId, emoji -> storyViewModel.reactToStory(storyId, emoji) }
            )
        }

        // Global payment-flow overlay — observes PaymentSession (an
        // app-singleton) and shows full-screen success / processing /
        // failure / pending screens regardless of which tab is active.
        // Renders nothing when state is Idle, so the overlay is
        // invisible and inert during normal usage.
        com.schoolsync.parent.ui.payment.PaymentFlowOverlay(
            onViewReceipt = { docId ->
                android.util.Log.i(
                    "PaymentNav",
                    "[NAV → ReceiptDetail] route=${Route.ReceiptDetail.createRoute(docId)}"
                )
                navController.navigate(Route.ReceiptDetail.createRoute(docId))
            }
        )
    }
}

// ─── Custom smooth bottom bar ─────────────────────────────────────────────────

@Composable
private fun SmoothBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    badges: Map<String, Int> = emptyMap(),
) {
    val c = LocalAppColors.current
    val haptics = com.schoolsync.parent.ui.components.rememberAppHaptics()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Hairline top divider — crisp separation from content, no floating
        // pill / drop shadow.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider)
        )

        // Flat, edge-to-edge bar: flush to the screen sides & bottom (system
        // nav-bar inset still respected), solid surface, no shadow.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surfaceElevated)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                        || (currentRoute != null && isAcademicsChild(currentRoute, item.route))

                SmoothNavItem(
                    item = item,
                    isSelected = isSelected,
                    badgeCount = badges[item.route] ?: 0,
                    onClick = {
                        android.util.Log.d("BottomNav", "tap ${item.route} (current=$currentRoute, selected=$isSelected)")
                        if (!isSelected) {
                            haptics.navTick()
                            // Special case: tapping Home (= Dashboard, the
                            // start destination) was a silent no-op because
                            // `popUpTo(Dashboard)` left Dashboard at the
                            // top, and then `launchSingleTop=true` on a
                            // navigate to Dashboard cancels the navigation.
                            // Net result: pop happened invisibly; the
                            // screen stayed on whatever was previously on
                            // top. Reported as "tap Home from Fees does
                            // nothing." Fix: explicitly popBackStack to
                            // Dashboard for the Home tab.
                            if (item.route == Route.Dashboard.route) {
                                navController.popBackStack(
                                    route = Route.Dashboard.route,
                                    inclusive = false
                                )
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(Route.Dashboard.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SmoothNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val c = LocalAppColors.current

    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 22.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "iconSize"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "iconAlpha"
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "labelAlpha"
    )

    val yOffset by animateDpAsState(
        targetValue = if (isSelected) (-2).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "yOffset"
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .offset(y = yOffset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wrap the icon area so we can paint the unread badge on top of it.
        // Color-only selection: no pill / underline / dot — the active tab is
        // signalled purely by accent tint + bolder label.
        Box {
            Box(
                modifier = Modifier.size(width = 46.dp, height = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = if (isSelected) c.navActive else c.navInactive,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer(alpha = iconAlpha)
                )
            }
            // Unread badge — only shown when count > 0.
            if (badgeCount > 0) {
                NavUnreadBadge(
                    count = badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) c.navActive else c.navInactive,
            modifier = Modifier.graphicsLayer(alpha = labelAlpha),
            maxLines = 1
        )
    }
}

/**
 * Unread badge for the bottom-nav icons. Renders a small dot for count == 1,
 * a pill with "N" for 2..98, or "99+" beyond that. Color-coded with the
 * theme's accent so it adapts to light/dark.
 */
@Composable
private fun NavUnreadBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val c = LocalAppColors.current
    val text = when {
        count > 99 -> "99+"
        else -> count.toString()
    }
    val isDot = count == 1
    Box(
        modifier = modifier
            .then(
                if (isDot) Modifier.size(8.dp)
                else Modifier
                    .height(16.dp)
                    .widthIn(min = 16.dp)
            )
            .clip(CircleShape)
            .background(c.accent)
            .border(width = 1.5.dp, color = c.bgEnd, shape = CircleShape)
            .padding(horizontal = if (isDot) 0.dp else 5.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isDot) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun isAcademicsChild(currentRoute: String?, itemRoute: String): Boolean {
    if (itemRoute != Route.Academics.route) return false
    // Strip any query args (e.g. "homework?hwId=…") before matching so the
    // Academics tab still highlights when a child route carries a deep-link arg.
    val base = currentRoute?.substringBefore("?")
    return base in listOf(
        Route.Attendance.route,
        Route.Results.route,
        Route.Homework.route,
        Route.Timetable.route,
        Route.Exams.route,
        Route.Events.route,
        Route.EventDetail.route,
        Route.Gallery.route,
        Route.GalleryDetail.route,
        Route.Library.route,
        Route.MyLessons.route
    )
}

// --- Academics hub screen ---

@Composable
fun AcademicsHubScreen(
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
    com.schoolsync.parent.ui.dashboard.AcademicsHubContent(
        onNavigateToAttendance = onNavigateToAttendance,
        onNavigateToResults = onNavigateToResults,
        onNavigateToHomework = onNavigateToHomework,
        onNavigateToTimetable = onNavigateToTimetable,
        onNavigateToEvents = onNavigateToEvents,
        onNavigateToGallery = onNavigateToGallery,
        onNavigateToLibrary = onNavigateToLibrary,
        onNavigateToPtmList = onNavigateToPtmList,
        onNavigateToLessons = onNavigateToLessons
    )
}
