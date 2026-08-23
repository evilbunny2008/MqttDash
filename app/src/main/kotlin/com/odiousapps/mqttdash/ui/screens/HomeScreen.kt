package com.odiousapps.mqttdash.ui.screens

import android.content.res.Configuration
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
import com.odiousapps.mqttdash.MqttDashApplication
import com.odiousapps.mqttdash.data.JsonPath
import com.odiousapps.mqttdash.data.Panel
import com.odiousapps.mqttdash.ui.components.SensorAlert
import com.odiousapps.mqttdash.ui.components.SensorTile
import com.odiousapps.mqttdash.ui.components.ToggleTile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as MqttDashApplication
    val config by app.configRepository.config.collectAsState()
    val payloads by app.connectionManager.latestPayloads.collectAsState()

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
    app: MqttDashApplication,
    navController: NavController,
    columns: Int,
    tileWidth: Dp
) {
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
            Text(name, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Renders a single Sensor or Toggle tile, wired up to its live payload and edit/toggle actions. */
@Composable
private fun PanelTile(
    panel: Panel,
    groupId: String,
    payloads: Map<String, String>,
    app: MqttDashApplication,
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
