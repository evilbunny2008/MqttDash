package com.odiousapps.z2mdash.ui.screens

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.odiousapps.z2mdash.Z2mDashApplication
import com.odiousapps.z2mdash.data.AppConfig
import com.odiousapps.z2mdash.data.AutoConfiguredDevice
import com.odiousapps.z2mdash.data.JsonPath
import com.odiousapps.z2mdash.data.Panel
import com.odiousapps.z2mdash.data.PanelGroup
import com.odiousapps.z2mdash.data.PendingAutoConfigDevice
import com.odiousapps.z2mdash.data.SensorDiscovery
import com.odiousapps.z2mdash.ui.components.SensorAlert
import com.odiousapps.z2mdash.ui.components.SensorTile
import com.odiousapps.z2mdash.ui.components.ToggleTile
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()
    val payloads by app.connectionManager.latestPayloads.collectAsState()
    val timestamps by app.connectionManager.latestPayloadTimestamps.collectAsState()

    // Ticks every few seconds purely to force the "updated N ago" captions to
    // refresh even when no new MQTT message has arrived to trigger recomposition -
    // frequent enough that the seconds-resolution display below actually looks live.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Nothing on this screen can do anything without a broker - send the user
    // straight to Add Broker rather than showing them an unusable empty Home.
    // Only re-fires if brokers go from present to empty again later (e.g. the
    // last one gets deleted), not on every recomposition.
    LaunchedEffect(config.brokers.isEmpty()) {
        if (config.brokers.isEmpty()) {
            navController.navigate("welcome")
        }
    }

    var pendingGroupDelete by remember { mutableStateOf<String?>(null) }

    // Standalone (non-clustered) panels, and panels inside a cluster card, both
    // lay out as an exact 3-column grid in portrait, by sizing each tile to a
    // third of the available width; in landscape they keep the old fixed-160dp
    // (standalone) / 2-column (cluster) layout.
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val columnsPerRow = if (isPortrait) 3 else 2
    val standaloneTileWidth = if (isPortrait) {
        val groupHorizontalPadding = 12.dp * 2
        val gapsBetweenColumns = 8.dp * (columnsPerRow - 1)
        (configuration.screenWidthDp.dp - groupHorizontalPadding - gapsBetweenColumns) / columnsPerRow
    } else {
        160.dp
    }

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
            items(config.pendingAutoConfigDevices, key = { "${it.brokerId}|${it.appConfigTopic}" }) { pending ->
                PendingDeviceBanner(
                    pending = pending,
                    onAdd = { addPendingDevice(app, config, payloads, pending) },
                    onIgnore = { app.configRepository.ignoreAppConfigTopic(pending.brokerId, pending.appConfigTopic) }
                )
            }
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
                        IconButton(onClick = { navController.navigate("group/${group.id}/panel/new") }) {
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
                            // Panels sharing a non-blank clusterName render together in one
                            // card; panels with a blank clusterName stay as standalone tiles,
                            // each getting its own unique bucket so they don't merge together.
                            val clusters = LinkedHashMap<String, MutableList<Panel>>()
                            group.panels.forEach { panel ->
                                val key = panel.clusterName.ifBlank { "__single__${panel.id}" }
                                clusters.getOrPut(key) { mutableListOf() }.add(panel)
                            }
                            // Sort clusters/standalone tiles by their lowest displayOrder (falls
                            // back to insertion order for anything left at the Int.MAX_VALUE default).
                            val orderedClusters = clusters.values.sortedBy { bucket ->
                                bucket.minOf { it.displayOrder }
                            }

                            orderedClusters.forEach { panelsInCluster ->
                                val name = panelsInCluster.first().clusterName
                                if (name.isBlank()) {
                                    PanelTile(
                                        panel = panelsInCluster.first(),
                                        groupId = group.id,
                                        payloads = payloads,
                                        app = app,
                                        navController = navController,
                                        modifier = Modifier.width(standaloneTileWidth)
                                    )
                                } else {
                                    ClusterCard(
                                        name = name,
                                        panels = panelsInCluster,
                                        groupId = group.id,
                                        payloads = payloads,
                                        timestamps = timestamps,
                                        nowMillis = nowMillis,
                                        app = app,
                                        navController = navController,
                                        columns = columnsPerRow,
                                        tileWidth = standaloneTileWidth
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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

/** Renders a bordered card containing every panel in [panels] ([columns] per row), with [name] as a caption below. */
@Composable
private fun ClusterCard(
    name: String,
    panels: List<Panel>,
    groupId: String,
    payloads: Map<String, String>,
    timestamps: Map<String, Long>,
    nowMillis: Long,
    app: Z2mDashApplication,
    navController: NavController,
    columns: Int,
    tileWidth: Dp
) {
    val ageText = remember(panels, payloads, timestamps, nowMillis) {
        val latestTimestamp = panels.mapNotNull { panel ->
            val topic = when (panel) {
                is Panel.Sensor -> panel.topic
                is Panel.Toggle -> panel.stateTopic.takeIf { it.isNotBlank() }
            } ?: return@mapNotNull null
            val key = "${panel.brokerId}|$topic"
            // Prefer the device's own reported time (Zigbee2MQTT's "last_seen"
            // field, when present) over our app's receipt time - it reflects
            // when the device itself last reported in, not just when this app
            // instance happened to be listening (which resets on reconnect).
            val deviceReportedMillis = payloads[key]
                ?.let { JsonPath.extract(it, "last_seen") }
                ?.let { JsonPath.parseIso8601(it) }
            deviceReportedMillis ?: timestamps[key]
        }.maxOrNull()
        latestTimestamp?.let {
            DateUtils.getRelativeTimeSpanString(it, nowMillis, DateUtils.SECOND_IN_MILLIS).toString()
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            panels.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { panel ->
                        PanelTile(
                            panel = panel,
                            groupId = groupId,
                            payloads = payloads,
                            app = app,
                            navController = navController,
                            modifier = Modifier.width(tileWidth)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                if (ageText != null) {
                    Text(
                        " \u2022 $ageText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Renders a single Sensor or Toggle tile, wired up to its live payload and edit/toggle actions. */
@Composable
private fun PanelTile(
    panel: Panel,
    groupId: String,
    payloads: Map<String, String>,
    app: Z2mDashApplication,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    when (panel) {
        is Panel.Sensor -> {
            val raw = payloads["${panel.brokerId}|${panel.topic}"]
            val value = raw?.let { JsonPath.extract(it, panel.jsonPath) } ?: "--"
            val alert = if (panel.idealRangeTopic.isBlank()) {
                SensorAlert.NONE
            } else {
                val numericValue = value.toDoubleOrNull()
                val idealRaw = payloads["${panel.brokerId}|${panel.idealRangeTopic}"]
                val min = idealRaw?.let { JsonPath.extract(it, panel.idealMinPath) }?.toDoubleOrNull()
                val max = idealRaw?.let { JsonPath.extract(it, panel.idealMaxPath) }?.toDoubleOrNull()
                when {
                    numericValue == null -> SensorAlert.NONE
                    min != null && numericValue < min -> SensorAlert.BELOW_MIN
                    max != null && numericValue > max -> SensorAlert.ABOVE_MAX
                    min != null || max != null -> SensorAlert.IN_RANGE
                    else -> SensorAlert.NONE
                }
            }
            SensorTile(
                modifier = modifier,
                icon = panel.icon,
                value = value,
                unit = panel.unit,
                alert = alert,
                label = panel.label,
                onClick = { navController.navigate("group/$groupId/panel/${panel.id}") }
            )
        }

        is Panel.Toggle -> {
            val statePayload = payloads["${panel.brokerId}|${panel.stateTopic}"]
            val resolvedState = statePayload?.let { JsonPath.extract(it, panel.stateJsonPath) }
            val isOn = resolvedState?.equals(panel.onPayload, ignoreCase = true) ?: false
            ToggleTile(
                modifier = modifier,
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
                onLongPress = { navController.navigate("group/$groupId/panel/${panel.id}") }
            )
        }
    }
}

/** Builds and stores the panels for a newly-accepted pending device, then clears it from the pending list. */
private fun addPendingDevice(
    app: Z2mDashApplication,
    config: AppConfig,
    payloads: Map<String, String>,
    pending: PendingAutoConfigDevice
) {
    val appConfigPayload = payloads["${pending.brokerId}|${pending.appConfigTopic}"] ?: return
    val deviceConfig = SensorDiscovery.parseDeviceAppConfig(appConfigPayload) ?: return
    val sensorPayload = payloads["${pending.brokerId}|${pending.sensorTopic}"]
    val sensorFieldKeys = sensorPayload?.let { SensorDiscovery.fieldKeysOf(it) } ?: emptySet()

    val newPanels = SensorDiscovery.buildPanels(
        brokerId = pending.brokerId,
        sensorTopic = pending.sensorTopic,
        sensorFieldKeys = sensorFieldKeys,
        appConfigTopic = pending.appConfigTopic,
        appConfigPayload = appConfigPayload,
        deviceConfig = deviceConfig
    )
    if (newPanels.isEmpty()) return

    val targetGroupId = deviceConfig.group?.let { name ->
        config.groups.find { it.name.equals(name, ignoreCase = true) }?.id
            ?: UUID.randomUUID().toString().also { id ->
                app.configRepository.upsertGroup(PanelGroup(id = id, name = name))
            }
    } ?: config.groups.firstOrNull()?.id
        ?: UUID.randomUUID().toString().also { id ->
            app.configRepository.upsertGroup(PanelGroup(id = id, name = "Discovered Sensors"))
        }

    val device = AutoConfiguredDevice(
        brokerId = pending.brokerId,
        sensorTopic = pending.sensorTopic,
        appConfigTopic = pending.appConfigTopic,
        lastAppliedPayload = appConfigPayload,
        createdPanelIds = newPanels.map { it.id }
    )
    app.configRepository.applyDeviceAutoConfig(device, targetGroupId, newPanels)
    app.configRepository.removePendingAutoConfigDevice(pending.brokerId, pending.appConfigTopic)
}

/** A dismissible card prompting the user to accept or ignore a newly-detected auto-config device. */
@Composable
private fun PendingDeviceBanner(
    pending: PendingAutoConfigDevice,
    onAdd: () -> Unit,
    onIgnore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("New device found", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "\"${pending.deviceName}\" (${pending.appConfigTopic}) published its own dashboard config.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onIgnore) { Text("Ignore") }
                TextButton(onClick = onAdd) { Text("Add") }
            }
        }
    }
}
