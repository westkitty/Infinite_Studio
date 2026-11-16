package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.features.VectorAndFoundryEngine
import com.example.model.StudioTool
import com.example.model.StudioUiState
import com.example.ui.theme.StudioColors
import com.example.viewmodel.StudioViewModel

private val WORKSPACE_TABS = listOf(
    "Surgery & Stage",
    "Vector & Foundry",
    "Material & Time",
    "Recipes & Forensics",
    "Vault, Branch & Director",
    "Constraints & Router"
)

/**
 * Clean tabbed tablet Studio Hub surfacing all 20 Creative Systems (F01-F10, P01-P10)
 * on top of the active CanonicalDocument without crowding the primary canvas.
 */
@Composable
fun StudioWorkspaceSheet(
    state: StudioUiState,
    selectedTab: Int,
    viewModel: StudioViewModel,
    onExportSvg: () -> Unit,
    onExportFoundry: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1000.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.FuturesDialogBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("studio_workspace_hub"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "STUDIO WORKSPACE HUB",
                            style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkyAccent, letterSpacing = 1.2.sp)
                        )
                        Text(
                            "20 Creative Engines · ${state.workspaceMode.label} Mode · rev ${state.document.revision}",
                            style = MaterialTheme.typography.titleLarge.copy(color = StudioColors.TextPrimary)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Workspace Hub",
                        tint = StudioColors.TextMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onClose)
                            .testTag("btn_close_hub")
                    )
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WORKSPACE_TABS.forEachIndexed { idx, title ->
                        val active = idx == selectedTab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) StudioColors.SkySoft else Color(0xFF1A242E))
                                .clickable { viewModel.toggleWorkspaceHub(open = true, tab = idx) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("hub_tab_$idx")
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (active) StudioColors.SkyDarkText else StudioColors.TextSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = StudioColors.FuturesDivider)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(410.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> SurgeryAndSpatialTab(state, viewModel, onClose)
                        1 -> VectorAndFoundryTab(state, viewModel, onExportSvg, onExportFoundry, onClose)
                        2 -> MaterialAndTimeTab(state, viewModel)
                        3 -> RecipesAndForensicsTab(state, viewModel)
                        4 -> VaultBranchDirectorTab(state, viewModel)
                        5 -> ConstraintsAndRouterTab(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurgeryAndSpatialTab(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onCloseHub: () -> Unit
) {
    var objName by remember { mutableStateOf("Hero Subject") }
    var yaw by remember(state.document.spatialStage.cameraYawDeg) { mutableFloatStateOf(state.document.spatialStage.cameraYawDeg) }
    var pitch by remember(state.document.spatialStage.cameraPitchDeg) { mutableFloatStateOf(state.document.spatialStage.cameraPitchDeg) }
    var zoom by remember(state.document.spatialStage.cameraZoom) { mutableFloatStateOf(state.document.spatialStage.cameraZoom) }

    Text("F01 Object Surgery (Non-Destructive Extraction, Mask Refine & Direct Gizmo)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = objName, onValueChange = { objName = it }, label = { Text("Object Name") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { viewModel.extractSelectionAsObject(objName) }, colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText), modifier = Modifier.testTag("btn_extract_object")) {
            Text("Extract Selection")
        }
        OutlinedButton(onClick = { onCloseHub(); viewModel.beginInteractiveTransform() }, modifier = Modifier.testTag("btn_begin_gizmo_tf")) {
            Text("Canvas Gizmo")
        }
    }
    state.document.semanticObjects.forEach { obj ->
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF151F28)).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${obj.name} (${obj.repairLabel})", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                Text("Bounds: (${obj.boundsX.toInt()}, ${obj.boundsY.toInt()}) ${obj.boundsW.toInt()}x${obj.boundsH.toInt()}", style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OutlinedButton(onClick = { viewModel.refineObjectMask(obj.id, 12f) }, modifier = Modifier.testTag("btn_mask_expand")) { Text("+Mask", fontSize = 10.sp) }
                OutlinedButton(onClick = { viewModel.refineObjectMask(obj.id, -10f) }, modifier = Modifier.testTag("btn_mask_shrink")) { Text("-Mask", fontSize = 10.sp) }
                OutlinedButton(onClick = { onCloseHub(); viewModel.beginInteractiveTransform(obj.id) }) { Text("Transform", fontSize = 10.sp) }
                OutlinedButton(onClick = { viewModel.revertSemanticObject(obj.id) }) { Text("Revert", fontSize = 10.sp) }
            }
        }
    }

    HorizontalDivider(color = StudioColors.FuturesDivider)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("F02 Spatial Stage (2.5D Depth Planes, Pitch/Yaw & Camera)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Stage Guides", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Switch(
                checked = state.document.spatialStage.enabled,
                onCheckedChange = viewModel::setSpatialGuidesEnabled,
                modifier = Modifier.testTag("switch_spatial_guides")
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        state.document.spatialStage.depthPlanes.forEach { plane ->
            OutlinedButton(onClick = { viewModel.assignActiveLayerDepthPlane(plane.id) }) {
                Text("Assign Active -> ${plane.name} (${plane.parallaxFactor}x)", fontSize = 10.5.sp)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Yaw ${yaw.toInt()}°", color = StudioColors.TextSecondary, modifier = Modifier.weight(0.22f), fontSize = 11.sp)
        Slider(value = yaw, onValueChange = { yaw = it }, valueRange = -25f..25f, modifier = Modifier.weight(0.78f))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pitch ${pitch.toInt()}°", color = StudioColors.TextSecondary, modifier = Modifier.weight(0.22f), fontSize = 11.sp)
        Slider(value = pitch, onValueChange = { pitch = it }, valueRange = -20f..20f, modifier = Modifier.weight(0.78f))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Zoom ${String.format("%.2f", zoom)}x", color = StudioColors.TextSecondary, modifier = Modifier.weight(0.22f), fontSize = 11.sp)
        Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 0.6f..1.6f, modifier = Modifier.weight(0.78f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.updateSpatialCamera(yaw, pitch, zoom) }, modifier = Modifier.testTag("btn_render_spatial")) { Text("Render 2.5D Composite") }
        OutlinedButton(onClick = viewModel::resetSpatialStageCamera, modifier = Modifier.testTag("btn_reset_spatial")) { Text("Reset Camera") }
    }
    state.spatialPreviewBitmap?.let { bmp ->
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "2.5D Spatial composite", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(10.dp)))
    }
}

