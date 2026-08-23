package com.odiousapps.mqttdash.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.odiousapps.mqttdash.data.JsonPath
import com.odiousapps.mqttdash.data.Panel
import com.odiousapps.mqttdash.data.PanelGroup
import com.odiousapps.mqttdash.data.SensorDiscovery
import java.util.UUID

private const val NEW_GROUP_ID = "__new_group__"

/**
 * Whether a discovered field should default to "use ideal range" ticked, when
 * its topic has a matching "<topic>/ideal" companion. Only pre-ticks the
 * field the range topic is actually meant for (moisture) - other numeric
 * fields on the same topic (temperature, battery, etc.) default unticked so
 * they don't end up permanently flashing against an unrelated min/max.
 */
private fun defaultUsesIdealRange(fieldKey: String): Boolean =
    fieldKey.contains("moisture", ignoreCase = true)

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

    // Resolves (creating if needed) the group new panels should land in. Shared
    // by the manual "Add N panels" flow and the one-tap "Apply device config" flow.
    fun resolveTargetGroupId(): String = if (selectedGroupId == NEW_GROUP_ID) {
        val id = UUID.randomUUID().toString()
        app.configRepository.upsertGroup(PanelGroup(id = id, name = newGroupName.ifBlank { "Discovered Sensors" }))
        id
    } else selectedGroupId

    // Finds (or creates) a group by exact name - used when a device's own
    // "group" field should decide placement, bypassing the UI's group picker.
    fun resolveGroupIdByName(name: String): String {
        val existing = config.groups.find { it.name.equals(name, ignoreCase = true) }
        if (existing != null) return existing.id
        val id = UUID.randomUUID().toString()
        app.configRepository.upsertGroup(PanelGroup(id = id, name = name))
        return id
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover Sensors") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (discovered.isNotEmpty()) {
                        val allExpanded = discovered.all { expandedTopics[it.topic] ?: false }
                        IconButton(onClick = {
                            val newState = !allExpanded
                            discovered.forEach { expandedTopics[it.topic] = newState }
                        }) {
                            Icon(
                                if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (allExpanded) "Collapse all" else "Expand all"
                            )
                        }
                    }
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
                        val targetGroupId = resolveTargetGroupId()
                        discovered.forEach { sensor ->
                            sensor.fields.forEach { field ->
                                val key = "${sensor.topic}|${field.key}"
                                if (selections[key] == true) {
                                    val useIdeal = useIdealRange[key] ?: defaultUsesIdealRange(field.key)
                                    val panel = Panel.Sensor(
                                        id = UUID.randomUUID().toString(),
                                        label = SensorDiscovery.suggestedLabel(field.key),
                                        brokerId = selectedBrokerId,
                                        topic = sensor.topic,
                                        jsonPath = field.key,
                                        unit = SensorDiscovery.suggestedUnit(field.key),
                                        icon = SensorDiscovery.suggestedIcon(field.key),
                                        idealRangeTopic = sensor.idealRangeTopic?.takeIf { useIdeal } ?: "",
                                        clusterName = sensor.topic.substringAfterLast("/")
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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
                    "Tick the fields you want as dashboard tiles, or use \"Apply device config\" " +
                        "if the device publishes its own <topic>/app config.",
                    style = MaterialTheme.typography.bodySmall
                )
                discovered.forEach { sensor ->
                    Spacer(Modifier.height(16.dp))
                    val isExpanded = expandedTopics[sensor.topic] ?: false
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

                    val appConfigTopic = sensor.appConfigTopic
                    val appConfigPayload = appConfigTopic?.let { brokerPayloads[it] }
                    val deviceConfig = appConfigPayload?.let { SensorDiscovery.parseDeviceAppConfig(it) }

                    if (isExpanded) {
                        if (appConfigTopic != null && deviceConfig != null) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    val targetGroupId = deviceConfig.group?.let { resolveGroupIdByName(it) }
                                        ?: resolveTargetGroupId()
                                    applyDeviceConfig(
                                        app = app,
                                        targetGroupId = targetGroupId,
                                        brokerId = selectedBrokerId,
                                        sensorTopic = sensor.topic,
                                        sensorFieldKeys = sensor.fields.map { it.key }.toSet(),
                                        appConfigTopic = appConfigTopic,
                                        appConfigPayload = appConfigPayload,
                                        deviceConfig = deviceConfig
                                    )
                                    navController.popBackStack()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Apply device config" +
                                        if (deviceConfig.name.isNotBlank()) ": ${deviceConfig.name}" else ""
                                )
                            }
                            Text(
                                "Creates ${deviceConfig.panelFields.size} panel(s) exactly as listed in " +
                                    "${appConfigTopic}" +
                                    (deviceConfig.group?.let { " into group \"$it\"" } ?: "") +
                                    ", skipping the checkboxes below.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                        } else if (appConfigTopic != null && appConfigPayload != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Found ${appConfigTopic} but couldn't read it as a device config " +
                                    "\u2013 check it's valid JSON with a non-empty \"panels\" array.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                        }

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
                                            checked = useIdealRange[key] ?: defaultUsesIdealRange(field.key),
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

/**
 * Creates one panel per field named in [deviceConfig].panelFields. Each field is
 * looked up first in the main sensor topic, then in the app-config topic itself
 * (covers fields like "moisture_min" that only exist in the config payload).
 * Any field that's part of a detected "<base>_min"/"<base>_max" pair gets wired
 * up with an ideal range pointing at the app-config topic - except the min/max
 * fields themselves, which would otherwise trivially compare against their own value.
 */
private fun applyDeviceConfig(
    app: MqttDashApplication,
    targetGroupId: String,
    brokerId: String,
    sensorTopic: String,
    sensorFieldKeys: Set<String>,
    appConfigTopic: String,
    appConfigPayload: String?,
    deviceConfig: SensorDiscovery.DeviceAppConfig
) {
    val rangeKeys = deviceConfig.rangePairs.values.flatMap { (min, max) -> listOf(min, max) }.toSet()
    val clusterName = deviceConfig.name.ifBlank { sensorTopic.substringAfterLast("/") }

    deviceConfig.panelFields.forEachIndexed { index, field ->
        val topic = when {
            field in sensorFieldKeys -> sensorTopic
            appConfigPayload != null && JsonPath.extract(appConfigPayload, field) != null -> appConfigTopic
            else -> null
        } ?: return@forEachIndexed

        val rangeBase = if (field !in rangeKeys) {
            deviceConfig.rangePairs.keys.find { base -> field.contains(base, ignoreCase = true) }
        } else null

        val label = deviceConfig.labels.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?: SensorDiscovery.suggestedLabel(field)

        val panel = Panel.Sensor(
            id = UUID.randomUUID().toString(),
            label = label,
            brokerId = brokerId,
            topic = topic,
            jsonPath = field,
            unit = SensorDiscovery.suggestedUnit(field),
            icon = SensorDiscovery.suggestedIcon(field),
            idealRangeTopic = if (rangeBase != null) appConfigTopic else "",
            idealMinPath = rangeBase?.let { deviceConfig.rangePairs[it]!!.first } ?: "min",
            idealMaxPath = rangeBase?.let { deviceConfig.rangePairs[it]!!.second } ?: "max",
            clusterName = clusterName,
            displayOrder = deviceConfig.groupOrder ?: Int.MAX_VALUE
        )
        app.configRepository.addPanelToGroup(targetGroupId, panel)
    }
}
