package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CommandRingAction
import com.example.model.StudioUiState
import com.example.ui.theme.StudioColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Stage E: Creative Command Ring (8-slot S-Pen radial menu with customizable slots,
 * reduced-motion support, and accessible list/grid fallback). Zero stroke-stealing:
 * triggers only on explicit toolbar invocation or S-Pen button shortcut, never bare canvas drag.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreativeCommandRingOverlay(
    state: StudioUiState,
    onTriggerAction: (CommandRingAction) -> Unit,
    onCustomizeSlot: (Int, CommandRingAction) -> Unit,
    onToggleAccessibleStrip: () -> Unit,
    onToggleReducedMotion: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var editingSlotIndex by remember { mutableIntStateOf(-1) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val animSpec = if (state.reducedMotionEnabled) snap<Float>() else tween<Float>(durationMillis = 120)
    val scaleFactor by animateFloatAsState(if (appeared || state.reducedMotionEnabled) 1f else 0.94f, animationSpec = animSpec, label = "ring_scale")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .testTag("command_ring_overlay"),
            contentAlignment = Alignment.Center
        ) {
            // Sibling backdrop scrim so dismiss clicks do not merge descendant semantics
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xA606090F))
                    .clickable(onClick = onDismiss)
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .padding(16.dp)
                    .scale(scaleFactor)
                    .clip(RoundedCornerShape(26.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.SkyAccent.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CREATIVE COMMAND RING", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkyAccent, letterSpacing = 1.2.sp))
                    Text(
                        if (editingSlotIndex >= 0) "Select replacement for slot ${editingSlotIndex + 1}" else "Tap an action or customize any slot",
                        style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextSecondary)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Command Ring",
                    tint = StudioColors.TextMuted,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onDismiss)
                        .testTag("btn_close_command_ring")
                )
            }

            if (state.commandRingAccessibleStrip) {
                // Accessible Grid/List Fallback
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_ring_accessible_grid"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    state.commandRingSlots.forEachIndexed { idx, action ->
                        RingSlotPill(
                            index = idx,
                            action = action,
                            editing = editingSlotIndex == idx,
                            onClick = {
                                if (editingSlotIndex >= 0) editingSlotIndex = idx else onTriggerAction(action)
                            }
                        )
                    }
                }
            } else {
                // Radial 8-Slot S-Pen Ring
                Box(
                    modifier = Modifier
                        .size(296.dp)
                        .testTag("command_ring_radial"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF283746), CircleShape)
                    )
                    // Center Dismiss / Hub Core
                    Column(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF15202B))
                            .border(1.5.dp, StudioColors.SkyAccent.copy(alpha = 0.6f), CircleShape)
                            .clickable(onClick = onDismiss),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss ring", tint = StudioColors.SkySoft, modifier = Modifier.size(18.dp))
                        Text("Close", color = StudioColors.TextSecondary, fontSize = 10.sp)
                    }

                    val radiusPx = with(LocalDensity.current) { 104.dp.toPx() }
                    state.commandRingSlots.forEachIndexed { idx, action ->
                        val angle = -PI / 2.0 + (idx * 2.0 * PI / state.commandRingSlots.size.coerceAtLeast(1))
                        val dx = (cos(angle) * radiusPx).roundToInt()
                        val dy = (sin(angle) * radiusPx).roundToInt()
                        Box(modifier = Modifier.offset { IntOffset(dx, dy) }) {
                            RingSlotPill(
                                index = idx,
                                action = action,
                                editing = editingSlotIndex == idx,
                                onClick = {
                                    if (editingSlotIndex >= 0) editingSlotIndex = idx else onTriggerAction(action)
                                }
                            )
                        }
                    }
                }
            }

            if (editingSlotIndex >= 0) {
                Text("Choose action for Slot #${editingSlotIndex + 1}:", color = StudioColors.AmberSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommandRingAction.entries.forEach { candidate ->
                        OutlinedButton(
                            onClick = {
                                onCustomizeSlot(editingSlotIndex, candidate)
                                editingSlotIndex = -1
                            },
                            modifier = Modifier.testTag("btn_ring_pick_${candidate.name.lowercase()}")
                        ) {
                            Text(candidate.label, fontSize = 10.sp)
                        }
                    }
                }
            }

            // Accessibility, Motion & Customization Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleAccessibleStrip,
                    modifier = Modifier.testTag("btn_ring_toggle_accessible")
                ) {
                    Text(if (state.commandRingAccessibleStrip) "Radial View" else "Accessible Grid", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onToggleReducedMotion,
                    modifier = Modifier.testTag("btn_ring_reduced_motion")
                ) {
                    Text(if (state.reducedMotionEnabled) "Motion: Reduced" else "Motion: Standard", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { editingSlotIndex = if (editingSlotIndex >= 0) -1 else 0 },
                    modifier = Modifier.testTag("btn_ring_customize")
                ) {
                    Icon(Icons.Filled.Tune, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (editingSlotIndex >= 0) "Done Editing" else "Customize Slots", fontSize = 11.sp)
                }
            }
        }
        }
    }
}

@Composable
private fun RingSlotPill(
    index: Int,
    action: CommandRingAction,
    editing: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(width = 74.dp, height = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (editing) StudioColors.AmberAccent.copy(alpha = 0.22f) else Color(0xFF17222E))
            .border(
                1.dp,
                if (editing) StudioColors.AmberAccent else StudioColors.SkyAccent.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag("ring_slot_$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = iconForRingAction(action),
            contentDescription = action.label,
            tint = if (editing) StudioColors.AmberSoft else StudioColors.SkySoft,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = action.label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

private fun iconForRingAction(action: CommandRingAction): ImageVector = when (action) {
    CommandRingAction.BRUSH -> Icons.Filled.Brush
    CommandRingAction.ERASER -> Icons.Filled.AutoFixHigh
    CommandRingAction.SELECT -> Icons.Filled.Gesture
    CommandRingAction.TRANSFORM -> Icons.Filled.OpenWith
    CommandRingAction.VECTOR -> Icons.Filled.Tune
    CommandRingAction.COLOR_PICKER -> Icons.Filled.Palette
    CommandRingAction.UNDO -> Icons.AutoMirrored.Filled.Undo
    CommandRingAction.REDO -> Icons.AutoMirrored.Filled.Redo
    CommandRingAction.FUTURES -> Icons.Filled.AutoAwesome
    CommandRingAction.LOOM -> Icons.Filled.AccountTree
    CommandRingAction.LAYERS -> Icons.Filled.Layers
    CommandRingAction.EYEDROPPER -> Icons.Filled.Colorize
}
