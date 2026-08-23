package com.odiousapps.z2mdash.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.odiousapps.z2mdash.ui.screens.AddEditBrokerScreen
import com.odiousapps.z2mdash.ui.screens.AddGroupScreen
import com.odiousapps.z2mdash.ui.screens.AddPanelScreen
import com.odiousapps.z2mdash.ui.screens.BrokersScreen
import com.odiousapps.z2mdash.ui.screens.DiscoverScreen
import com.odiousapps.z2mdash.ui.screens.GroupsScreen
import com.odiousapps.z2mdash.ui.screens.HomeScreen
import com.odiousapps.z2mdash.ui.screens.PlaceholderScreen
import com.odiousapps.z2mdash.ui.screens.SettingsScreen
import com.odiousapps.z2mdash.ui.screens.WelcomeScreen

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab("home", "Home", Icons.Default.Home),
    BottomTab("scripts", "Scripts", Icons.Default.PlayArrow),
    BottomTab("terminal", "Terminal", Icons.Default.Terminal),
    BottomTab("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (bottomTabs.any { it.route == currentRoute }) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(navController) }
            composable("welcome") { WelcomeScreen(navController) }
            composable("scripts") { PlaceholderScreen("Scripts") }
            composable("terminal") { PlaceholderScreen("Terminal") }
            composable("settings") { SettingsScreen(navController) }
            composable("brokers") { BrokersScreen(navController) }
            composable("discover") { DiscoverScreen(navController) }
            composable("discover/{brokerId}") { entry ->
                DiscoverScreen(navController, initialBrokerId = entry.arguments?.getString("brokerId"))
            }
            composable("groups") { GroupsScreen(navController) }
            composable("broker/{brokerId}") { entry ->
                val id = entry.arguments?.getString("brokerId")
                AddEditBrokerScreen(navController, if (id == "new") null else id)
            }
            composable("addGroup") { AddGroupScreen(navController) }
            composable("group/{groupId}/panel/{panelId}") { entry ->
                val groupId = entry.arguments?.getString("groupId")
                val panelId = entry.arguments?.getString("panelId")
                if (groupId != null) {
                    AddPanelScreen(navController, groupId, if (panelId == "new") null else panelId)
                }
            }
        }
    }
}
