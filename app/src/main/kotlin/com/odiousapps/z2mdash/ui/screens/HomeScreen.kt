package com.odiousapps.z2mdash.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()
    val payloads by app.connectionManager.latestPayloads.collectAsState()
    val timestamps by app.connectionManager.latestPayloadTimestamps.collectAsState()

    // Ticks every second so "N seconds ago" counts up smoothly and resets the
    // moment a fresh MQTT message (or last_seen field) actually arrives - the
    // ageText computation below always re-derives from whichever timestamp is
    // most recent, so a new message naturally overrides a stale running count.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000.milliseconds)
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
    var pendingClusterDelete by remember { mutableStateOf<PendingClusterDelete?>(null) }

    // Standalone (non-clustered) panels, and panels inside a cluster card, both
    // lay out as an exact 3-column grid. Tile width is capped at a sensible
    // maximum rather than always scaling proportionally to screen size -
    // otherwise a tablet's shorter dimension is still much bigger than a
    // phone's, so tiles (and whole clusters) end up oversized there too,
    // leaving no room for a second cluster even on a wide screen. A fixed cap
    // keeps clusters a consistent, comfortable size on any device, so the
    // manual row-packing below (see packedRows) can fit as many side-by-side
    // as actually fit, rather than relying on FlowRow's own wrapping logic.
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val screenHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val columnsPerRow = 3
    val standaloneTileWidth = run {
        val referenceWidthDp = minOf(screenWidthDp, screenHeightDp)
        val groupHorizontalPadding = 12.dp * 2
        val gapsBetweenColumns = 8.dp * (columnsPerRow - 1)
        val proportionalWidth = (referenceWidthDp - groupHorizontalPadding - gapsBetweenColumns) / columnsPerRow
        minOf(proportionalWidth, 110.dp)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            // Extra bottom padding so the last group's trailing icons (add
            // panel, delete group) can scroll clear of the FAB rather than
            // sitting underneath it - the FAB floats on top of content and
            // doesn't reserve space for itself otherwise.
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(config.pendingAutoConfigDevices, key = { "${it.brokerId}|${it.appConfigTopic}" }) { pending ->
                PendingDeviceBanner(
                    pending = pending,
                    onAdd = {
                        addPendingDevice(app, config, payloads, pending)
                        // Both actions mean the user has already handled this
                        // prompt via the in-app banner, so the matching system
                        // notification (posted with the same deviceName-based
                        // ID) shouldn't keep lingering in the shade too.
                        NotificationManagerCompat.from(context).cancel(pending.deviceName.hashCode())
                    },
                    onIgnore = {
                        app.configRepository.ignoreAppConfigTopic(pending.brokerId, pending.appConfigTopic)
                        NotificationManagerCompat.from(context).cancel(pending.deviceName.hashCode())
                    }
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
                        // Panels sharing a non-blank clusterName render together in one
                        // card; panels with a blank clusterName stay as standalone tiles,
                        // each getting its own unique bucket so they don't merge together.
                        val clusters = LinkedHashMap<String, MutableList<Panel>>()
                        group.panels.forEach { panel ->
                            val key = panel.clusterName.ifBlank { "__single__${panel.id}" }
                            clusters.getOrPut(key) { mutableListOf() }.add(panel)
                        }
                        // Sort clusters/standalone tiles by their lowest displayOrder (falls
                        // back to insertion order for anything left at the Int.MAX_VALUE default),
                        // and also sort each cluster's own panels by displayOrder so panels can
                        // be reordered *within* a cluster, not just relative to other clusters.
                        val orderedClusters = clusters.values
                            .map { bucket -> bucket.sortedBy { it.displayOrder } }
                            .sortedBy { bucket -> bucket.minOf { it.displayOrder } }

                        // Manually pack clusters/tiles into rows rather than relying on
                        // FlowRow's own wrapping - computed directly against each item's
                        // known width, so multiple clusters land on the same row whenever
                        // they actually fit, on any screen size or orientation.
                        val clusterCardWidth = standaloneTileWidth * columnsPerRow + 8.dp * (columnsPerRow - 1)
                        val availableRowWidth = screenWidthDp - 24.dp
                        val packedRows = remember(orderedClusters, standaloneTileWidth, availableRowWidth) {
                            val rows = mutableListOf<MutableList<List<Panel>>>()
                            var currentRow = mutableListOf<List<Panel>>()
                            var usedWidth = 0.dp
                            orderedClusters.forEach { panelsInCluster ->
                                val itemWidth = if (panelsInCluster.first().clusterName.isBlank()) {
                                    standaloneTileWidth
                                } else {
                                    clusterCardWidth
                                }
                                val gapNeeded = if (currentRow.isEmpty()) 0.dp else 8.dp
                                if (currentRow.isNotEmpty() && usedWidth + gapNeeded + itemWidth > availableRowWidth) {
                                    rows.add(currentRow)
                                    currentRow = mutableListOf()
                                    usedWidth = 0.dp
                                }
                                if (currentRow.isNotEmpty()) usedWidth += 8.dp
                                currentRow.add(panelsInCluster)
                                usedWidth += itemWidth
                            }
                            if (currentRow.isNotEmpty()) rows.add(currentRow)
                            rows
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            packedRows.forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { panelsInCluster ->
                                        val name = panelsInCluster.first().clusterName
                                        // Stable per-cluster identity, independent of list
                                        // position - without this, Compose can reuse another
                                        // cluster's remembered state (like ageText) when the
                                        // list reorders as devices are added/removed, since
                                        // it otherwise identifies composables by call position.
                                        key(panelsInCluster.first().id) {
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
                                                    tileWidth = standaloneTileWidth,
                                                    onDelete = {
                                                        pendingClusterDelete = PendingClusterDelete(
                                                            groupId = group.id,
                                                            name = name,
                                                            panelIds = panelsInCluster.map { it.id }
                                                        )
                                                    }
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

    pendingClusterDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingClusterDelete = null },
            title = { Text("Delete \"${pending.name}\"?") },
            text = { Text("This removes all ${pending.panelIds.size} panels for this device.") },
            confirmButton = {
                TextButton(onClick = {
                    app.configRepository.removePanels(pending.groupId, pending.panelIds)
                    pendingClusterDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingClusterDelete = null }) { Text("Cancel") } }
        )
    }
}

private data class PendingClusterDelete(val groupId: String, val name: String, val panelIds: List<String>)

/**
 * If this cluster's panels belong to an auto-configured device, publishes an
 * updated (retained) "/app" payload reflecting the new panel order, so the
 * device's own config topic stays in sync with a manual reorder - otherwise,
 * a future republish of that topic (for an unrelated reason) would rebuild
 * the panels using the device's original order, silently undoing this.
 */
private fun pushOrderUpdateIfAutoConfigured(
    app: Z2mDashApplication,
    payloads: Map<String, String>,
    orderedPanelIds: List<String>,
    clusterPanels: List<Panel>
) {
    val config = app.configRepository.config.value
    val panelIdSet = orderedPanelIds.toSet()
    val device = config.autoConfiguredDevices.find { it.createdPanelIds.any { id -> id in panelIdSet } } ?: return
    val currentPayload = payloads["${device.brokerId}|${device.appConfigTopic}"] ?: return

    val orderByFieldOrLabel: Map<String, Int> = orderedPanelIds.withIndex().mapNotNull { (index, id) ->
        val panel = clusterPanels.find { it.id == id } ?: return@mapNotNull null
        val key = when (panel) {
            is Panel.Sensor -> panel.jsonPath
            is Panel.Toggle -> panel.label
        }
        key to index
    }.toMap()

    val updatedPayload = SensorDiscovery.updateOrderingInAppPayload(currentPayload, orderByFieldOrLabel) ?: return
    app.connectionManager.publish(device.brokerId, device.appConfigTopic, updatedPayload, retain = true)
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
    tileWidth: Dp,
    onDelete: () -> Unit
) {
    val ageText = remember(panels, payloads, timestamps, nowMillis) {
        fun topicFor(panel: Panel): String? = when (panel) {
            is Panel.Sensor -> panel.topic
            is Panel.Toggle -> panel.stateTopic.takeIf { it.isNotBlank() }
        }

        // Prefer the device's own reported time (Zigbee2MQTT's "last_seen" field)
        // over our app's receipt time - it reflects when the device itself last
        // reported in, not just when this app instance happened to receive a
        // message (which can be bumped by things unrelated to real freshness,
        // like a broker redelivering a retained message on resubscribe).
        val deviceReportedTimestamps = panels.mapNotNull { panel ->
            val topic = topicFor(panel) ?: return@mapNotNull null
            payloads["${panel.brokerId}|$topic"]
                ?.let { JsonPath.extract(it, "last_seen") }
                ?.let { JsonPath.parseIso8601(it) }
        }

        // Only fall back to receipt time if NONE of this cluster's panels have a
        // genuine last_seen anywhere - otherwise a config-only topic without one
        // (like the device's own "/app" topic) could drag the cluster's displayed
        // freshness down just because its unrelated topic happened to update.
        val latestTimestamp = if (deviceReportedTimestamps.isNotEmpty()) {
            deviceReportedTimestamps.max()
        } else {
            panels.mapNotNull { panel ->
                val topic = topicFor(panel) ?: return@mapNotNull null
                timestamps["${panel.brokerId}|$topic"]
            }.maxOrNull()
        }

        latestTimestamp?.let {
            DateUtils.getRelativeTimeSpanString(it, nowMillis, DateUtils.SECOND_IN_MILLIS).toString()
        }
    }

    // Drag-to-reorder state, local to this one cluster card. Long-press
    // directly on a tile starts the drag (via detectDragGesturesAfterLongPress
    // on each tile's own modifier chain, ahead of that tile's plain
    // short-press-to-edit clickable) - no separate reorder-mode toggle or icon
    // needed. Panels render in a plain Column/Row here (not a LazyColumn), so
    // unlike the Groups screen's drag-reorder there's no LazyListState
    // scroll-anchor tracking to fight, but the same "don't actually move
    // anything during the drag" principle still applies for its own sake: it
    // keeps the interaction simple and avoids offset-compensation math
    // entirely. Rows stay static; only the dragged tile is highlighted, and
    // the drop target tile gets an outline. The real reorder - and, if this
    // cluster came from an auto-configured device, a republished /app message
    // reflecting the new order - commits once, when the drag ends.
    var draggedPanelId by remember { mutableStateOf<String?>(null) }
    var draggedFromIndex by remember { mutableIntStateOf(-1) }
    var draggedToIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var tileWidthPx by remember { mutableFloatStateOf(0f) }
    var tileHeightPx by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        // Every row gets the same fixed width (a full 3-column row), regardless
        // of how many panels actually land in it - a short trailing row, or a
        // whole cluster with fewer than 3 panels, then centers within that
        // fixed width instead of bunching to the left with empty space beside it.
        val fullRowWidth = tileWidth * columns + 8.dp * (columns - 1)
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            panels.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.width(fullRowWidth)
                ) {
                    row.forEachIndexed { columnIndex, panel ->
                        val panelIndex = rowIndex * columns + columnIndex
                        val isDragging = draggedPanelId == panel.id
                        val isDropTarget = draggedPanelId != null &&
                            draggedPanelId != panel.id &&
                            panelIndex == draggedToIndex

                        Box(
                            modifier = Modifier
                                .width(tileWidth)
                                .then(
                                    if (isDropTarget) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            PanelTile(
                                panel = panel,
                                groupId = groupId,
                                payloads = payloads,
                                app = app,
                                navController = navController,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (isDragging) 0.5f else 1f)
                                    .onGloballyPositioned { coordinates ->
                                        if (tileWidthPx == 0f) tileWidthPx = coordinates.size.width.toFloat()
                                        if (tileHeightPx == 0f) tileHeightPx = coordinates.size.height.toFloat()
                                    }
                                    .pointerInput(panel.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedPanelId = panel.id
                                                draggedFromIndex = panelIndex
                                                draggedToIndex = panelIndex
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                val fromIndex = draggedFromIndex
                                                val toIndex = draggedToIndex
                                                if (toIndex != fromIndex && fromIndex >= 0 && toIndex >= 0) {
                                                    val reordered = panels.toMutableList()
                                                    val moved = reordered.removeAt(fromIndex)
                                                    reordered.add(toIndex, moved)
                                                    val orderedIds = reordered.map { it.id }
                                                    app.configRepository.reorderPanelsInCluster(groupId, orderedIds)
                                                    pushOrderUpdateIfAutoConfigured(app, payloads, orderedIds, panels)
                                                }
                                                draggedPanelId = null
                                                draggedFromIndex = -1
                                                draggedToIndex = -1
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedPanelId = null
                                                draggedFromIndex = -1
                                                draggedToIndex = -1
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x
                                                dragOffsetY += dragAmount.y
                                                val w = tileWidthPx
                                                val h = tileHeightPx
                                                if (w > 0f && h > 0f) {
                                                    val columnDelta = (dragOffsetX / w).roundToInt()
                                                    val rowDelta = (dragOffsetY / h).roundToInt()
                                                    val linearDelta = rowDelta * columns + columnDelta
                                                    draggedToIndex = (draggedFromIndex + linearDelta)
                                                        .coerceIn(0, panels.lastIndex)
                                                }
                                            }
                                        )
                                    }
                            )
                        }
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
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete $name",
                        modifier = Modifier.size(16.dp)
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
                onEdit = { navController.navigate("group/$groupId/panel/${panel.id}") }
            )
        }

        is Panel.Toggle -> {
            val statePayload = payloads["${panel.brokerId}|${panel.stateTopic}"]
            val resolvedState = statePayload?.let { JsonPath.extract(it, panel.stateJsonPath) }
            // onPayload might be a whole JSON command like {"state":"OPEN"}, not
            // just the bare value the state topic reports back - pull the same
            // field back out of it (via the same stateJsonPath) to get a fair
            // comparison. Falls back to the raw onPayload string for simple
            // non-JSON commands like a bare "ON", where extraction fails.
            val expectedOnValue = JsonPath.extract(panel.onPayload, panel.stateJsonPath) ?: panel.onPayload
            val isOn = resolvedState != null && resolvedState.equals(expectedOnValue, ignoreCase = true)
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
                onEdit = { navController.navigate("group/$groupId/panel/${panel.id}") }
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
    app.configRepository.applyDeviceAutoConfig(
        oldPanelIds = emptySet(),
        updatedDevice = device,
        targetGroupId = targetGroupId,
        newPanels = newPanels
    )
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
