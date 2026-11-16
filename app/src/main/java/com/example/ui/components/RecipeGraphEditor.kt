package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.document.RecipeGraph
import com.example.document.RecipeNodeKind
import com.example.features.TimeAndRecipeEngine
import com.example.model.StudioUiState
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

/**
 * F04 Visual Creative Recipe DAG Editor:
 * Interactive node-graph canvas with Bezier wires, node drag, tap-to-connect + cycle rejection,
 * per-node status badges (CACHED / INVALIDATED / ERROR / PENDING), parameter editing, and presets.
 */
@Composable
fun RecipeGraphEditor(
    state: StudioUiState,
    onRunRecipe: () -> Unit,
    onCancelRecipe: () -> Unit,
    onSelectRecipePreset: (String) -> Unit,
    onSaveRecipePreset: (String, String) -> Unit,
    onAddNode: (String, RecipeNodeKind, String) -> Unit,
    onConnectNodes: (String, String, String) -> Unit,
    onDisconnectNodes: (String, String, String) -> Unit,
    onMoveNode: (String, String, Float, Float) -> Unit,
    onRemoveNode: (String, String) -> Unit,
    onUpdateNodeParam: (String, String, String, String) -> Unit
) {
    val activeRaw = state.document.recipes.firstOrNull() ?: RecipeGraph()
    val graph = remember(activeRaw) { TimeAndRecipeEngine.ensureNodeLayout(activeRaw) }
    val invalidatedIds = remember(state.document, graph) {
        TimeAndRecipeEngine.computeInvalidatedNodeIds(state.document, graph.id)
    }

    var selectedNodeId by remember(graph.id) { mutableStateOf(graph.nodes.firstOrNull()?.id) }
    var pendingConnectFromId by remember(graph.id) { mutableStateOf<String?>(null) }
    var presetName by remember { mutableStateOf("Studio Preset") }
    var newNodeKind by remember { mutableStateOf(RecipeNodeKind.EXPORT_PREP) }

    val selectedNode = graph.nodes.find { it.id == selectedNodeId } ?: graph.nodes.firstOrNull()
    var editingParamKey by remember(selectedNode?.id) {
        mutableStateOf(selectedNode?.parameters?.keys?.firstOrNull() ?: "threshold")
    }
    var editingParamVal by remember(selectedNode?.id, selectedNode?.parameters) {
        mutableStateOf(selectedNode?.parameters?.get(editingParamKey) ?: "")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("recipe_graph_editor")) {
        // Preset Selector & Save
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.document.recipes.forEach { preset ->
                val active = preset.id == graph.id
                OutlinedButton(
                    onClick = { onSelectRecipePreset(preset.id) },
                    modifier = Modifier.testTag("recipe_preset_${preset.id}")
                ) {
                    Text(
                        preset.name,
                        color = if (active) StudioColors.SkyAccent else StudioColors.TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Toolbar: Execute / Cancel / Add Node / Save Preset
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRunRecipe,
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText),
                modifier = Modifier.testTag("btn_run_recipe")
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Execute DAG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onCancelRecipe, modifier = Modifier.testTag("btn_cancel_recipe")) {
                Icon(Icons.Filled.Stop, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel", fontSize = 11.sp)
            }
            RecipeNodeKind.entries.forEach { kind ->
                OutlinedButton(
                    onClick = { newNodeKind = kind },
                    modifier = Modifier.testTag("btn_kind_${kind.name.lowercase()}")
                ) {
                    Text(
                        kind.name.take(6),
                        color = if (newNodeKind == kind) StudioColors.AmberSoft else StudioColors.TextMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
            Button(
                onClick = { onAddNode(graph.id, newNodeKind, newNodeKind.name) },
                modifier = Modifier.testTag("btn_add_recipe_node")
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                Text("Add Node", fontSize = 11.sp)
            }
        }

        state.lastRecipeError?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x44EF4444))
                    .border(1.dp, Color(0xAAEF4444), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("recipe_error_banner")
            ) {
                Text("DAG Error: $err", color = Color(0xFFFCA5A5), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (pendingConnectFromId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x3338BDF8))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tap a target node's Link button to connect from '$pendingConnectFromId'", color = StudioColors.SkySoft, fontSize = 11.sp)
                OutlinedButton(onClick = { pendingConnectFromId = null }) { Text("Cancel Link", fontSize = 10.sp) }
            }
        }

        // Interactive Graph Canvas
        val maxContentWidthDp = remember(graph.nodes) {
            val maxX = graph.nodes.maxOfOrNull { it.posX } ?: 400f
            (maxX + 260f).coerceAtLeast(780f).dp
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(255.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0A1118))
                .border(1.dp, Color(0xFF283A4A), RoundedCornerShape(14.dp))
                .horizontalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.width(maxContentWidthDp).height(255.dp)) {
                // Draw Bezier DAG Wires
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val nodeMap = graph.nodes.associateBy { it.id }
                    val nodeW = 158.dp.toPx()
                    val nodeH = 74.dp.toPx()
                    graph.nodes.forEach { target ->
                        val end = Offset(target.posX, target.posY + nodeH * 0.5f)
                        target.inputNodeIds.forEach { upId ->
                            val src = nodeMap[upId] ?: return@forEach
                            val start = Offset(src.posX + nodeW, src.posY + nodeH * 0.5f)
                            val dx = kotlin.math.max(48f, kotlin.math.abs(end.x - start.x) * 0.45f)
                            val path = Path().apply {
                                moveTo(start.x, start.y)
                                cubicTo(start.x + dx, start.y, end.x - dx, end.y, end.x, end.y)
                            }
                            drawPath(path, color = Color(0xFF38BDF8), style = Stroke(width = 2.5.dp.toPx()))
                        }
                    }
                }

                // Positioned Draggable Node Cards
                graph.nodes.forEach { node ->
                    val res = graph.results[node.id]
                    val isErr = res?.status?.startsWith("ERROR") == true
                    val isInvalidated = node.id in invalidatedIds
                    val statusLabel = when {
                        isErr -> "ERROR"
                        res == null -> "PENDING"
                        isInvalidated -> "INVALIDATED"
                        else -> "CACHED"
                    }
                    val statusColor = when (statusLabel) {
                        "CACHED" -> Color(0xFF34D399)
                        "INVALIDATED" -> Color(0xFFFBBF24)
                        "ERROR" -> Color(0xFFF87171)
                        else -> Color(0xFF94A3B8)
                    }
                    val selected = node.id == selectedNode?.id
                    val isLinkSrc = pendingConnectFromId == node.id

                    Column(
                        modifier = Modifier
                            .offset { IntOffset(node.posX.roundToInt(), node.posY.roundToInt()) }
                            .size(width = 160.dp, height = 84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color(0xFF1A2B3C) else Color(0xFF131D28))
                            .border(
                                width = if (selected || isLinkSrc) 2.dp else 1.dp,
                                color = if (isLinkSrc) StudioColors.AmberAccent else if (selected) StudioColors.SkyAccent else statusColor.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedNodeId = node.id }
                            .pointerInput(graph.id, node.id) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onMoveNode(graph.id, node.id, node.posX + dragAmount.x, node.posY + dragAmount.y)
                                }
                            }
                            .padding(7.dp)
                            .testTag("recipe_node_${node.id}"),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                statusLabel,
                                color = statusColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Link,
                                    contentDescription = "Connect node",
                                    tint = if (isLinkSrc) StudioColors.AmberSoft else StudioColors.SkySoft,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            val fromId = pendingConnectFromId
                                            if (fromId == null) {
                                                pendingConnectFromId = node.id
                                            } else {
                                                pendingConnectFromId = null
                                                onConnectNodes(graph.id, fromId, node.id)
                                            }
                                        }
                                        .testTag("btn_link_node_${node.id}")
                                )
                                if (graph.nodes.size > 1) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove node",
                                        tint = StudioColors.TextMuted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onRemoveNode(graph.id, node.id) }
                                            .testTag("btn_remove_node_${node.id}")
                                    )
                                }
                            }
                        }
                        Text(node.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            node.parameters.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { node.kind.name },
                            color = StudioColors.TextMuted,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Selected Node Parameter Editor & Upstream Disconnect
        selectedNode?.let { node ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF151F28))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(0.9f)) {
                    Text("${node.title} (${node.kind.name})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (node.inputNodeIds.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            node.inputNodeIds.forEach { upId ->
                                OutlinedButton(onClick = { onDisconnectNodes(graph.id, upId, node.id) }) {
                                    Text("Unlink $upId", fontSize = 9.5.sp)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = editingParamVal,
                    onValueChange = { editingParamVal = it },
                    label = { Text("Param: $editingParamKey") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("input_recipe_param_val")
                )
                Button(
                    onClick = { onUpdateNodeParam(graph.id, node.id, editingParamKey, editingParamVal) },
                    modifier = Modifier.testTag("btn_apply_recipe_param")
                ) {
                    Text("Set Param", fontSize = 11.sp)
                }
            }
        }

        // Save Preset Strip
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text("Save Preset Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { onSaveRecipePreset(graph.id, presetName) },
                modifier = Modifier.testTag("btn_save_recipe_preset")
            ) {
                Text("Save Preset")
            }
        }

        Text(state.lastRecipeReportSummary, style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextSecondary))
    }
}
