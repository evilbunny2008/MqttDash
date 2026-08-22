package com.opensource.mqttdash.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
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
import androidx.navigation.NavController
import com.opensource.mqttdash.MqttDashApplication
import com.opensource.mqttdash.mqtt.ConnectionState

@Composable
fun BrokersScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as MqttDashApplication
    val config by app.configRepository.config.collectAsState()
    val states by app.connectionManager.connectionStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brokers") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(config.brokers, key = { it.id }) { broker ->
                val state = states[broker.id] ?: ConnectionState.DISCONNECTED
                ListItem(
                    headlineContent = { Text(broker.name) },
                    supportingContent = { Text("${broker.host}:${broker.port} \u00b7 ${broker.protocol} \u00b7 $state") },
                    modifier = Modifier.clickable { navController.navigate("broker/${broker.id}") }
                )
                Divider()
            }
        }
    }
}
