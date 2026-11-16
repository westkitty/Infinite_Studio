package com.example.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.example.document.ConstraintKind
import com.example.document.Vec2
import com.example.document.VectorKind
import com.example.features.VectorAndFoundryEngine
import com.example.input.PointerIntent
import com.example.input.StrokeSample
import com.example.input.StylusDiagnostics
import com.example.input.StylusInputRouter
import com.example.model.PaperStyle
import com.example.model.ShapeGuide
import com.example.model.StudioTool
import com.example.model.StudioUiState
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.tools.TileBrushEngine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Native Android View host wired to Compose via AndroidView so raw MotionEvent streams
 * (S-Pen stylus vs touch vs eraser, historical batched samples, tilt, pressure, hover,
 * ACTION_CANCEL, and Android 13+ FLAG_CANCELED) flow directly into StylusInputRouter and
 * SparseTileRenderer at full refresh cadence without Compose recomposition overhead.
 */
@Composable
fun InfiniteCanvasView(
    state: StudioUiState,
    tileRenderer: SparseTileRenderer,
    onViewportSizeChanged: (IntSize) -> Unit,
    onUpdateViewport: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onBeginPointer: (worldPos: PointF, pressure: Float) -> Pair<PointF?, PointF?>,
    onMovePointer: (worldPos: PointF, pressure: Float) -> Pair<PointF?, PointF?>,
    onEndPointer: () -> Unit,
    onCancelStroke: () -> Unit,
    onTwoFingerTapUndo: () -> Unit,
    onThreeFingerTapRedo: () -> Unit,
    onStylusDiagnostics: (StylusDiagnostics) -> Unit,
    onSelectVector: (String?) -> Unit = {},
    onMoveVectorLive: (String, Float, Float) -> Unit = { _, _, _ -> },
    onResizeVectorLive: (String, Float, Float) -> Unit = { _, _, _ -> },
    onMoveBezierPartLive: (String, Int, VectorAndFoundryEngine.BezierHandlePart, Vec2) -> Unit = { _, _, _, _ -> },
    onMoveBalloonTailLive: (String, Vec2) -> Unit = { _, _ -> },
    onCommitVectorEdits: (String) -> Unit = {},
    onUpdateTransformLive: (Float, Float, Float?, Float) -> Unit = { _, _, _, _ -> },
    onCommitTransform: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val latestState by rememberUpdatedState(state)
    val latestOnUpdateViewport by rememberUpdatedState(onUpdateViewport)
    val latestOnBeginPointer by rememberUpdatedState(onBeginPointer)
    val latestOnMovePointer by rememberUpdatedState(onMovePointer)
    val latestOnEndPointer by rememberUpdatedState(onEndPointer)
    val latestOnCancelStroke by rememberUpdatedState(onCancelStroke)
    val latestOnUndo by rememberUpdatedState(onTwoFingerTapUndo)
    val latestOnRedo by rememberUpdatedState(onThreeFingerTapRedo)
    val latestOnDiag by rememberUpdatedState(onStylusDiagnostics)
    val latestOnSelectVector by rememberUpdatedState(onSelectVector)
    val latestOnMoveVector by rememberUpdatedState(onMoveVectorLive)
    val latestOnResizeVector by rememberUpdatedState(onResizeVectorLive)
    val latestOnMoveBezier by rememberUpdatedState(onMoveBezierPartLive)
    val latestOnMoveTail by rememberUpdatedState(onMoveBalloonTailLive)
    val latestOnCommitVector by rememberUpdatedState(onCommitVectorEdits)
    val latestOnUpdateTransform by rememberUpdatedState(onUpdateTransformLive)
    val latestOnCommitTransform by rememberUpdatedState(onCommitTransform)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("studio_canvas_viewport")
            .onSizeChanged { onViewportSizeChanged(it) }
    ) {
        AndroidView(
            factory = { ctx ->
                StudioNativeCanvasView(
                    context = ctx,
                    tileRenderer = tileRenderer,
                    getState = { latestState },
                    onUpdateViewport = { s, ox, oy -> latestOnUpdateViewport(s, ox, oy) },
                    onBeginPointer = { wp, p -> latestOnBeginPointer(wp, p) },
                    onMovePointer = { wp, p -> latestOnMovePointer(wp, p) },
                    onEndPointer = { latestOnEndPointer() },
                    onCancelStroke = { latestOnCancelStroke() },
                    onTwoFingerTapUndo = { latestOnUndo() },
                    onThreeFingerTapRedo = { latestOnRedo() },
                    onDiagnostics = { latestOnDiag(it) },
                    onSelectVector = { latestOnSelectVector(it) },
                    onMoveVectorLive = { id, dx, dy -> latestOnMoveVector(id, dx, dy) },
                    onResizeVectorLive = { id, w, h -> latestOnResizeVector(id, w, h) },
                    onMoveBezierPartLive = { id, idx, part, pos -> latestOnMoveBezier(id, idx, part, pos) },
                    onMoveBalloonTailLive = { id, tip -> latestOnMoveTail(id, tip) },
                    onCommitVectorEdits = { latestOnCommitVector(it) },
                    onUpdateTransformLive = { dx, dy, sf, rd -> latestOnUpdateTransform(dx, dy, sf, rd) },
                    onCommitTransform = { latestOnCommitTransform() }
                )
            },
            update = { view ->
                view.syncSelectionAnimation(state.selection.active)
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private enum class CanvasGizmoDrag {
    NONE,
    TRANSFORM_TRANSLATE,
    TRANSFORM_SCALE,
    TRANSFORM_ROTATE,
    VECTOR_BODY,
    VECTOR_RESIZE,
    VECTOR_TAIL_TIP,
    VECTOR_BEZIER
}

private class StudioNativeCanvasView(
    context: Context,
    private val tileRenderer: SparseTileRenderer,
    private val getState: () -> StudioUiState,
    private val onUpdateViewport: (Float, Float, Float) -> Unit,
    private val onBeginPointer: (PointF, Float) -> Pair<PointF?, PointF?>,
    private val onMovePointer: (PointF, Float) -> Pair<PointF?, PointF?>,
    private val onEndPointer: () -> Unit,
    private val onCancelStroke: () -> Unit,
    private val onTwoFingerTapUndo: () -> Unit,
    private val onThreeFingerTapRedo: () -> Unit,
    private val onDiagnostics: (StylusDiagnostics) -> Unit,
    private val onSelectVector: (String?) -> Unit,
    private val onMoveVectorLive: (String, Float, Float) -> Unit,
    private val onResizeVectorLive: (String, Float, Float) -> Unit,
    private val onMoveBezierPartLive: (String, Int, VectorAndFoundryEngine.BezierHandlePart, Vec2) -> Unit,
    private val onMoveBalloonTailLive: (String, Vec2) -> Unit,
    private val onCommitVectorEdits: (String) -> Unit,
    private val onUpdateTransformLive: (Float, Float, Float?, Float) -> Unit,
    private val onCommitTransform: () -> Unit
) : View(context) {

    private var dashPhase: Float = 0f
    private var selectionAnimating: Boolean = false
    private val marqueeTicker = object : Runnable {
        override fun run() {
            if (selectionAnimating) {
                dashPhase = (dashPhase - 1.5f) % 32f
                invalidate()
                postDelayed(this, 32L)
            }
        }
    }

    private val provisionalSamples = mutableListOf<Pair<Vec2, Float>>()
    private var shapePreviewStart: PointF? = null
    private var shapePreviewEnd: PointF? = null
    private var hoverWorldPos: PointF? = null
    private var hoverIntent: PointerIntent = PointerIntent.TOUCH

    // Cached Paints, Shader & Path to eliminate per-frame allocations inside onDraw()
    private val checkerPaint: Paint by lazy {
        val cell = 24
        val tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(tile)
        c.drawColor(Color.rgb(18, 18, 20))
        val light = Paint().apply { color = Color.rgb(24, 24, 27) }
        c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), light)
        c.drawRect(cell.toFloat(), cell.toFloat(), (cell * 2).toFloat(), (cell * 2).toFloat(), light)
        Paint().apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    private val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 161, 161, 170)
        style = Paint.Style.STROKE
    }
    private val symPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 56, 189, 248)
        style = Paint.Style.STROKE
    }
    private val stagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 129, 140, 248)
        style = Paint.Style.STROKE
    }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 245, 158, 11)
        style = Paint.Style.STROKE
    }
    private val constraintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val guidancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shapePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val marqueeFillPaint = Paint().apply {
        color = Color.argb(22, 56, 189, 248)
        style = Paint.Style.FILL
    }
    private val marqueeStrokeWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val marqueeStrokeBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(2, 132, 199)
        style = Paint.Style.STROKE
    }
    private val hoverRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val gizmoStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(245, 158, 11)
    }
    private val gizmoHandleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(56, 189, 248)
    }
    private val gizmoHandleRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val gizmoVectorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(56, 189, 248)
    }
    private val gizmoSnapGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(120, 56, 189, 248)
    }
    private val scratchPath = Path()
    private val scratchRect = RectF()

    private var gizmoDragMode: CanvasGizmoDrag = CanvasGizmoDrag.NONE
    private var lastGizmoX: Float = 0f
    private var lastGizmoY: Float = 0f
    private var activeBezierNodeIdx: Int = -1
    private var activeBezierPart: VectorAndFoundryEngine.BezierHandlePart = VectorAndFoundryEngine.BezierHandlePart.ANCHOR
    private var activeSnapPoint: PointF? = null

    private var cachedDashScale: Float = -1f
    private var cachedSymDash: DashPathEffect? = null
    private var cachedRulerDash: DashPathEffect? = null
    private var cachedConstraintDash: DashPathEffect? = null
    private var cachedGuidanceDash: DashPathEffect? = null

    fun syncSelectionAnimation(active: Boolean) {
        if (active && !selectionAnimating) {
            selectionAnimating = true
            removeCallbacks(marqueeTicker)
            postDelayed(marqueeTicker, 32L)
        } else if (!active && selectionAnimating) {
            selectionAnimating = false
            removeCallbacks(marqueeTicker)
        }
    }

    override fun onDetachedFromWindow() {
        selectionAnimating = false
        removeCallbacks(marqueeTicker)
        super.onDetachedFromWindow()
    }

    private fun ensureScaledDashEffects(viewScale: Float) {
        if (cachedDashScale == viewScale) return
        cachedDashScale = viewScale
        cachedSymDash = DashPathEffect(floatArrayOf(8f / viewScale, 6f / viewScale), 0f)
        cachedRulerDash = DashPathEffect(floatArrayOf(6f / viewScale, 6f / viewScale), 0f)
        cachedConstraintDash = DashPathEffect(floatArrayOf(10f / viewScale, 6f / viewScale), 0f)
        cachedGuidanceDash = DashPathEffect(floatArrayOf(12f / viewScale, 8f / viewScale), 0f)
    }

    private fun computeTransformGizmoRect(state: StudioUiState, outRect: RectF) {
        val targetId = state.transformSession?.targetLayerId ?: state.document.activeLayerId
        val layer = state.document.layers.find { it.id == targetId }
        val semObj = state.document.semanticObjects.find {
            it.id == state.transformSession?.semanticObjectId || it.extractedLayerId == layer?.id
        }
        if (semObj != null) {
            outRect.set(semObj.boundsX, semObj.boundsY, semObj.boundsX + semObj.boundsW, semObj.boundsY + semObj.boundsH)
        } else {
            val ox = layer?.transform?.offsetX ?: 0f
            val oy = layer?.transform?.offsetY ?: 0f
            val sx = abs(layer?.transform?.scaleX ?: 1f).coerceAtLeast(0.25f)
            val sy = abs(layer?.transform?.scaleY ?: 1f).coerceAtLeast(0.25f)
            outRect.set(ox - 240f * sx, oy - 170f * sy, ox + 240f * sx, oy + 170f * sy)
        }
    }

    private fun hypot(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    private val router = StylusInputRouter(
        onBeginStroke = { sample ->
            val wp = PointF(sample.worldX, sample.worldY)
            provisionalSamples.clear()
            gizmoDragMode = CanvasGizmoDrag.NONE
            activeSnapPoint = null
            lastGizmoX = sample.worldX
            lastGizmoY = sample.worldY

            val st = getState()
            val scale = max(0.1f, st.viewScale)
            val hitTol = 28f / scale

            if (st.activeTool == StudioTool.TRANSFORM) {
                computeTransformGizmoRect(st, scratchRect)
                val rotHandleY = scratchRect.top - 48f / scale
                gizmoDragMode = when {
                    hypot(sample.worldX - scratchRect.centerX(), sample.worldY - rotHandleY) <= hitTol -> CanvasGizmoDrag.TRANSFORM_ROTATE
                    hypot(sample.worldX - scratchRect.right, sample.worldY - scratchRect.bottom) <= hitTol -> CanvasGizmoDrag.TRANSFORM_SCALE
                    else -> CanvasGizmoDrag.TRANSFORM_TRANSLATE
                }
                invalidate()
            } else if (st.activeTool == StudioTool.VECTOR_EDIT) {
                val selObj = st.document.vectorObjects.find { it.id == st.selectedVectorId }
                var matched = false
                if (selObj != null) {
                    val tail = selObj.tailTip
                    if (tail != null && hypot(sample.worldX - tail.x, sample.worldY - tail.y) <= hitTol) {
                        gizmoDragMode = CanvasGizmoDrag.VECTOR_TAIL_TIP
                        matched = true
                    } else if (selObj.kind == VectorKind.PATH) {
                        selObj.nodes.forEachIndexed { idx, node ->
                            if (!matched && hypot(sample.worldX - node.anchor.x, sample.worldY - node.anchor.y) <= hitTol) {
                                gizmoDragMode = CanvasGizmoDrag.VECTOR_BEZIER
                                activeBezierNodeIdx = idx
                                activeBezierPart = VectorAndFoundryEngine.BezierHandlePart.ANCHOR
                                matched = true
                            } else if (!matched && node.handleIn != null && hypot(sample.worldX - node.handleIn.x, sample.worldY - node.handleIn.y) <= hitTol) {
                                gizmoDragMode = CanvasGizmoDrag.VECTOR_BEZIER
                                activeBezierNodeIdx = idx
                                activeBezierPart = VectorAndFoundryEngine.BezierHandlePart.HANDLE_IN
                                matched = true
                            } else if (!matched && node.handleOut != null && hypot(sample.worldX - node.handleOut.x, sample.worldY - node.handleOut.y) <= hitTol) {
                                gizmoDragMode = CanvasGizmoDrag.VECTOR_BEZIER
                                activeBezierNodeIdx = idx
                                activeBezierPart = VectorAndFoundryEngine.BezierHandlePart.HANDLE_OUT
                                matched = true
                            }
                        }
                    }
                    if (!matched && hypot(sample.worldX - (selObj.x + selObj.width), sample.worldY - (selObj.y + selObj.height)) <= hitTol) {
                        gizmoDragMode = CanvasGizmoDrag.VECTOR_RESIZE
                        matched = true
                    }
                }
                if (!matched) {
                    val hit = VectorAndFoundryEngine.findVectorAt(st.document, sample.worldX, sample.worldY, hitTol)
                    onSelectVector(hit?.id)
                    gizmoDragMode = if (hit != null) CanvasGizmoDrag.VECTOR_BODY else CanvasGizmoDrag.NONE
                }
                invalidate()
            } else {
                val isBrushStroke = st.shapeGuide == ShapeGuide.FREEHAND && st.activeTool !in listOf(
                    StudioTool.LASSO, StudioTool.RECT_SELECT, StudioTool.TRANSFORM,
                    StudioTool.VECTOR_EDIT, StudioTool.FILL, StudioTool.EYEDROPPER, StudioTool.ERASER
                )
                if (isBrushStroke) {
                    provisionalSamples.add(Vec2(sample.worldX, sample.worldY) to sample.pressure)
                }
                val (s, e) = onBeginPointer(wp, sample.pressure)
                shapePreviewStart = s
                shapePreviewEnd = e
                invalidate()
            }
        },
        onMoveStroke = { samples: List<StrokeSample> ->
            val st = getState()
            if (st.activeTool == StudioTool.TRANSFORM && gizmoDragMode != CanvasGizmoDrag.NONE) {
                val last = samples.lastOrNull()
                if (last != null) {
                    val dx = last.worldX - lastGizmoX
                    val dy = last.worldY - lastGizmoY
                    when (gizmoDragMode) {
                        CanvasGizmoDrag.TRANSFORM_TRANSLATE -> onUpdateTransformLive(dx, dy, null, 0f)
                        CanvasGizmoDrag.TRANSFORM_SCALE -> {
                            computeTransformGizmoRect(st, scratchRect)
                            val cx = scratchRect.centerX()
                            val cy = scratchRect.centerY()
                            val prevDist = max(12f, hypot(lastGizmoX - cx, lastGizmoY - cy))
                            val currDist = max(12f, hypot(last.worldX - cx, last.worldY - cy))
                            onUpdateTransformLive(0f, 0f, (currDist / prevDist).coerceIn(0.85f, 1.2f), 0f)
                        }
                        CanvasGizmoDrag.TRANSFORM_ROTATE -> {
                            computeTransformGizmoRect(st, scratchRect)
                            val cx = scratchRect.centerX()
                            val cy = scratchRect.centerY()
                            val prevAng = Math.toDegrees(atan2((lastGizmoY - cy).toDouble(), (lastGizmoX - cx).toDouble())).toFloat()
                            val currAng = Math.toDegrees(atan2((last.worldY - cy).toDouble(), (last.worldX - cx).toDouble())).toFloat()
                            onUpdateTransformLive(0f, 0f, null, currAng - prevAng)
                        }
                        else -> Unit
                    }
                    activeSnapPoint = PointF(last.worldX, last.worldY)
                    lastGizmoX = last.worldX
                    lastGizmoY = last.worldY
                }
                invalidate()
            } else if (st.activeTool == StudioTool.VECTOR_EDIT && gizmoDragMode != CanvasGizmoDrag.NONE) {
                val last = samples.lastOrNull()
                val selObj = st.document.vectorObjects.find { it.id == st.selectedVectorId }
                if (last != null && selObj != null) {
                    val dx = last.worldX - lastGizmoX
                    val dy = last.worldY - lastGizmoY
                    when (gizmoDragMode) {
                        CanvasGizmoDrag.VECTOR_BODY -> onMoveVectorLive(selObj.id, dx, dy)
                        CanvasGizmoDrag.VECTOR_RESIZE -> onResizeVectorLive(selObj.id, last.worldX - selObj.x, last.worldY - selObj.y)
                        CanvasGizmoDrag.VECTOR_TAIL_TIP -> onMoveBalloonTailLive(selObj.id, Vec2(last.worldX, last.worldY))
                        CanvasGizmoDrag.VECTOR_BEZIER -> onMoveBezierPartLive(selObj.id, activeBezierNodeIdx, activeBezierPart, Vec2(last.worldX, last.worldY))
                        else -> Unit
                    }
                    activeSnapPoint = PointF(last.worldX, last.worldY)
                    lastGizmoX = last.worldX
                    lastGizmoY = last.worldY
                }
                invalidate()
            } else {
                val isBrushStroke = st.shapeGuide == ShapeGuide.FREEHAND && st.activeTool !in listOf(
                    StudioTool.LASSO, StudioTool.RECT_SELECT, StudioTool.TRANSFORM,
                    StudioTool.VECTOR_EDIT, StudioTool.FILL, StudioTool.EYEDROPPER, StudioTool.ERASER
                )
                for (s in samples) {
                    if (isBrushStroke) {
                        provisionalSamples.add(Vec2(s.worldX, s.worldY) to s.pressure)
                    }
                    val (ps, pe) = onMovePointer(PointF(s.worldX, s.worldY), s.pressure)
                    if (ps != null || pe != null) {
                        shapePreviewStart = ps
                        shapePreviewEnd = pe
                    }
                }
                invalidate()
            }
        },
        onEndStroke = {
            provisionalSamples.clear()
            shapePreviewStart = null
            shapePreviewEnd = null
            activeSnapPoint = null
            val st = getState()
            when {
                st.activeTool == StudioTool.TRANSFORM && gizmoDragMode != CanvasGizmoDrag.NONE -> {
                    gizmoDragMode = CanvasGizmoDrag.NONE
                }
                st.activeTool == StudioTool.VECTOR_EDIT && gizmoDragMode != CanvasGizmoDrag.NONE -> {
                    val desc = if (gizmoDragMode == CanvasGizmoDrag.VECTOR_RESIZE) "Resize Vector" else "Direct Vector Edit"
                    gizmoDragMode = CanvasGizmoDrag.NONE
                    onCommitVectorEdits(desc)
                }
                else -> {
                    gizmoDragMode = CanvasGizmoDrag.NONE
                    onEndPointer()
                }
            }
            invalidate()
        },
        onCancelStroke = {
            provisionalSamples.clear()
            shapePreviewStart = null
            shapePreviewEnd = null
            activeSnapPoint = null
            gizmoDragMode = CanvasGizmoDrag.NONE
            onCancelStroke()
            invalidate()
        },
        onHoverMove = { wp, intent ->
            hoverWorldPos = wp
            hoverIntent = intent
            invalidate()
        },
        onPanZoom = { scaleDelta, dx, dy ->
            val st = getState()
            val nextScale = (st.viewScale * scaleDelta).coerceIn(0.15f, 4.0f)
            onUpdateViewport(nextScale, st.viewOffsetX + dx, st.viewOffsetY + dy)
        },
        onTwoFingerTapUndo = { onTwoFingerTapUndo() },
        onThreeFingerTapRedo = { onThreeFingerTapRedo() }
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val st = getState()
        val handled = router.handleMotionEvent(
            event = event,
            viewportWidth = max(1, width).toFloat(),
            viewportHeight = max(1, height).toFloat(),
            viewScale = st.viewScale,
            viewOffsetX = st.viewOffsetX,
            viewOffsetY = st.viewOffsetY,
            sPenGuardEnabled = st.sPenOnly
        )
        onDiagnostics(router.diagnostics)
        return handled
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val st = getState()
        val handled = router.handleMotionEvent(
            event = event,
            viewportWidth = max(1, width).toFloat(),
            viewportHeight = max(1, height).toFloat(),
            viewScale = st.viewScale,
            viewOffsetX = st.viewOffsetX,
            viewOffsetY = st.viewOffsetY,
            sPenGuardEnabled = st.sPenOnly
        )
        onDiagnostics(router.diagnostics)
        return handled
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = getState()
        val vw = width.toFloat()
        val vh = height.toFloat()
        val scale = max(0.05f, state.viewScale)
        ensureScaledDashEffects(scale)

        // 1. Paper or cached BitmapShader Checkerboard (single draw call)
        if (state.paperStyle == PaperStyle.TRANSPARENT) {
            canvas.drawRect(0f, 0f, vw, vh, checkerPaint)
        } else {
            canvas.drawColor(state.paperStyle.colorInt)
        }

        canvas.save()
        canvas.translate(vw / 2f + state.viewOffsetX, vh / 2f + state.viewOffsetY)
        canvas.scale(scale, scale)

        // 2. Sparse 256x256 tile composite + vectors + onion skin
        tileRenderer.renderDocumentViewport(
            nativeCanvas = canvas,
            doc = state.document,
            viewportWidth = vw,
            viewportHeight = vh,
            viewScale = scale,
            viewOffsetX = state.viewOffsetX,
            viewOffsetY = state.viewOffsetY
        )

        // 3. Zero-lag provisional stroke overlay before commit
        if (provisionalSamples.isNotEmpty() && state.activeTool != StudioTool.AI_GUIDANCE) {
            val params = TileBrushEngine.StrokeRenderParams(
                tool = state.activeTool,
                colorInt = state.brushColorInt,
                preset = state.activeBrushPreset,
                symmetryMode = state.symmetryMode,
                activeMask = state.activeSelectionMask
            )
            TileBrushEngine.renderStrokeOnCanvas(
                canvas = canvas,
                canvasBounds = state.document.bounds,
                worldPoints = provisionalSamples,
                params = params,
                alphaLock = false
            )
        }

        // 4. Origin Crosshair
        originPaint.strokeWidth = 1.5f / scale
        canvas.drawLine(-16f, 0f, 16f, 0f, originPaint)
        canvas.drawLine(0f, -16f, 0f, 16f, originPaint)

        // 5. Symmetry Guides
        val b = state.document.bounds
        if (state.symmetryMode != SymmetryMode.NONE) {
            symPaint.strokeWidth = 2f / scale
            symPaint.pathEffect = cachedSymDash
            if (state.symmetryMode in listOf(SymmetryMode.VERTICAL, SymmetryMode.QUADRANT, SymmetryMode.KALEIDOSCOPE)) {
                canvas.drawLine(0f, b.minY, 0f, b.maxY, symPaint)
            }
            if (state.symmetryMode in listOf(SymmetryMode.HORIZONTAL, SymmetryMode.QUADRANT, SymmetryMode.KALEIDOSCOPE)) {
                canvas.drawLine(b.minX, 0f, b.maxX, 0f, symPaint)
            }
            if (state.symmetryMode == SymmetryMode.KALEIDOSCOPE) {
                canvas.drawLine(-3000f, -3000f, 3000f, 3000f, symPaint)
                canvas.drawLine(3000f, -3000f, -3000f, 3000f, symPaint)
            }
        }

        // 6. Spatial Stage 2.5D Perspective Vanishing Lines
        if (state.document.spatialStage.enabled) {
            val vp = state.document.spatialStage.vanishingPoint
            stagePaint.strokeWidth = 1.5f / scale
            val bMaxY = b.maxY
            canvas.drawLine(vp.x, vp.y, -1200f, bMaxY, stagePaint)
            canvas.drawLine(vp.x, vp.y, -600f, bMaxY, stagePaint)
            canvas.drawLine(vp.x, vp.y, 0f, bMaxY, stagePaint)
            canvas.drawLine(vp.x, vp.y, 600f, bMaxY, stagePaint)
            canvas.drawLine(vp.x, vp.y, 1200f, bMaxY, stagePaint)
            canvas.drawCircle(vp.x, vp.y, 10f / scale, stagePaint)
        }

        // 7. Character Studio Proportion Ruler (8-head grid)
        val charSheet = state.document.characterSheets.firstOrNull()
        if (charSheet?.showProportionRuler == true) {
            rulerPaint.strokeWidth = 1.2f / scale
            rulerPaint.pathEffect = cachedRulerDash
            for (headIdx in -4..4) {
                val hy = headIdx * 75f
                canvas.drawLine(-420f, hy, 420f, hy, rulerPaint)
            }
        }

        // 8. Constraint Constellation Overlays
        state.document.constraints.forEach { c ->
            if (c.points.size >= 3) {
                scratchPath.reset()
                scratchPath.moveTo(c.points[0].x, c.points[0].y)
                for (i in 1 until c.points.size) scratchPath.lineTo(c.points[i].x, c.points[i].y)
                scratchPath.close()
                constraintPaint.strokeWidth = 2.5f / scale
                constraintPaint.color = when (c.kind) {
                    ConstraintKind.FORBIDDEN_REGION -> Color.argb(200, 239, 68, 68)
                    ConstraintKind.PROTECTED_SILHOUETTE -> Color.argb(200, 16, 185, 129)
                    else -> Color.argb(180, 245, 158, 11)
                }
                constraintPaint.pathEffect = cachedConstraintDash
                canvas.drawPath(scratchPath, constraintPaint)
            }
        }

        // 9. AI Brush Guidance Marks
        state.document.guidanceMarks.forEach { g ->
            if (g.points.size >= 2) {
                scratchPath.reset()
                scratchPath.moveTo(g.points[0].x, g.points[0].y)
                for (i in 1 until g.points.size) scratchPath.lineTo(g.points[i].x, g.points[i].y)
                guidancePaint.strokeWidth = 4f / scale
                guidancePaint.color = try { Color.parseColor(g.colorHex) } catch (_: Exception) { Color.CYAN }
                guidancePaint.alpha = 180
                guidancePaint.pathEffect = cachedGuidanceDash
                canvas.drawPath(scratchPath, guidancePaint)
            }
        }

        // 10. Live Assisted Shape Preview
        val st = shapePreviewStart
        val en = shapePreviewEnd
        if (state.shapeGuide != ShapeGuide.FREEHAND && st != null && en != null) {
            shapePreviewPaint.color = state.brushColorInt
            shapePreviewPaint.alpha = 190
            shapePreviewPaint.strokeWidth = state.brushSize
            when (state.shapeGuide) {
                ShapeGuide.LINE -> canvas.drawLine(st.x, st.y, en.x, en.y, shapePreviewPaint)
                ShapeGuide.RECT -> canvas.drawRect(min(st.x, en.x), min(st.y, en.y), max(st.x, en.x), max(st.y, en.y), shapePreviewPaint)
                ShapeGuide.ELLIPSE -> canvas.drawOval(min(st.x, en.x), min(st.y, en.y), max(st.x, en.x), max(st.y, en.y), shapePreviewPaint)
                ShapeGuide.FREEHAND -> Unit
            }
        }

        // 11. Active AI Selection Marquee
        if (state.selection.active && state.selection.points.size >= 2) {
            scratchPath.reset()
            val pts = state.selection.points
            scratchPath.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) scratchPath.lineTo(pts[i].x, pts[i].y)
            scratchPath.close()

            canvas.drawPath(scratchPath, marqueeFillPaint)
            val dashLen = 8f / scale
            val phaseIntervals = floatArrayOf(dashLen, dashLen)
            marqueeStrokeWhite.strokeWidth = 2.5f / scale
            marqueeStrokeWhite.pathEffect = DashPathEffect(phaseIntervals, dashPhase / scale)
            canvas.drawPath(scratchPath, marqueeStrokeWhite)

            marqueeStrokeBlue.strokeWidth = 2.5f / scale
            marqueeStrokeBlue.pathEffect = DashPathEffect(phaseIntervals, (dashPhase + 8f) / scale)
            canvas.drawPath(scratchPath, marqueeStrokeBlue)
        }

        // 11.5 Direct Manipulation Gizmos (F01 Transform & F09 Vector Edit)
        if (state.activeTool == StudioTool.TRANSFORM) {
            computeTransformGizmoRect(state, scratchRect)
            gizmoStrokePaint.strokeWidth = 2f / scale
            gizmoStrokePaint.pathEffect = cachedConstraintDash
            canvas.drawRect(scratchRect, gizmoStrokePaint)
            gizmoStrokePaint.pathEffect = null

            // Corner Scale Handles & Top Rotation Handle
            val r = 9f / scale
            gizmoHandleRimPaint.strokeWidth = 2f / scale
            val corners = arrayOf(
                scratchRect.left to scratchRect.top,
                scratchRect.right to scratchRect.top,
                scratchRect.right to scratchRect.bottom,
                scratchRect.left to scratchRect.bottom
            )
            for ((cx, cy) in corners) {
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, gizmoHandleFillPaint)
                canvas.drawRect(cx - r, cy - r, cx + r, cy + r, gizmoHandleRimPaint)
            }
            val rotY = scratchRect.top - 48f / scale
            canvas.drawLine(scratchRect.centerX(), scratchRect.top, scratchRect.centerX(), rotY, gizmoStrokePaint)
            canvas.drawCircle(scratchRect.centerX(), rotY, r * 1.1f, gizmoHandleFillPaint)
            canvas.drawCircle(scratchRect.centerX(), rotY, r * 1.1f, gizmoHandleRimPaint)
        } else if (state.activeTool == StudioTool.VECTOR_EDIT) {
            val hRad = 8f / scale
            gizmoVectorStrokePaint.strokeWidth = 2f / scale
            gizmoHandleRimPaint.strokeWidth = 2f / scale
            state.document.vectorObjects.forEach { obj ->
                if (!obj.visible) return@forEach
                val selected = obj.id == state.selectedVectorId
                gizmoVectorStrokePaint.pathEffect = if (selected) cachedSymDash else null
                gizmoVectorStrokePaint.alpha = if (selected) 235 else 90
                canvas.drawRect(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height, gizmoVectorStrokePaint)
                if (selected) {
                    // Bottom-right resize handle
                    val rx = obj.x + obj.width
                    val ry = obj.y + obj.height
                    canvas.drawRect(rx - hRad, ry - hRad, rx + hRad, ry + hRad, gizmoHandleFillPaint)
                    canvas.drawRect(rx - hRad, ry - hRad, rx + hRad, ry + hRad, gizmoHandleRimPaint)

                    // Balloon tailTip handle
                    obj.tailTip?.let { tip ->
                        canvas.drawLine(obj.x + obj.width * 0.35f, obj.y + obj.height, tip.x, tip.y, gizmoStrokePaint)
                        canvas.drawCircle(tip.x, tip.y, hRad * 1.1f, gizmoStrokePaint)
                        canvas.drawCircle(tip.x, tip.y, hRad * 0.7f, gizmoHandleFillPaint)
                    }

                    // Bezier anchor & tangent handles
                    if (obj.kind == VectorKind.PATH) {
                        obj.nodes.forEach { n ->
                            n.handleIn?.let { hi ->
                                canvas.drawLine(n.anchor.x, n.anchor.y, hi.x, hi.y, gizmoVectorStrokePaint)
                                canvas.drawCircle(hi.x, hi.y, hRad * 0.85f, gizmoHandleRimPaint)
                            }
                            n.handleOut?.let { ho ->
                                canvas.drawLine(n.anchor.x, n.anchor.y, ho.x, ho.y, gizmoVectorStrokePaint)
                                canvas.drawCircle(ho.x, ho.y, hRad * 0.85f, gizmoHandleRimPaint)
                            }
                            canvas.drawCircle(n.anchor.x, n.anchor.y, hRad, gizmoHandleFillPaint)
                            canvas.drawCircle(n.anchor.x, n.anchor.y, hRad, gizmoHandleRimPaint)
                        }
                    }
                }
            }
            gizmoVectorStrokePaint.pathEffect = null
        }

        // Active Snap Grid Crosshairs
        val snapPt = activeSnapPoint
        if (snapPt != null && (state.vectorSnapEnabled || state.transformSession?.snapEnabled == true)) {
            gizmoSnapGuidePaint.strokeWidth = 1.2f / scale
            gizmoSnapGuidePaint.pathEffect = cachedRulerDash
            canvas.drawLine(snapPt.x, b.minY, snapPt.x, b.maxY, gizmoSnapGuidePaint)
            canvas.drawLine(b.minX, snapPt.y, b.maxX, snapPt.y, gizmoSnapGuidePaint)
        }

        // 12. Stylus Hover Cursor Ring
        val cursor = hoverWorldPos
        if (cursor != null && state.activeTool !in listOf(
                StudioTool.LASSO, StudioTool.RECT_SELECT, StudioTool.TRANSFORM,
                StudioTool.VECTOR_EDIT, StudioTool.FILL, StudioTool.EYEDROPPER
            )
        ) {
            hoverRingPaint.color = if (hoverIntent == PointerIntent.STYLUS || hoverIntent == PointerIntent.ERASER) {
                Color.argb(210, 56, 189, 248)
            } else {
                Color.argb(175, 161, 161, 170)
            }
            hoverRingPaint.strokeWidth = 1.5f / scale
            canvas.drawCircle(cursor.x, cursor.y, max(2f, state.brushSize / 2f), hoverRingPaint)
        }

        canvas.restore()
    }
}
