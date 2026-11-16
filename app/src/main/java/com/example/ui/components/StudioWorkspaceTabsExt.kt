package com.example.ui.components

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.document.CharacterSheet
import com.example.document.ConstraintKind
import com.example.features.FuturesAndConstraintsEngine
import com.example.features.VaultApprovalFilter
import com.example.features.VaultBranchOrchardEngine
import com.example.model.StudioUiState
import com.example.services.ProviderMode
import com.example.ui.theme.StudioColors
import com.example.viewmodel.StudioViewModel
import kotlin.math.roundToInt

@Composable
internal fun MaterialAndTimeTab(
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    var matName by remember { mutableStateOf("Copper Specular") }
    var roughness by remember { mutableFloatStateOf(0.28f) }
    var metalness by remember { mutableFloatStateOf(0.75f) }
    var azimuth by remember { mutableFloatStateOf(45f) }
    var elevation by remember { mutableFloatStateOf(55f) }
    var emissiveHex by remember { mutableStateOf("#000000") }
    var emissionIntensity by remember { mutableFloatStateOf(0f) }
    var selectedMaskId by remember { mutableStateOf<String?>(state.activeSelectionMask?.id) }

    Text("F06 Material Intelligence (Non-Destructive Metadata + Optional Bake)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = matName, onValueChange = { matName = it }, label = { Text("Material") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = emissiveHex, onValueChange = { emissiveHex = it }, label = { Text("Emissive #Hex") }, singleLine = true, modifier = Modifier.weight(0.75f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Roughness: ${String.format("%.2f", roughness)}", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = roughness, onValueChange = { roughness = it }, valueRange = 0.05f..1f)
        }
        Column(Modifier.weight(1f)) {
            Text("Metalness: ${String.format("%.2f", metalness)}", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = metalness, onValueChange = { metalness = it }, valueRange = 0f..1f)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Light Azimuth: ${azimuth.toInt()}°", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = azimuth, onValueChange = { azimuth = it }, valueRange = 0f..360f)
        }
        Column(Modifier.weight(1f)) {
            Text("Elevation: ${elevation.toInt()}°", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = elevation, onValueChange = { elevation = it }, valueRange = 10f..88f)
        }
        Column(Modifier.weight(1f)) {
            Text("Emission: ${String.format("%.2f", emissionIntensity)}", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = emissionIntensity, onValueChange = { emissionIntensity = it }, valueRange = 0f..1f)
        }
    }
    if (state.document.masks.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = { selectedMaskId = null }) {
                Text("Full Layer", color = if (selectedMaskId == null) StudioColors.SkyAccent else StudioColors.TextMuted, fontSize = 10.sp)
            }
            state.document.masks.forEach { m ->
                OutlinedButton(onClick = { selectedMaskId = m.id }) {
                    Text(m.name, color = if (selectedMaskId == m.id) StudioColors.SkyAccent else StudioColors.TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        OutlinedButton(
            onClick = {
                viewModel.applyMaterialStudy(matName, roughness, metalness, 0.5f, azimuth, elevation, emissiveHex, emissionIntensity, selectedMaskId, bakeLayer = false)
            },
            modifier = Modifier.testTag("btn_apply_material_meta")
        ) { Text("Save Metadata Only") }
        Button(
            onClick = {
                viewModel.applyMaterialStudy(matName, roughness, metalness, 0.5f, azimuth, elevation, emissiveHex, emissionIntensity, selectedMaskId, bakeLayer = true)
            },
            modifier = Modifier.testTag("btn_bake_material")
        ) { Text("Bake Material Layer") }
        OutlinedButton(
            onClick = { viewModel.resetMaterialStudy(state.document.materials.lastOrNull()?.id) },
            modifier = Modifier.testTag("btn_reset_material")
        ) { Text("Reset") }
    }
    state.materialPreviewBitmap?.let { bmp ->
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Material Lit Preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)))
    }

    HorizontalDivider(color = StudioColors.FuturesDivider)
    val tl = state.document.timeline
    Text("F05 Time Canvas (${tl.frames.size} frames @ ${tl.fps} fps · Loop ${tl.loopStartIndex + 1}..${if (tl.loopEndIndex < 0) tl.frames.size else tl.loopEndIndex + 1})", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Button(onClick = viewModel::toggleTimelinePlayback, modifier = Modifier.testTag("btn_timeline_play")) {
            Text(if (state.timelinePlaying) "Pause" else "Play Loop", fontSize = 11.sp)
        }
        Button(onClick = viewModel::addTimelineFrame, modifier = Modifier.testTag("btn_add_frame")) { Text("+ Frame", fontSize = 11.sp) }
        OutlinedButton(onClick = { viewModel.duplicateTimelineFrame() }, modifier = Modifier.testTag("btn_dup_frame")) { Text("Duplicate", fontSize = 11.sp) }
        OutlinedButton(onClick = { viewModel.deleteTimelineFrame() }, modifier = Modifier.testTag("btn_del_frame")) { Text("Delete", fontSize = 11.sp) }
        OutlinedButton(onClick = viewModel::captureKeyframesToCurrentFrame) { Text("Key Layers", fontSize = 11.sp) }
        Button(onClick = viewModel::renderSpriteSheetPreview, modifier = Modifier.testTag("btn_render_spritesheet")) { Text("Sprite Sheet", fontSize = 11.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        tl.frames.forEachIndexed { i, f ->
            val active = i == tl.currentFrameIndex
            OutlinedButton(onClick = { viewModel.selectTimelineFrame(i) }, modifier = Modifier.testTag("btn_frame_$i")) {
                Text(if (active) "[${f.label}]" else f.label, color = if (active) StudioColors.SkyAccent else StudioColors.TextSecondary, fontSize = 10.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("FPS: ${tl.fps}", color = StudioColors.TextSecondary, fontSize = 11.sp)
        Slider(
            value = tl.fps.toFloat(),
            onValueChange = { viewModel.setTimelineFps(it.roundToInt()) },
            valueRange = 1f..24f,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = {
            viewModel.setTimelineLoopRange(0, tl.frames.lastIndex)
        }) { Text("Full Loop", fontSize = 10.sp) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = viewModel::toggleOnionSkin, modifier = Modifier.testTag("btn_toggle_onion")) {
            Text(if (tl.onionSkinEnabled) "Before Onion: ON" else "Before Onion: OFF", fontSize = 10.sp)
        }
        OutlinedButton(onClick = {
            viewModel.updateOnionSkinConfig(tl.onionSkinEnabled, tl.onionSkinBeforeOpacity, !tl.onionSkinAfterEnabled, tl.onionSkinAfterOpacity)
        }) {
            Text(if (tl.onionSkinAfterEnabled) "After Onion: ON" else "After Onion: OFF", fontSize = 10.sp)
        }
        state.document.activeLayerId?.let { activeId ->
            OutlinedButton(
                onClick = {
                    val activeLyr = state.document.layers.find { it.id == activeId }
                    if (activeLyr != null) {
                        viewModel.updateLayerKeyframe(
                            frameIndex = tl.currentFrameIndex,
                            layerId = activeId,
                            offsetX = activeLyr.transform.offsetX + 18f,
                            offsetY = activeLyr.transform.offsetY,
                            opacity = activeLyr.opacity,
                            visible = activeLyr.visible
                        )
                    }
                },
                modifier = Modifier.testTag("btn_update_keyframe")
            ) { Text("Nudge Keyframe +18px", fontSize = 10.sp) }
        }
    }
    Text(
        state.spriteSheetMeta?.videoExportNote
            ?: "Exports PNG sprite sheet & JSON frame timing; hardware MP4 MediaCodec encoding is excluded in sandboxed headless builds.",
        style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted, fontSize = 10.5.sp)
    )
    state.spriteSheetPreviewBitmap?.let { bmp ->
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Sprite sheet preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(90.dp).background(Color.Black))
    }
}

@Composable
internal fun VaultBranchDirectorTab(
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    var vaultTag by remember { mutableStateOf("lantern, sky, key") }
    var searchName by remember { mutableStateOf("") }
    var searchTag by remember { mutableStateOf("") }
    var searchColor by remember { mutableStateOf("") }
    var approvalFilter by remember { mutableStateOf(VaultApprovalFilter.ALL) }
    var typeFilter by remember { mutableStateOf("ALL") }

    val sheet = state.document.characterSheets.firstOrNull() ?: CharacterSheet()
    var charName by remember(sheet.name) { mutableStateOf(sheet.name) }
    var charProportions by remember(sheet.proportionNotes) { mutableStateOf(sheet.proportionNotes) }
    var charPose by remember(sheet.poseNotes) { mutableStateOf(sheet.poseNotes) }
    var charTurnaround by remember(sheet.turnaroundNotes) { mutableStateOf(sheet.turnaroundNotes) }
    var charPalette by remember(sheet.paletteHexes) { mutableStateOf(sheet.paletteHexes.joinToString(", ")) }
    var charProvenance by remember(sheet.referenceProvenanceNote) { mutableStateOf(sheet.referenceProvenanceNote) }

    var branchLabel by remember { mutableStateOf("Alt Lighting") }
    var directorCmd by remember { mutableStateOf("add text CHAPTER ONE") }

    // Primary Top Controls: F10 Save, P04 Ruler & P07/P08 Branches + World Orchard
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("P07 Branches & P08 World Orchard (Active: ${state.document.activeBranchId})", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
        OutlinedButton(onClick = { viewModel.toggleWorkspaceHub(false); viewModel.toggleFuturesLoom(true) }, modifier = Modifier.testTag("btn_open_loom_from_hub")) {
            Text("Open Futures Loom", fontSize = 10.5.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = branchLabel, onValueChange = { branchLabel = it }, label = { Text("Branch / Scene Name") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { viewModel.forkCreativeBranch(branchLabel) }, modifier = Modifier.testTag("btn_fork_branch")) { Text("Fork Branch") }
        OutlinedButton(onClick = { viewModel.addOrchardSceneNode(branchLabel) }, modifier = Modifier.testTag("btn_add_orchard")) { Text("+ Orchard Scene") }
        OutlinedButton(onClick = viewModel::toggleCharacterProportionRuler, modifier = Modifier.testTag("btn_toggle_ruler")) { Text("8-Head Ruler") }
    }
    if (state.document.branches.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            state.document.branches.forEach { br ->
                OutlinedButton(onClick = { viewModel.rollbackToBranch(br.branchId) }) { Text("Checkout ${br.label}") }
            }
        }
    }
    val orchard = remember(state.document.unknownFields) { VaultBranchOrchardEngine.readOrchardNodes(state.document) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        orchard.forEach { node ->
            val active = node.branchId == state.document.activeBranchId
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) StudioColors.SkySoft.copy(alpha = 0.16f) else Color(0xFF151F28))
                    .border(1.dp, if (active) StudioColors.SkyAccent else StudioColors.FuturesCardBorder, RoundedCornerShape(10.dp))
                    .clickable { viewModel.navigateToOrchardNode(node) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("orchard_node_${node.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${node.title} (@${node.branchId} · ${node.x.toInt()},${node.y.toInt()})", color = if (active) StudioColors.SkySoft else StudioColors.TextPrimary, fontSize = 11.sp)
            }
        }
    }

    // F10 Search & Filters
    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("F10 Visual Memory Vault (${state.document.vaultAssets.size} items)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = vaultTag, onValueChange = { vaultTag = it }, label = { Text("Save Tags (CSV)") }, singleLine = true, modifier = Modifier.weight(1f))
        Button(onClick = { viewModel.saveCurrentCanvasToVault("Canvas Ref ${state.document.vaultAssets.size + 1}", vaultTag) }, modifier = Modifier.testTag("btn_save_vault")) { Text("Save to Vault") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value = searchName, onValueChange = { searchName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f).testTag("input_vault_search_name"))
        OutlinedTextField(value = searchTag, onValueChange = { searchTag = it }, label = { Text("Tag") }, singleLine = true, modifier = Modifier.weight(1f).testTag("input_vault_search_tag"))
        OutlinedTextField(value = searchColor, onValueChange = { searchColor = it }, label = { Text("#Color") }, singleLine = true, modifier = Modifier.weight(0.9f).testTag("input_vault_search_color"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        VaultApprovalFilter.entries.forEach { f ->
            OutlinedButton(onClick = { approvalFilter = f }) {
                Text(f.name.replace("_", " "), color = if (approvalFilter == f) StudioColors.SkyAccent else StudioColors.TextMuted, fontSize = 9.5.sp)
            }
        }
        listOf("ALL", "REFERENCE", "CANVAS_STUDY").forEach { tf ->
            OutlinedButton(onClick = { typeFilter = tf }) {
                Text(tf, color = if (typeFilter == tf) StudioColors.AmberSoft else StudioColors.TextMuted, fontSize = 9.5.sp)
            }
        }
        OutlinedButton(
            onClick = { searchName = ""; searchTag = ""; searchColor = ""; approvalFilter = VaultApprovalFilter.ALL; typeFilter = "ALL" },
            modifier = Modifier.testTag("btn_vault_clear_filters")
        ) { Text("Clear Filters", fontSize = 9.5.sp) }
    }

    val hits = remember(state.document.vaultAssets, searchName, searchTag, searchColor, approvalFilter, typeFilter) {
        VaultBranchOrchardEngine.searchVaultAssets(
            doc = state.document,
            queryTag = searchTag,
            targetColorHex = searchColor,
            queryName = searchName,
            assetTypeFilter = typeFilter,
            approvalFilter = approvalFilter
        )
    }
    if (state.document.vaultAssets.isEmpty()) {
        Text("Vault is empty. Save canvas captures or local references above.", color = StudioColors.TextMuted, fontSize = 11.sp, modifier = Modifier.testTag("vault_empty_state"))
    } else if (hits.isEmpty()) {
        Text("No matching Vault assets for current filters.", color = StudioColors.AmberSoft, fontSize = 11.sp, modifier = Modifier.testTag("vault_no_results"))
    } else {
        hits.forEach { hit ->
            val asset = hit.asset
            val thumbBmp = remember(asset.id, asset.thumbnailPng?.size) {
                asset.thumbnailPng?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF151F28)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                if (thumbBmp != null) {
                    Image(bitmap = thumbBmp, contentDescription = asset.title, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("${asset.title} [${asset.assetType}]", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tags: ${asset.tags.joinToString()} | Colors: ${asset.dominantColorHexes.joinToString()} | Approved: ${asset.userApproved}", color = StudioColors.TextMuted, fontSize = 10.sp)
                }
                OutlinedButton(onClick = { viewModel.toggleVaultAssetApproval(asset.id, !asset.userApproved) }) {
                    Text(if (asset.userApproved) "Revoke" else "Approve", fontSize = 10.sp)
                }
            }
        }
    }

    // P04 Character Studio
    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("P04 Character Studio (User-Authored Sheets & Manual Canon)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = charName, onValueChange = { charName = it }, label = { Text("Character Name") }, singleLine = true, modifier = Modifier.weight(1f).testTag("input_char_name"))
        OutlinedTextField(value = charPalette, onValueChange = { charPalette = it }, label = { Text("Palette CSV") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = charProportions, onValueChange = { charProportions = it }, label = { Text("Proportions") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = charPose, onValueChange = { charPose = it }, label = { Text("Pose Notes") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = charTurnaround, onValueChange = { charTurnaround = it }, label = { Text("Turnaround") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(value = charProvenance, onValueChange = { charProvenance = it }, label = { Text("Provenance") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = {
                viewModel.updateCharacterSheetFull(
                    name = charName,
                    proportionNotes = charProportions,
                    poseNotes = charPose,
                    turnaroundNotes = charTurnaround,
                    paletteHexes = charPalette.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    expressionTags = sheet.expressionTags,
                    poseTags = sheet.poseTags,
                    referenceAssetIds = state.document.vaultAssets.filter { it.userApproved }.map { it.id },
                    referenceProvenanceNote = charProvenance
                )
            },
            modifier = Modifier.testTag("btn_save_char_sheet")
        ) { Text("Save Sheet", fontSize = 11.sp) }
        Text("Manual Canon:", color = StudioColors.TextSecondary, fontSize = 11.sp)
        Switch(
            checked = sheet.userApprovedCanon,
            onCheckedChange = viewModel::setCharacterCanonApproval,
            modifier = Modifier.testTag("switch_char_canon_approved")
        )
    }

    // P05 Art Director
    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("P05 Conversational Art Director (Deterministic Grammar)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = directorCmd, onValueChange = { directorCmd = it }, label = { Text("Command") }, singleLine = true, modifier = Modifier.weight(1f).testTag("input_director_cmd"))
        Button(onClick = { viewModel.runArtDirectorCommand(directorCmd) }, modifier = Modifier.testTag("btn_run_director")) { Text("Execute") }
    }
    state.document.artDirectorHistory.takeLast(3).forEach { e ->
        Text("• [${if (e.applied) "APPLIED" else "SKIPPED"}] ${e.instruction} -> ${e.proposedOperation}", color = StudioColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
internal fun ConstraintsAndRouterTab(
    state: StudioUiState,
    viewModel: StudioViewModel
) {
    var leftTiles by remember { mutableIntStateOf(0) }
    var rightTiles by remember { mutableIntStateOf(1) }
    var topTiles by remember { mutableIntStateOf(0) }
    var bottomTiles by remember { mutableIntStateOf(0) }
    var blendStrength by remember { mutableFloatStateOf(0.78f) }
    var featherPx by remember { mutableFloatStateOf(36f) }
    var outpaintPreview by remember { mutableStateOf<FuturesAndConstraintsEngine.OutpaintPreviewInfo?>(null) }

    Text("P03 Constraint Constellation (${state.document.constraints.size} rules)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { viewModel.addConstraintFromSelection(ConstraintKind.FORBIDDEN_REGION, "Forbidden Zone") }, modifier = Modifier.testTag("btn_add_forbidden")) { Text("+ Forbidden Zone") }
        OutlinedButton(onClick = { viewModel.addConstraintFromSelection(ConstraintKind.PROTECTED_SILHOUETTE, "Protected Silhouette") }) { Text("+ Protected Silhouette") }
    }
    state.document.constraints.forEach { c ->
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF151F28)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${c.kind.name}: ${c.label} (Locked: ${c.userLocked})", color = Color.White, fontSize = 12.sp)
            OutlinedButton(onClick = { viewModel.toggleConstraintLock(c.id) }) { Text(if (c.userLocked) "Unlock" else "Lock") }
        }
    }

    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("P06 Outpainting (Custom Left/Right/Top/Bottom Tile Extension)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        OutlinedButton(onClick = { leftTiles = (leftTiles + 1) % 4 }) { Text("L: $leftTiles") }
        OutlinedButton(onClick = { rightTiles = (rightTiles + 1) % 4 }) { Text("R: $rightTiles") }
        OutlinedButton(onClick = { topTiles = (topTiles + 1) % 4 }) { Text("T: $topTiles") }
        OutlinedButton(onClick = { bottomTiles = (bottomTiles + 1) % 4 }) { Text("B: $bottomTiles") }
        OutlinedButton(
            onClick = {
                outpaintPreview = FuturesAndConstraintsEngine.buildOutpaintContextPreview(
                    state.document, viewModel.tileRenderer, leftTiles, rightTiles, topTiles, bottomTiles
                )
            },
            modifier = Modifier.testTag("btn_outpaint_preview")
        ) { Text("Context Preview") }
        Button(
            onClick = { viewModel.runCustomLocalOutpaint(leftTiles, rightTiles, topTiles, bottomTiles, blendStrength, featherPx) },
            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText),
            modifier = Modifier.testTag("btn_outpaint_custom_apply")
        ) { Text("Apply Local Edge Study", fontWeight = FontWeight.Bold) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Blend Strength: ${String.format("%.2f", blendStrength)}", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = blendStrength, onValueChange = { blendStrength = it }, valueRange = 0.2f..1f)
        }
        Column(Modifier.weight(1f)) {
            Text("Feather: ${featherPx.toInt()}px", color = StudioColors.TextSecondary, fontSize = 11.sp)
            Slider(value = featherPx, onValueChange = { featherPx = it }, valueRange = 0f..96f)
        }
    }
    outpaintPreview?.let { info ->
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E161E)).padding(8.dp).testTag("outpaint_preview_card"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("${info.currentSizeLabel} -> ${info.targetSizeLabel}", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            Text(info.disclaimer, color = StudioColors.AmberSoft, fontSize = 10.sp)
            Image(bitmap = info.previewBitmap.asImageBitmap(), contentDescription = "Outpaint context preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(95.dp))
        }
    }

    HorizontalDivider(color = StudioColors.FuturesDivider)
    Text("P10 Secure Provider Router (${state.providerMode.name})", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
    Text(state.providerHealthSummary, style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextSecondary))
    Text("Privacy Disclosure: Zero API keys are embedded in this APK. Loopback companion (127.0.0.1:8787) sends cropped selection only on explicit consent.", color = StudioColors.TextMuted, fontSize = 10.5.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        ProviderMode.entries.forEach { mode ->
            val active = mode == state.providerMode
            OutlinedButton(onClick = { viewModel.setProviderMode(mode) }, modifier = Modifier.testTag("btn_provider_${mode.name.lowercase()}")) {
                Text(mode.name.replace("_", " "), color = if (active) StudioColors.SkyAccent else StudioColors.TextSecondary, fontSize = 10.sp)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::pingCompanionHealth, modifier = Modifier.testTag("btn_ping_router")) { Text("Ping Health", fontSize = 11.sp) }
        OutlinedButton(onClick = viewModel::cancelActiveProviderJob, enabled = state.activeProviderJobId != null, modifier = Modifier.testTag("btn_cancel_provider_job")) { Text("Cancel Active Job", fontSize = 11.sp) }
        OutlinedButton(onClick = viewModel::fallbackToLocalMode, modifier = Modifier.testTag("btn_fallback_local")) { Text("Fallback Offline", fontSize = 11.sp) }
    }
}
