package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.features.FuturesTableSessionV3
import com.example.model.AiEditModel
import com.example.services.ProviderMode
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun FuturesTableDialog(
    session: FuturesTableSessionV3,
    selectedModel: AiEditModel,
    providerMode: ProviderMode,
    remainingCompanionCalls: Int,
    activeProviderJobId: String? = null,
    onCancelProviderJob: () -> Unit = {},
    onFallbackLocalMode: () -> Unit = {},
    onPromptChange: (String) -> Unit,
    onSelectBranch: (Int) -> Unit,
    onSelectModel: (AiEditModel) -> Unit,
    onConfirmModelGenerate: () -> Unit,
    onSplitChange: (Int) -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit
) {
    var showModelDropdown by remember { mutableStateOf(false) }
    var showPaidConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !session.isBusy) { onClose() }

    Dialog(
        onDismissRequest = { if (!session.isBusy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioColors.FuturesShade)
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .widthIn(max = 1040.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.FuturesDialogBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("futures_table_dialog")
            ) {
                val isWide = maxWidth >= 680.dp
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = stringResource(R.string.futures_kicker),
                                style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.FuturesKicker, letterSpacing = 1.6.sp, fontSize = 11.sp)
                            )
                            Text(
                                text = stringResource(R.string.futures_title),
                                style = MaterialTheme.typography.headlineLarge.copy(color = StudioColors.FuturesHeading, fontSize = if (isWide) 30.sp else 24.sp)
                            )
                            Text(
                                text = stringResource(R.string.futures_subtitle),
                                style = MaterialTheme.typography.bodyMedium.copy(color = StudioColors.FuturesSubtext)
                            )
                            if (session.constraintWarnings.isNotEmpty()) {
                                Text(
                                    text = session.constraintWarnings.joinToString(" | "),
                                    style = MaterialTheme.typography.labelSmall.copy(color = StudioColors.AmberSoft)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF48515C), CircleShape)
                                .clickable(enabled = !session.isBusy, onClick = onClose)
                                .testTag("btn_futures_close"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Close, "Close futures", tint = Color(0xFFE6EBF1), modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = StudioColors.FuturesDivider)

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            FuturesLeftColumn(
                                session = session,
                                selectedModel = selectedModel,
                                providerMode = providerMode,
                                remainingCompanionCalls = remainingCompanionCalls,
                                activeProviderJobId = activeProviderJobId,
                                onCancelProviderJob = onCancelProviderJob,
                                onFallbackLocalMode = onFallbackLocalMode,
                                showModelDropdown = showModelDropdown,
                                onToggleModelDropdown = { showModelDropdown = it },
                                onPromptChange = onPromptChange,
                                onSelectBranch = onSelectBranch,
                                onSelectModel = onSelectModel,
                                onRequestGenerate = {
                                    if (providerMode == ProviderMode.LOOPBACK_COMPANION) {
                                        showPaidConfirmDialog = true
                                    } else {
                                        onConfirmModelGenerate()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FuturesRightColumn(
                                session = session,
                                onSplitChange = onSplitChange,
                                onAccept = onAccept,
                                onDiscard = onDiscard,
                                modifier = Modifier.weight(1.25f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            FuturesLeftColumn(
                                session = session,
                                selectedModel = selectedModel,
                                providerMode = providerMode,
                                remainingCompanionCalls = remainingCompanionCalls,
                                activeProviderJobId = activeProviderJobId,
                                onCancelProviderJob = onCancelProviderJob,
                                onFallbackLocalMode = onFallbackLocalMode,
                                showModelDropdown = showModelDropdown,
                                onToggleModelDropdown = { showModelDropdown = it },
                                onPromptChange = onPromptChange,
                                onSelectBranch = onSelectBranch,
                                onSelectModel = onSelectModel,
                                onRequestGenerate = {
                                    if (providerMode == ProviderMode.LOOPBACK_COMPANION) {
                                        showPaidConfirmDialog = true
                                    } else {
                                        onConfirmModelGenerate()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            FuturesRightColumn(
                                session = session,
                                onSplitChange = onSplitChange,
                                onAccept = onAccept,
                                onDiscard = onDiscard,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPaidConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPaidConfirmDialog = false },
            containerColor = StudioColors.FuturesDialogBg,
            titleContentColor = StudioColors.FuturesHeading,
            textContentColor = StudioColors.FuturesSubtext,
            title = { Text("Confirm Companion Dispatch") },
            text = {
                Text("Send cropped selection + mask to loopback companion (127.0.0.1:8787)? If companion remote mode is armed on your host, this may invoke external inference.")
            },
            confirmButton = {
                Button(
                    onClick = { showPaidConfirmDialog = false; onConfirmModelGenerate() },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText)
                ) { Text("Confirm Call") }
            },
            dismissButton = {
                TextButton(onClick = { showPaidConfirmDialog = false }) { Text("Cancel", color = StudioColors.FuturesSubtext) }
            }
        )
    }
}

@Composable
private fun FuturesLeftColumn(
    session: FuturesTableSessionV3,
    selectedModel: AiEditModel,
    providerMode: ProviderMode,
    remainingCompanionCalls: Int,
    activeProviderJobId: String?,
    onCancelProviderJob: () -> Unit,
    onFallbackLocalMode: () -> Unit,
    showModelDropdown: Boolean,
    onToggleModelDropdown: (Boolean) -> Unit,
    onPromptChange: (String) -> Unit,
    onSelectBranch: (Int) -> Unit,
    onSelectModel: (AiEditModel) -> Unit,
    onRequestGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("DESCRIBE THE CHANGE")
            OutlinedTextField(
                value = session.prompt,
                onValueChange = onPromptChange,
                minLines = 2,
                maxLines = 4,
                placeholder = { Text("E.g. faceted glass lantern following my silhouette", color = StudioColors.TextMuted, fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFEDF3F8)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioColors.SkyAccent,
                    unfocusedBorderColor = StudioColors.FuturesInputBorder,
                    focusedContainerColor = StudioColors.FuturesInputBg,
                    unfocusedContainerColor = StudioColors.FuturesInputBg
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("input_futures_direction")
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("LOCAL STUDIES · NO API CALLS")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session.branches.forEachIndexed { idx, branch ->
                    val selected = idx == session.selectedIndex
                    val bmp = remember(branch.previewBitmap) { branch.previewBitmap.asImageBitmap() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) StudioColors.FuturesCardActiveBg else StudioColors.FuturesCardBg)
                            .border(if (selected) 2.dp else 1.dp, if (selected) StudioColors.FuturesCardActiveBorder else StudioColors.FuturesCardBorder, RoundedCornerShape(14.dp))
                            .clickable(enabled = !session.isBusy) { onSelectBranch(idx) }
                            .padding(7.dp)
                            .testTag("branch_card_${branch.branchId.id}"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "${branch.label} preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF17191E))
                        )
                        Text(branch.label, style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFE5EEF2), fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(branch.sourceLabel, style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF9DB4C0), fontSize = 8.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("PROVIDER ROUTER (${providerMode.name})")
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioColors.FuturesInputBg)
                        .border(1.dp, StudioColors.FuturesInputBorder, RoundedCornerShape(10.dp))
                        .clickable { onToggleModelDropdown(true) }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag("select_futures_model"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedModel.label, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFEDF3F8)))
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = StudioColors.FuturesSubtext, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showModelDropdown, onDismissRequest = { onToggleModelDropdown(false) }) {
                    AiEditModel.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { onToggleModelDropdown(false); onSelectModel(m) })
                    }
                }
            }

            val canInvokeRouter = when (providerMode) {
                ProviderMode.OFFLINE_LOCAL_ONLY -> false
                ProviderMode.OFFLINE_MOCK_PROVIDER -> !session.isBusy
                ProviderMode.LOOPBACK_COMPANION -> remainingCompanionCalls > 0 && !session.isBusy
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRequestGenerate,
                    enabled = canInvokeRouter,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A56)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF191A21), contentColor = Color(0xFFEEEEF4), disabledContentColor = Color(0x66EEEEF4)),
                    modifier = Modifier.testTag("btn_generate_model_future")
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Provider Route", fontSize = 12.sp)
                }
                Text(
                    text = when (providerMode) {
                        ProviderMode.OFFLINE_LOCAL_ONLY -> "Offline mode · local studies only"
                        ProviderMode.OFFLINE_MOCK_PROVIDER -> "Mock provider ready (no spend)"
                        ProviderMode.LOOPBACK_COMPANION -> "Loopback companion 127.0.0.1:8787 ($remainingCompanionCalls left)"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA9C2CB)),
                    modifier = Modifier.weight(1f)
                )
            }
            if (activeProviderJobId != null || providerMode != ProviderMode.OFFLINE_LOCAL_ONLY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeProviderJobId != null) {
                        OutlinedButton(
                            onClick = onCancelProviderJob,
                            modifier = Modifier.testTag("btn_futures_cancel_job")
                        ) {
                            Text("Cancel Job", fontSize = 11.sp, color = StudioColors.AmberSoft)
                        }
                    }
                    if (providerMode != ProviderMode.OFFLINE_LOCAL_ONLY) {
                        OutlinedButton(
                            onClick = onFallbackLocalMode,
                            modifier = Modifier.testTag("btn_futures_fallback_local")
                        ) {
                            Text("Fallback Local", fontSize = 11.sp, color = StudioColors.SkySoft)
                        }
                    }
                }
            }

            Text(
                text = buildAnnotatedString {
                    append("The three cards above are deterministic local color studies, ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = StudioColors.FuturesHeading)) { append("not AI output") }
                    append(". Change Provider Mode in Studio Hub to test Mock or Loopback Companion.")
                },
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA6B7C4))
            )
        }
    }
}

