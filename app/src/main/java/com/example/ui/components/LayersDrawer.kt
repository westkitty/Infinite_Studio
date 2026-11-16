package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.document.DocumentLayer
import com.example.model.LayerBlendMode
import com.example.model.PaperStyle
import com.example.model.StudioUiState
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun LayersDock(
    state: StudioUiState,
    onTogglePanel: () -> Unit,
    onAddLayer: () -> Unit,
    onDuplicateLayer: () -> Unit,
    onMergeDown: () -> Unit,
    onClearLayer: () -> Unit,
    onSelectBlendMode: (LayerBlendMode) -> Unit,
    onToggleAlphaLock: () -> Unit,
    onLayerOpacityChange: (Float) -> Unit,
    onLayerOpacityCommit: () -> Unit,
    onSelectLayer: (String) -> Unit,
    onMoveLayerUp: (String) -> Unit,
    onMoveLayerDown: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onSelectPaperStyle: (PaperStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLayer = state.layers.find { it.id == state.activeLayerId } ?: state.layers.lastOrNull()
    var showBlendDropdown by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(visible = state.showLayersPanel) {
            Column(
                modifier = Modifier
                    .width(292.dp)
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, StudioColors.GlassPanelBorder, RoundedCornerShape(24.dp))
                    .padding(14.dp)
                    .testTag("panel_layers"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = null,
                            tint = StudioColors.SkyAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "LAYERS",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = StudioColors.TextPrimary,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LayerActionIcon(Icons.Filled.Add, "New Layer", StudioColors.SkyAccent, StudioColors.SkyAccent.copy(alpha = 0.2f), "btn_add_layer", onAddLayer)
                        LayerActionIcon(Icons.Filled.ContentCopy, "Duplicate Layer", StudioColors.TextSecondary, Color.Transparent, "btn_dup_layer", onDuplicateLayer)
                        LayerActionIcon(Icons.AutoMirrored.Filled.MergeType, "Merge Down", StudioColors.TextSecondary, Color.Transparent, "btn_merge_down", onMergeDown)
                        LayerActionIcon(Icons.Filled.DeleteSweep, "Clear Layer", StudioColors.DangerRed, Color.Transparent, "btn_clear_layer", onClearLayer)
                    }
                }

                if (activeLayer != null) {
                    val activeBlend = LayerBlendMode.fromId(activeLayer.blendModeId)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xCC18181B))
                            .border(1.dp, Color(0xCC27272A), RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF27272A))
                                        .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(8.dp))
                                        .clickable { showBlendDropdown = true }
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                        .testTag("btn_blend_mode"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activeBlend.displayName, style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextPrimary))
                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = StudioColors.TextMuted, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = showBlendDropdown, onDismissRequest = { showBlendDropdown = false }) {
                                    LayerBlendMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.displayName) },
                                            onClick = { showBlendDropdown = false; onSelectBlendMode(mode) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activeLayer.alphaLock) StudioColors.AmberAccent.copy(alpha = 0.2f) else Color(0xFF27272A))
                                    .border(1.dp, if (activeLayer.alphaLock) StudioColors.AmberAccent.copy(alpha = 0.5f) else Color(0xFF3F3F46), RoundedCornerShape(8.dp))
                                    .clickable(onClick = onToggleAlphaLock)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                    .testTag("btn_alpha_lock"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Lock, "Alpha Lock", tint = if (activeLayer.alphaLock) StudioColors.AmberSoft else StudioColors.TextMuted, modifier = Modifier.size(12.dp))
                                Text("Alpha Lock", style = MaterialTheme.typography.labelMedium.copy(color = if (activeLayer.alphaLock) StudioColors.AmberSoft else StudioColors.TextMuted, fontSize = 11.sp))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Opacity", style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted))
                            Slider(
                                value = activeLayer.opacity,
                                onValueChange = onLayerOpacityChange,
                                onValueChangeFinished = onLayerOpacityCommit,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = StudioColors.SkyAccent, activeTrackColor = StudioColors.SkyAccent),
                                modifier = Modifier.weight(1f).testTag("slider_layer_opacity")
                            )
                            Text("${(activeLayer.opacity * 100f).roundToInt()}%", style = MaterialTheme.typography.labelSmall.copy(color = StudioColors.TextSecondary))
                        }
                    }
                }

                val reversed = remember(state.layers) { state.layers.reversed() }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(reversed, key = { _, l -> l.id }) { revIdx, layer ->
                        LayerRowItem(
                            layer = layer,
                            isActive = layer.id == state.activeLayerId,
                            canMoveUp = revIdx > 0,
                            canMoveDown = revIdx < reversed.lastIndex,
                            canDelete = state.layers.size > 1,
                            onSelect = { onSelectLayer(layer.id) },
                            onMoveUp = { onMoveLayerUp(layer.id) },
                            onMoveDown = { onMoveLayerDown(layer.id) },
                            onToggleVisibility = { onToggleVisibility(layer.id) },
                            onDelete = { onDeleteLayer(layer.id) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Canvas Paper", style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperStyle.entries.forEach { paper ->
                            val selected = state.paperStyle == paper
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (paper == PaperStyle.TRANSPARENT) StudioColors.CanvasCheckerLight else paper.composeColor)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) StudioColors.SkyAccent else Color(0xFF71717A), CircleShape)
                                    .clickable { onSelectPaperStyle(paper) }
                                    .testTag("paper_${paper.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(StudioColors.GlassPanel)
                .border(1.dp, StudioColors.GlassPanelBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onTogglePanel)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag("btn_toggle_layers"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.Layers, "Toggle Layers Panel", tint = StudioColors.SkyAccent, modifier = Modifier.size(18.dp))
            Text(
                text = "${state.layers.size}",
                style = MaterialTheme.typography.labelSmall.copy(color = StudioColors.TextSecondary, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF27272A)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun LayerActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    bgColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(bgColor).clickable(onClick = onClick).testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LayerRowItem(
    layer: DocumentLayer,
    isActive: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit
) {
    val blend = LayerBlendMode.fromId(layer.blendModeId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) StudioColors.SkyAccent.copy(alpha = 0.15f) else Color(0x9918181B))
            .border(1.dp, if (isActive) StudioColors.SkyAccent.copy(alpha = 0.5f) else Color(0xCC27272A), RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(StudioColors.CanvasCheckerDark)
                    .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Layers, null, tint = StudioColors.TextMuted, modifier = Modifier.size(12.dp))
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = layer.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isActive) StudioColors.TextPrimary else StudioColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (layer.alphaLock) {
                        Icon(Icons.Filled.Lock, "Alpha Locked", tint = StudioColors.AmberSoft, modifier = Modifier.size(10.dp))
                    }
                }
                Text(
                    text = "${blend.displayName} • ${(layer.opacity * 100f).roundToInt()}% • ${layer.tiles.size} tiles",
                    style = MaterialTheme.typography.labelSmall.copy(color = StudioColors.TextMuted, fontSize = 9.5.sp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.KeyboardArrowUp, "Move Layer Up", tint = if (canMoveUp) StudioColors.TextMuted else StudioColors.TextMuted.copy(alpha = 0.2f), modifier = Modifier.size(22.dp).clickable(enabled = canMoveUp, onClick = onMoveUp).padding(2.dp))
            Icon(Icons.Filled.KeyboardArrowDown, "Move Layer Down", tint = if (canMoveDown) StudioColors.TextMuted else StudioColors.TextMuted.copy(alpha = 0.2f), modifier = Modifier.size(22.dp).clickable(enabled = canMoveDown, onClick = onMoveDown).padding(2.dp))
            Icon(if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Toggle Visibility", tint = if (layer.visible) StudioColors.TextPrimary else StudioColors.TextMuted.copy(alpha = 0.45f), modifier = Modifier.size(24.dp).clickable(onClick = onToggleVisibility).padding(3.dp))
            if (canDelete) {
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Filled.Close, "Delete Layer", tint = StudioColors.TextMuted, modifier = Modifier.size(22.dp).clickable(onClick = onDelete).padding(3.dp))
            }
        }
    }
}
