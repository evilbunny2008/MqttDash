package com.odiousapps.z2mdash.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.odiousapps.z2mdash.Z2mDashApplication
import com.odiousapps.z2mdash.mqtt.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrokersScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()
    val states by app.connectionManager.connectionStates.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Brokers") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("broker/new") }) {
                Icon(Icons.Default.Add, contentDescription = "Add broker")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(config.brokers, key = { it.id }) { broker ->
                val state = states[broker.id] ?: ConnectionState.DISCONNECTED
                ListItem(
                    headlineContent = { Text(broker.name) },
                    supportingContent = { Text("${broker.host}:${broker.port} \u00b7 ${broker.protocol} \u00b7 $state") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { app.connectionManager.reconnect(broker.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reconnect to ${broker.name}")
                            }
                            IconButton(onClick = { navController.navigate("discover/${broker.id}") }) {
                                Icon(Icons.Default.Search, contentDescription = "Discover sensors on ${broker.name}")
                            }
                        }
                    },
                    modifier = Modifier.clickable { navController.navigate("broker/${broker.id}") }
                )
                HorizontalDivider()
            }
        }
    }
}
