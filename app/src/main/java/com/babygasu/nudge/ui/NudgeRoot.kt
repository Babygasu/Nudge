package com.babygasu.nudge.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.babygasu.nudge.AppContainer
import com.babygasu.nudge.ui.screens.AllRemindersScreen
import com.babygasu.nudge.ui.screens.CaptureScreen
import com.babygasu.nudge.ui.screens.HomeScreen
import com.babygasu.nudge.ui.screens.OnboardingScreen
import com.babygasu.nudge.ui.screens.PrivacyScreen
import com.babygasu.nudge.ui.screens.ReminderEditScreen
import com.babygasu.nudge.ui.screens.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val REMINDERS = "reminders"
    const val PRIVACY = "privacy"
    const val SETTINGS = "settings"
    const val CAPTURE = "capture"
    const val EDIT = "edit/{id}?title={title}"

    fun edit(id: Long, title: String? = null): String =
        "edit/$id" + (title?.let { "?title=" + Uri.encode(it) } ?: "")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun NudgeRoot(container: AppContainer) {
    val vm: NudgeViewModel = viewModel { NudgeViewModel(container) }
    val settingsState by vm.settings.collectAsStateWithLifecycle()
    val settings = settingsState
    if (settings == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val start = remember { if (settings.onboardingDone) Routes.HOME else Routes.ONBOARDING }
    val shared by container.sharedText.collectAsStateWithLifecycle()
    val openId by container.openReminderRequest.collectAsStateWithLifecycle()

    LaunchedEffect(settings.onboardingDone) {
        val current = nav.currentDestination?.route
        if (settings.onboardingDone && current == Routes.ONBOARDING) {
            nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
        } else if (!settings.onboardingDone && current != null && current != Routes.ONBOARDING) {
            nav.navigate(Routes.ONBOARDING) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }
    LaunchedEffect(shared, settings.onboardingDone) {
        if (shared != null && settings.onboardingDone) nav.navigate(Routes.CAPTURE) { launchSingleTop = true }
    }
    LaunchedEffect(openId, settings.onboardingDone) {
        val id = openId
        if (id != null && settings.onboardingDone) {
            container.openReminderRequest.value = null
            nav.navigate(Routes.edit(id))
        }
    }

    val tabs = listOf(
        Tab(Routes.HOME, "Today", Icons.Filled.Home),
        Tab(Routes.REMINDERS, "Reminders", Icons.AutoMirrored.Filled.List),
        Tab(Routes.PRIVACY, "Privacy", Icons.Filled.Lock),
        Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable(Routes.ONBOARDING) { OnboardingScreen(vm) }
            composable(Routes.HOME) {
                HomeScreen(
                    vm,
                    onOpen = { nav.navigate(Routes.edit(it)) },
                    onAdd = { nav.navigate(Routes.edit(0)) },
                    onCapture = { nav.navigate(Routes.CAPTURE) },
                    onOpenPrivacy = {
                        nav.navigate(Routes.PRIVACY) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true }
                    },
                )
            }
            composable(Routes.REMINDERS) {
                AllRemindersScreen(vm, onOpen = { nav.navigate(Routes.edit(it)) }, onAdd = { nav.navigate(Routes.edit(0)) })
            }
            composable(Routes.PRIVACY) { PrivacyScreen(vm) }
            composable(Routes.SETTINGS) { SettingsScreen(vm) }
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    vm,
                    onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                    onManual = { title -> nav.navigate(Routes.edit(0, title)) { popUpTo(Routes.CAPTURE) { inclusive = true } } },
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                ReminderEditScreen(
                    vm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialTitle = entry.arguments?.getString("title"),
                    onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                )
            }
        }
    }
}
