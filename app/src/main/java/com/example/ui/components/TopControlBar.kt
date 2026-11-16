package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ShapeGuide
import com.example.model.StudioUiState
import com.example.model.SymmetryMode
import com.example.model.WorkspaceMode
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun TopControlBar(
    state: StudioUiState,
    isCompactWidth: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCycleSymmetry: () -> Unit,
    onCycleShapeGuide: () -> Unit,
    onToggleSPenGuard: () -> Unit,
    onResetView: () -> Unit,
    onImportImage: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenProjectVault: () -> Unit,
    onOpenFutures: () -> Unit,
    onOpenBrushStudio: () -> Unit,
    onOpenWorkspaceHub: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSetWorkspaceMode: (WorkspaceMode) -> Unit = {},
    onOpenLoom: () -> Unit = {},
    onOpenCommandRing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tapCountState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 10.dp, end = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Primary Top Floating Dock
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(StudioColors.GlassPanel)
                .border(1.dp, StudioColors.GlassPanelBorder, RoundedCornerShape(18.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Brand Badge (5-tap reveals Developer Diagnostics)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        tapCountState.intValue++
                        if (tapCountState.intValue >= 5) {
                            tapCountState.intValue = 0
                            onOpenDiagnostics()
                        }
                    }
                    .padding(end = 6.dp)
                    .testTag("brand_badge_diag_trigger")
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(StudioColors.SkyAccent, StudioColors.IndigoAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Infinite Studio",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (!isCompactWidth) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "INFINITE ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = StudioColors.TextPrimary,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Text(
                        text = "STUDIO",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = StudioColors.SkyAccent,
                            letterSpacing = 0.8.sp
                        )
                    )
                }
            }

            BarDivider()

            // Undo / Redo
            TopIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Undo",
                enabled = state.canUndo,
                testTag = "btn_undo",
                onClick = onUndo
            )
            TopIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = "Redo",
                enabled = state.canRedo,
                testTag = "btn_redo",
                onClick = onRedo
            )

            BarDivider()

            // Symmetry Mode Cycle
            TopTogglePill(
                icon = Icons.Filled.Flip,
                label = if (isCompactWidth) null else state.symmetryMode.label,
                contentDescription = state.symmetryMode.label,
                active = state.symmetryMode != SymmetryMode.NONE,
                testTag = "btn_symmetry",
                onClick = onCycleSymmetry
            )

            // Shape Guide Cycle
            val shapeIcon = when (state.shapeGuide) {
                ShapeGuide.FREEHAND -> Icons.Filled.Gesture
                ShapeGuide.LINE -> Icons.Filled.HorizontalRule
                ShapeGuide.RECT -> Icons.Filled.CropSquare
                ShapeGuide.ELLIPSE -> Icons.Filled.RadioButtonUnchecked
            }
            TopTogglePill(
                icon = shapeIcon,
                label = if (isCompactWidth) null else state.shapeGuide.label,
                contentDescription = "Shape Guide ${state.shapeGuide.label}",
                active = state.shapeGuide != ShapeGuide.FREEHAND,
                testTag = "btn_shape_guide",
                onClick = onCycleShapeGuide
            )

            // S-Pen Guard Toggle
            TopTogglePill(
                icon = Icons.Filled.Draw,
                label = if (isCompactWidth) null else if (state.sPenOnly) "S-Pen Guard" else "Touch + Pen",
                contentDescription = if (state.sPenOnly) "S-Pen Guard ON" else "Touch and Pen ON",
                active = state.sPenOnly,
                testTag = "btn_spen_guard",
                onClick = onToggleSPenGuard
            )

            BarDivider()

            // Zoom / Fit Canvas
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onResetView)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .testTag("btn_reset_view"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(state.viewScale * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = StudioColors.TextSecondary,
                        fontSize = 11.5.sp
                    )
                )
            }

            // Import Image
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xE627272A))
                    .border(1.dp, Color(0xCC3F3F46), RoundedCornerShape(11.dp))
                    .clickable(onClick = onImportImage)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .testTag("btn_import"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FileOpen,
                    contentDescription = "Import Image",
                    tint = StudioColors.SkyAccent,
                    modifier = Modifier.size(15.dp)
                )
                if (!isCompactWidth) {
                    Text(
                        text = "Import",
                        style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.TextPrimary)
                    )
                }
            }

            // Export Artwork
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color(0xFFF4F4F5))
                    .clickable(onClick = onOpenExport)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
                    .testTag("btn_export"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Export Artwork",
                    tint = Color(0xFF18181B),
                    modifier = Modifier.size(15.dp)
                )
                if (!isCompactWidth) {
                    Text(
                        text = "Export",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF18181B),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Studio Laboratory & Task-Adaptive Workspace Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isCompactWidth) {
                Text(
                    text = state.statusBannerText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = StudioColors.FuturesSubtext,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Task-Adaptive Workspace Modes
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE614171F))
                    .border(1.dp, Color(0xFF333945), RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WorkspaceMode.entries.forEach { mode ->
                    val active = state.workspaceMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (active) StudioColors.SkyAccent.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable { onSetWorkspaceMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("mode_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (active) StudioColors.SkySoft else StudioColors.TextMuted,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }

            if (state.workspaceMode != WorkspaceMode.PAINT) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioColors.AmberAccent.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioColors.AmberAccent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSetWorkspaceMode(WorkspaceMode.PAINT) }
                        .testTag("btn_back_to_canvas")
                ) {
                    Text(
                        text = "Back to Canvas",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = StudioColors.AmberSoft,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF191A21),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioColors.SkyAccent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenCommandRing)
                    .testTag("btn_command_ring")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.DonutLarge, "Command Ring", tint = StudioColors.SkyAccent, modifier = Modifier.size(14.dp))
                    Text("Ring", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFEEEEF4)))
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF191A21),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioColors.SkyAccent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenLoom)
                    .testTag("btn_open_loom")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.AccountTree, "Futures Loom", tint = StudioColors.SkySoft, modifier = Modifier.size(14.dp))
                    Text("Loom", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkySoft))
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF191A21),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A56)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenBrushStudio)
                    .testTag("btn_brush_studio")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Brush", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFEEEEF4)))
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF191A21),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioColors.IndigoAccent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenWorkspaceHub)
                    .testTag("btn_studio_hub")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Filled.Timeline, null, tint = StudioColors.IndigoSoft, modifier = Modifier.size(14.dp))
                    Text("Studio", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.IndigoSoft))
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF191A21),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A56)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenProjectVault)
                    .testTag("btn_project_vault")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFFEEEEF4),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFEEEEF4))
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioColors.SkySoft,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9F6FC)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenFutures)
                    .testTag("btn_open_futures")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = StudioColors.SkyDarkText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Futures",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = StudioColors.SkyDarkText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(20.dp)
            .background(Color(0xB33F3F46))
    )
}

@Composable
private fun TopIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) StudioColors.TextSecondary else StudioColors.TextMuted.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TopTogglePill(
    icon: ImageVector,
    label: String?,
    contentDescription: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) StudioColors.SkyAccent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) StudioColors.SkyAccent else StudioColors.TextMuted,
            modifier = Modifier.size(16.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (active) StudioColors.SkyAccent else StudioColors.TextMuted,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
