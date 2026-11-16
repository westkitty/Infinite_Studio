package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.document.CanonicalDocument
import com.example.document.ConstraintKind
import com.example.document.CustomBrushPreset
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentCommand
import com.example.document.DocumentLayer
import com.example.document.LayerTransform
import com.example.document.MaterialRegion
import com.example.document.RecipeNodeKind
import com.example.document.SelectionMaskRecord
import com.example.document.TransactionEngine
import com.example.document.Vec2
import com.example.features.CherryPickResolution
import com.example.features.FuturesAndConstraintsEngine
import com.example.features.LivingPreviewEngine
import com.example.features.ObjectSurgeryAndMaterialEngine
import com.example.features.TimeAndRecipeEngine
import com.example.features.VaultBranchOrchardEngine
import com.example.features.VectorAndFoundryEngine
import com.example.input.StylusDiagnostics
import com.example.model.AiEditModel
import com.example.model.CommandRingAction
import com.example.model.ExportFormat
import com.example.model.InteractiveTransformState
import com.example.model.LayerBlendMode
import com.example.model.PaperStyle
import com.example.model.SelectionState
import com.example.model.SelectionType
import com.example.model.ShapeGuide
import com.example.model.StudioTool
import com.example.model.StudioUiState
import com.example.model.SymmetryMode
import com.example.model.WorkspaceMode
import com.example.render.SparseTileRenderer
import com.example.services.ProviderJobRequest
import com.example.services.ProviderJobStatus
import com.example.services.ProviderMode
import com.example.services.ProviderRouter
import com.example.storage.ProjectStorageManager
import com.example.tools.TileBrushEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    val tileRenderer = SparseTileRenderer()
    val storageManager = ProjectStorageManager(application)
    val providerRouter = ProviderRouter()

    private val transactionEngine = TransactionEngine(createInitialDocument())
    private val livingPreviewEngine = LivingPreviewEngine(viewModelScope, tileRenderer)

    private val _uiState = MutableStateFlow(StudioUiState(document = transactionEngine.current()))
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    // Active stroke accumulation (fast in-memory list; committed atomically on endPointerAction)
    private val activeStrokeSamples = mutableListOf<Pair<Vec2, Float>>()
    private var shapeStartPt: PointF? = null
    private var shapeEndPt: PointF? = null
    private var transformDragOrigin: PointF? = null
    private var transformInitialOffsetX = 0f
    private var transformInitialOffsetY = 0f

    private var autosaveJob: Job? = null
    private var toastJob: Job? = null
    private var playbackJob: Job? = null
    private var activeProviderJob: Job? = null
    @Volatile
    private var recipeCancelRequested: Boolean = false

    override fun onCleared() {
        playbackJob?.cancel()
        activeProviderJob?.cancel()
        autosaveJob?.cancel()
        toastJob?.cancel()
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            transactionEngine.document.collectLatest { doc ->
                val metrics = transactionEngine.metrics()
                _uiState.update {
                    it.copy(
                        document = doc,
                        canUndo = metrics.canUndo,
                        canRedo = metrics.canRedo,
                        historyEstimatedBytes = metrics.estimatedBytes,
                        historyStackSize = metrics.stackSize,
                        tileCacheStats = tileRenderer.stats(),
                        renderTick = it.renderTick + 1
                    )
                }
                livingPreviewEngine.schedulePreview(doc)
                queueAutosave(doc)
            }
        }
        viewModelScope.launch {
            livingPreviewEngine.state.collectLatest { snap ->
                _uiState.update { it.copy(livingPreview = snap) }
            }
        }

        _uiState.update {
            it.copy(
                remainingCompanionCalls = providerRouter.remainingCompanionCalls(),
                statusBannerText = buildStatusBanner(it.providerMode)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val hasDraft = storageManager.hasRecoverableDraft()
            if (hasDraft) {
                _uiState.update { it.copy(hasLocalDraft = true) }
            }
        }
    }

    private fun createInitialDocument(): CanonicalDocument {
        val sketch = DocumentLayer(id = "layer_sketch", name = "Sketch Base")
        val ink = DocumentLayer(id = "layer_ink", name = "Ink & Detail")
        return CanonicalDocument(
            title = "Infinite Studio Canvas",
            layers = listOf(sketch, ink),
            activeLayerId = ink.id
        )
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        val nonce = System.currentTimeMillis()
        _uiState.update { it.copy(toastMessage = message, toastNonce = nonce) }
        toastJob = viewModelScope.launch {
            delay(2200)
            _uiState.update { if (it.toastNonce == nonce) it.copy(toastMessage = null) else it }
        }
    }

    private fun logError(msg: String) {
        _uiState.update { it.copy(recentErrors = (it.recentErrors + msg).takeLast(16)) }
    }

    private fun commitDoc(
        description: String,
        processingMode: String = "LOCAL_EDIT",
        providerId: String? = null,
        transform: (CanonicalDocument) -> CanonicalDocument
    ) {
        transactionEngine.commit(
            DocumentCommand(
                description = description,
                processingMode = processingMode,
                providerId = providerId,
                transform = transform
            )
        )
    }

    // --- Viewport & Navigation ---
    fun updateViewport(scale: Float, offsetX: Float, offsetY: Float) {
        _uiState.update {
            it.copy(
                viewScale = scale.coerceIn(0.15f, 4.0f),
                viewOffsetX = offsetX,
                viewOffsetY = offsetY
            )
        }
    }

    fun fitCanvasToScreen(viewportW: Float, viewportH: Float) {
        val doc = transactionEngine.current()
        val art = tileRenderer.computeArtworkBoundingBox(doc)
        if (art != null && viewportW > 0f && viewportH > 0f) {
            val pad = 140f
            val sx = (viewportW - pad) / art.width()
            val sy = (viewportH - pad) / art.height()
            val nextScale = max(0.2f, min(min(sx, sy), 1.25f))
            _uiState.update {
                it.copy(
                    viewScale = nextScale,
                    viewOffsetX = -art.centerX() * nextScale,
                    viewOffsetY = -art.centerY() * nextScale
                )
            }
        } else {
            _uiState.update { it.copy(viewScale = 1.0f, viewOffsetX = 0f, viewOffsetY = 0f) }
        }
        showToast("View Centered")
    }

    // --- Tools, Brushes & Stylus Diagnostics ---
    fun selectTool(tool: StudioTool) {
        val preset = TileBrushEngine.defaultPresetForTool(tool, _uiState.value.brushSize, _uiState.value.brushOpacity)
        _uiState.update {
            it.copy(
                activeTool = tool,
                activeBrushPreset = preset,
                showColorPopover = false
            )
        }
        when (tool) {
            StudioTool.TRANSFORM -> {
                beginInteractiveTransform()
                showToast("Drag handles or layer on canvas · Snap ready")
            }
            StudioTool.VECTOR_EDIT -> showToast("Tap or drag vectors, Bezier handles & balloon tails")
            StudioTool.LASSO -> showToast("Draw a freehand loop around any area")
            StudioTool.RECT_SELECT -> showToast("Drag a box around any area")
            StudioTool.AI_GUIDANCE -> showToast("Draw guidance stroke for Futures / Art Director")
            else -> Unit
        }
    }

    fun setBrushSize(size: Float) {
        val clamped = size.coerceIn(1f, 120f)
        _uiState.update {
            it.copy(
                brushSize = clamped,
                activeBrushPreset = it.activeBrushPreset.copy(radius = clamped)
            )
        }
    }

    fun setBrushOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.05f, 1f)
        _uiState.update {
            it.copy(
                brushOpacity = clamped,
                activeBrushPreset = it.activeBrushPreset.copy(opacity = clamped)
            )
        }
    }

    fun updateBrushPreset(preset: CustomBrushPreset) {
        _uiState.update {
            it.copy(
                brushSize = preset.radius,
                brushOpacity = preset.opacity,
                activeBrushPreset = preset
            )
        }
    }

    fun saveCustomBrushPreset(preset: CustomBrushPreset) {
        updateBrushPreset(preset)
        commitDoc("Saved brush preset '${preset.name}'") { doc ->
            val updated = doc.customPresets.filterNot { it.id == preset.id } + preset
            doc.copy(customPresets = updated)
        }
        showToast("Brush preset saved")
    }

    fun toggleBrushStudio(open: Boolean? = null) {
        _uiState.update { it.copy(showBrushStudio = open ?: !it.showBrushStudio) }
    }

    fun updateStylusDiagnostics(diag: StylusDiagnostics) {
        _uiState.update { it.copy(stylusDiagnostics = diag) }
    }

    fun toggleColorPopover(open: Boolean? = null) {
        _uiState.update { it.copy(showColorPopover = open ?: !it.showColorPopover) }
    }

    fun setActiveColor(hex: String) {
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(normalized)) return
        val colorInt = Color.parseColor(normalized)
        _uiState.update { it.copy(brushColorHex = normalized.uppercase(), brushColorInt = colorInt) }
    }

    fun setActiveColorInt(colorInt: Int) {
        val hex = String.format("#%06X", 0xFFFFFF and colorInt)
        _uiState.update { it.copy(brushColorHex = hex, brushColorInt = colorInt or (0xFF shl 24)) }
    }

    fun cycleSymmetryMode() {
        val vals = SymmetryMode.entries
        val next = vals[(_uiState.value.symmetryMode.ordinal + 1) % vals.size]
        _uiState.update { it.copy(symmetryMode = next) }
        showToast(next.label)
    }

    fun cycleShapeGuide() {
        val vals = ShapeGuide.entries
        val next = vals[(_uiState.value.shapeGuide.ordinal + 1) % vals.size]
        _uiState.update { it.copy(shapeGuide = next) }
        showToast("Shape Guide: ${next.label}")
    }

    fun toggleSPenGuard() {
        val next = !_uiState.value.sPenOnly
        _uiState.update { it.copy(sPenOnly = next) }
        showToast(if (next) "S-Pen Guard ON: Touch pans, S-Pen draws" else "Touch + Pen ON")
    }

    fun toggleLayersPanel(open: Boolean? = null) {
        _uiState.update { it.copy(showLayersPanel = open ?: !it.showLayersPanel) }
    }

    // --- Pointer Actions ---
    fun beginPointerAction(worldPos: PointF, pressure: Float): Pair<PointF?, PointF?> {
        val s = _uiState.value
        val doc = s.document
        activeStrokeSamples.clear()
        shapeStartPt = null
        shapeEndPt = null

        when (s.activeTool) {
            StudioTool.EYEDROPPER -> {
                val sampled = TileBrushEngine.sampleColorAt(doc, tileRenderer, worldPos.x, worldPos.y)
                setActiveColorInt(sampled)
                _uiState.update { it.copy(activeTool = StudioTool.PENCIL) }
                showToast("Sampled ${String.format("#%06X", 0xFFFFFF and sampled)}")
                return null to null
            }

            StudioTool.TRANSFORM -> {
                val layer = doc.layers.find { it.id == doc.activeLayerId }
                if (layer == null || !layer.visible) {
                    showToast("Select a visible layer to transform")
                    return null to null
                }
                transformDragOrigin = worldPos
                transformInitialOffsetX = layer.transform.offsetX
                transformInitialOffsetY = layer.transform.offsetY
                return null to null
            }

            StudioTool.FILL -> {
                val activeId = doc.activeLayerId ?: return null to null
                val (nextDoc, msg) = TileBrushEngine.floodFillOnLayer(
                    doc = doc,
                    renderer = tileRenderer,
                    targetLayerId = activeId,
                    worldX = worldPos.x,
                    worldY = worldPos.y,
                    fillColorInt = s.brushColorInt,
                    opacity = s.brushOpacity
                )
                if (msg != null) showToast(msg)
                else commitDoc("Flood Fill on layer") { nextDoc }
                return null to null
            }

            StudioTool.LASSO, StudioTool.RECT_SELECT -> {
                val selType = if (s.activeTool == StudioTool.LASSO) SelectionType.LASSO else SelectionType.RECT
                _uiState.update {
                    it.copy(selection = SelectionState(active = true, type = selType, points = listOf(worldPos)))
                }
                return null to null
            }

            StudioTool.AI_GUIDANCE -> {
                activeStrokeSamples.add(Vec2(worldPos.x, worldPos.y) to pressure)
                return null to null
            }

            else -> {
                shapeStartPt = worldPos
                shapeEndPt = worldPos
                activeStrokeSamples.add(Vec2(worldPos.x, worldPos.y) to pressure)
                return shapeStartPt to shapeEndPt
            }
        }
    }

    fun movePointerAction(worldPos: PointF, pressure: Float): Pair<PointF?, PointF?> {
        val s = _uiState.value
        when (s.activeTool) {
            StudioTool.TRANSFORM -> {
                val origin = transformDragOrigin ?: return null to null
                val dx = worldPos.x - origin.x
                val dy = worldPos.y - origin.y
                val activeId = s.document.activeLayerId ?: return null to null
                // Live non-committing visual offset update; committed on UP
                _uiState.update { state ->
                    state.copy(
                        document = state.document.copy(
                            layers = state.document.layers.map {
                                if (it.id == activeId) it.copy(
                                    transform = it.transform.copy(
                                        offsetX = transformInitialOffsetX + dx,
                                        offsetY = transformInitialOffsetY + dy
                                    )
                                ) else it
                            }
                        ),
                        renderTick = state.renderTick + 1
                    )
                }
                return null to null
            }

            StudioTool.LASSO -> {
                _uiState.update {
                    it.copy(selection = it.selection.copy(points = it.selection.points + worldPos))
                }
                return null to null
            }

            StudioTool.RECT_SELECT -> {
                val st = s.selection.points.firstOrNull() ?: return null to null
                val rectPts = listOf(
                    PointF(st.x, st.y),
                    PointF(worldPos.x, st.y),
                    PointF(worldPos.x, worldPos.y),
                    PointF(st.x, worldPos.y)
                )
                _uiState.update { it.copy(selection = it.selection.copy(points = rectPts)) }
                return null to null
            }

            StudioTool.EYEDROPPER, StudioTool.FILL -> return null to null

            StudioTool.AI_GUIDANCE -> {
                activeStrokeSamples.add(Vec2(worldPos.x, worldPos.y) to pressure)
                return null to null
            }

            else -> {
                if (s.shapeGuide != ShapeGuide.FREEHAND) {
                    shapeEndPt = worldPos
                    return shapeStartPt to shapeEndPt
                }
                activeStrokeSamples.add(Vec2(worldPos.x, worldPos.y) to pressure)
                return null to null
            }
        }
    }

    fun endPointerAction() {
        val s = _uiState.value
        val doc = transactionEngine.current()
        if (s.activeTool == StudioTool.TRANSFORM && transformDragOrigin != null) {
            transformDragOrigin = null
            val liveLayers = _uiState.value.document.layers
            commitDoc("Transform Layer Position") { base -> base.copy(layers = liveLayers) }
            return
        }

        if (s.activeTool == StudioTool.LASSO || s.activeTool == StudioTool.RECT_SELECT) {
            if (s.selection.points.size < 3) {
                clearSelection()
            } else {
                val vecPts = s.selection.points.map { Vec2(it.x, it.y) }
                val selBounds = FuturesAndConstraintsEngine.computePolygonBounds(vecPts)
                val maskRec = SelectionMaskRecord(
                    id = "mask_${UUID.randomUUID().toString().take(8)}",
                    name = "${s.activeTool.label} Selection",
                    points = vecPts
                )
                commitDoc("Created ${s.activeTool.label} selection mask") { base ->
                    base.copy(masks = base.masks.takeLast(4) + maskRec)
                }
                _uiState.update {
                    it.copy(
                        selection = it.selection.copy(bounds = selBounds),
                        showAiPromptDock = true,
                        aiPromptBadge = "AI Selection Transform"
                    )
                }
            }
            return
        }

        if (s.activeTool == StudioTool.AI_GUIDANCE && activeStrokeSamples.isNotEmpty()) {
            val pts = activeStrokeSamples.map { it.first }
            activeStrokeSamples.clear()
            commitDoc("Added AI Brush guidance stroke") { base ->
                FuturesAndConstraintsEngine.addGuidanceMark(
                    doc = base,
                    kind = "STROKE",
                    points = pts,
                    colorHex = s.brushColorHex,
                    note = "Coordinate-bound guidance"
                )
            }
            showToast("Guidance stroke saved for Futures")
            return
        }

        val activeId = doc.activeLayerId ?: doc.layers.lastOrNull()?.id ?: return
        if (s.shapeGuide != ShapeGuide.FREEHAND && shapeStartPt != null && shapeEndPt != null) {
            val st = Vec2(shapeStartPt!!.x, shapeStartPt!!.y)
            val en = Vec2(shapeEndPt!!.x, shapeEndPt!!.y)
            shapeStartPt = null
            shapeEndPt = null
            activeStrokeSamples.clear()
            commitDoc("Shape ${s.shapeGuide.label}") { base ->
                TileBrushEngine.commitAssistedShape(
                    doc = base,
                    renderer = tileRenderer,
                    targetLayerId = activeId,
                    shapeGuide = s.shapeGuide,
                    isEraser = s.activeTool == StudioTool.ERASER,
                    colorInt = s.brushColorInt,
                    brushSize = s.brushSize,
                    brushOpacity = s.brushOpacity,
                    symmetryMode = s.symmetryMode,
                    activeMask = s.activeSelectionMask,
                    wStart = st,
                    wEnd = en
                )
            }
            return
        }

        if (activeStrokeSamples.isNotEmpty()) {
            val samples = activeStrokeSamples.toList()
            activeStrokeSamples.clear()
            shapeStartPt = null
            shapeEndPt = null
            val params = TileBrushEngine.StrokeRenderParams(
                tool = s.activeTool,
                colorInt = s.brushColorInt,
                preset = s.activeBrushPreset,
                symmetryMode = s.symmetryMode,
                activeMask = s.activeSelectionMask
            )
            commitDoc("Brush stroke (${s.activeTool.label})") { base ->
                TileBrushEngine.commitStrokeToDocument(
                    doc = base,
                    renderer = tileRenderer,
                    targetLayerId = activeId,
                    worldPoints = samples,
                    params = params
                )
            }
        }
    }

    fun cancelActiveStroke() {
        activeStrokeSamples.clear()
        shapeStartPt = null
        shapeEndPt = null
        transformDragOrigin = null
        _uiState.update { it.copy(stylusDiagnostics = it.stylusDiagnostics, renderTick = it.renderTick + 1) }
    }

    fun undo() {
        if (transactionEngine.undo() == null) showToast("Nothing to Undo") else showToast("Undo")
    }

    fun redo() {
        if (transactionEngine.redo() == null) showToast("Nothing to Redo") else showToast("Redo")
    }

    // --- Layer Transforms ---
    fun transformActiveLayerFlipH(vw: Float, vh: Float) = applyBakedTransform("Flip H") { px, py -> setScale(-1f, 1f, px, py) }
    fun transformActiveLayerFlipV(vw: Float, vh: Float) = applyBakedTransform("Flip V") { px, py -> setScale(1f, -1f, px, py) }
    fun transformActiveLayerScale(factor: Float, vw: Float, vh: Float) = applyBakedTransform("Scale") { px, py -> setScale(factor, factor, px, py) }
    fun transformActiveLayerRotate(deg: Float, vw: Float, vh: Float) = applyBakedTransform("Rotate $deg°") { px, py -> setRotate(deg, px, py) }

    private fun applyBakedTransform(label: String, config: android.graphics.Matrix.(Float, Float) -> Unit) {
        val doc = transactionEngine.current()
        val activeId = doc.activeLayerId ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val transformed = TileBrushEngine.bakeLayerTransformAndClearMatrix(doc, tileRenderer, activeId, config)
            withContext(Dispatchers.Main) {
                commitDoc("Transform: $label") { transformed }
            }
        }
    }

    // --- Layer Management ---
    fun selectLayer(layerId: String) {
        commitDoc("Select active layer") { base -> base.copy(activeLayerId = layerId) }
    }

    fun addLayer() {
        commitDoc("Add Layer") { base ->
            val newLayer = DocumentLayer(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = "Layer ${base.layers.size + 1}"
            )
            val idx = base.layers.indexOfFirst { it.id == base.activeLayerId }
            val list = base.layers.toMutableList()
            if (idx >= 0) list.add(idx + 1, newLayer) else list.add(newLayer)
            base.copy(layers = list, activeLayerId = newLayer.id)
        }
        showToast("Added Layer")
    }

    fun duplicateActiveLayer() {
        val base = transactionEngine.current()
        val active = base.layers.find { it.id == base.activeLayerId } ?: return
        commitDoc("Duplicate '${active.name}'") { doc ->
            val copyLayer = active.copy(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = "${active.name} Copy",
                tiles = active.tiles.mapValues { (_, b) -> b.copyOf() }
            )
            val idx = doc.layers.indexOfFirst { it.id == active.id }
            val list = doc.layers.toMutableList()
            if (idx >= 0) list.add(idx + 1, copyLayer) else list.add(copyLayer)
            doc.copy(layers = list, activeLayerId = copyLayer.id)
        }
        showToast("Layer Duplicated")
    }

    fun clearActiveLayer() {
        val activeId = transactionEngine.current().activeLayerId ?: return
        commitDoc("Clear active layer") { base ->
            base.copy(layers = base.layers.map {
                if (it.id == activeId) it.copy(tiles = emptyMap(), tileHashes = emptyMap()) else it
            })
        }
        showToast("Cleared active layer")
    }

    fun mergeDownActiveLayer() {
        val base = transactionEngine.current()
        val idx = base.layers.indexOfFirst { it.id == base.activeLayerId }
        if (idx <= 0) {
            showToast("No layer below to merge into")
            return
        }
        val upper = base.layers[idx]
        val lower = base.layers[idx - 1]
        viewModelScope.launch(Dispatchers.Default) {
            val lowerBmp = tileRenderer.materializeLayerBitmap(base, lower)
            val upperBmp = tileRenderer.materializeLayerBitmap(base, upper)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (upper.opacity * 255f).toInt().coerceIn(0, 255)
                xfermode = android.graphics.PorterDuffXfermode(LayerBlendMode.fromId(upper.blendModeId).porterDuffMode)
            }
            android.graphics.Canvas(lowerBmp).drawBitmap(upperBmp, 0f, 0f, paint)
            upperBmp.recycle()
            val mergedTiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(lowerBmp, base.tileSize)
            lowerBmp.recycle()
            val updatedLower = lower.copy(
                tiles = mergedTiles,
                tileHashes = mergedTiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
            )
            withContext(Dispatchers.Main) {
                commitDoc("Merge '${upper.name}' down") { doc ->
                    val curIdx = doc.layers.indexOfFirst { it.id == upper.id }
                    if (curIdx <= 0) return@commitDoc doc
                    val updatedList = doc.layers.toMutableList()
                    updatedList[curIdx - 1] = updatedLower
                    updatedList.removeAt(curIdx)
                    doc.copy(layers = updatedList, activeLayerId = lower.id)
                }
                showToast("Merged Down")
            }
        }
    }

    fun toggleAlphaLock() {
        val base = transactionEngine.current()
        val active = base.layers.find { it.id == base.activeLayerId } ?: return
        val next = !active.alphaLock
        commitDoc("Alpha Lock ${if (next) "ON" else "OFF"}") { doc ->
            doc.copy(layers = doc.layers.map { if (it.id == active.id) it.copy(alphaLock = next) else it })
        }
        showToast(if (next) "Alpha Lock ON" else "Alpha Lock OFF")
    }

    fun setActiveLayerBlendMode(mode: LayerBlendMode) {
        val activeId = transactionEngine.current().activeLayerId ?: return
        commitDoc("Blend mode: ${mode.displayName}") { doc ->
            doc.copy(layers = doc.layers.map { if (it.id == activeId) it.copy(blendModeId = mode.id) else it })
        }
    }

    fun setActiveLayerOpacity(opacity: Float) {
        val activeId = _uiState.value.document.activeLayerId ?: return
        _uiState.update { state ->
            state.copy(
                document = state.document.copy(
                    layers = state.document.layers.map {
                        if (it.id == activeId) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it
                    }
                ),
                renderTick = state.renderTick + 1
            )
        }
    }

    fun commitActiveLayerOpacityHistory() {
        val activeId = _uiState.value.document.activeLayerId ?: return
        val targetOpacity = _uiState.value.document.layers.find { it.id == activeId }?.opacity ?: return
        commitDoc("Adjust Layer Opacity") { doc ->
            doc.copy(layers = doc.layers.map { if (it.id == activeId) it.copy(opacity = targetOpacity) else it })
        }
    }

    fun moveLayerUp(layerId: String) {
        commitDoc("Move layer up") { doc ->
            val list = doc.layers.toMutableList()
            val idx = list.indexOfFirst { it.id == layerId }
            if (idx in 0 until list.lastIndex) {
                val tmp = list[idx]; list[idx] = list[idx + 1]; list[idx + 1] = tmp
            }
            doc.copy(layers = list)
        }
    }

    fun moveLayerDown(layerId: String) {
        commitDoc("Move layer down") { doc ->
            val list = doc.layers.toMutableList()
            val idx = list.indexOfFirst { it.id == layerId }
            if (idx > 0) {
                val tmp = list[idx]; list[idx] = list[idx - 1]; list[idx - 1] = tmp
            }
            doc.copy(layers = list)
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        commitDoc("Toggle layer visibility") { doc ->
            doc.copy(layers = doc.layers.map { if (it.id == layerId) it.copy(visible = !it.visible) else it })
        }
    }

    fun deleteLayer(layerId: String) {
        if (transactionEngine.current().layers.size <= 1) return
        commitDoc("Delete layer") { doc ->
            val updated = doc.layers.filterNot { it.id == layerId }
            val nextActive = if (doc.activeLayerId == layerId) updated.last().id else doc.activeLayerId
            doc.copy(layers = updated, activeLayerId = nextActive)
        }
    }

    fun setPaperStyle(style: PaperStyle) {
        commitDoc("Paper style: ${style.label}") { doc ->
            doc.copy(paperColorHex = style.id)
        }
    }

    // --- Selection & Futures Table ---
    fun selectFullCanvasForAi() {
        val doc = transactionEngine.current()
        val art = tileRenderer.computeArtworkBoundingBox(doc) ?: RectF(-500f, -350f, 500f, 350f)
        val pts = listOf(
            PointF(art.left, art.top), PointF(art.right, art.top),
            PointF(art.right, art.bottom), PointF(art.left, art.bottom)
        )
        val vecPts = pts.map { Vec2(it.x, it.y) }
        val maskRec = SelectionMaskRecord("mask_full_${System.currentTimeMillis()}", "Full Artwork", vecPts)
        commitDoc("Selected full artwork bounds") { base -> base.copy(masks = base.masks.takeLast(4) + maskRec) }
        _uiState.update {
            it.copy(
                selection = SelectionState(active = true, type = SelectionType.FULL, points = pts, bounds = art),
                showAiPromptDock = true,
                aiPromptBadge = "Full Artwork Selection"
            )
        }
    }

    fun updateAiPromptText(text: String) {
        _uiState.update { it.copy(aiPromptText = text) }
    }

    fun clearSelection() {
        val s = _uiState.value
        val nextTool = if (s.activeTool == StudioTool.LASSO || s.activeTool == StudioTool.RECT_SELECT) StudioTool.PENCIL else s.activeTool
        _uiState.update { it.copy(selection = SelectionState(), showAiPromptDock = false, activeTool = nextTool) }
    }

    fun openFuturesTable(initialPrompt: String = _uiState.value.aiPromptText) {
        val s = _uiState.value
        if (s.futuresSession?.isBusy == true) return
        viewModelScope.launch {
            val session = withContext(Dispatchers.Default) {
                FuturesAndConstraintsEngine.buildFuturesSession(
                    doc = s.document,
                    renderer = tileRenderer,
                    activeSelection = s.activeSelectionMask,
                    initialPrompt = initialPrompt.trim()
                )
            }
            _uiState.update { it.copy(futuresSession = session) }
        }
    }

    fun closeFuturesTable(discardMessage: Boolean = false) {
        if (_uiState.value.futuresSession?.isBusy == true) return
        _uiState.update { it.copy(futuresSession = null) }
        if (discardMessage) showToast("Candidate changes discarded. Canvas untouched.")
    }

    fun updateFuturesPrompt(prompt: String) {
        _uiState.update { s -> s.futuresSession?.let { s.copy(futuresSession = it.copy(prompt = prompt)) } ?: s }
    }

    fun selectFuturesModel(model: AiEditModel) {
        _uiState.update { it.copy(selectedAiModel = model) }
    }

    fun selectFutureBranch(index: Int) {
        _uiState.update { state ->
            val ses = state.futuresSession ?: return@update state
            if (ses.isBusy || index !in ses.branches.indices) return@update state
            val branch = ses.branches[index]
            state.copy(
                futuresSession = ses.copy(
                    selectedIndex = index,
                    statusMessage = "${branch.label} (${branch.sourceLabel}) selected. Source layers untouched."
                )
            )
        }
    }

    fun updateFuturesSplit(percent: Int) {
        _uiState.update { s ->
            s.futuresSession?.let { s.copy(futuresSession = it.copy(splitPercent = percent.coerceIn(0, 100))) } ?: s
        }
    }

    fun acceptSelectedFuture() {
        val s = _uiState.value
        val ses = s.futuresSession ?: return
        if (ses.isBusy) return
        val branch = ses.branches.getOrNull(ses.selectedIndex) ?: return

        commitDoc("Accept future: ${branch.label}", processingMode = branch.sourceLabel) { base ->
            FuturesAndConstraintsEngine.acceptBranchToNewLayer(base, ses, ses.selectedIndex).first
        }
        _uiState.update {
            it.copy(
                futuresSession = null,
                selection = SelectionState(),
                showAiPromptDock = false,
                activeTool = if (it.activeTool == StudioTool.LASSO || it.activeTool == StudioTool.RECT_SELECT) StudioTool.PENCIL else it.activeTool
            )
        }
        showToast("Accepted onto new layer. Originals preserved.")
    }

    fun generateModelFutureConfirmed(confirmCompanionDispatch: Boolean = true) {
        val s = _uiState.value
        val ses = s.futuresSession ?: return
        if (ses.isBusy) return
        val prompt = ses.prompt.trim()
        if (prompt.isEmpty() && providerRouter.mode == ProviderMode.LOOPBACK_COMPANION) {
            _uiState.update { it.copy(futuresSession = ses.copy(statusMessage = "Enter a direction before dispatching to companion.")) }
            return
        }

        val targetIdx = ses.selectedIndex
        val branchDef = ses.branches[targetIdx].branchId
        val activeJobId = "job_${UUID.randomUUID().toString().take(8)}"
        _uiState.update {
            it.copy(
                activeProviderJobId = activeJobId,
                futuresSession = ses.copy(isBusy = true, statusMessage = "Submitting job to ${providerRouter.mode.name}...")
            )
        }

        activeProviderJob?.cancel()
        activeProviderJob = viewModelScope.launch {
            val cropB64 = withContext(Dispatchers.Default) {
                val b = ByteArrayOutputStream()
                ses.originalBitmap.compress(Bitmap.CompressFormat.PNG, 100, b)
                Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP)
            }
            val maskB64 = if (ses.selectionMask != null) {
                withContext(Dispatchers.Default) {
                    val b = ByteArrayOutputStream()
                    ses.maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, b)
                    Base64.encodeToString(b.toByteArray(), Base64.NO_WRAP)
                }
            } else null

            val jobReq = ProviderJobRequest(
                documentId = ses.docId,
                baseRevision = ses.baseRevision,
                branchLabel = branchDef.label,
                directionSuffix = branchDef.directionSuffix,
                prompt = prompt.ifBlank { branchDef.directionSuffix },
                modelId = s.selectedAiModel.id,
                cropPngBase64 = cropB64,
                maskPngBase64 = maskB64,
                userConfirmedPaidEgress = confirmCompanionDispatch
            )
            val jobRecord = providerRouter.submitCandidateJob(jobReq, transactionEngine.current().revision)
            if (jobRecord.status != ProviderJobStatus.SUCCEEDED) {
                logError("Provider job failed: ${jobRecord.errorCode} - ${jobRecord.message}")
            }
            _uiState.update { state ->
                val cur = state.futuresSession ?: return@update state.copy(activeProviderJobId = null)
                val updatedSes = FuturesAndConstraintsEngine.applyProviderJobToSession(cur, targetIdx, jobRecord)
                state.copy(
                    activeProviderJobId = null,
                    futuresSession = updatedSes,
                    remainingCompanionCalls = providerRouter.remainingCompanionCalls()
                )
            }
        }
    }

    // --- All 20 Feature Actions (Workspace Hub) ---
    fun toggleWorkspaceHub(open: Boolean? = null, tab: Int? = null) {
        _uiState.update {
            it.copy(
                showWorkspaceHub = open ?: !it.showWorkspaceHub,
                workspaceTab = tab ?: it.workspaceTab
            )
        }
    }

    fun toggleDiagnosticsDialog(open: Boolean? = null) {
        _uiState.update {
            it.copy(
                showDiagnostics = open ?: !it.showDiagnostics,
                tileCacheStats = tileRenderer.stats()
            )
        }
    }

    fun setProviderMode(mode: ProviderMode) {
        providerRouter.mode = mode
        viewModelScope.launch {
            val h = providerRouter.checkHealth()
            _uiState.update {
                it.copy(
                    providerMode = mode,
                    providerHealthSummary = h.detail,
                    statusBannerText = buildStatusBanner(mode)
                )
            }
            showToast("Provider: ${mode.name}")
        }
    }

    fun pingCompanionHealth() {
        viewModelScope.launch {
            val h = providerRouter.checkHealth()
            _uiState.update {
                it.copy(providerHealthSummary = "[${h.status.uppercase()}] ${h.detail}")
            }
            showToast("Router: ${h.status}")
        }
    }

    fun cancelActiveProviderJob() {
        val jobId = _uiState.value.activeProviderJobId
        activeProviderJob?.cancel()
        viewModelScope.launch {
            if (jobId != null) {
                providerRouter.cancelJob(jobId)
            }
            _uiState.update { state ->
                state.copy(
                    activeProviderJobId = null,
                    futuresSession = state.futuresSession?.copy(
                        isBusy = false,
                        statusMessage = "Provider dispatch canceled. Local canvas untouched."
                    )
                )
            }
            showToast("Job canceled cleanly")
        }
    }

    fun fallbackToLocalMode() {
        activeProviderJob?.cancel()
        providerRouter.mode = ProviderMode.OFFLINE_LOCAL_ONLY
        _uiState.update { state ->
            state.copy(
                activeProviderJobId = null,
                providerMode = ProviderMode.OFFLINE_LOCAL_ONLY,
                providerHealthSummary = "Fell back to strict offline local mode.",
                statusBannerText = buildStatusBanner(ProviderMode.OFFLINE_LOCAL_ONLY),
                futuresSession = state.futuresSession?.copy(
                    isBusy = false,
                    statusMessage = "Switched to Offline Local mode. Local studies remain active."
                )
            )
        }
        showToast("Offline Local Mode active")
    }

    // F01 Object Surgery & Direct On-Canvas Interactive Transform
    fun beginInteractiveTransform(semanticObjectId: String? = null) {
        val doc = transactionEngine.current()
        val obj = semanticObjectId?.let { id -> doc.semanticObjects.find { it.id == id } }
            ?: doc.semanticObjects.find { it.extractedLayerId == doc.activeLayerId }
        val layerId = obj?.extractedLayerId ?: doc.activeLayerId ?: return
        val layer = doc.layers.find { it.id == layerId } ?: return
        _uiState.update {
            it.copy(
                activeTool = StudioTool.TRANSFORM,
                transformSession = InteractiveTransformState(
                    targetLayerId = layer.id,
                    semanticObjectId = obj?.id,
                    label = obj?.name ?: layer.name,
                    initialTransform = layer.transform,
                    snapEnabled = it.transformSession?.snapEnabled ?: true
                )
            )
        }
    }

    fun updateInteractiveTransform(
        offsetXDelta: Float = 0f,
        offsetYDelta: Float = 0f,
        scaleFactor: Float? = null,
        rotationDegDelta: Float = 0f
    ) {
        val s = _uiState.value
        val session = s.transformSession ?: run {
            beginInteractiveTransform()
            _uiState.value.transformSession
        } ?: return
        val layer = s.document.layers.find { it.id == session.targetLayerId } ?: return
        val snap = session.snapEnabled
        val rawX = layer.transform.offsetX + offsetXDelta
        val rawY = layer.transform.offsetY + offsetYDelta
        val rawRot = layer.transform.rotationDeg + rotationDegDelta
        val nextX = if (snap) kotlin.math.round(rawX / 16f) * 16f else rawX
        val nextY = if (snap) kotlin.math.round(rawY / 16f) * 16f else rawY
        val nextRot = if (snap && rotationDegDelta != 0f) kotlin.math.round(rawRot / 15f) * 15f else rawRot
        val nextScaleX = if (scaleFactor != null) layer.transform.scaleX * scaleFactor else layer.transform.scaleX
        val nextScaleY = if (scaleFactor != null) layer.transform.scaleY * scaleFactor else layer.transform.scaleY

        val nextTransform = layer.transform.copy(
            offsetX = nextX,
            offsetY = nextY,
            scaleX = nextScaleX,
            scaleY = nextScaleY,
            rotationDeg = nextRot
        )
        val previewDoc = ObjectSurgeryAndMaterialEngine.applyTransformToLayerOrObject(
            doc = s.document,
            targetLayerId = session.targetLayerId,
            semanticObjectId = session.semanticObjectId,
            newTransform = nextTransform
        )
        _uiState.update { it.copy(document = previewDoc, renderTick = it.renderTick + 1) }
    }

    fun flipInteractiveTransform(horizontal: Boolean) {
        val s = _uiState.value
        val session = s.transformSession ?: run {
            beginInteractiveTransform()
            _uiState.value.transformSession
        } ?: return
        val layer = s.document.layers.find { it.id == session.targetLayerId } ?: return
        val nextTransform = if (horizontal) {
            layer.transform.copy(scaleX = -layer.transform.scaleX)
        } else {
            layer.transform.copy(scaleY = -layer.transform.scaleY)
        }
        val previewDoc = ObjectSurgeryAndMaterialEngine.applyTransformToLayerOrObject(
            doc = s.document,
            targetLayerId = session.targetLayerId,
            semanticObjectId = session.semanticObjectId,
            newTransform = nextTransform
        )
        _uiState.update { it.copy(document = previewDoc, renderTick = it.renderTick + 1) }
    }

    fun toggleTransformSnap() {
        _uiState.update { state ->
            val cur = state.transformSession
            if (cur != null) {
                state.copy(transformSession = cur.copy(snapEnabled = !cur.snapEnabled))
            } else state
        }
    }

    fun commitInteractiveTransform() {
        val s = _uiState.value
        val session = s.transformSession ?: return
        val liveLayer = s.document.layers.find { it.id == session.targetLayerId } ?: return
        val finalTransform = liveLayer.transform
        commitDoc("Transform '${session.label}'") { base ->
            ObjectSurgeryAndMaterialEngine.applyTransformToLayerOrObject(
                doc = base,
                targetLayerId = session.targetLayerId,
                semanticObjectId = session.semanticObjectId,
                newTransform = finalTransform
            )
        }
        _uiState.update {
            it.copy(transformSession = null, activeTool = StudioTool.PENCIL)
        }
        showToast("Transform applied")
    }

    fun cancelInteractiveTransform() {
        val session = _uiState.value.transformSession
        if (session != null) {
            val baseDoc = transactionEngine.current()
            _uiState.update {
                it.copy(
                    document = baseDoc,
                    transformSession = null,
                    activeTool = StudioTool.PENCIL,
                    renderTick = it.renderTick + 1
                )
            }
            showToast("Transform canceled")
        } else {
            _uiState.update { it.copy(activeTool = StudioTool.PENCIL) }
        }
    }

    fun extractSelectionAsObject(objectName: String) {
        val doc = transactionEngine.current()
        val activeId = doc.activeLayerId ?: doc.layers.last().id
        val mask = doc.masks.lastOrNull() ?: SelectionMaskRecord(
            id = "mask_center",
            name = "Center Box",
            points = listOf(Vec2(-220f, -220f), Vec2(220f, -220f), Vec2(220f, 220f), Vec2(-220f, 220f))
        )
        viewModelScope.launch(Dispatchers.Default) {
            val (nextDoc, _) = ObjectSurgeryAndMaterialEngine.extractObjectFromSelection(doc, tileRenderer, activeId, mask, objectName)
            withContext(Dispatchers.Main) {
                commitDoc("Object Surgery: Extract '$objectName'") { nextDoc }
                showToast("Object extracted cleanly with speculative repair preview")
            }
        }
    }

    fun nudgeSemanticObject(objectId: String, dx: Float, dy: Float) {
        commitDoc("Move extracted object") { base ->
            ObjectSurgeryAndMaterialEngine.moveSemanticObject(base, objectId, dx, dy)
        }
    }

    fun refineObjectMask(objectId: String, expandPx: Float) {
        val doc = transactionEngine.current()
        viewModelScope.launch(Dispatchers.Default) {
            val refined = ObjectSurgeryAndMaterialEngine.refineObjectMaskAndReextract(doc, tileRenderer, objectId, expandPx)
            withContext(Dispatchers.Main) {
                commitDoc("Refine Object Mask (${if (expandPx >= 0) "+" else ""}${expandPx.toInt()}px)") { refined }
                showToast("Object mask refined non-destructively")
            }
        }
    }

    fun revertSemanticObject(objectId: String) {
        commitDoc("Revert object extraction") { base ->
            ObjectSurgeryAndMaterialEngine.revertObjectExtraction(base, objectId)
        }
        showToast("Object extraction reverted")
    }

    // F09 Vector & Typography
    fun addVectorTextBox(text: String) {
        commitDoc("Add Text Box") { base -> VectorAndFoundryEngine.addTextBox(base, text.ifBlank { "Infinite Studio" }) }
        val lastVecId = transactionEngine.current().vectorObjects.lastOrNull()?.id
        _uiState.update { it.copy(selectedVectorId = lastVecId) }
        showToast("Text box placed")
    }

    fun addVectorSpeechBalloon(text: String) {
        commitDoc("Add Speech Balloon") { base -> VectorAndFoundryEngine.addSpeechBalloon(base, text.ifBlank { "Hello Canvas!" }) }
        val lastVecId = transactionEngine.current().vectorObjects.lastOrNull()?.id
        _uiState.update { it.copy(selectedVectorId = lastVecId) }
        showToast("Speech balloon placed")
    }

    fun addSampleBezierPath() {
        commitDoc("Add Bezier Curve") { base ->
            VectorAndFoundryEngine.addBezierCurve(base, listOf(Vec2(-200f, 40f), Vec2(0f, -110f), Vec2(200f, 40f)), _uiState.value.brushColorHex)
        }
        val lastVecId = transactionEngine.current().vectorObjects.lastOrNull()?.id
        _uiState.update { it.copy(selectedVectorId = lastVecId) }
        showToast("Bezier curve added")
    }

    fun selectVectorObject(vectorId: String?) {
        _uiState.update { it.copy(selectedVectorId = vectorId) }
    }

    fun toggleVectorSnap() {
        _uiState.update { it.copy(vectorSnapEnabled = !it.vectorSnapEnabled) }
    }

    fun moveVectorLive(vectorId: String, dx: Float, dy: Float) {
        val snap = _uiState.value.vectorSnapEnabled
        _uiState.update { state ->
            val updated = VectorAndFoundryEngine.moveVectorObject(state.document, vectorId, dx, dy, snap)
            state.copy(document = updated, selectedVectorId = vectorId, renderTick = state.renderTick + 1)
        }
    }

    fun resizeVectorLive(vectorId: String, newWidth: Float, newHeight: Float) {
        val snap = _uiState.value.vectorSnapEnabled
        _uiState.update { state ->
            val obj = state.document.vectorObjects.find { it.id == vectorId } ?: return@update state
            val updated = VectorAndFoundryEngine.resizeVectorObject(
                state.document, vectorId, obj.x, obj.y, newWidth, newHeight, snap
            )
            state.copy(document = updated, selectedVectorId = vectorId, renderTick = state.renderTick + 1)
        }
    }

    fun moveBezierPartLive(
        vectorId: String,
        nodeIndex: Int,
        part: VectorAndFoundryEngine.BezierHandlePart,
        worldPos: Vec2
    ) {
        val snap = _uiState.value.vectorSnapEnabled
        _uiState.update { state ->
            val updated = VectorAndFoundryEngine.moveBezierNodeOrHandle(
                state.document, vectorId, nodeIndex, part, worldPos, snap
            )
            state.copy(document = updated, selectedVectorId = vectorId, renderTick = state.renderTick + 1)
        }
    }

    fun moveBalloonTailLive(vectorId: String, tipPos: Vec2) {
        val snap = _uiState.value.vectorSnapEnabled
        _uiState.update { state ->
            val updated = VectorAndFoundryEngine.moveBalloonTailTip(state.document, vectorId, tipPos, snap)
            state.copy(document = updated, selectedVectorId = vectorId, renderTick = state.renderTick + 1)
        }
    }

    fun commitVectorLiveEdits(description: String = "Direct Vector Edit") {
        val liveVectors = _uiState.value.document.vectorObjects
        commitDoc(description) { base -> base.copy(vectorObjects = liveVectors) }
    }

    fun updateVectorProperties(
        vectorId: String,
        text: String? = null,
        fontSizeSp: Float? = null,
        strokeHex: String? = null,
        fillHex: String? = null
    ) {
        commitDoc("Update Vector Style") { base ->
            VectorAndFoundryEngine.updateVectorStyle(base, vectorId, text, fontSizeSp, strokeHex, fillHex)
        }
    }

    fun deleteVectorObject(vectorId: String) {
        commitDoc("Delete vector object") { base -> VectorAndFoundryEngine.deleteVectorObject(base, vectorId) }
        _uiState.update { if (it.selectedVectorId == vectorId) it.copy(selectedVectorId = null) else it }
    }

    // F06 Material Intelligence
    fun previewAndBakeMaterial(name: String, roughness: Float, metalness: Float, relief: Float, azimuth: Float) {
        applyMaterialStudy(
            name = name,
            roughness = roughness,
            metalness = metalness,
            relief = relief,
            azimuth = azimuth,
            elevation = 55f,
            emissiveHex = "#000000",
            emissionIntensity = 0.0f,
            maskId = null,
            bakeLayer = true
        )
    }

    fun applyMaterialStudy(
        name: String,
        roughness: Float,
        metalness: Float,
        relief: Float,
        azimuth: Float,
        elevation: Float,
        emissiveHex: String,
        emissionIntensity: Float,
        maskId: String?,
        bakeLayer: Boolean
    ) {
        val doc = transactionEngine.current()
        val activeId = doc.activeLayerId ?: doc.layers.last().id
        val mat = MaterialRegion(
            id = "mat_${UUID.randomUUID().toString().take(6)}",
            name = name.ifBlank { "Metallic Study" },
            targetLayerId = activeId,
            baseColorHex = _uiState.value.brushColorHex,
            roughness = roughness,
            metalness = metalness,
            reliefStrength = relief,
            lightAzimuthDeg = azimuth,
            lightElevationDeg = elevation,
            emissiveHex = emissiveHex,
            emissionIntensity = emissionIntensity,
            maskId = maskId
        )
        viewModelScope.launch(Dispatchers.Default) {
            val lit = ObjectSurgeryAndMaterialEngine.renderMaterialLitPreview(doc, tileRenderer, mat)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(materialPreviewBitmap = lit) }
                if (bakeLayer) {
                    commitDoc("Bake Material '${mat.name}'") { base ->
                        ObjectSurgeryAndMaterialEngine.applyMaterialPreviewAsNewLayer(base, tileRenderer, mat)
                    }
                    showToast("Material baked onto new reversible layer")
                } else {
                    commitDoc("Save Material Metadata '${mat.name}'") { base ->
                        ObjectSurgeryAndMaterialEngine.upsertMaterial(base, mat)
                    }
                    showToast("Material metadata saved non-destructively")
                }
            }
        }
    }

    fun resetMaterialStudy(materialId: String?) {
        if (materialId != null) {
            commitDoc("Remove Material Region") { base ->
                ObjectSurgeryAndMaterialEngine.removeMaterial(base, materialId)
            }
        }
        _uiState.update { it.copy(materialPreviewBitmap = null) }
        showToast("Material preview reset")
    }

    // F02 Spatial Stage
    fun assignActiveLayerDepthPlane(planeId: String) {
        val activeId = transactionEngine.current().activeLayerId ?: return
        commitDoc("Assign layer to depth plane $planeId") { base ->
            ObjectSurgeryAndMaterialEngine.assignLayerToDepthPlane(base, activeId, planeId)
        }
        refreshSpatialPreview()
    }

    fun updateSpatialCamera(yaw: Float, pitch: Float, zoom: Float) {
        commitDoc("Update Spatial Stage camera") { base ->
            ObjectSurgeryAndMaterialEngine.updateSpatialStage(base) {
                it.copy(enabled = true, cameraYawDeg = yaw, cameraPitchDeg = pitch, cameraZoom = zoom)
            }
        }
        refreshSpatialPreview()
    }

    fun resetSpatialStageCamera() {
        commitDoc("Reset Spatial Stage camera") { base ->
            ObjectSurgeryAndMaterialEngine.resetSpatialCamera(base)
        }
        refreshSpatialPreview()
        showToast("Spatial camera reset")
    }

    fun setSpatialGuidesEnabled(enabled: Boolean) {
        commitDoc("Spatial perspective guides ${if (enabled) "ON" else "OFF"}") { base ->
            ObjectSurgeryAndMaterialEngine.updateSpatialStage(base) { it.copy(enabled = enabled) }
        }
    }

    fun refreshSpatialPreview() {
        val doc = transactionEngine.current()
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = ObjectSurgeryAndMaterialEngine.renderSpatialStageComposite(doc, tileRenderer)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(spatialPreviewBitmap = bmp) }
            }
        }
    }

    // F05 Time Canvas (with lifecycle-safe playback coroutine)
    fun addTimelineFrame() {
        commitDoc("Add timeline frame") { base -> TimeAndRecipeEngine.addTimelineFrame(base) }
    }

    fun duplicateTimelineFrame(index: Int = _uiState.value.document.timeline.currentFrameIndex) {
        commitDoc("Duplicate timeline frame") { base -> TimeAndRecipeEngine.duplicateTimelineFrame(base, index) }
        showToast("Frame duplicated")
    }

    fun deleteTimelineFrame(index: Int = _uiState.value.document.timeline.currentFrameIndex) {
        if (transactionEngine.current().timeline.frames.size <= 1) {
            showToast("At least 1 frame required")
            return
        }
        commitDoc("Delete timeline frame") { base -> TimeAndRecipeEngine.deleteTimelineFrame(base, index) }
        showToast("Frame deleted")
    }

    fun selectTimelineFrame(index: Int) {
        commitDoc("Switch timeline frame $index") { base -> TimeAndRecipeEngine.selectTimelineFrame(base, index) }
    }

    fun setTimelineFps(fps: Int) {
        commitDoc("Set Timeline FPS $fps") { base -> TimeAndRecipeEngine.setTimelineFps(base, fps) }
    }

    fun setTimelineLoopRange(startIndex: Int, endIndex: Int) {
        commitDoc("Set loop range $startIndex..$endIndex") { base ->
            TimeAndRecipeEngine.setTimelineLoopRange(base, startIndex, endIndex)
        }
    }

    fun captureKeyframesToCurrentFrame() {
        commitDoc("Capture layer positions to frame") { base -> TimeAndRecipeEngine.captureCurrentLayersToFrame(base) }
        showToast("Keyframe captured")
    }

    fun updateLayerKeyframe(
        frameIndex: Int,
        layerId: String,
        offsetX: Float,
        offsetY: Float,
        opacity: Float,
        visible: Boolean
    ) {
        commitDoc("Update frame keyframe") { base ->
            TimeAndRecipeEngine.updateLayerKeyframe(base, frameIndex, layerId, offsetX, offsetY, opacity, visible)
        }
    }

    fun toggleOnionSkin() {
        commitDoc("Toggle onion skin") { base -> TimeAndRecipeEngine.toggleOnionSkin(base) }
    }

    fun updateOnionSkinConfig(
        enabled: Boolean,
        beforeOpacity: Float,
        afterEnabled: Boolean,
        afterOpacity: Float
    ) {
        commitDoc("Configure onion skin") { base ->
            TimeAndRecipeEngine.updateOnionSkinConfig(base, enabled, beforeOpacity, afterEnabled, afterOpacity)
        }
    }

    fun toggleTimelinePlayback() {
        if (_uiState.value.timelinePlaying) {
            stopTimelinePlayback()
            return
        }
        val doc = transactionEngine.current()
        if (doc.timeline.frames.size < 2) {
            showToast("Add at least 2 frames to play")
            return
        }
        _uiState.update { it.copy(timelinePlaying = true) }
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_uiState.value.timelinePlaying) {
                val curDoc = transactionEngine.current()
                val range = TimeAndRecipeEngine.effectiveLoopRange(curDoc)
                val curIdx = curDoc.timeline.currentFrameIndex
                val nextIdx = if (curIdx + 1 > range.last) range.first else curIdx + 1
                val frameDoc = TimeAndRecipeEngine.selectTimelineFrame(curDoc, nextIdx)
                _uiState.update { it.copy(document = frameDoc, renderTick = it.renderTick + 1) }
                val delayMs = (1000L / kotlin.math.max(1, curDoc.timeline.fps)).coerceAtLeast(42L)
                delay(delayMs)
            }
        }
    }

    fun stopTimelinePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        if (_uiState.value.timelinePlaying) {
            val baseDoc = transactionEngine.current()
            _uiState.update { it.copy(timelinePlaying = false, document = baseDoc, renderTick = it.renderTick + 1) }
        }
    }

    fun renderSpriteSheetPreview() {
        val doc = transactionEngine.current()
        viewModelScope.launch(Dispatchers.Default) {
            val (bmp, meta) = TimeAndRecipeEngine.exportSpriteSheet(doc, tileRenderer)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(spriteSheetPreviewBitmap = bmp, spriteSheetMeta = meta) }
                showToast("Sprite sheet ready (${meta.frames.size} frames)")
            }
        }
    }

    // F04 Creative Recipes & F03 Living Preview
    fun runCreativeRecipe() {
        recipeCancelRequested = false
        val doc = transactionEngine.current()
        val report = TimeAndRecipeEngine.executeRecipeGraph(doc, shouldCancel = { recipeCancelRequested })
        commitDoc("Execute Creative Recipe DAG") { base ->
            val updatedList = if (base.recipes.any { it.id == report.updatedGraph.id }) {
                base.recipes.map { if (it.id == report.updatedGraph.id) report.updatedGraph else it }
            } else {
                listOf(report.updatedGraph) + base.recipes
            }
            base.copy(recipes = updatedList)
        }
        val errNote = report.updatedGraph.results.values.firstOrNull { it.status.startsWith("ERROR") }?.status
        val msg = "Computed: ${report.computedNodeIds.joinToString()} | Reused cache: ${report.reusedNodeIds.joinToString()}"
        _uiState.update { it.copy(lastRecipeReportSummary = msg, lastRecipeError = errNote) }
        showToast(if (errNote != null) "Recipe finished with node error" else "Recipe complete (${report.computedNodeIds.size} computed, ${report.reusedNodeIds.size} cached)")
    }

    fun cancelCreativeRecipe() {
        recipeCancelRequested = true
        showToast("Recipe cancel requested")
    }

    fun tweakDownstreamRecipeRoughness(value: String) {
        updateRecipeNodeParam("recipe_default", "n_mat", "roughness", value)
    }

    fun updateRecipeNodeParam(recipeId: String, nodeId: String, key: String, value: String) {
        commitDoc("Update Recipe parameter $key") { base ->
            TimeAndRecipeEngine.updateRecipeNodeParameter(base, recipeId, nodeId, key, value)
        }
        _uiState.update { it.copy(lastRecipeError = null) }
    }

    fun addRecipeNode(recipeId: String, kind: RecipeNodeKind, title: String) {
        commitDoc("Add Recipe node '${title.ifBlank { kind.name }}'") { base ->
            val targetGraph = base.recipes.find { it.id == recipeId }
            val upstream = targetGraph?.nodes?.lastOrNull()?.id?.let(::listOf) ?: emptyList()
            TimeAndRecipeEngine.addRecipeNode(base, recipeId, kind, title, upstream)
        }
        showToast("Added node ${kind.name}")
    }

    fun connectRecipeNodes(recipeId: String, fromId: String, toId: String) {
        val res = TimeAndRecipeEngine.connectRecipeNodes(transactionEngine.current(), recipeId, fromId, toId)
        res.fold(
            onSuccess = { nextDoc ->
                commitDoc("Connect Recipe nodes $fromId -> $toId") { nextDoc }
                _uiState.update { it.copy(lastRecipeError = null) }
                showToast("Connected $fromId -> $toId")
            },
            onFailure = { err ->
                _uiState.update { it.copy(lastRecipeError = err.message ?: "Cycle rejected") }
                showToast(err.message ?: "Connection rejected")
            }
        )
    }

    fun disconnectRecipeNodes(recipeId: String, fromId: String, toId: String) {
        commitDoc("Disconnect Recipe $fromId -> $toId") { base ->
            TimeAndRecipeEngine.disconnectRecipeNodes(base, recipeId, fromId, toId)
        }
    }

    fun moveRecipeNode(recipeId: String, nodeId: String, posX: Float, posY: Float) {
        commitDoc("Move Recipe node $nodeId") { base ->
            TimeAndRecipeEngine.moveRecipeNode(base, recipeId, nodeId, posX, posY)
        }
    }

    fun removeRecipeNode(recipeId: String, nodeId: String) {
        commitDoc("Remove Recipe node $nodeId") { base ->
            TimeAndRecipeEngine.removeRecipeNode(base, recipeId, nodeId)
        }
    }

    fun saveRecipePreset(sourceRecipeId: String, newName: String) {
        commitDoc("Save Recipe preset '$newName'") { base ->
            TimeAndRecipeEngine.saveRecipePresetCopy(base, sourceRecipeId, newName)
        }
        showToast("Saved recipe preset")
    }

    fun selectActiveRecipe(recipeId: String) {
        commitDoc("Select Recipe $recipeId") { base ->
            TimeAndRecipeEngine.selectActiveRecipe(base, recipeId)
        }
    }

    fun toggleLivingPreviewBatteryPause(paused: Boolean) {
        livingPreviewEngine.setLowPowerPaused(paused)
    }

    // F07 Visual Forensics & P03 Constraints
    fun runVisualForensicsAudit() {
        val doc = transactionEngine.current()
        if (doc.layers.size < 2) {
            showToast("Need at least 2 layers to compare")
            return
        }
        val baseId = doc.layers.first().id
        val cmpId = doc.activeLayerId ?: doc.layers.last().id
        viewModelScope.launch(Dispatchers.Default) {
            val report = FuturesAndConstraintsEngine.computeForensicsReport(
                doc, tileRenderer, baseId, cmpId, _uiState.value.activeSelectionMask
            )
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(forensicsReport = report) }
            }
        }
    }

    fun addConstraintFromSelection(kind: ConstraintKind, label: String) {
        val pts = _uiState.value.activeSelectionMask?.points
            ?: listOf(Vec2(-150f, -150f), Vec2(150f, -150f), Vec2(150f, 150f), Vec2(-150f, 150f))
        commitDoc("Add ${kind.name} constraint") { base ->
            FuturesAndConstraintsEngine.addConstraint(base, kind, label, pts)
        }
        showToast("Constraint added & locked")
    }

    fun toggleConstraintLock(id: String) {
        commitDoc("Toggle constraint lock") { base -> FuturesAndConstraintsEngine.toggleConstraintLock(base, id) }
    }

    // P06 Outpainting
    fun runLocalOutpaint(direction: FuturesAndConstraintsEngine.OutpaintDirection) {
        commitDoc("Local Outpaint (${direction.name})") { base ->
            FuturesAndConstraintsEngine.generateLocalOutpaintCandidate(base, tileRenderer, direction)
        }
        showToast("Extended bounds with non-destructive Outpaint layer")
    }

    fun runCustomLocalOutpaint(
        leftTiles: Int,
        rightTiles: Int,
        topTiles: Int,
        bottomTiles: Int,
        blendStrength: Float,
        featherPx: Float
    ) {
        if (leftTiles + rightTiles + topTiles + bottomTiles == 0) {
            showToast("Set at least one edge margin > 0 tiles")
            return
        }
        commitDoc("Local Outpaint (L$leftTiles R$rightTiles T$topTiles B$bottomTiles)") { base ->
            FuturesAndConstraintsEngine.generateCustomLocalOutpaint(
                base, tileRenderer, leftTiles, rightTiles, topTiles, bottomTiles, blendStrength, featherPx
            )
        }
        showToast("Extended canvas deterministically (Local Edge Study)")
    }

    // F10 Vault, P04 Character, P05 Art Director, P07 Branches, P08 Orchard, P09 Workshop
    fun saveCurrentCanvasToVault(title: String, tagsCsv: String) {
        val doc = transactionEngine.current()
        val tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.Default) {
            val thumb = storageManager.renderExportPreview(doc, tileRenderer)
            val updatedDoc = VaultBranchOrchardEngine.addVaultAsset(
                doc = doc,
                title = title,
                sourceUri = "local://canvas/${doc.documentId}",
                sourceId = "canvas_capture",
                tags = tags,
                bitmap = thumb
            ).first
            thumb.recycle()
            withContext(Dispatchers.Main) {
                commitDoc("Save reference '$title' to Vault") { updatedDoc }
                showToast("Saved reference to Visual Memory Vault")
            }
        }
    }

    fun toggleVaultAssetApproval(assetId: String, approved: Boolean) {
        commitDoc("Set Vault asset approval=$approved") { base ->
            VaultBranchOrchardEngine.setVaultAssetApproval(base, assetId, approved)
        }
    }

    fun toggleCharacterProportionRuler() {
        commitDoc("Toggle Character Proportion Ruler") { base ->
            VaultBranchOrchardEngine.toggleProportionRuler(base)
        }
    }

    fun updateCharacterSheetFull(
        name: String,
        proportionNotes: String,
        poseNotes: String,
        turnaroundNotes: String,
        paletteHexes: List<String>,
        expressionTags: List<String>,
        poseTags: List<String>,
        referenceAssetIds: List<String>,
        referenceProvenanceNote: String
    ) {
        commitDoc("Update Character Sheet '$name'") { base ->
            VaultBranchOrchardEngine.updateCharacterSheetFull(
                doc = base,
                name = name,
                proportionNotes = proportionNotes,
                poseNotes = poseNotes,
                turnaroundNotes = turnaroundNotes,
                paletteHexes = paletteHexes,
                expressionTags = expressionTags,
                poseTags = poseTags,
                referenceAssetIds = referenceAssetIds,
                referenceProvenanceNote = referenceProvenanceNote
            )
        }
        showToast("Character study saved")
    }

    fun setCharacterCanonApproval(approved: Boolean) {
        commitDoc("Set Character canon approval=$approved") { base ->
            VaultBranchOrchardEngine.setCharacterCanonApproval(base, userApproved = approved)
        }
        showToast(if (approved) "Character canon approved by artist" else "Character canon unmarked")
    }

    fun runArtDirectorCommand(command: String) {
        val result = VaultBranchOrchardEngine.executeArtDirectorCommand(
            doc = transactionEngine.current(),
            instruction = command,
            renderer = tileRenderer
        )
        commitDoc("Art Director: $command") { result.updatedDoc }
        showToast(result.message)
    }

    // Stage B: The Futures Loom + P07 Branch Comparison & Cherry-Pick
    fun toggleFuturesLoom(open: Boolean? = null) {
        _uiState.update { it.copy(showFuturesLoom = open ?: !it.showFuturesLoom) }
    }

    fun forkCreativeBranch(label: String, note: String = "") {
        commitDoc("Fork branch '$label'") { base ->
            VaultBranchOrchardEngine.forkBranch(base, label, note, tileRenderer)
        }
        showToast("Forked branch '$label'")
    }

    fun rollbackToBranch(branchId: String) {
        val doc = transactionEngine.current()
        val restored = VaultBranchOrchardEngine.rollbackToBranch(doc, branchId) ?: return
        commitDoc("Rollback to branch $branchId") { restored }
        showToast("Switched to branch $branchId")
    }

    fun compareCreativeBranches(branchAId: String, branchBId: String) {
        val doc = transactionEngine.current()
        viewModelScope.launch(Dispatchers.Default) {
            val report = VaultBranchOrchardEngine.buildBranchComparisonReport(doc, branchAId, branchBId, tileRenderer)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(branchComparison = report) }
                if (report == null) showToast("Both branches need snapshots to compare")
            }
        }
    }

    fun clearBranchComparison() {
        _uiState.update { it.copy(branchComparison = null) }
    }

    fun previewCherryPick(fromBranchId: String, layerNameOrId: String) {
        val doc = transactionEngine.current()
        val preview = VaultBranchOrchardEngine.previewCherryPickLayer(doc, fromBranchId, layerNameOrId)
        _uiState.update { it.copy(cherryPickPreview = preview) }
        if (preview == null) showToast("Layer snapshot unavailable")
    }

    fun applyCherryPick(resolution: CherryPickResolution) {
        val preview = _uiState.value.cherryPickPreview ?: return
        val doc = transactionEngine.current()
        val updated = VaultBranchOrchardEngine.cherryPickLayerWithResolution(
            doc = doc,
            fromBranchId = preview.fromBranchId,
            layerNameOrId = preview.sourceLayerName,
            resolution = resolution
        )
        if (updated != null) {
            commitDoc("Cherry-pick '${preview.sourceLayerName}' (${resolution.name})") { updated }
            _uiState.update { it.copy(cherryPickPreview = null) }
            showToast("Cherry-picked '${preview.sourceLayerName}' safely")
        }
    }

    fun clearCherryPickPreview() {
        _uiState.update { it.copy(cherryPickPreview = null) }
    }

    fun addOrchardSceneNode(title: String) {
        val doc = transactionEngine.current()
        val count = VaultBranchOrchardEngine.readOrchardNodes(doc).size
        commitDoc("Add Orchard scene '$title'") { base ->
            VaultBranchOrchardEngine.addOrchardNode(base, title, base.activeBranchId, count * 140f, (count % 2) * 90f)
        }
        showToast("Scene node added to World Orchard")
    }

    fun navigateToOrchardNode(node: com.example.features.OrchardSceneNode) {
        val doc = transactionEngine.current()
        if (node.branchId != doc.activeBranchId && doc.branches.any { it.branchId == node.branchId }) {
            VaultBranchOrchardEngine.rollbackToBranch(doc, node.branchId)?.let { restored ->
                commitDoc("Orchard checkout '${node.title}' (${node.branchId})") { restored }
            }
        }
        _uiState.update { state ->
            state.copy(
                viewOffsetX = -node.x * state.viewScale,
                viewOffsetY = -node.y * state.viewScale,
                showWorkspaceHub = false,
                showFuturesLoom = false
            )
        }
        showToast("Orchard: ${node.title} (@${node.branchId})")
    }

    fun applyWorkshopTemplate(templateId: String) {
        commitDoc("Apply Workshop template $templateId") { base ->
            VectorAndFoundryEngine.applyWorkshopTemplate(base, templateId)
        }
        showToast("Workshop template applied")
    }

    // Stage E: Workspace Mode & Creative Command Ring
    fun setWorkspaceMode(mode: WorkspaceMode) {
        if (mode != WorkspaceMode.ANIMATE && _uiState.value.timelinePlaying) {
            stopTimelinePlayback()
        }
        _uiState.update { state ->
            val suggestedTool = when (mode) {
                WorkspaceMode.EDIT -> if (state.activeTool != StudioTool.VECTOR_EDIT) StudioTool.TRANSFORM else state.activeTool
                WorkspaceMode.PAINT -> if (state.activeTool == StudioTool.TRANSFORM || state.activeTool == StudioTool.VECTOR_EDIT) StudioTool.PENCIL else state.activeTool
                else -> state.activeTool
            }
            state.copy(
                workspaceMode = mode,
                activeTool = suggestedTool,
                spatialOrbitActive = (mode == WorkspaceMode.EDIT && state.spatialOrbitActive)
            )
        }
    }

    fun toggleCommandRing(open: Boolean? = null) {
        _uiState.update { it.copy(showCommandRing = open ?: !it.showCommandRing) }
    }

    fun toggleAccessibleRingStrip() {
        _uiState.update { it.copy(commandRingAccessibleStrip = !it.commandRingAccessibleStrip) }
    }

    fun toggleReducedMotion() {
        _uiState.update { it.copy(reducedMotionEnabled = !it.reducedMotionEnabled) }
    }

    fun customizeCommandRingSlot(slotIndex: Int, action: CommandRingAction) {
        _uiState.update { state ->
            if (slotIndex !in state.commandRingSlots.indices) return@update state
            val updated = state.commandRingSlots.toMutableList().also { it[slotIndex] = action }
            state.copy(commandRingSlots = updated)
        }
    }

    fun triggerCommandRingAction(action: CommandRingAction) {
        _uiState.update { it.copy(showCommandRing = false) }
        when (action) {
            CommandRingAction.BRUSH -> selectTool(StudioTool.PENCIL)
            CommandRingAction.ERASER -> selectTool(StudioTool.ERASER)
            CommandRingAction.SELECT -> selectTool(StudioTool.LASSO)
            CommandRingAction.TRANSFORM -> beginInteractiveTransform()
            CommandRingAction.VECTOR -> selectTool(StudioTool.VECTOR_EDIT)
            CommandRingAction.COLOR_PICKER -> toggleColorPopover(true)
            CommandRingAction.UNDO -> undo()
            CommandRingAction.REDO -> redo()
            CommandRingAction.FUTURES -> openFuturesTable()
            CommandRingAction.LOOM -> toggleFuturesLoom(true)
            CommandRingAction.LAYERS -> toggleLayersPanel(true)
            CommandRingAction.EYEDROPPER -> selectTool(StudioTool.EYEDROPPER)
        }
    }

    // --- Storage, Autosave & Export ---
    private fun queueAutosave(doc: CanonicalDocument) {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(2500)
            storageManager.saveLocalDraftAtomic(doc).onSuccess {
                _uiState.update {
                    it.copy(
                        hasLocalDraft = true,
                        statusBannerText = "Saved locally · rev ${doc.revision}"
                    )
                }
            }.onFailure { err ->
                logError("Autosave failed: ${err.message}")
            }
        }
    }

    fun toggleProjectDialog(open: Boolean) {
        _uiState.update {
            it.copy(
                showProjectDialog = open,
                projectStatusMessage = if (open && it.hasLocalDraft) "Local v3 draft available for recovery." else ""
            )
        }
    }

    fun saveProjectToUri(uri: Uri) {
        val doc = transactionEngine.current()
        viewModelScope.launch {
            storageManager.saveArchiveToUri(uri, doc).fold(
                onSuccess = { _uiState.update { it.copy(projectStatusMessage = "Saved portable .iastudio v3 archive.") } },
                onFailure = { e ->
                    logError("Save archive failed: ${e.message}")
                    _uiState.update { it.copy(projectStatusMessage = "Save failed: ${e.message}") }
                }
            )
        }
    }

    fun openProjectFromUri(uri: Uri) {
        viewModelScope.launch {
            storageManager.openArchiveFromUri(uri).fold(
                onSuccess = { loaded ->
                    transactionEngine.resetRoot(loaded)
                    _uiState.update { it.copy(projectStatusMessage = "Loaded archive (${loaded.layers.size} layers preserved).") }
                    showToast("Loaded '${loaded.title}'")
                },
                onFailure = { e ->
                    logError("Open archive failed: ${e.message}")
                    _uiState.update { it.copy(projectStatusMessage = "Could not open file safely: ${e.message}") }
                }
            )
        }
    }

    fun recoverLocalDraft() {
        viewModelScope.launch {
            storageManager.loadLocalDraft().fold(
                onSuccess = { loaded ->
                    transactionEngine.resetRoot(loaded)
                    _uiState.update { it.copy(projectStatusMessage = "Recovered local draft rev ${loaded.revision}.") }
                },
                onFailure = { e ->
                    logError("Draft recovery failed: ${e.message}")
                    _uiState.update { it.copy(projectStatusMessage = "Recovery failed: ${e.message}") }
                }
            )
        }
    }

    fun createNewBlankCanvas() {
        stopTimelinePlayback()
        transactionEngine.resetRoot(createInitialDocument())
        _uiState.update { it.copy(showProjectDialog = false, viewScale = 1f, viewOffsetX = 0f, viewOffsetY = 0f, transformSession = null, selectedVectorId = null) }
        showToast("New canvas ready")
    }

    fun loadSampleProject() {
        stopTimelinePlayback()
        viewModelScope.launch {
            val sample = withContext(Dispatchers.Default) {
                com.example.document.SampleProjectFactory.buildSignatureDemo(tileRenderer)
            }
            transactionEngine.resetRoot(sample)
            _uiState.update {
                it.copy(
                    showProjectDialog = false,
                    viewScale = 1f,
                    viewOffsetX = 0f,
                    viewOffsetY = 0f,
                    projectStatusMessage = "Loaded offline sample project (${sample.layers.size} layers)."
                )
            }
            showToast("Offline sample loaded · One Mark, Three Futures")
        }
    }

    fun loadLivingWorkshopJourney() {
        stopTimelinePlayback()
        viewModelScope.launch {
            val sample = withContext(Dispatchers.Default) {
                com.example.document.SampleProjectFactory.buildLivingWorkshopJourney(tileRenderer)
            }
            transactionEngine.resetRoot(sample)
            _uiState.update {
                it.copy(
                    showProjectDialog = false,
                    viewScale = 1f,
                    viewOffsetX = 0f,
                    viewOffsetY = 0f,
                    workspaceMode = WorkspaceMode.EXPLORE,
                    projectStatusMessage = "Loaded Living Workshop Journey (${sample.layers.size} layers, ${sample.branches.size} branches)."
                )
            }
            showToast("Living Workshop Journey loaded")
        }
    }

    fun importImageUri(uri: Uri) {
        viewModelScope.launch {
            storageManager.decodePhotoToLayer(uri, transactionEngine.current()).fold(
                onSuccess = { layer ->
                    commitDoc("Import Photo Layer") { doc ->
                        doc.copy(layers = doc.layers + layer, activeLayerId = layer.id)
                    }
                    showToast("Photo imported as layer")
                },
                onFailure = { e ->
                    logError("Photo import failed: ${e.message}")
                    showToast("Image import failed: ${e.message}")
                }
            )
        }
    }

    // --- Export Artwork / SVG / Foundry ---
    fun openExportDialog() {
        viewModelScope.launch(Dispatchers.Default) {
            val preview = storageManager.renderExportPreview(transactionEngine.current(), tileRenderer)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(showExportDialog = true, exportPreviewBitmap = preview) }
            }
        }
    }

    fun closeExportDialog() {
        _uiState.update { it.copy(showExportDialog = false, exportPreviewBitmap = null) }
    }

    fun setExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun setJpgQuality(quality: Int) {
        _uiState.update { it.copy(jpgQuality = quality.coerceIn(50, 100)) }
    }

    fun exportToUri(uri: Uri) {
        val s = _uiState.value
        val bmp = s.exportPreviewBitmap ?: return
        viewModelScope.launch {
            storageManager.exportFlattenedBitmapToUri(uri, bmp, s.exportFormat, s.jpgQuality).fold(
                onSuccess = {
                    closeExportDialog()
                    showToast("Exported ${s.exportFormat.name}")
                },
                onFailure = { e ->
                    logError("Bitmap export failed: ${e.message}")
                    showToast("Export failed: ${e.message}")
                }
            )
        }
    }

    fun exportSvgToUri(uri: Uri) {
        val svg = VectorAndFoundryEngine.exportDocumentToSvg(transactionEngine.current())
        viewModelScope.launch {
            storageManager.saveTextOrBytesToUri(uri, svg.toByteArray(Charsets.UTF_8)).fold(
                onSuccess = { showToast("Vector SVG exported") },
                onFailure = { e -> logError("SVG export failed: ${e.message}"); showToast("SVG export failed") }
            )
        }
    }

    fun exportFoundryBundleToUri(uri: Uri) {
        val doc = transactionEngine.current()
        viewModelScope.launch {
            val zipBytes = withContext(Dispatchers.Default) {
                VectorAndFoundryEngine.exportFoundryBundleZip(doc, tileRenderer)
            }
            storageManager.saveTextOrBytesToUri(uri, zipBytes).fold(
                onSuccess = { showToast("Production Foundry bundle exported") },
                onFailure = { e -> logError("Foundry export failed: ${e.message}"); showToast("Foundry export failed") }
            )
        }
    }

    fun shareExportedArtwork(onReady: (Uri, String) -> Unit) {
        val s = _uiState.value
        val bmp = s.exportPreviewBitmap ?: return
        viewModelScope.launch {
            storageManager.buildShareIntentForBitmap(bmp, s.exportFormat, s.jpgQuality).onSuccess { intent ->
                val uri = androidx.core.content.IntentCompat.getParcelableExtra(
                    intent,
                    android.content.Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                val mime = intent.type ?: "image/png"
                if (uri != null) onReady(uri, mime)
            }.onFailure {
                showToast("Share failed: ${it.message}")
            }
        }
    }

    fun buildRedactedDiagnosticsReport(): String {
        val s = _uiState.value
        val doc = s.document
        return buildString {
            appendLine("=== INFINITE STUDIO DIAGNOSTICS (REDACTED) ===")
            appendLine("Document ID: ${doc.documentId} | Mode: ${s.workspaceMode.name}")
            appendLine("Schema Version: v${doc.schemaVersion} | Revision: ${doc.revision}")
            appendLine("Bounds: ${doc.bounds.width}x${doc.bounds.height} @ (${doc.bounds.minX}, ${doc.bounds.minY})")
            appendLine("Layers: ${doc.layers.size} | Vectors: ${doc.vectorObjects.size} | Semantic Objects: ${doc.semanticObjects.size}")
            appendLine("Constraints: ${doc.constraints.size} | Branches: ${doc.branches.size} | Vault Assets: ${doc.vaultAssets.size}")
            appendLine("Tile Cache: ${s.tileCacheStats.cachedTiles} tiles (${s.tileCacheStats.allocatedBytes / 1024} KB), hits=${s.tileCacheStats.hits}, misses=${s.tileCacheStats.misses}, hitRatio=${s.tileCacheStats.hitRatioPercent}%")
            appendLine("Render Frame Timing: last=${s.tileCacheStats.lastFrameDrawMicros} us | ema=${s.tileCacheStats.emaFrameDrawMicros} us | visibleTiles=${s.tileCacheStats.lastVisibleTilesRendered}")
            appendLine("History Stack: ${s.historyStackSize} states (~${s.historyEstimatedBytes / 1024} KB)")
            appendLine("Stylus: intent=${s.stylusDiagnostics.lastIntent}, pressure=${s.stylusDiagnostics.lastPressure}, tilt=${s.stylusDiagnostics.lastTiltDeg}, hist=${s.stylusDiagnostics.historicalSamplesConsumed}, canceled=${s.stylusDiagnostics.canceledTransactions}")
            appendLine("Provider Mode: ${s.providerMode.name} | Companion Calls Left: ${s.remainingCompanionCalls}")
            appendLine("Client Secrets: NONE (zero API credentials in APK or BuildConfig)")
            appendLine("Recent Errors: ${if (s.recentErrors.isEmpty()) "None" else s.recentErrors.joinToString("; ")}")
            appendLine("(Note: Artwork pixel bytes and secrets are strictly excluded.)")
        }
    }

    private fun buildStatusBanner(mode: ProviderMode): String = when (mode) {
        ProviderMode.OFFLINE_LOCAL_ONLY -> "Offline local canvas · zero network"
        ProviderMode.OFFLINE_MOCK_PROVIDER -> "Offline mock router active"
        ProviderMode.LOOPBACK_COMPANION -> "Loopback companion 127.0.0.1:8787"
    }
}
