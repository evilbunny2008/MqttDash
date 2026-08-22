package com.odiousapps.mqttdash.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.odiousapps.mqttdash.MqttDashApplication
import com.odiousapps.mqttdash.data.JsonPath
import com.odiousapps.mqttdash.data.Panel
import com.odiousapps.mqttdash.ui.components.SensorTile
import com.odiousapps.mqttdash.ui.components.ToggleTile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as MqttDashApplication
    val config by app.configRepository.config.collectAsState()
    val payloads by app.connectionManager.latestPayloads.collectAsState()

    var pendingDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // groupId to panelId
    var pendingGroupDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("addGroup") }) {
                Icon(Icons.Default.Add, contentDescription = "Add group")
            }
        }
    ) { padding ->
        if (config.groups.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Tap + to create your first group, then add panels to it.")
            }
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(config.groups, key = { it.id }) { group ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f)
                                .clickable { app.configRepository.setGroupCollapsed(group.id, !group.collapsed) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (group.collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = { navController.navigate("group/${group.id}/addPanel") }) {
                            Icon(Icons.Default.Add, contentDescription = "Add panel to ${group.name}")
                        }
                        IconButton(onClick = { pendingGroupDelete = group.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${group.name}")
                        }
                    }

                    if (!group.collapsed) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            group.panels.forEach { panel ->
                                when (panel) {
                                    is Panel.Sensor -> {
                                        val raw = payloads["${panel.brokerId}|${panel.topic}"]
                                        val value = raw?.let { JsonPath.extract(it, panel.jsonPath) } ?: "--"
                                        SensorTile(
                                            modifier = Modifier.width(160.dp),
                                            icon = panel.icon,
                                            value = value,
                                            unit = panel.unit,
                                            label = panel.label,
                                            onLongPress = { pendingDelete = group.id to panel.id }
                                        )
                                    }

                                    is Panel.Toggle -> {
                                        val statePayload = payloads["${panel.brokerId}|${panel.stateTopic}"]
                                        val resolvedState = statePayload?.let { JsonPath.extract(it, panel.stateJsonPath) }
                                        val isOn = resolvedState?.equals(panel.onPayload, ignoreCase = true) ?: false
                                        ToggleTile(
                                            modifier = Modifier.width(160.dp),
                                            icon = panel.icon,
                                            label = panel.label,
                                            isOn = isOn,
                                            onToggle = {
                                                app.connectionManager.publish(
                                                    panel.brokerId,
                                                    panel.commandTopic,
                                                    if (isOn) panel.offPayload else panel.onPayload
                                                )
                                            },
                                            onLongPress = { pendingDelete = group.id to panel.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { (groupId, panelId) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove panel?") },
            confirmButton = {
                TextButton(onClick = {
                    app.configRepository.removePanel(groupId, panelId)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    pendingGroupDelete?.let { groupId ->
        AlertDialog(
            onDismissRequest = { pendingGroupDelete = null },
            title = { Text("Delete group?") },
            text = { Text("This removes the group and every panel in it.") },
            confirmButton = {
                TextButton(onClick = {
                    app.configRepository.deleteGroup(groupId)
                    pendingGroupDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingGroupDelete = null }) { Text("Cancel") } }
        )
    }
}
