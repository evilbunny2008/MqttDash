package com.odiousapps.z2mdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.odiousapps.z2mdash.Z2mDashApplication
import com.odiousapps.z2mdash.data.PanelGroup
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(navController: NavController) {
    val app = LocalContext.current.applicationContext as Z2mDashApplication
    val config by app.configRepository.config.collectAsState()

    var renamingGroup by remember { mutableStateOf<PanelGroup?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Drag-to-reorder state, shared across every row. Only one row can be
    // dragged at a time, so a single set of variables (rather than per-row
    // state) is enough. rowHeightPx is measured once from whichever row
    // happens to report its size first - rows are visually uniform, so this
    // is an accurate-enough basis for "how many rows has this drag crossed".
    var draggedGroupId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (config.groups.isEmpty()) {
            Text(
                "No groups yet \u2013 create one from the Home tab.",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(config.groups, key = { it.id }) { group ->
                val index = config.groups.indexOfFirst { it.id == group.id }
                val isDragging = draggedGroupId == group.id
                ListItem(
                    headlineContent = { Text(group.name) },
                    supportingContent = { Text("${group.panels.size} panel${if (group.panels.size == 1) "" else "s"}") },
                    leadingContent = {
                        DragHandle(
                            modifier = Modifier
                                .pointerInput(group.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedGroupId = group.id
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggedGroupId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedGroupId = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val height = rowHeightPx
                                            if (height > 0f) {
                                                val indexDelta = (dragOffsetY / height).roundToInt()
                                                if (indexDelta != 0) {
                                                    app.configRepository.moveGroup(group.id, indexDelta)
                                                    dragOffsetY -= indexDelta * height
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { app.configRepository.moveGroup(group.id, -1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { app.configRepository.moveGroup(group.id, 1) },
                                enabled = index < config.groups.lastIndex
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    },
                    modifier = Modifier
                        // Measures the *whole row*, not just the drag handle -
                        // the handle alone is much shorter than the full
                        // ListItem (headline + supporting text + trailing
                        // buttons), so using its height as "how tall is one
                        // row" badly undercounted, causing every unit of
                        // actual drag distance to register as crossing
                        // roughly twice as many rows as it really did.
                        .onGloballyPositioned { coordinates ->
                            if (rowHeightPx == 0f) rowHeightPx = coordinates.size.height.toFloat()
                        }
                        .clickable {
                            renamingGroup = group
                            renameText = group.name
                        }
                        .then(
                            if (isDragging) {
                                Modifier.zIndex(1f).offset { IntOffset(0, dragOffsetY.roundToInt()) }
                            } else {
                                Modifier
                            }
                        )
                )
                HorizontalDivider()
            }
        }
    }

    renamingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { renamingGroup = null },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        app.configRepository.upsertGroup(group.copy(name = renameText))
                    }
                    renamingGroup = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingGroup = null }) { Text("Cancel") }
            }
        )
    }
}

/** Three short bars, the standard "grip" visual for a drag handle - built from basic primitives rather than a named icon, to avoid depending on one specific icon's presence in this project's icon set. */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(1.dp))
            )
        }
    }
}
