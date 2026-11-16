package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.StudioColors

private enum class PendingVaultAction { OPEN, RECOVER, NEW_CANVAS, LOAD_SAMPLE, LOAD_WORKSHOP }

@Composable
fun ProjectVaultDialog(
    hasLocalDraft: Boolean,
    statusMessage: String,
    onSaveProject: () -> Unit,
    onOpenProject: () -> Unit,
    onRecoverDraft: () -> Unit,
    onNewCanvas: () -> Unit,
    onLoadSample: () -> Unit = {},
    onLoadLivingWorkshop: () -> Unit = {},
    onClose: () -> Unit
) {
    var pendingConfirm by remember { mutableStateOf<PendingVaultAction?>(null) }

    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioColors.FuturesShade)
                .safeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.FuturesDialogBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .testTag("project_vault_dialog"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.project_kicker),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = StudioColors.FuturesKicker,
                                letterSpacing = 1.6.sp
                            )
                        )
                        Text(
                            text = stringResource(R.string.project_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = StudioColors.FuturesHeading,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = stringResource(R.string.project_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.FuturesSubtext)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color(0xFF48515C), CircleShape)
                            .clickable(onClick = onClose)
                            .testTag("btn_project_close"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFE6EBF1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = StudioColors.FuturesDivider)

                // 2x2 Action Grid
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Button(
                            onClick = onSaveProject,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioColors.SkySoft,
                                contentColor = StudioColors.SkyDarkText
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_project")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Save editable project", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        VaultActionButton(
                            label = "Open a project",
                            icon = Icons.Filled.UploadFile,
                            enabled = true,
                            testTag = "btn_load_project",
                            onClick = { pendingConfirm = PendingVaultAction.OPEN },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        VaultActionButton(
                            label = "Recover local draft",
                            icon = Icons.Filled.History,
                            enabled = hasLocalDraft,
                            testTag = "btn_recover_project",
                            onClick = { pendingConfirm = PendingVaultAction.RECOVER },
                            modifier = Modifier.weight(1f)
                        )

                        VaultActionButton(
                            label = "New canvas",
                            icon = Icons.Filled.Add,
                            enabled = true,
                            testTag = "btn_new_project",
                            onClick = { pendingConfirm = PendingVaultAction.NEW_CANVAS },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { pendingConfirm = PendingVaultAction.LOAD_SAMPLE },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A56)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF191A21),
                        contentColor = StudioColors.SkySoft
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_load_sample")
                ) {
                    Text("Load Offline Sample (One Mark, Three Futures)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { pendingConfirm = PendingVaultAction.LOAD_WORKSHOP },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioColors.IndigoAccent.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF171B2A),
                        contentColor = StudioColors.IndigoSoft
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_load_living_workshop")
                ) {
                    Text("Load Living Workshop Journey (Complete Multi-System Demo)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider(color = Color(0xFF34404D))

                Text(
                    text = "Draft recovery uses local app storage; it is not a backup. Save a .iastudio file periodically for a portable, editable copy. Opening or creating a project replaces the current canvas only after confirmation.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA9B9C6))
                )

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB6C8D1))
                    )
                }
            }
        }
    }

    pendingConfirm?.let { action ->
        val (title, body) = when (action) {
            PendingVaultAction.OPEN -> "Open Project?" to "The current canvas will be replaced. Save it first if needed."
            PendingVaultAction.RECOVER -> "Recover Local Draft?" to "Replace the current canvas with this device's recovered draft?"
            PendingVaultAction.NEW_CANVAS -> "Start Blank Canvas?" to "Export the current project before replacing it with a blank canvas."
            PendingVaultAction.LOAD_SAMPLE -> "Load Offline Sample?" to "Loads the repeatable offline sample project exercising Futures, masks, vectors, materials, branches, recipe & timeline."
            PendingVaultAction.LOAD_WORKSHOP -> "Load Living Workshop Journey?" to "Loads the complete multi-system guided sample with Branches, Orchard, Constraints, Vectors, Material Lighting, Recipe DAG & Timeline."
        }
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            containerColor = StudioColors.FuturesDialogBg,
            titleContentColor = StudioColors.FuturesHeading,
            textContentColor = StudioColors.FuturesSubtext,
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirm = null
                        when (action) {
                            PendingVaultAction.OPEN -> onOpenProject()
                            PendingVaultAction.RECOVER -> onRecoverDraft()
                            PendingVaultAction.NEW_CANVAS -> onNewCanvas()
                            PendingVaultAction.LOAD_SAMPLE -> onLoadSample()
                            PendingVaultAction.LOAD_WORKSHOP -> onLoadLivingWorkshop()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.SkySoft,
                        contentColor = StudioColors.SkyDarkText
                    )
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) {
                    Text("Cancel", color = StudioColors.FuturesSubtext)
                }
            }
        )
    }
}

@Composable
private fun VaultActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A56)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF191A21),
            contentColor = Color(0xFFEEEEF4),
            disabledContentColor = Color(0x55EEEEF4)
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.5.sp)
    }
}
