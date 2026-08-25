package com.odiousapps.z2mdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    // state) is enough.
    //
    // Deliberately doesn't move/offset any row during the drag at all -
    // earlier versions tried reordering the real list mid-drag (which made
    // LazyListState's internal scroll-anchor tracking "chase" the moved
    // item, causing unwanted auto-scrolling) and then tried purely visual
    // per-row offsets (which fought the same anchor-preservation machinery
    // in subtler ways). Instead, every row stays exactly where it is for
    // the whole gesture, the dragged row just gets a highlight, and a
    // colored insertion line shows where it would land. Nothing about the
    // layout changes until the drag ends, at which point a single clean
    // reorder is committed and the list updates normally.
    var draggedGroupId by remember { mutableStateOf<String?>(null) }
    var draggedFromIndex by remember { mutableIntStateOf(-1) }
    var draggedToIndex by remember { mutableIntStateOf(-1) }
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
                val naturalIndex = config.groups.indexOfFirst { it.id == group.id }
                val isDragging = draggedGroupId == group.id
                val isInsertionTarget = draggedGroupId != null &&
                    draggedGroupId != group.id &&
                    naturalIndex == draggedToIndex

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
                                            draggedFromIndex = naturalIndex
                                            draggedToIndex = naturalIndex
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            if (draggedToIndex != draggedFromIndex && draggedToIndex >= 0) {
                                                app.configRepository.moveGroupToIndex(group.id, draggedToIndex + 1)
                                            }
                                            draggedGroupId = null
                                            draggedFromIndex = -1
                                            draggedToIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedGroupId = null
                                            draggedFromIndex = -1
                                            draggedToIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val height = rowHeightPx
                                            if (height > 0f) {
                                                draggedToIndex = (draggedFromIndex + (dragOffsetY / height).roundToInt())
                                                    .coerceIn(0, config.groups.lastIndex)
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
                                enabled = naturalIndex > 0
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { app.configRepository.moveGroup(group.id, 1) },
                                enabled = naturalIndex < config.groups.lastIndex
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    },
                    colors = if (isDragging) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        // Measures the *whole row*, not just the drag handle -
                        // the handle alone is much shorter than the full
                        // ListItem, so using its height as "how tall is one
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
                )
                if (isInsertionTarget) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    HorizontalDivider()
                }
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
