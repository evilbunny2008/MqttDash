package com.opensource.mqttdash.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opensource.mqttdash.MqttDashApplication
import com.opensource.mqttdash.data.Panel
import com.opensource.mqttdash.data.TileIcon
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPanelScreen(navController: NavController, groupId: String) {
    val app = LocalContext.current.applicationContext as MqttDashApplication
    val config by app.configRepository.config.collectAsState()

    var panelType by remember { mutableStateOf("Sensor") }
    var selectedBrokerId by remember { mutableStateOf(config.brokers.firstOrNull()?.id ?: "") }
    var label by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(TileIcon.GAUGE) }

    // Sensor fields
    var topic by remember { mutableStateOf("") }
    var jsonPath by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    // Toggle fields
    var commandTopic by remember { mutableStateOf("") }
    var onPayload by remember { mutableStateOf("ON") }
    var offPayload by remember { mutableStateOf("OFF") }
    var stateTopic by remember { mutableStateOf("") }
    var stateJsonPath by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Panel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (config.brokers.isNotEmpty()) {
                Button(
                    onClick = {
                        val panel: Panel = if (panelType == "Sensor") {
                            Panel.Sensor(
                                id = UUID.randomUUID().toString(),
                                label = label.ifBlank { "Sensor" },
                                brokerId = selectedBrokerId,
                                topic = topic,
                                jsonPath = jsonPath,
                                unit = unit,
                                icon = icon
                            )
                        } else {
                            Panel.Toggle(
                                id = UUID.randomUUID().toString(),
                                label = label.ifBlank { "Toggle" },
                                brokerId = selectedBrokerId,
                                commandTopic = commandTopic,
                                onPayload = onPayload,
                                offPayload = offPayload,
                                stateTopic = stateTopic,
                                stateJsonPath = stateJsonPath,
                                icon = icon
                            )
                        }
                        app.configRepository.addPanelToGroup(groupId, panel)
                        navController.popBackStack()
                    },
                    enabled = selectedBrokerId.isNotBlank() &&
                        (if (panelType == "Sensor") topic.isNotBlank() else commandTopic.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Add") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (config.brokers.isEmpty()) {
                Text("Add a broker first (Settings \u2192 Brokers) before creating panels.")
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = panelType == "Sensor",
                        onClick = { panelType = "Sensor" },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Sensor") }
                    SegmentedButton(
                        selected = panelType == "Toggle",
                        onClick = { panelType = "Toggle" },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Toggle") }
                }

                Spacer(Modifier.height(16.dp))
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
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                var iconExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = iconExpanded, onExpandedChange = { iconExpanded = it }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = icon.name,
                        onValueChange = {},
                        label = { Text("Icon") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = iconExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = iconExpanded, onDismissRequest = { iconExpanded = false }) {
                        TileIcon.values().forEach { ic ->
                            DropdownMenuItem(text = { Text(ic.name) }, onClick = { icon = ic; iconExpanded = false })
                        }
                    }
                }

                if (panelType == "Sensor") {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Topic") },
                        placeholder = { Text("e.g. zigbee2mqtt/Soil Sensor 1") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = jsonPath,
                        onValueChange = { jsonPath = it },
                        label = { Text("JSON field (blank = raw payload)") },
                        placeholder = { Text("e.g. temperature or state.battery") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = commandTopic,
                        onValueChange = { commandTopic = it },
                        label = { Text("Command topic") },
                        placeholder = { Text("e.g. zigbee2mqtt/Kitchen Plug/set") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = onPayload,
                            onValueChange = { onPayload = it },
                            label = { Text("ON payload") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = offPayload,
                            onValueChange = { offPayload = it },
                            label = { Text("OFF payload") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = stateTopic,
                        onValueChange = { stateTopic = it },
                        label = { Text("State topic (optional)") },
                        placeholder = { Text("e.g. zigbee2mqtt/Kitchen Plug") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = stateJsonPath,
                        onValueChange = { stateJsonPath = it },
                        label = { Text("State JSON field (optional)") },
                        placeholder = { Text("e.g. state") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
