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

    // Academics sub-screens
    data object Attendance : Route("attendance")
    data object Results : Route("results")
    data object Homework : Route("homework")
    data object Timetable : Route("timetable")
    data object Exams : Route("exams")

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
        label = "Academics",
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

    // Track whether the Messages screen is currently inside an open chat —
    // when true we hide the bottom bar so the chat input isn't covered.
    var inChatView by remember { mutableStateOf(false) }

    // Phase 8: consume FCM deep-link intents. MainActivity publishes the
    // target route onto DeepLinkBridge when the app is launched (or
    // foregrounded) by a notification tap; we navigate once the main
    // scaffold is up. Calling consume() clears the flag so a tab switch
    // later doesn't re-route.
    val pendingDeepLink by DeepLinkBridge.pending.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        // Allow-listed main-tab routes, plus the events + event_detail routes
        // for push-notification tap deep-links. Unknown targets dropped silently.
        val allowedTabs     = listOf("fees", "messages", "dashboard", "profile", "events", "notices")
        val isEventDetail   = target.startsWith("event_detail/")
        if (target in allowedTabs || isEventDetail) {
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
                DashboardScreen(
                    onNavigateToAttendance = { navController.navigate(Route.Attendance.route) },
                    onNavigateToResults = { navController.navigate(Route.Results.route) },
                    onNavigateToFees = { navController.navigate(Route.Fees.route) },
                    onNavigateToTimetable = { navController.navigate(Route.Timetable.route) },
                    onNavigateToHomework = { navController.navigate(Route.Homework.route) },
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
                    onNavigateToStoryViewer = { teacherId ->
                        navController.navigate(Route.StoryViewer.createRoute(teacherId))
                    },
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

            composable(Route.Profile.route) { ProfileScreen(onLogout = onLogout) }

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
                Route.Results.route,
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

            composable(
                Route.Homework.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                HomeworkScreen(onBack = { navController.popBackStack() })
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
                NoticesScreen(onBack = { navController.popBackStack() })
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
                    onBack = { navController.popBackStack() }
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

            composable(
                route = Route.StoryViewer.route,
                arguments = listOf(navArgument("teacherId") { type = NavType.StringType }),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) { backStackEntry ->
                val teacherId = backStackEntry.arguments?.getString("teacherId") ?: ""
                val storyViewModel: StoryViewModel = hiltViewModel()
                val storyState by storyViewModel.uiState.collectAsState()
                StoryViewer(
                    storyGroups = storyState.storyGroups,
                    initialTeacherId = teacherId,
                    onClose = { navController.popBackStack() },
                    onStoryViewed = { storyId -> storyViewModel.markStoryViewed(storyId) }
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
        // Soft fade edge so content doesn't hard-cut into the bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, c.bgEnd)
                    )
                )
        )

        // Solid bar area — matches app background, no see-through
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bgEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp)
                .padding(top = 6.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (c.isDark) c.surfaceElevated
                    else Color.White.copy(alpha = 0.75f)
                )
                .border(
                    width = 0.5.dp,
                    color = c.glassBorder,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
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

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "indicator"
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
        Box {
        if (isSelected) {
            // PhonePe-style 3D gradient pill behind the active icon.
            val top = Color(
                red = (c.navActive.red + (1f - c.navActive.red) * 0.28f).coerceIn(0f, 1f),
                green = (c.navActive.green + (1f - c.navActive.green) * 0.28f).coerceIn(0f, 1f),
                blue = (c.navActive.blue + (1f - c.navActive.blue) * 0.28f).coerceIn(0f, 1f),
                alpha = 1f
            )
            val bottom = Color(
                red = (c.navActive.red * 0.78f).coerceIn(0f, 1f),
                green = (c.navActive.green * 0.78f).coerceIn(0f, 1f),
                blue = (c.navActive.blue * 0.78f).coerceIn(0f, 1f),
                alpha = 1f
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = c.navActive,
                        spotColor = c.navActive
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(top, bottom))),
                contentAlignment = Alignment.Center
            ) {
                // Top highlight for the glossy sheen.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                endY = 55f
                            )
                        )
                )
                Icon(
                    imageVector = item.selectedIcon,
                    contentDescription = item.label,
                    tint = Color.White,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer(alpha = iconAlpha)
                )
            }
        } else {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.unselectedIcon,
                    contentDescription = item.label,
                    tint = c.navInactive,
                    modifier = Modifier
                        .size(iconSize)
                        .graphicsLayer(alpha = iconAlpha)
                )
            }
        }
            // Unread badge — only shown when count > 0. Aligned to the
            // top-right corner of the 42dp icon container.
            if (badgeCount > 0) {
                NavUnreadBadge(
                    count = badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) c.navActive else c.navInactive,
            modifier = Modifier.graphicsLayer(alpha = labelAlpha),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer(alpha = indicatorAlpha)
                .clip(CircleShape)
                .background(c.navDot)
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
    return currentRoute in listOf(
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
