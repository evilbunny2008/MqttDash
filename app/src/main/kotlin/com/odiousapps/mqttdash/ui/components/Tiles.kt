package com.odiousapps.mqttdash.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odiousapps.mqttdash.data.TileIcon

private fun iconFor(tileIcon: TileIcon): ImageVector = when (tileIcon) {
    TileIcon.HUMIDITY -> Icons.Default.WaterDrop
    TileIcon.MOISTURE -> Icons.Default.Opacity
    TileIcon.TEMPERATURE -> Icons.Default.Thermostat
    TileIcon.SIGNAL -> Icons.Default.SignalWifi4Bar
    TileIcon.POWER -> Icons.Default.Power
    TileIcon.GAUGE -> Icons.Default.Speed
    TileIcon.BATTERY -> Icons.Default.BatteryFull
    TileIcon.LIGHT -> Icons.Default.Lightbulb
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SensorTile(
    modifier: Modifier = Modifier,
    icon: TileIcon,
    value: String,
    unit: String,
    label: String,
    onLongPress: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress?.invoke() }),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(iconFor(icon), contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(
                if (unit.isNotBlank()) "$value $unit" else value,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToggleTile(
    modifier: Modifier = Modifier,
    icon: TileIcon,
    label: String,
    isOn: Boolean,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.combinedClickable(onClick = onToggle, onLongClick = { onLongPress?.invoke() }),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(iconFor(icon), contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Switch(checked = isOn, onCheckedChange = { onToggle() })
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
