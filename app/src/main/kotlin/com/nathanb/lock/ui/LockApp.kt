package com.nathanb.lock.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import com.nathanb.lock.ui.components.FloatingNavBar
import com.nathanb.lock.ui.components.ManualModeBanner
import com.nathanb.lock.ui.components.ManualModeInfoSheet
import com.nathanb.lock.ui.screens.AppPickerScreen
import com.nathanb.lock.ui.screens.ProfileDetailScreen
import com.nathanb.lock.ui.screens.ProfilesListScreen
import com.nathanb.lock.ui.screens.NewProfileWizardScreen
import com.nathanb.lock.ui.screens.HomeScreen
import com.nathanb.lock.ui.screens.NfcTagsScreen
import com.nathanb.lock.ui.screens.SettingsScreen
import com.nathanb.lock.ui.screens.schedules.ScheduleEditScreen
import com.nathanb.lock.ui.screens.schedules.SchedulesListScreen
import com.nathanb.lock.ui.screens.onboarding.OnboardingScreen
import com.nathanb.lock.ui.screens.SplashScreen
import com.nathanb.lock.ui.screens.StatsScreen
import com.nathanb.lock.ui.screens.settings.DataScreen
import com.nathanb.lock.ui.screens.settings.PermissionsScreen
import com.nathanb.lock.ui.screens.settings.SessionSettingsScreen
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.viewmodel.LockViewModel

private val TAB_ROUTES = setOf("home", "stats", "settings")

@Composable
fun LockApp(viewModel: LockViewModel, isNfcLaunch: Boolean = false) {
    val isSetupLoaded by viewModel.isSetupLoaded.collectAsStateWithLifecycle()
    val isSetupCompleted by viewModel.isSetupCompleted.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }

    // Wait for DataStore to load before rendering — prevents flash to setup screen
    if (!isSetupLoaded) return

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()

    // For NFC launches, the toggle already happened by the time we render.
    // lockState.isLocked is the POST-toggle state. So if NFC + now free → was locked → unlock splash.
    val isUnlockingSplash = isNfcLaunch && !lockState.isLocked

    // Show splash screen for ~2 seconds
    if (showSplash) {
        LockTheme(themeMode = themeMode) {
            SplashScreen(
                isLocked = lockState.isLocked,
                isUnlocking = isUnlockingSplash,
                onSplashFinished = { showSplash = false },
            )
        }
        return
    }

    val navController = rememberNavController()
    val startDestination = if (isSetupCompleted) "home" else "onboarding"

    // Observe visual lock state (lags behind real state — waits for animation)
    val visualLocked by viewModel.visualLocked.collectAsStateWithLifecycle()
    val isEmergencyActive by viewModel.isEmergencyActive.collectAsStateWithLifecycle()
    val profilesForNav by viewModel.profilesSorted.collectAsStateWithLifecycle()
    val defaultProfileId = profilesForNav.firstOrNull { it.isDefault }?.id
        ?: profilesForNav.firstOrNull()?.id ?: 0L
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // When lock activates, force navigate back to home
    LaunchedEffect(lockState.isLocked) {
        if (lockState.isLocked && currentRoute != null && currentRoute != "home" && currentRoute != "onboarding") {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val isManualMode = lockState.isManualMode
    var showManualModeInfo by remember { mutableStateOf(false) }

    val showNavBar = currentRoute in TAB_ROUTES &&
        (!visualLocked || isEmergencyActive)

    LockTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            val bannerVisible = isManualMode && currentRoute in TAB_ROUTES && !visualLocked

            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable("onboarding") {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onOnboardingComplete = {
                            navController.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                    )
                }

                composable("home") {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToApps = { navController.navigate("app-picker/$defaultProfileId") },
                        onNavigateToPermissions = { navController.navigate("permissions") },
                        onNavigateToNfcTags = { navController.navigate("nfc-tags") },
                    )
                }

                composable("settings") {
                    Box(modifier = if (bannerVisible) Modifier.padding(top = 48.dp) else Modifier) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateToProfileDetail = { id ->
                                navController.navigate("profile-detail/$id")
                            },
                            onNavigateToProfiles = {
                                navController.navigate("profiles")
                            },
                            onNavigateToNfcTags = {
                                navController.navigate("nfc-tags")
                            },
                            onNavigateToPermissions = {
                                navController.navigate("permissions")
                            },
                            onNavigateToData = {
                                navController.navigate("data")
                            },
                            onNavigateToSessionSettings = {
                                navController.navigate("session-settings")
                            },
                            onNavigateToSchedules = {
                                navController.navigate("schedules")
                            },
                        )
                    }
                }

                composable(
                    "schedules",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) {
                    SchedulesListScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToEdit = { id -> navController.navigate("schedule-edit?scheduleId=$id") },
                    )
                }

                composable(
                    "schedule-edit?scheduleId={scheduleId}",
                    arguments = listOf(navArgument("scheduleId") { type = NavType.LongType; defaultValue = -1L }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("scheduleId") ?: -1L
                    ScheduleEditScreen(
                        viewModel = viewModel,
                        scheduleId = id,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    "nfc-tags?profileId={profileId}",
                    arguments = listOf(navArgument("profileId") { type = NavType.LongType; defaultValue = -1L }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) { backStackEntry ->
                    val pid = backStackEntry.arguments?.getLong("profileId") ?: -1L
                    NfcTagsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        preselectProfileId = pid.takeIf { it >= 0 },
                    )
                }

                composable("stats") {
                    Box(modifier = if (bannerVisible) Modifier.padding(top = 48.dp) else Modifier) {
                        StatsScreen(
                            viewModel = viewModel,
                        )
                    }
                }

                composable(
                    "profiles",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) {
                    ProfilesListScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToProfileDetail = { navController.navigate("profile-detail/$it") },
                        onNewProfile = { navController.navigate("new-profile") },
                    )
                }

                composable(
                    "new-profile",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) {
                    NewProfileWizardScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() },
                    )
                }

                composable(
                    "profile-detail/{profileId}",
                    arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("profileId") ?: defaultProfileId
                    ProfileDetailScreen(
                        viewModel = viewModel,
                        profileId = id,
                        onBack = { navController.popBackStack() },
                        onEditApps = { navController.navigate("app-picker/$it") },
                        onAssociateTag = { navController.navigate("nfc-tags?profileId=$it") },
                    )
                }

                composable(
                    "app-picker/{profileId}",
                    arguments = listOf(navArgument("profileId") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) { backStackEntry ->
                    val profileId = backStackEntry.arguments?.getLong("profileId") ?: defaultProfileId
                    AppPickerScreen(
                        viewModel = viewModel,
                        profileId = profileId,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    "permissions",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) {
                    PermissionsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    "data",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) {
                    DataScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    "session-settings",
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
                ) {
                    SessionSettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Floating nav bar
            AnimatedVisibility(
                visible = showNavBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ) + fadeIn(animationSpec = tween(400)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingNavBar(
                    currentRoute = currentRoute,
                    onTabSelected = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }

            // Manual mode banner — top of screen on tab routes
            if (isManualMode && currentRoute in TAB_ROUTES && !visualLocked) {
                ManualModeBanner(
                    onClick = { showManualModeInfo = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
        }

        if (showManualModeInfo) {
            ManualModeInfoSheet(
                onDismiss = { showManualModeInfo = false },
                onDisableManualMode = { viewModel.disableManualMode() },
            )
        }
    }
}
