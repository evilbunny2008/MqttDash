package com.odiousapps.mqttdash.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.odiousapps.mqttdash.MqttDashApplication
import com.odiousapps.mqttdash.data.Panel
import com.odiousapps.mqttdash.data.PanelGroup
import com.odiousapps.mqttdash.data.SensorDiscovery
import java.util.UUID

private const val NEW_GROUP_ID = "__new_group__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as MqttDashApplication
    val config by app.configRepository.config.collectAsState()
    val allPayloads by app.connectionManager.latestPayloads.collectAsState()

    var selectedBrokerId by remember { mutableStateOf(config.brokers.firstOrNull()?.id ?: "") }
    var selectedGroupId by remember { mutableStateOf(config.groups.firstOrNull()?.id ?: NEW_GROUP_ID) }
    var newGroupName by remember { mutableStateOf("Discovered Sensors") }

    // key = "topic|fieldKey"
    val selections = remember { mutableStateMapOf<String, Boolean>() }
    val useIdealRange = remember { mutableStateMapOf<String, Boolean>() }
    val expandedTopics = remember { mutableStateMapOf<String, Boolean>() }

    // Kick off discovery ("#") the moment a broker is chosen, and let the user
    // re-trigger it (e.g. after powering on a device) with the refresh button.
    LaunchedEffect(selectedBrokerId) {
        if (selectedBrokerId.isNotBlank()) app.connectionManager.discoverAll(selectedBrokerId)
    }

    val brokerPayloads = remember(allPayloads, selectedBrokerId) {
        val prefix = "$selectedBrokerId|"
        allPayloads.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    }
    val discovered = remember(brokerPayloads) { SensorDiscovery.discoverSensors(brokerPayloads) }
    val selectedCount = selections.values.count { it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover Sensors") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (selectedBrokerId.isNotBlank()) app.connectionManager.discoverAll(selectedBrokerId)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                Button(
                    onClick = {
                        val targetGroupId = if (selectedGroupId == NEW_GROUP_ID) {
                            val id = UUID.randomUUID().toString()
                            app.configRepository.upsertGroup(
                                PanelGroup(id = id, name = newGroupName.ifBlank { "Discovered Sensors" })
                            )
                            id
                        } else selectedGroupId

                        discovered.forEach { sensor ->
                            sensor.fields.forEach { field ->
                                val key = "${sensor.topic}|${field.key}"
                                if (selections[key] == true) {
                                    val applyIdeal = sensor.idealRangeTopic != null &&
                                        (useIdealRange[key] ?: true)
                                    val panel = Panel.Sensor(
                                        id = UUID.randomUUID().toString(),
                                        label = SensorDiscovery.suggestedLabel(field.key),
                                        brokerId = selectedBrokerId,
                                        topic = sensor.topic,
                                        jsonPath = field.key,
                                        unit = SensorDiscovery.suggestedUnit(field.key),
                                        icon = SensorDiscovery.suggestedIcon(field.key),
                                        idealRangeTopic = if (applyIdeal) sensor.idealRangeTopic!! else ""
                                    )
                                    app.configRepository.addPanelToGroup(targetGroupId, panel)
                                }
                            }
                        }
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Add $selectedCount panel${if (selectedCount == 1) "" else "s"}") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (config.brokers.isEmpty()) {
                Text("Add a broker first (Settings \u2192 Brokers) before discovering sensors.")
                return@Column
            }

            var brokerExpanded by remember { mutableStateOf(false) }
            val selectedBrokerName = config.brokers.find { it.id == selectedBrokerId }?.name ?: ""
            ExposedDropdownMenuBox(expanded = brokerExpanded, onExpandedChange = { brokerExpanded = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedBrokerName,
                    onValueChange = {},
                    label = { Text("Broker") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brokerExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = brokerExpanded, onDismissRequest = { brokerExpanded = false }) {
                    config.brokers.forEach { b ->
                        DropdownMenuItem(text = { Text(b.name) }, onClick = {
                            selectedBrokerId = b.id
                            brokerExpanded = false
                        })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            var groupExpanded by remember { mutableStateOf(false) }
            val selectedGroupName = if (selectedGroupId == NEW_GROUP_ID) {
                "+ New group"
            } else {
                config.groups.find { it.id == selectedGroupId }?.name ?: "+ New group"
            }
            ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedGroupName,
                    onValueChange = {},
                    label = { Text("Add panels to group") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                    config.groups.forEach { g ->
                        DropdownMenuItem(text = { Text(g.name) }, onClick = {
                            selectedGroupId = g.id
                            groupExpanded = false
                        })
                    }
                    DropdownMenuItem(text = { Text("+ New group") }, onClick = {
                        selectedGroupId = NEW_GROUP_ID
                        groupExpanded = false
                    })
                }
            }
            if (selectedGroupId == NEW_GROUP_ID) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("New group name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
            if (discovered.isEmpty()) {
                Text(
                    "No sensor-shaped topics found yet. Retained messages can take a " +
                        "moment to arrive after subscribing - try the refresh icon above, " +
                        "or check the broker is actually retaining messages on those topics."
                )
            } else {
                Text(
                    "Tick the fields you want as dashboard tiles. Topics with no numeric " +
                        "JSON fields (commands, availability, bridge status, etc.) are left out automatically.",
                    style = MaterialTheme.typography.bodySmall
                )
                discovered.forEach { sensor ->
                    Spacer(Modifier.height(16.dp))
                    val isExpanded = expandedTopics[sensor.topic] ?: true
                    val selectedInTopic = sensor.fields.count { selections["${sensor.topic}|${it.key}"] == true }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedTopics[sensor.topic] = !isExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sensor.topic, style = MaterialTheme.typography.titleSmall)
                            if (!isExpanded && selectedInTopic > 0) {
                                Text(
                                    "$selectedInTopic of ${sensor.fields.size} selected",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                    if (isExpanded) {
                        sensor.fields.forEach { field ->
                            val key = "${sensor.topic}|${field.key}"
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = selections[key] ?: false,
                                    onCheckedChange = { checked -> selections[key] = checked }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${SensorDiscovery.suggestedLabel(field.key)} (${field.key})")
                                    Text(
                                        "sample: ${field.sampleValue}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (sensor.idealRangeTopic != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ideal range", style = MaterialTheme.typography.labelSmall)
                                        Checkbox(
                                            checked = useIdealRange[key] ?: true,
                                            onCheckedChange = { checked -> useIdealRange[key] = checked },
                                            enabled = selections[key] == true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
