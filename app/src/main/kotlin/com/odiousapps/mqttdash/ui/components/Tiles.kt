package com.odiousapps.mqttdash.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odiousapps.mqttdash.data.TileIcon

/** Whether a sensor's current value has drifted outside its configured ideal range. */
enum class SensorAlert { NONE, BELOW_MIN, ABOVE_MAX }

private val AlertRed = Color(0xFFE53935)
private val AlertBlue = Color(0xFF1E88E5)

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
    alert: SensorAlert = SensorAlert.NONE,
    onLongPress: (() -> Unit)? = null
) {
    val alertColor = when (alert) {
        SensorAlert.BELOW_MIN -> AlertRed
        SensorAlert.ABOVE_MAX -> AlertBlue
        SensorAlert.NONE -> null
    }

    val backgroundColor = if (alertColor != null) {
        val infiniteTransition = rememberInfiniteTransition(label = "sensorAlert")
        val flashFraction = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flashFraction"
        ).value
        lerp(MaterialTheme.colorScheme.surfaceVariant, alertColor, flashFraction)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress?.invoke() }),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = if (alertColor != null) 0.dp else 1.dp
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