@Composable
private fun FuturesRightColumn(
    session: FuturesTableSessionV3,
    onSplitChange: (Int) -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBranch = session.branches.getOrNull(session.selectedIndex)
    val beforeImg = remember(session.originalBitmap) { session.originalBitmap.asImageBitmap() }
    val afterImg = remember(activeBranch?.previewBitmap) { activeBranch?.previewBitmap?.asImageBitmap() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        SectionTitle("PEEL BETWEEN THE ORIGINAL AND THE SELECTED FUTURE")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, Color(0xFF3B505F), RoundedCornerShape(15.dp))
                .pointerInput(session.isBusy) {
                    detectTapGestures { pos ->
                        if (!session.isBusy && size.width > 0) onSplitChange(((pos.x / size.width) * 100f).roundToInt().coerceIn(0, 100))
                    }
                }
                .pointerInput(session.isBusy) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (!session.isBusy && size.width > 0) onSplitChange(((change.position.x / size.width) * 100f).roundToInt().coerceIn(0, 100))
                    }
                }
                .testTag("futures_peel_preview")
        ) {
            CheckerboardBackground(
                darkColor = Color(0xFF0D141C),
                lightColor = Color(0xFF252F36),
                cellPx = 22,
                modifier = Modifier.fillMaxSize()
            )
            Image(bitmap = beforeImg, contentDescription = "Original selection", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            if (afterImg != null) {
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val splitFraction = session.splitPercent / 100f
                    Image(
                        bitmap = afterImg,
                        contentDescription = "Candidate preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(
                            androidx.compose.foundation.shape.GenericShape { shapeSize, _ ->
                                addRect(Rect(shapeSize.width * splitFraction, 0f, shapeSize.width, shapeSize.height))
                            }
                        )
                    )
                }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width * (session.splitPercent / 100f)
                drawLine(Color(0xFFD9EFF7), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(11.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC081017)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text("ORIGINAL", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp))
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(11.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC081017)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text("POSSIBILITY", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 10.sp))
            }
        }

        Column {
            Text("Comparison: ${100 - session.splitPercent}% revealed", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFD3E1EC)))
            Slider(
                value = session.splitPercent.toFloat(),
                onValueChange = { onSplitChange(it.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF8BE2F1), activeTrackColor = Color(0xFF8BE2F1)),
                modifier = Modifier.fillMaxWidth().testTag("slider_futures_split")
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = onAccept,
                enabled = !session.isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText),
                modifier = Modifier.weight(1f).testTag("btn_futures_accept")
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Accept as a new layer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDiscard,
                enabled = !session.isBusy,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF536372)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF242E37), contentColor = Color(0xFFE3EEF4)),
                modifier = Modifier.testTag("btn_futures_discard")
            ) {
                Text("Discard without changes", fontSize = 12.sp)
            }
        }

        Text(session.progressMessageOrStatus(), style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB6DDEC)))
        HorizontalDivider(color = Color(0xFF34404D))
        Text(
            "CONSERVATION RULE: Accepted results render strictly inside your mask (and outside locked constraints) onto a new layer. Originals never change.",
            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA9B9C6))
        )
    }
}

private fun FuturesTableSessionV3.progressMessageOrStatus(): String = statusMessage

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFFA2CDD9), letterSpacing = 1.1.sp, fontSize = 12.sp)
    )
}
