package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.QUICK_STYLE_CHIPS
import com.example.model.StudioTool
import com.example.model.StudioUiState
import com.example.model.WorkspaceMode
import com.example.ui.theme.StudioColors

@Composable
fun BottomToolAndContextDock(
    state: StudioUiState,
    onSelectTool: (StudioTool) -> Unit,
    onFullCanvasAi: () -> Unit,
    onTransformFlipH: () -> Unit,
    onTransformFlipV: () -> Unit,
    onTransformScaleDown: () -> Unit,
    onTransformScaleUp: () -> Unit,
    onTransformRotate: () -> Unit,
    onTransformToggleSnap: () -> Unit = {},
    onTransformCancel: () -> Unit = {},
    onTransformDone: () -> Unit,
    onToggleVectorSnap: () -> Unit = {},
    onDeleteSelectedVector: (String) -> Unit = {},
    onAddQuickVectorText: () -> Unit = {},
    onToggleTimelinePlay: () -> Unit = {},
    onSelectTimelineFrame: (Int) -> Unit = {},
    onToggleOnionSkin: () -> Unit = {},
    onCancelSelection: () -> Unit,
    onAiPromptChange: (String) -> Unit,
    onRunAiFutures: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp, start = 10.dp, end = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Animate Mode Transport Strip
        AnimatedVisibility(visible = state.workspaceMode == WorkspaceMode.ANIMATE) {
            val tl = state.document.timeline
            val total = tl.frames.size.coerceAtLeast(1)
            val cur = tl.currentFrameIndex.coerceIn(0, total - 1)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, StudioColors.SkyAccent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("animate_transport_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransformButton(
                    label = if (state.timelinePlaying) "Pause" else "Play",
                    icon = if (state.timelinePlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    testTag = "btn_anim_bar_play",
                    onClick = onToggleTimelinePlay
                )
                TransformButton("Prev", Icons.Filled.ZoomOut, "btn_anim_bar_prev") {
                    onSelectTimelineFrame((cur - 1).coerceAtLeast(0))
                }
                Text(
                    "Frame ${cur + 1}/$total (${tl.fps} fps)",
                    color = StudioColors.SkySoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TransformButton("Next", Icons.Filled.ZoomIn, "btn_anim_bar_next") {
                    onSelectTimelineFrame((cur + 1).coerceAtMost(total - 1))
                }
                TransformButton(
                    label = if (tl.onionSkinEnabled) "Onion: ON" else "Onion: OFF",
                    icon = Icons.Filled.AutoFixHigh,
                    testTag = "btn_anim_bar_onion",
                    onClick = onToggleOnionSkin
                )
            }
        }

        // Floating Vector Edit Context Dock
        AnimatedVisibility(visible = state.activeTool == StudioTool.VECTOR_EDIT) {
            val selVec = state.document.vectorObjects.find { it.id == state.selectedVectorId }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, StudioColors.SkyAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("vector_edit_dock"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selVec?.let { "VECTOR: ${it.kind.name}" } ?: "VECTOR EDIT (Tap or drag object)",
                    style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkySoft, letterSpacing = 0.5.sp)
                )
                TransformButton(
                    label = if (state.vectorSnapEnabled) "Snap 16px: ON" else "Snap: OFF",
                    icon = Icons.Filled.OpenWith,
                    testTag = "btn_vec_snap",
                    onClick = onToggleVectorSnap
                )
                TransformButton("+ Text Box", Icons.Filled.TextFields, "btn_vec_quick_add", onAddQuickVectorText)
                if (selVec != null) {
                    TransformButton("Delete", Icons.Filled.Close, "btn_vec_delete") {
                        onDeleteSelectedVector(selVec.id)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioColors.SkySoft)
                        .clickable { onSelectTool(StudioTool.PENCIL) }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                        .testTag("btn_vec_done")
                ) {
                    Text("Done", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkyDarkText, fontWeight = FontWeight.Bold))
                }
            }
        }

        // Floating Layer / Object Transform Dock
        AnimatedVisibility(visible = state.activeTool == StudioTool.TRANSFORM) {
            val snapOn = state.transformSession?.snapEnabled ?: true
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, StudioColors.AmberAccent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .testTag("transform_dock"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = null,
                    tint = StudioColors.AmberSoft,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = state.transformSession?.label?.uppercase() ?: "LAYER TRANSFORM",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = StudioColors.AmberSoft,
                        letterSpacing = 0.6.sp
                    )
                )
                TransformButton(if (snapOn) "Snap: ON" else "Snap: OFF", Icons.Filled.OpenWith, "btn_tf_snap", onTransformToggleSnap)
                TransformButton("Flip H", Icons.Filled.Flip, "btn_tf_flip_h", onTransformFlipH)
                TransformButton("Flip V", Icons.Filled.Flip, "btn_tf_flip_v", onTransformFlipV)
                TransformButton(null, Icons.Filled.ZoomOut, "btn_tf_scale_down", onTransformScaleDown)
                TransformButton(null, Icons.Filled.ZoomIn, "btn_tf_scale_up", onTransformScaleUp)
                TransformButton("15°", Icons.AutoMirrored.Filled.RotateRight, "btn_tf_rotate", onTransformRotate)
                TransformButton("Cancel", Icons.Filled.Close, "btn_tf_cancel", onTransformCancel)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioColors.AmberAccent)
                        .clickable(onClick = onTransformDone)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("btn_tf_done")
                ) {
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF09090B),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Floating AI Selection Action Bar
        AnimatedVisibility(visible = state.showAiPromptDock) {
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, StudioColors.SkyAccent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(12.dp)
                    .testTag("ai_prompt_dock"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StudioColors.SkyAccent)
                        )
                        Text(
                            text = state.aiPromptBadge.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = StudioColors.SkySoft,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancelSelection)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_cancel_selection"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear selection",
                            tint = StudioColors.TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.TextMuted)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.aiPromptText,
                        onValueChange = onAiPromptChange,
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Describe what to explore in this selection...",
                                fontSize = 12.sp,
                                color = StudioColors.TextMuted
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = StudioColors.TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StudioColors.SkyAccent,
                            unfocusedBorderColor = Color(0xFF3F3F46),
                            focusedContainerColor = Color(0xE609090B),
                            unfocusedContainerColor = Color(0xE609090B)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_ai_prompt")
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(StudioColors.SkyAccent, StudioColors.IndigoAccent)
                                )
                            )
                            .clickable(onClick = onRunAiFutures)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("btn_run_ai_futures"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Render",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Quick Style Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QUICK_STYLE_CHIPS.forEach { (chipLabel, promptValue) ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xE627272A))
                                .border(1.dp, Color(0x993F3F46), CircleShape)
                                .clickable { onAiPromptChange(promptValue) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chipLabel,
                                style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextSecondary)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Tool Dock
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(StudioColors.GlassPanel)
                .border(1.dp, StudioColors.GlassPanelBorder, RoundedCornerShape(24.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("bottom_tool_dock"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToolButton(StudioTool.PENCIL, Icons.Filled.Edit, state.activeTool, onSelectTool)
            ToolButton(StudioTool.INK, Icons.Filled.Create, state.activeTool, onSelectTool)
            ToolButton(StudioTool.MARKER, Icons.Filled.Brush, state.activeTool, onSelectTool)
            ToolButton(StudioTool.WATERCOLOR, Icons.Filled.AutoFixHigh, state.activeTool, onSelectTool)
            ToolButton(StudioTool.AIRBRUSH, Icons.Filled.Grain, state.activeTool, onSelectTool)
            ToolButton(StudioTool.SMUDGE, Icons.Filled.TouchApp, state.activeTool, onSelectTool)
            ToolButton(StudioTool.FILL, Icons.Filled.FormatColorFill, state.activeTool, onSelectTool)
            ToolButton(StudioTool.ERASER, Icons.Filled.AutoFixHigh, state.activeTool, onSelectTool, tintOverride = StudioColors.TextMuted)

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(Color(0xB33F3F46))
            )

            ToolButton(
                StudioTool.TRANSFORM,
                Icons.Filled.OpenWith,
                state.activeTool,
                onSelectTool,
                iconAccent = StudioColors.AmberAccent
            )
            ToolButton(
                StudioTool.VECTOR_EDIT,
                Icons.Filled.TextFields,
                state.activeTool,
                onSelectTool,
                iconAccent = StudioColors.SkySoft
            )
            ToolButton(
                StudioTool.LASSO,
                Icons.Filled.Gesture,
                state.activeTool,
                onSelectTool,
                iconAccent = StudioColors.IndigoSoft
            )
            ToolButton(
                StudioTool.RECT_SELECT,
                Icons.Filled.CropFree,
                state.activeTool,
                onSelectTool,
                iconAccent = StudioColors.IndigoSoft
            )
            ToolButton(
                StudioTool.AI_GUIDANCE,
                Icons.Filled.Gesture,
                state.activeTool,
                onSelectTool,
                iconAccent = StudioColors.SkyAccent
            )

            // AI Canvas Full Button
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                StudioColors.SkyAccent.copy(alpha = 0.2f),
                                StudioColors.IndigoAccent.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .border(1.dp, StudioColors.IndigoSoft.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onFullCanvasAi)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("btn_ai_canvas"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "AI Canvas",
                    tint = StudioColors.SkyAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "AI Canvas",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = StudioColors.IndigoSoft,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: StudioTool,
    icon: ImageVector,
    activeTool: StudioTool,
    onSelect: (StudioTool) -> Unit,
    iconAccent: Color? = null,
    tintOverride: Color? = null
) {
    val isActive = tool == activeTool
    val tint = when {
        isActive -> StudioColors.SkyAccent
        iconAccent != null -> iconAccent
        tintOverride != null -> tintOverride
        else -> StudioColors.TextMuted
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) StudioColors.SkyAccent.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (isActive) 1.dp else 0.dp,
                color = if (isActive) StudioColors.SkyAccent.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect(tool) }
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("tool_${tool.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tool.label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isActive) StudioColors.SkyAccent else StudioColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
            )
        )
    }
}

@Composable
private fun TransformButton(
    label: String?,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF27272A))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label ?: testTag,
            tint = StudioColors.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = StudioColors.TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}
