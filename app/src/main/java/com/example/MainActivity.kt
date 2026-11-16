package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ExportFormat
import com.example.model.StudioTool
import com.example.model.WorkspaceMode
import com.example.ui.canvas.InfiniteCanvasView
import com.example.ui.components.BottomToolAndContextDock
import com.example.ui.components.BrushStudioDialog
import com.example.ui.components.ColorStudioPopover
import com.example.ui.components.CreativeCommandRingOverlay
import com.example.ui.components.DiagnosticsDialog
import com.example.ui.components.ExportDialog
import com.example.ui.components.FuturesLoomDialog
import com.example.ui.components.FuturesTableDialog
import com.example.ui.components.LayersDock
import com.example.ui.components.LeftBrushDock
import com.example.ui.components.ProjectVaultDialog
import com.example.ui.components.StudioWorkspaceSheet
import com.example.ui.components.TopControlBar
import com.example.ui.theme.InfiniteStudioTheme
import com.example.ui.theme.StudioColors
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InfiniteStudioTheme {
                InfiniteStudioApp()
            }
        }
    }
}

@Composable
fun InfiniteStudioApp(viewModel: StudioViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Zero-permission Android Photo Picker for Image Import
    val importImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.importImageUri(uri)
    }

    // SAF launchers for .iastudio v3 archives, SVG, Foundry ZIP, & PNG/JPG exports
    val saveProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.saveProjectToUri(uri)
    }

    val openProjectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.openProjectFromUri(uri)
    }

    val exportPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    val exportJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null) viewModel.exportToUri(uri)
    }

    val exportSvgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        if (uri != null) viewModel.exportSvgToUri(uri)
    }

    val exportFoundryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportFoundryBundleToUri(uri)
    }

    BackHandler(
        enabled = state.showCommandRing || state.showFuturesLoom || state.showColorPopover ||
            state.showLayersPanel || state.showAiPromptDock || state.showBrushStudio ||
            state.showWorkspaceHub || state.showDiagnostics
    ) {
        when {
            state.showCommandRing -> viewModel.toggleCommandRing(false)
            state.showFuturesLoom -> viewModel.toggleFuturesLoom(false)
            state.showDiagnostics -> viewModel.toggleDiagnosticsDialog(false)
            state.showWorkspaceHub -> viewModel.toggleWorkspaceHub(false)
            state.showBrushStudio -> viewModel.toggleBrushStudio(false)
            state.showColorPopover -> viewModel.toggleColorPopover(false)
            state.showLayersPanel -> viewModel.toggleLayersPanel(false)
            state.showAiPromptDock -> viewModel.clearSelection()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.VoidBlack)
    ) {
        val isCompactWidth = maxWidth < 680.dp
        val isCompactHeight = maxHeight < 640.dp

        // 1. Sparse-Tile + Native S-Pen Canvas
        InfiniteCanvasView(
            state = state,
            tileRenderer = viewModel.tileRenderer,
            onViewportSizeChanged = { viewportSize = it },
            onUpdateViewport = viewModel::updateViewport,
            onBeginPointer = viewModel::beginPointerAction,
            onMovePointer = viewModel::movePointerAction,
            onEndPointer = viewModel::endPointerAction,
            onCancelStroke = viewModel::cancelActiveStroke,
            onTwoFingerTapUndo = viewModel::undo,
            onThreeFingerTapRedo = viewModel::redo,
            onStylusDiagnostics = viewModel::updateStylusDiagnostics,
            onSelectVector = viewModel::selectVectorObject,
            onMoveVectorLive = viewModel::moveVectorLive,
            onResizeVectorLive = viewModel::resizeVectorLive,
            onMoveBezierPartLive = viewModel::moveBezierPartLive,
            onMoveBalloonTailLive = viewModel::moveBalloonTailLive,
            onCommitVectorEdits = viewModel::commitVectorLiveEdits,
            onUpdateTransformLive = viewModel::updateInteractiveTransform,
            onCommitTransform = viewModel::commitInteractiveTransform
        )

        // 2. Top Floating Control Bar
        TopControlBar(
            state = state,
            isCompactWidth = isCompactWidth,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onCycleSymmetry = viewModel::cycleSymmetryMode,
            onCycleShapeGuide = viewModel::cycleShapeGuide,
            onToggleSPenGuard = viewModel::toggleSPenGuard,
            onResetView = {
                viewModel.fitCanvasToScreen(viewportSize.width.toFloat(), viewportSize.height.toFloat())
            },
            onImportImage = {
                importImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onOpenExport = viewModel::openExportDialog,
            onOpenProjectVault = { viewModel.toggleProjectDialog(true) },
            onOpenFutures = { viewModel.openFuturesTable() },
            onOpenBrushStudio = { viewModel.toggleBrushStudio(true) },
            onOpenWorkspaceHub = { viewModel.toggleWorkspaceHub(true, tab = state.workspaceMode.defaultHubTab) },
            onOpenDiagnostics = { viewModel.toggleDiagnosticsDialog(true) },
            onSetWorkspaceMode = viewModel::setWorkspaceMode,
            onOpenLoom = { viewModel.toggleFuturesLoom(true) },
            onOpenCommandRing = { viewModel.toggleCommandRing(true) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Left Floating Ergonomic Brush & Color Dock
        LeftBrushDock(
            state = state,
            compact = isCompactHeight,
            onToggleColorPicker = { viewModel.toggleColorPopover() },
            onBrushSizeChange = viewModel::setBrushSize,
            onBrushOpacityChange = viewModel::setBrushOpacity,
            onSelectEyedropper = { viewModel.selectTool(StudioTool.EYEDROPPER) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
        )

        // 4. Color Studio Popover
        AnimatedVisibility(
            visible = state.showColorPopover,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 82.dp)
        ) {
            ColorStudioPopover(
                currentColorHex = state.brushColorHex,
                currentColorInt = state.brushColorInt,
                onSelectColorHex = viewModel::setActiveColor,
                onSelectColorInt = viewModel::setActiveColorInt,
                onClose = { viewModel.toggleColorPopover(false) }
            )
        }

        // 5. Right Floating Layers Drawer & Trigger Pill
        LayersDock(
            state = state,
            onTogglePanel = { viewModel.toggleLayersPanel() },
            onAddLayer = viewModel::addLayer,
            onDuplicateLayer = viewModel::duplicateActiveLayer,
            onMergeDown = viewModel::mergeDownActiveLayer,
            onClearLayer = viewModel::clearActiveLayer,
            onSelectBlendMode = viewModel::setActiveLayerBlendMode,
            onToggleAlphaLock = viewModel::toggleAlphaLock,
            onLayerOpacityChange = viewModel::setActiveLayerOpacity,
            onLayerOpacityCommit = viewModel::commitActiveLayerOpacityHistory,
            onSelectLayer = viewModel::selectLayer,
            onMoveLayerUp = viewModel::moveLayerUp,
            onMoveLayerDown = viewModel::moveLayerDown,
            onToggleVisibility = viewModel::toggleLayerVisibility,
            onDeleteLayer = viewModel::deleteLayer,
            onSelectPaperStyle = viewModel::setPaperStyle,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        )

        // 6. Bottom Floating Tool Dock + Transform Dock + Vector Dock + Animate Transport + AI Selection
        BottomToolAndContextDock(
            state = state,
            onSelectTool = viewModel::selectTool,
            onFullCanvasAi = viewModel::selectFullCanvasForAi,
            onTransformFlipH = { viewModel.flipInteractiveTransform(horizontal = true) },
            onTransformFlipV = { viewModel.flipInteractiveTransform(horizontal = false) },
            onTransformScaleDown = { viewModel.updateInteractiveTransform(scaleFactor = 0.9f) },
            onTransformScaleUp = { viewModel.updateInteractiveTransform(scaleFactor = 1.1f) },
            onTransformRotate = { viewModel.updateInteractiveTransform(rotationDegDelta = 15f) },
            onTransformToggleSnap = viewModel::toggleTransformSnap,
            onTransformCancel = viewModel::cancelInteractiveTransform,
            onTransformDone = viewModel::commitInteractiveTransform,
            onToggleVectorSnap = viewModel::toggleVectorSnap,
            onDeleteSelectedVector = viewModel::deleteVectorObject,
            onAddQuickVectorText = { viewModel.addVectorTextBox("New Caption") },
            onToggleTimelinePlay = viewModel::toggleTimelinePlayback,
            onSelectTimelineFrame = viewModel::selectTimelineFrame,
            onToggleOnionSkin = viewModel::toggleOnionSkin,
            onCancelSelection = viewModel::clearSelection,
            onAiPromptChange = viewModel::updateAiPromptText,
            onRunAiFutures = { viewModel.openFuturesTable() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 7. Floating Toast Notification Pill
        AnimatedVisibility(
            visible = state.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, StudioColors.SkyAccent.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("toast_pill")
            ) {
                Text(
                    text = state.toastMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkySoft)
                )
            }
        }

        // 8. The Futures Table Modal
        state.futuresSession?.let { session ->
            FuturesTableDialog(
                session = session,
                selectedModel = state.selectedAiModel,
                providerMode = state.providerMode,
                remainingCompanionCalls = state.remainingCompanionCalls,
                activeProviderJobId = state.activeProviderJobId,
                onCancelProviderJob = viewModel::cancelActiveProviderJob,
                onFallbackLocalMode = viewModel::fallbackToLocalMode,
                onPromptChange = viewModel::updateFuturesPrompt,
                onSelectBranch = viewModel::selectFutureBranch,
                onSelectModel = viewModel::selectFuturesModel,
                onConfirmModelGenerate = { viewModel.generateModelFutureConfirmed(true) },
                onSplitChange = viewModel::updateFuturesSplit,
                onAccept = viewModel::acceptSelectedFuture,
                onDiscard = { viewModel.closeFuturesTable(discardMessage = true) },
                onClose = { viewModel.closeFuturesTable(discardMessage = false) }
            )
        }

        // 8.5 The Futures Loom Modal (Connected Creative Continuum + Branch Diff/Cherry-Pick)
        if (state.showFuturesLoom) {
            FuturesLoomDialog(
                state = state,
                onForkBranch = viewModel::forkCreativeBranch,
                onCheckoutBranch = viewModel::rollbackToBranch,
                onCompareBranches = viewModel::compareCreativeBranches,
                onClearComparison = viewModel::clearBranchComparison,
                onPreviewCherryPick = viewModel::previewCherryPick,
                onApplyCherryPick = viewModel::applyCherryPick,
                onClearCherryPick = viewModel::clearCherryPickPreview,
                onOpenFuturesTable = { viewModel.openFuturesTable() },
                onOpenStudioTab = { tab -> viewModel.toggleWorkspaceHub(open = true, tab = tab) },
                onSwitchWorkspaceMode = viewModel::setWorkspaceMode,
                onRunForensics = viewModel::runVisualForensicsAudit,
                onClose = { viewModel.toggleFuturesLoom(false) }
            )
        }

        // 9. Brush Studio Modal
        if (state.showBrushStudio) {
            BrushStudioDialog(
                currentPreset = state.activeBrushPreset,
                savedPresets = state.document.customPresets,
                onUpdatePreset = viewModel::updateBrushPreset,
                onSavePreset = viewModel::saveCustomBrushPreset,
                onClose = { viewModel.toggleBrushStudio(false) }
            )
        }

        // 10. Studio Workspace Hub (20 Creative Engines)
        if (state.showWorkspaceHub) {
            StudioWorkspaceSheet(
                state = state,
                selectedTab = state.workspaceTab,
                viewModel = viewModel,
                onExportSvg = { exportSvgLauncher.launch("InfiniteStudio_${System.currentTimeMillis()}.svg") },
                onExportFoundry = { exportFoundryLauncher.launch("InfiniteFoundry_${System.currentTimeMillis()}.zip") },
                onClose = { viewModel.toggleWorkspaceHub(false) }
            )
        }

        // 11. Developer Diagnostics (Redacted)
        if (state.showDiagnostics) {
            DiagnosticsDialog(
                reportText = viewModel.buildRedactedDiagnosticsReport(),
                onClose = { viewModel.toggleDiagnosticsDialog(false) }
            )
        }

        // 12. Project Vault Modal
        if (state.showProjectDialog) {
            ProjectVaultDialog(
                hasLocalDraft = state.hasLocalDraft,
                statusMessage = state.projectStatusMessage,
                onSaveProject = {
                    saveProjectLauncher.launch("InfiniteStudio_${System.currentTimeMillis()}.iastudio")
                },
                onOpenProject = {
                    openProjectLauncher.launch(arrayOf("application/zip", "application/json", "*/*"))
                },
                onRecoverDraft = viewModel::recoverLocalDraft,
                onNewCanvas = viewModel::createNewBlankCanvas,
                onLoadSample = viewModel::loadSampleProject,
                onLoadLivingWorkshop = viewModel::loadLivingWorkshopJourney,
                onClose = { viewModel.toggleProjectDialog(false) }
            )
        }

        // 14. Creative Command Ring Overlay
        if (state.showCommandRing) {
            CreativeCommandRingOverlay(
                state = state,
                onTriggerAction = viewModel::triggerCommandRingAction,
                onCustomizeSlot = viewModel::customizeCommandRingSlot,
                onToggleAccessibleStrip = viewModel::toggleAccessibleRingStrip,
                onToggleReducedMotion = viewModel::toggleReducedMotion,
                onDismiss = { viewModel.toggleCommandRing(false) }
            )
        }

        // 13. Export Artwork Modal
        if (state.showExportDialog) {
            ExportDialog(
                previewBitmap = state.exportPreviewBitmap,
                selectedFormat = state.exportFormat,
                jpgQuality = state.jpgQuality,
                onSelectFormat = viewModel::setExportFormat,
                onJpgQualityChange = viewModel::setJpgQuality,
                onSaveFile = {
                    val name = "InfiniteStudio_${System.currentTimeMillis()}"
                    if (state.exportFormat == ExportFormat.PNG) exportPngLauncher.launch("$name.png")
                    else exportJpgLauncher.launch("$name.jpg")
                },
                onShareFile = {
                    viewModel.shareExportedArtwork { uri, mime ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Artwork"))
                    }
                },
                onClose = viewModel::closeExportDialog
            )
        }
    }
}
