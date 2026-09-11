package com.pebblesoft.toolbox.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pebblesoft.toolbox.R
import com.pebblesoft.toolbox.ui.components.isWide
import com.pebblesoft.toolbox.ui.home.HomeScreen
import com.pebblesoft.toolbox.ui.numbers.NumbersScreen
import com.pebblesoft.toolbox.ui.recordings.RecordingsScreen
import com.pebblesoft.toolbox.ui.settings.SettingsScreen
import com.pebblesoft.toolbox.ui.setup.SetupScreen

/** The app's destinations. Four tabs, plus the setup flow reached from Home. */
private enum class Tab(val route: String, val icon: ImageVector, val label: Int) {
    HOME("home", Icons.Filled.Home, R.string.nav_home),
    RECORDINGS("recordings", Icons.AutoMirrored.Filled.List, R.string.nav_recordings),
    NUMBERS("numbers", Icons.Filled.Dialpad, R.string.nav_numbers),
    SETTINGS("settings", Icons.Filled.Settings, R.string.nav_settings),
}

private const val ROUTE_SETUP = "setup"

/**
 * The shell: a title bar, four destinations, and one screen at a time.
 *
 * Four is the ceiling on purpose. Every extra destination is one more thing to
 * understand while frightened; anything that does not belong in these four
 * belongs INSIDE one of them. Setup is deliberately not a destination — it is a
 * flow reached from the one card that asks for it, and it disappears once done.
 *
 * The four sit at the BOTTOM on an upright phone and down the SIDE on anything
 * wider. That is not decoration: in landscape a bottom bar eats a quarter of the
 * short axis — the axis that is already starved — while the wide axis sits
 * empty. The rail spends the axis that has room to spare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(versionName: String) {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()
    val state by vm.state.collectAsState()
    val storage by vm.storageBytes.collectAsState()

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val wide = isWide()

    val title = when {
        current?.route == ROUTE_SETUP -> stringResource(R.string.setup_title)
        else -> Tab.entries.firstOrNull { tab ->
            current?.hierarchy?.any { it.route == tab.route } == true
        }?.let { stringResource(it.label) } ?: stringResource(R.string.app_name)
    }

    fun isSelected(tab: Tab) = current?.hierarchy?.any { it.route == tab.route } == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!wide) {
                // Explicit colours: Material's defaults tint these surfaces from a
                // palette we never chose, and the selected pill must clear 3:1
                // against the bar (WCAG 1.4.11) — the pale container colour does not.
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            selected = isSelected(tab),
                            onClick = { nav.go(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding)) {
            if (wide) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            selected = isSelected(tab),
                            onClick = { nav.go(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }

            NavHost(navController = nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) {
                    HomeScreen(
                        state = state,
                        onOpenSetup = { nav.navigate(ROUTE_SETUP) },
                        onOpenRecordings = { nav.go(Tab.RECORDINGS.route) },
                        onOpenRecord = { /* detail screen lands with the player */ },
                    )
                }
                composable(Tab.RECORDINGS.route) {
                    RecordingsScreen(records = state.records, onOpen = { })
                }
                composable(Tab.NUMBERS.route) {
                    NumbersScreen(
                        state = state,
                        onAdd = vm::addRule,
                        onRemove = vm::removeRule,
                        onDefaultRule = vm::setDefaultRule,
                        onUnknownRule = vm::setUnknownRule,
                    )
                }
                composable(Tab.SETTINGS.route) {
                    SettingsScreen(
                        biometricOn = false,
                        onBiometric = { },
                        neutralLook = false,
                        onNeutralLook = { },
                        storageBytes = storage,
                        versionName = versionName,
                    )
                }
                composable(ROUTE_SETUP) {
                    SetupScreen(onDone = { vm.rearm(); nav.popBackStack() })
                }
            }
        }
    }
}

/** Move to a destination without stacking duplicates, keeping each tab's state. */
private fun NavHostController.go(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