@Composable
private fun VectorAndFoundryTab(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onExportSvg: () -> Unit,
    onExportFoundry: () -> Unit,
    onCloseHub: () -> Unit
) {
    var caption by remember { mutableStateOf("HELLO FUTURES") }
    val selVec = state.document.vectorObjects.find { it.id == state.selectedVectorId } ?: state.document.vectorObjects.lastOrNull()
    var editText by remember(selVec?.id) { mutableStateOf(selVec?.text ?: "") }
    var editFontSize by remember(selVec?.id) { mutableFloatStateOf(selVec?.fontSizeSp ?: 20f) }
    var editStrokeHex by remember(selVec?.id) { mutableStateOf(selVec?.strokeColorHex ?: "#38BDF8") }

    Text("F09 Vector & Typography Studio (${state.document.vectorObjects.size} objects)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Vector Text / Speech Balloon Copy") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("input_vector_text"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Button(onClick = { viewModel.addVectorTextBox(caption) }, modifier = Modifier.testTag("btn_add_textbox")) { Text("Add Text Box") }
        Button(onClick = { viewModel.addVectorSpeechBalloon(caption) }, modifier = Modifier.testTag("btn_add_balloon")) { Text("Add Speech Balloon") }
        OutlinedButton(onClick = viewModel::addSampleBezierPath) { Text("+ Bezier Arc") }
        OutlinedButton(onClick = { onCloseHub(); viewModel.selectTool(StudioTool.VECTOR_EDIT) }, modifier = Modifier.testTag("btn_open_vector_edit")) { Text("Edit on Canvas") }
    }

    if (state.document.vectorObjects.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            state.document.vectorObjects.forEach { v ->
                OutlinedButton(onClick = { viewModel.selectVectorObject(v.id) }) {
                    Text("${v.kind.name.take(4)}: ${v.text.ifBlank { v.id.take(6) }}", color = if (v.id == selVec?.id) StudioColors.SkyAccent else StudioColors.TextSecondary, fontSize = 10.sp)
                }
            }
        }
        selVec?.let { v ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, label = { Text("Edit Copy") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = editStrokeHex, onValueChange = { editStrokeHex = it }, label = { Text("Stroke #Hex") }, singleLine = true, modifier = Modifier.weight(0.75f))
                Button(
                    onClick = { viewModel.updateVectorProperties(v.id, text = editText, fontSizeSp = editFontSize, strokeHex = editStrokeHex) },
                    modifier = Modifier.testTag("btn_apply_vec_props")
                ) { Text("Update") }
                OutlinedButton(onClick = { viewModel.deleteVectorObject(v.id) }) { Text("Delete") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Font ${editFontSize.toInt()}sp", color = StudioColors.TextSecondary, fontSize = 11.sp)
                Slider(value = editFontSize, onValueChange = { editFontSize = it }, valueRange = 12f..48f, modifier = Modifier.weight(1f))
            }
        }
    }

    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("F08 Production Foundry & P09 Asset Workshop", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExportSvg, colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText), modifier = Modifier.testTag("btn_export_svg")) { Text("Export Artwork SVG") }
        Button(onClick = onExportFoundry, colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText), modifier = Modifier.testTag("btn_export_foundry")) { Text("Export Foundry Bundle (.zip)") }
    }
    Text("Workshop Templates:", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.TextSecondary))
    VectorAndFoundryEngine.WORKSHOP_TEMPLATES.forEach { tpl ->
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF151F28)).padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tpl.title, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
                Text(tpl.description, style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted))
            }
            OutlinedButton(onClick = { viewModel.applyWorkshopTemplate(tpl.id) }) { Text("Apply") }
        }
    }
}

