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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AccountBalanceWallet
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.schoolsync.parent.ui.attendance.AttendanceScreen
import com.schoolsync.parent.ui.dashboard.DashboardScreen
import com.schoolsync.parent.ui.events.EventDetailScreen
import com.schoolsync.parent.ui.events.EventsScreen
import com.schoolsync.parent.ui.gallery.GalleryDetailScreen
import com.schoolsync.parent.ui.gallery.GalleryScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.schoolsync.parent.ui.fees.FeesScreen
import com.schoolsync.parent.ui.leave.LeaveScreen
import com.schoolsync.parent.ui.homework.HomeworkScreen
import com.schoolsync.parent.ui.messages.MessagesScreen
import com.schoolsync.parent.ui.notices.NoticesScreen
import com.schoolsync.parent.ui.profile.ProfileScreen
import com.schoolsync.parent.ui.results.ResultsScreen
import com.schoolsync.parent.ui.timetable.TimetableScreen
import com.schoolsync.parent.ui.transport.TransportScreen
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.schoolsync.parent.ui.auth.LoginScreen
import com.schoolsync.parent.ui.library.LibraryScreen
import com.schoolsync.parent.ui.redflags.RedFlagScreen
import com.schoolsync.parent.ui.splash.SplashScreen
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
    data object Transport : Route("transport")
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
    data object RedFlags : Route("red_flags")
    data object StoryViewer : Route("story_viewer/{teacherId}") {
        fun createRoute(teacherId: String) = "story_viewer/$teacherId"
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
        route = Route.Messages.route,
        label = "Messages",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
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
                        navController.navigate(Route.Main.route) {
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
                onLoginSuccess = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
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
    Route.Messages.route,
    Route.Profile.route
)

@Composable
fun MainScreen(
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomBarRoutes
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
                    onNavigateToGallery = { navController.navigate(Route.Gallery.route) },
                    onNavigateToRedFlags = { navController.navigate(Route.RedFlags.route) },
                    onNavigateToLibrary = { navController.navigate(Route.Library.route) },
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
                    onNavigateToLibrary = { navController.navigate(Route.Library.route) }
                )
            }

            composable(Route.Fees.route) { FeesScreen() }

            composable(Route.Messages.route) { MessagesScreen() }

            composable(Route.Profile.route) { ProfileScreen(onLogout = onLogout) }

            // ── Sub-screens (bottom bar hidden, slide transitions) ──────────

            composable(
                Route.Attendance.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                AttendanceScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Route.Results.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                ResultsScreen(onBack = { navController.popBackStack() })
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
                Route.Notices.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                NoticesScreen(onBack = { navController.popBackStack() })
            }

            composable(
                Route.Transport.route,
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(slideDuration)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(fadeDuration)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(slideDuration)) }
            ) {
                TransportScreen(onBack = { navController.popBackStack() })
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
                currentRoute = currentRoute
            )
        }
    }
}

// ─── Custom smooth bottom bar ─────────────────────────────────────────────────

@Composable
private fun SmoothBottomBar(
    navController: NavHostController,
    currentRoute: String?
) {
    val c = LocalAppColors.current

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
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(item.route) {
                                popUpTo(Route.Dashboard.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
    onClick: () -> Unit
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
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .offset(y = yOffset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = if (isSelected) c.navActive else c.navInactive,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer(alpha = iconAlpha)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) c.navActive else c.navInactive,
            modifier = Modifier.graphicsLayer(alpha = labelAlpha),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer(alpha = indicatorAlpha)
                .clip(CircleShape)
                .background(c.navDot)
        )
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
        Route.Library.route
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
    onNavigateToLibrary: () -> Unit = {}
) {
    com.schoolsync.parent.ui.dashboard.AcademicsHubContent(
        onNavigateToAttendance = onNavigateToAttendance,
        onNavigateToResults = onNavigateToResults,
        onNavigateToHomework = onNavigateToHomework,
        onNavigateToTimetable = onNavigateToTimetable,
        onNavigateToEvents = onNavigateToEvents,
        onNavigateToGallery = onNavigateToGallery,
        onNavigateToLibrary = onNavigateToLibrary
    )
}
