package com.example.ui.components

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.features.CherryPickResolution
import com.example.features.VaultBranchOrchardEngine
import com.example.model.StudioUiState
import com.example.model.WorkspaceMode
import com.example.ui.theme.StudioColors

/**
 * The Futures Loom: Signature Living Workshop workbench connecting
 * Futures Table, Creative Branches (with thumbnails, side-by-side & diff-overlay comparison,
 * and conflict-aware layer cherry-picking), Object Surgery, Constraints, Visual Forensics,
 * Vector/Typography, Material Intelligence, Time Canvas, and Production Foundry.
 */
@Composable
fun FuturesLoomDialog(
    state: StudioUiState,
    onForkBranch: (String, String) -> Unit,
    onCheckoutBranch: (String) -> Unit,
    onCompareBranches: (String, String) -> Unit,
    onClearComparison: () -> Unit,
    onPreviewCherryPick: (String, String) -> Unit,
    onApplyCherryPick: (CherryPickResolution) -> Unit,
    onClearCherryPick: () -> Unit,
    onOpenFuturesTable: () -> Unit,
    onOpenStudioTab: (Int) -> Unit,
    onSwitchWorkspaceMode: (WorkspaceMode) -> Unit,
    onRunForensics: () -> Unit,
    onClose: () -> Unit
) {
    val doc = state.document
    var forkName by remember { mutableStateOf("Study ${doc.branches.size + 1}") }
    var forkNote by remember { mutableStateOf("Iterative variation") }
    var compareA by remember(doc.branches) { mutableStateOf(doc.branches.firstOrNull()?.branchId ?: doc.activeBranchId) }
    var compareB by remember(doc.branches) { mutableStateOf(doc.branches.lastOrNull()?.branchId ?: doc.activeBranchId) }
    var diffOverlayMode by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioColors.FuturesShade)
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.FuturesDialogBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("futures_loom_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.AccountTree, null, tint = StudioColors.SkyAccent, modifier = Modifier.size(20.dp))
                        Column {
                            Text("THE FUTURES LOOM", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkyAccent, letterSpacing = 1.4.sp))
                            Text(
                                "${doc.title} · Active: ${doc.activeBranchId} · rev ${doc.revision}",
                                style = MaterialTheme.typography.titleMedium.copy(color = StudioColors.TextPrimary)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Futures Loom",
                        tint = StudioColors.TextMuted,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(onClick = onClose)
                            .testTag("btn_close_loom")
                    )
                }

                // Connected Creative Continuum Launch Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LoomStagePill("Futures Table", true, "btn_loom_futures") { onClose(); onOpenFuturesTable() }
                    LoomStagePill("Object Surgery", false, "btn_loom_surgery") { onClose(); onSwitchWorkspaceMode(WorkspaceMode.EDIT); onOpenStudioTab(0) }
                    LoomStagePill("Constraints (${doc.constraints.size})", false, "btn_loom_constraints") { onClose(); onOpenStudioTab(5) }
                    LoomStagePill("Forensics", false, "btn_loom_forensics") { onRunForensics(); onClose(); onOpenStudioTab(3) }
                    LoomStagePill("Vector & SVG", false, "btn_loom_vector") { onClose(); onSwitchWorkspaceMode(WorkspaceMode.EDIT); onOpenStudioTab(1) }
                    LoomStagePill("Material Light", false, "btn_loom_material") { onClose(); onSwitchWorkspaceMode(WorkspaceMode.PRODUCE); onOpenStudioTab(2) }
                    LoomStagePill("Time Canvas", false, "btn_loom_time") { onClose(); onSwitchWorkspaceMode(WorkspaceMode.ANIMATE); onOpenStudioTab(2) }
                    LoomStagePill("Foundry ZIP", false, "btn_loom_foundry") { onClose(); onSwitchWorkspaceMode(WorkspaceMode.PRODUCE); onOpenStudioTab(1) }
                }

                HorizontalDivider(color = StudioColors.FuturesDivider)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Fork new immutable snapshot branch
                    Text("Fork Immutable Creative Branch", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = forkName,
                            onValueChange = { forkName = it },
                            label = { Text("Branch Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_loom_fork_name")
                        )
                        OutlinedTextField(
                            value = forkNote,
                            onValueChange = { forkNote = it },
                            label = { Text("Artist Note") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onForkBranch(forkName, forkNote) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText),
                            modifier = Modifier.testTag("btn_loom_fork")
                        ) {
                            Text("Fork Snapshot", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Branch Nodes Strip with PNG thumbnails, ancestry & checkout
                    Text("Branch Tree (${doc.branches.size} snapshots preserved)", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
                    if (doc.branches.isEmpty()) {
                        Text(
                            "No branch snapshots yet. Fork a snapshot to compare alternatives and cherry-pick layers safely.",
                            style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            doc.branches.forEach { br ->
                                val isActive = br.branchId == doc.activeBranchId
                                val thumbBmp = remember(br.branchId, br.thumbnailPng?.size) {
                                    br.thumbnailPng?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                                }
                                val brDoc = remember(br.branchId, br.snapshotJsonBytes?.size) {
                                    VaultBranchOrchardEngine.readBranchSnapshotDocument(doc, br.branchId)
                                }
                                Column(
                                    modifier = Modifier
                                        .width(210.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isActive) StudioColors.FuturesCardActiveBg else StudioColors.FuturesCardBg)
                                        .border(if (isActive) 2.dp else 1.dp, if (isActive) StudioColors.SkyAccent else StudioColors.FuturesCardBorder, RoundedCornerShape(14.dp))
                                        .padding(10.dp)
                                        .testTag("loom_branch_${br.branchId}"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (thumbBmp != null) {
                                        Image(
                                            bitmap = thumbBmp,
                                            contentDescription = "${br.label} thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF151F28)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(br.label, color = StudioColors.TextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                    Text(br.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "rev ${br.revisionAtFork} · parent=${br.parentBranchId ?: "root"}",
                                        color = StudioColors.TextMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                    if (br.note.isNotBlank()) {
                                        Text(br.note, color = StudioColors.TextSecondary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { onCheckoutBranch(br.branchId) },
                                            modifier = Modifier.weight(1f).testTag("btn_checkout_${br.branchId}")
                                        ) {
                                            Text(if (isActive) "Active" else "Checkout", fontSize = 10.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                if (compareA == br.branchId) compareB = br.branchId else compareA = br.branchId
                                            }
                                        ) {
                                            Text(if (compareA == br.branchId) "A" else if (compareB == br.branchId) "B" else "Pick", fontSize = 10.sp)
                                        }
                                    }
                                    // Per-branch cherry-pickable layers
                                    brDoc?.layers?.takeLast(3)?.forEach { lyr ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF101922))
                                                .clickable { onPreviewCherryPick(br.branchId, lyr.name) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                                .testTag("btn_cherrypick_preview_${br.branchId}_${lyr.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Filled.Layers, null, tint = StudioColors.SkyAccent, modifier = Modifier.size(12.dp))
                                                Text(lyr.name, color = Color(0xFFD6E6F2), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("Pick", color = StudioColors.SkySoft, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Side-by-side / Diff-Overlay Branch Comparison
                    if (doc.branches.size >= 2) {
                        HorizontalDivider(color = StudioColors.FuturesDivider)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Compare Branches: $compareA vs $compareB", style = MaterialTheme.typography.labelLarge.copy(color = StudioColors.SkySoft))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onCompareBranches(compareA, compareB) },
                                    modifier = Modifier.testTag("btn_compare_branches")
                                ) {
                                    Icon(Icons.Filled.Compare, null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Compare")
                                }
                                if (state.branchComparison != null) {
                                    OutlinedButton(onClick = { diffOverlayMode = !diffOverlayMode }, modifier = Modifier.testTag("btn_toggle_diff_overlay")) {
                                        Text(if (diffOverlayMode) "Side-by-Side" else "Diff Overlay")
                                    }
                                    OutlinedButton(onClick = onClearComparison) { Text("Clear") }
                                }
                            }
                        }
                    }

                    state.branchComparison?.let { cmp ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0E161E))
                                .border(1.dp, Color(0xFF2D4356), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                                .testTag("branch_comparison_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${cmp.branchALabel} vs ${cmp.branchBLabel} · Changed pixels: ${String.format("%.1f", cmp.changedPixelPercent)}% · Shared layers: ${cmp.sharedLayerCount} (Modified: ${cmp.modifiedSharedLayers.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${cmp.vectorCountDiff} | ${cmp.materialCountDiff} | Only in A: ${cmp.onlyInA.ifEmpty { listOf("none") }.joinToString()} | Only in B: ${cmp.onlyInB.ifEmpty { listOf("none") }.joinToString()}",
                                color = StudioColors.TextSecondary,
                                fontSize = 11.sp
                            )
                            cmp.protectedRegionWarnings.forEach { w ->
                                Text("⚠ $w", color = StudioColors.AmberSoft, fontSize = 11.sp)
                            }
                            if (diffOverlayMode) {
                                Image(
                                    bitmap = cmp.diffOverlayBitmap.asImageBitmap(),
                                    contentDescription = "Branch pixel diff overlay",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Image(
                                        bitmap = cmp.bitmapA.asImageBitmap(),
                                        contentDescription = cmp.branchALabel,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                    Image(
                                        bitmap = cmp.bitmapB.asImageBitmap(),
                                        contentDescription = cmp.branchBLabel,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    }

                    // Cherry-Pick Conflict & Diff Preview Panel
                    state.cherryPickPreview?.let { cp ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF17212C))
                                .border(1.dp, StudioColors.AmberAccent.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                                .testTag("cherry_pick_preview_card"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Cherry-Pick Layer Preview: '${cp.sourceLayerName}' from ${cp.fromBranchLabel}", color = StudioColors.AmberSoft, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Tiles: ${cp.tileCount} | SHA-256 prefix: ${cp.sourceLayerContentHash}", color = StudioColors.TextSecondary, fontSize = 11.sp)
                            cp.protectedConstraintWarnings.forEach { w ->
                                Text("• $w", color = Color(0xFFFDE68A), fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onApplyCherryPick(CherryPickResolution.RENAME_NEW) },
                                    modifier = Modifier.testTag("btn_cp_rename_new")
                                ) {
                                    Text("Add as '${cp.proposedRenamedName}'", fontSize = 11.sp)
                                }
                                if (cp.collidingCurrentLayerId != null) {
                                    OutlinedButton(
                                        onClick = { onApplyCherryPick(CherryPickResolution.REPLACE_MATCHING) },
                                        modifier = Modifier.testTag("btn_cp_replace")
                                    ) {
                                        Text("Replace '${cp.collidingCurrentLayerName}'", fontSize = 11.sp)
                                    }
                                }
                                OutlinedButton(onClick = onClearCherryPick) { Text("Cancel", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoomStagePill(
    label: String,
    highlighted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) StudioColors.SkySoft else Color(0xFF19242E))
            .border(1.dp, if (highlighted) StudioColors.SkyAccent else Color(0xFF334454), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (highlighted) StudioColors.SkyDarkText else StudioColors.TextPrimary,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}
