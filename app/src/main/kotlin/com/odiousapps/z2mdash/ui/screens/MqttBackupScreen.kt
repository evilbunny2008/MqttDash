package com.odiousapps.z2mdash.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.odiousapps.z2mdash.Z2mDashApplication
import com.odiousapps.z2mdash.data.BackupCodec

private const val DEFAULT_BACKUP_TOPIC = "z2mdash/backup"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MqttBackupScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()
    val payloads by app.connectionManager.latestPayloads.collectAsState()

    var mode by remember { mutableStateOf("Backup") }
    var selectedBrokerId by remember { mutableStateOf(config.brokers.firstOrNull()?.id ?: "") }
    var topic by remember { mutableStateOf(DEFAULT_BACKUP_TOPIC) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isWaitingForRestore by remember { mutableStateOf(false) }

    // Reacts on its own each recomposition once payloads (collected above)
    // includes something for this broker/topic - MQTT delivery isn't
    // instant, especially for a retained message right after subscribing.
    if (isWaitingForRestore) {
        val raw = payloads["$selectedBrokerId|$topic"]
        if (raw != null) {
            isWaitingForRestore = false
            try {
                val json = BackupCodec.decompressFromBase64(raw)
                app.configRepository.importJson(json)
                statusMessage = "Configuration restored from MQTT"
            } catch (_: Exception) {
                statusMessage = "Restore failed: payload on that topic wasn't a valid compressed backup"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup / Restore via MQTT") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (config.brokers.isEmpty()) {
                Text("Add a broker first (Settings \u2192 Brokers) before using this.")
                return@Column
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == "Backup",
                    onClick = { mode = "Backup"; statusMessage = null },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Backup") }
                SegmentedButton(
                    selected = mode == "Restore",
                    onClick = { mode = "Restore"; statusMessage = null },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Restore") }
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
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topic") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            if (mode == "Backup") {
                Text(
                    "Publishes your current brokers, groups and panels - gzip compressed, then " +
                        "base64-encoded so it travels as a normal MQTT payload - as a retained message " +
                        "on this topic.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val compressed = BackupCodec.compressToBase64(app.configRepository.exportJson())
                        app.connectionManager.publish(selectedBrokerId, topic, compressed, retain = true)
                        statusMessage = "Published (${compressed.length} bytes) to $topic"
                    },
                    enabled = topic.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Publish Backup") }
            } else {
                Text(
                    "Subscribes to this topic and restores from whatever compressed backup is retained " +
                        "there. This replaces your current brokers, groups and panels.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        statusMessage = null
                        isWaitingForRestore = true
                        app.connectionManager.subscribe(selectedBrokerId, topic)
                    },
                    enabled = topic.isNotBlank() && !isWaitingForRestore,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore from Topic") }
            }

            if (isWaitingForRestore) {
                Spacer(Modifier.height(16.dp))
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for data on $topic\u2026", style = MaterialTheme.typography.bodySmall)
                }
            }

            statusMessage?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
