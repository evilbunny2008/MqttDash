package com.odiousapps.z2mdash.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.odiousapps.z2mdash.Z2mDashApplication

@Composable
fun SettingsScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val context = LocalContext.current
    val config by app.configRepository.config.collectAsState()

    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openOutputStream(it, "wt")?.use { out ->
                out.write(app.configRepository.exportJson().toByteArray())
            }
            snackbarMessage = "Configuration exported"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                val text = input.readBytes().toString(Charsets.UTF_8)
                try {
                    app.configRepository.importJson(text)
                    snackbarMessage = "Configuration imported"
                } catch (_: Exception) {
                    snackbarMessage = "Import failed: not a valid config file"
                }
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Discover Sensors") },
                    supportingContent = { Text("Scan a broker's retained topics and auto-suggest sensor panels") },
                    leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.clickable { navController.navigate("discover") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Groups") },
                    supportingContent = { Text("${config.groups.size} groups \u2013 rename or reorder them") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    modifier = Modifier.clickable { navController.navigate("groups") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Brokers") },
                    supportingContent = { Text("${config.brokers.size} configured") },
                    leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                    modifier = Modifier.clickable { navController.navigate("brokers") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Background Work") },
                    supportingContent = { Text("Keep broker connections alive when the app is closed") },
                    leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = config.backgroundWorkEnabled,
                            onCheckedChange = { enabled ->
                                app.configRepository.update { it.copy(backgroundWorkEnabled = enabled) }
                            }
                        )
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Configuration Backup") },
                    supportingContent = { Text("Save your brokers, groups and panels as plain JSON you own") },
                    leadingContent = { Icon(Icons.Default.Backup, contentDescription = null) },
                    modifier = Modifier.clickable { exportLauncher.launch("z2mdash-config.json") }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Configuration Recovery") },
                    supportingContent = { Text("Restore from a previously exported JSON file") },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("About") },
                    supportingContent = { Text("Z2M Dash \u2013 MIT licensed, no cloud, no account, no lock-in") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
        }
    }
}