@Composable
private fun RecipesAndForensicsTab(
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    Text("F04 Creative Recipes (Visual Acyclic DAG + Selective Invalidation)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    RecipeGraphEditor(
        state = state,
        onRunRecipe = viewModel::runCreativeRecipe,
        onCancelRecipe = viewModel::cancelCreativeRecipe,
        onSelectRecipePreset = viewModel::selectActiveRecipe,
        onSaveRecipePreset = viewModel::saveRecipePreset,
        onAddNode = viewModel::addRecipeNode,
        onConnectNodes = viewModel::connectRecipeNodes,
        onDisconnectNodes = viewModel::disconnectRecipeNodes,
        onMoveNode = viewModel::moveRecipeNode,
        onRemoveNode = viewModel::removeRecipeNode,
        onUpdateNodeParam = viewModel::updateRecipeNodeParam
    )

    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("F03 Living Preview", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val paused = state.livingPreview?.isPausedForBattery ?: false
        Text("Low-Power Pause", color = StudioColors.TextSecondary)
        Switch(checked = paused, onCheckedChange = viewModel::toggleLivingPreviewBatteryPause)
        Text(state.livingPreview?.statusText ?: "", style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted))
    }
    state.livingPreview?.bitmap?.let { bmp ->
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Living preview", contentScale = ContentScale.Fit, modifier = Modifier.height(105.dp).clip(RoundedCornerShape(8.dp)))
    }

    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("F07 Visual Forensics (Diff Heatmap & Mask Boundary Proof)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Button(onClick = viewModel::runVisualForensicsAudit, modifier = Modifier.testTag("btn_run_forensics")) { Text("Audit Base vs Active Layer") }
    state.forensicsReport?.let { rep ->
        Text("Inside Mask Changed: ${rep.changedPixelsInsideMask} px | Outside Mask Changed: ${rep.changedPixelsOutsideMask} px | Max Delta: ${rep.maxChannelDelta}", color = Color.White, fontSize = 12.sp)
        rep.advisoryChecklist.forEach { Text("• $it", color = StudioColors.TextSecondary, fontSize = 11.sp) }
        Image(bitmap = rep.heatmapBitmap.asImageBitmap(), contentDescription = "Forensics Heatmap", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(120.dp))
    }
}
