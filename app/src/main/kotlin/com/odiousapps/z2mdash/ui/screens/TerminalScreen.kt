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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.odiousapps.z2mdash.Z2mDashApplication
import com.odiousapps.z2mdash.mqtt.LoggedMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration.Companion.milliseconds

private const val ALL_BROKERS = "__all__"
private const val MAX_LOGGED_MESSAGES_LABEL = "300"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()
    val messageLog by app.connectionManager.messageLog.collectAsState()

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

    val filtered = remember(messageLog, filterText, selectedBrokerId) {
        messageLog.asReversed().filter { entry ->
            (selectedBrokerId == ALL_BROKERS || entry.brokerId == selectedBrokerId) &&
                (filterText.isBlank() ||
                    entry.topic.contains(filterText, ignoreCase = true) ||
                    entry.payload.contains(filterText, ignoreCase = true))
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // Only recomposes when this actually flips true/false, not on every pixel
    // scrolled - the button just needs to know "am I away from the top".
    val showJumpToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Whether the user is currently sitting at the top, wanting to keep seeing
    // the latest entry. Updated only once a genuine interactive scroll/fling
    // settles (isScrollInProgress) - a passive index shift from new content
    // being inserted above (LazyColumn's key-based anchoring otherwise keeps
    // whatever was visible in the same visual spot, which would silently
    // scroll the user away from a brand new top item) never sets this false,
    // since that isn't a real scroll gesture.
    var isAnchoredToTop by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (!inProgress) {
                isAnchoredToTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            }
        }
    }

    // New entries render at index 0 (newest first) - jump back there whenever
    // one arrives while anchored to top, so the latest message actually stays
    // in view instead of being pushed off-screen by the anchoring above.
    var lastEntryCount by remember { mutableIntStateOf(filtered.size) }
    LaunchedEffect(filtered.size) {
        if (filtered.size != lastEntryCount) {
            if (isAnchoredToTop) {
                listState.scrollToItem(0)
            }
            lastEntryCount = filtered.size
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
            AnimatedVisibility(visible = showJumpToTop, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = {
                    // Set synchronously rather than waiting for the async
                    // scroll-settle detection below to catch up - if new
                    // messages keep arriving while the animation is still in
                    // flight, the size-change effect needs to already see
                    // "anchored" as true, or it'll skip re-scrolling and the
                    // view ends up stuck away from the true top.
                    isAnchoredToTop = true
                    coroutineScope.launch { listState.scrollToItem(0) }
                }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Jump to newest")
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
                "${filtered.size} of ${messageLog.size} messages (newest first, last $MAX_LOGGED_MESSAGES_LABEL kept)",
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
