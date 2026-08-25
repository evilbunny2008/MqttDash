package com.odiousapps.z2mdash.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.odiousapps.z2mdash.Z2mDashApplication
import com.odiousapps.z2mdash.mqtt.LoggedMessage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.milliseconds

private const val ALL_BROKERS = "__all__"
private const val MAX_LOGGED_MESSAGES_LABEL = "300"

/** Everything the auto-scroll effect needs to decide whether to jump, computed fresh on every check - see the LaunchedEffect in TerminalScreen. */
private data class ScrollCheck(val rawSize: Int, val request: Int, val filteredSize: Int, val isAtBottom: Boolean)

/** Shared by the rendered list and the auto-scroll effect, so they can never disagree on what's currently showing. */
private fun filterMessages(log: List<LoggedMessage>, filterText: String, brokerId: String): List<LoggedMessage> =
    log.filter { entry ->
        (brokerId == ALL_BROKERS || entry.brokerId == brokerId) &&
            (filterText.isBlank() ||
                entry.topic.contains(filterText, ignoreCase = true) ||
                entry.payload.contains(filterText, ignoreCase = true))
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()
    // Kept as the raw State object (not the `by`-delegated value) specifically
    // so snapshotFlow below can observe it directly - see the LaunchedEffect
    // further down for why.
    val messageLogState = app.connectionManager.messageLog.collectAsState()
    val messageLog = messageLogState.value

    var filterText by remember { mutableStateOf("") }
    var selectedBrokerId by remember { mutableStateOf(ALL_BROKERS) }

    // Ticks once a second so the relative timestamps ("3s ago") stay live,
    // same approach as the dashboard's cluster age display.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000.milliseconds)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Chronological order (oldest first, newest last) - matches how
    // MqttConnectionManager already stores the log (appends to the end), and
    // lets new entries land at the bottom without disturbing anything above,
    // unlike the earlier newest-first/prepend approach where every new
    // message required fighting LazyColumn's own anchor-preservation just to
    // stay visible.
    val filtered = remember(messageLog, filterText, selectedBrokerId) {
        filterMessages(messageLog, filterText, selectedBrokerId)
    }

    val listState = rememberLazyListState()
    // Only recomposes when this actually flips true/false, not on every pixel
    // scrolled - the button just needs to know "am I away from the bottom".
    val showJumpToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible == null || lastVisible.index != layoutInfo.totalItemsCount - 1
        }
    }

    // New entries append at the end - jump there whenever one arrives while
    // the user is genuinely at the bottom, OR when they explicitly tap the
    // jump button below. A single long-lived effect (keyed on listState,
    // which never changes) reacting to a merged snapshotFlow, rather than
    // LaunchedEffect(filtered.size) - that would restart (cancel + relaunch)
    // its whole coroutine on every single incoming message, and on a busy
    // broker, each new message could cancel the previous scroll before it
    // settles, thrashing the main thread hard enough to trigger an ANR.
    // Critically, this is also the *only* place in the whole screen that
    // ever calls scrollToItem on this listState - the FAB below just bumps
    // a counter rather than launching its own competing coroutine, so two
    // scroll calls can never race for the same underlying scroll mutex.
    //
    // Everything this needs - filtered size, and whether the user is
    // currently at the bottom - is recomputed fresh *inside* the snapshotFlow
    // lambda by reading listState/messageLogState/filterText/selectedBrokerId
    // directly, rather than closing over any composable-scope val like
    // `filtered` or `showJumpToBottom`. This LaunchedEffect only launches
    // once, so a captured closure variable would stay frozen at whatever it
    // was during the very first composition, silently going stale forever
    // instead of tracking the list as it actually changes.
    var lastMessageLogSize by remember { mutableIntStateOf(-1) }
    var jumpToBottomRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        var lastHandledRequest = jumpToBottomRequest
        snapshotFlow {
            val currentFilteredSize = filterMessages(messageLogState.value, filterText, selectedBrokerId).size
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            val isAtBottom = lastVisible != null && lastVisible.index == layoutInfo.totalItemsCount - 1
            ScrollCheck(messageLogState.value.size, jumpToBottomRequest, currentFilteredSize, isAtBottom)
        }.collect { check ->
            val newMessageArrived = check.rawSize != lastMessageLogSize
            val manualRequest = check.request != lastHandledRequest
            val isFirstLoad = lastMessageLogSize == -1
            lastMessageLogSize = check.rawSize
            lastHandledRequest = check.request
            if (check.filteredSize > 0 &&
                (manualRequest || isFirstLoad || (newMessageArrived && check.isAtBottom))
            ) {
                listState.scrollToItem(check.filteredSize - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                actions = {
                    IconButton(onClick = { app.connectionManager.clearMessageLog() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear log")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showJumpToBottom, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = { jumpToBottomRequest++ }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Jump to newest")
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
            if (config.brokers.size > 1) {
                var brokerExpanded by remember { mutableStateOf(false) }
                val selectedName = if (selectedBrokerId == ALL_BROKERS) {
                    "All brokers"
                } else {
                    config.brokers.find { it.id == selectedBrokerId }?.name ?: "All brokers"
                }
                ExposedDropdownMenuBox(expanded = brokerExpanded, onExpandedChange = { brokerExpanded = it }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selectedName,
                        onValueChange = {},
                        label = { Text("Broker") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brokerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = brokerExpanded, onDismissRequest = { brokerExpanded = false }) {
                        DropdownMenuItem(text = { Text("All brokers") }, onClick = {
                            selectedBrokerId = ALL_BROKERS
                            brokerExpanded = false
                        })
                        config.brokers.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = {
                                selectedBrokerId = b.id
                                brokerExpanded = false
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text("Filter by topic or payload") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Text(
                "${filtered.size} of ${messageLog.size} messages (oldest first, last $MAX_LOGGED_MESSAGES_LABEL kept)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            if (messageLog.isEmpty()) {
                Text(
                    "No messages received yet. Make sure a broker is connected and subscribed " +
                        "(check Brokers in Settings, or that its base topic matches your setup).",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (filtered.isEmpty()) {
                Text("No messages match that filter.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { "${it.timestamp}|${it.topic}|${it.brokerId}" }) { entry ->
                        MessageRow(
                            entry = entry,
                            brokerName = if (config.brokers.size > 1) {
                                config.brokers.find { it.id == entry.brokerId }?.name
                            } else null,
                            nowMillis = nowMillis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(entry: LoggedMessage, brokerName: String?, nowMillis: Long) {
    val displayPayload = remember(entry.payload) { prettyPrintIfJson(entry.payload) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    entry.topic,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.timestamp, nowMillis, DateUtils.SECOND_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (brokerName != null) {
                Text(brokerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                displayPayload,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/** Reformats valid JSON payloads with indentation for readability; anything else (or invalid JSON) is shown as-is. */
private fun prettyPrintIfJson(payload: String): String = try {
    val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
    val element = Json.parseToJsonElement(payload)
    prettyJson.encodeToString(JsonElement.serializer(), element)
} catch (_: Exception) {
    payload
}
