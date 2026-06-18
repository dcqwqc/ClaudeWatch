package io.qwqc.claudewatch.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.qwqc.claudewatch.presentation.home.HomeScreen
import io.qwqc.claudewatch.presentation.settings.SettingsScreen
import io.qwqc.claudewatch.presentation.terminal.TerminalEditScreen
import io.qwqc.claudewatch.presentation.terminal.TerminalScreen

object Routes {
    const val HOME = "home"
    const val TERMINAL = "terminal" // terminal/{id}
    const val TERMINAL_EDIT = "terminalEdit" // terminalEdit/{id} ("new" = add)
    const val SETTINGS = "settings"
}

@Composable
fun ClaudeApp() {
    val nav = rememberSwipeDismissableNavController()
    RequestNotificationPermission()

    SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTerminal = { id -> nav.navigate("${Routes.TERMINAL}/$id") },
                onEditTerminal = { id -> nav.navigate("${Routes.TERMINAL_EDIT}/$id") },
                onAddTerminal = { nav.navigate("${Routes.TERMINAL_EDIT}/new") },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable("${Routes.TERMINAL}/{id}") { entry ->
            TerminalScreen(
                profileId = entry.arguments?.getString("id").orEmpty(),
                onConfigure = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable("${Routes.TERMINAL_EDIT}/{id}") { entry ->
            val id = entry.arguments?.getString("id")
            TerminalEditScreen(
                editId = id.takeUnless { it == "new" },
                onDone = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onSaved = { nav.popBackStack() })
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* result ignored; user can grant later in system settings */ }
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
