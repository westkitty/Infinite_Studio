package com.example.input

import android.graphics.PointF
import android.os.Build
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.max

enum class PointerIntent {
    STYLUS,
    ERASER,
    TOUCH,
    MOUSE
}

data class StrokeSample(
    val screenX: Float,
    val screenY: Float,
    val worldX: Float,
    val worldY: Float,
    val pressure: Float,
    val tiltRad: Float,
    val orientationRad: Float,
    val eventTimeMs: Long,
    val intent: PointerIntent
)

data class StylusDiagnostics(
    val lastIntent: PointerIntent = PointerIntent.TOUCH,
    val lastPressure: Float = 0f,
    val lastTiltDeg: Float = 0f,
    val historicalSamplesConsumed: Long = 0L,
    val canceledTransactions: Long = 0L
)

/**
 * Native Android MotionEvent router supporting:
 * - Stylus vs Touch vs Mouse vs Eraser classification
 * - Historical batched MotionEvent samples for high-Hz S-Pen precision
 * - Pressure, tilt, orientation, and hover tracking
 * - Android 13+ FLAG_CANCELED & ACTION_CANCEL provisional stroke rollback
 * - Multi-touch pan & pinch-zoom + 2-finger tap Undo / 3-finger tap Redo
 * - S-Pen Palm Guard: resting palm/finger touches are ignored or pan-only while stylus draws
 */
class StylusInputRouter(
    private val onBeginStroke: (StrokeSample) -> Unit,
    private val onMoveStroke: (List<StrokeSample>) -> Unit,
    private val onEndStroke: () -> Unit,
    private val onCancelStroke: () -> Unit,
    private val onHoverMove: (worldPos: PointF?, intent: PointerIntent) -> Unit,
    private val onPanZoom: (scaleDelta: Float, dx: Float, dy: Float) -> Unit,
    private val onTwoFingerTapUndo: () -> Unit,
    private val onThreeFingerTapRedo: () -> Unit
) {
    private data class ActivePointer(
        val id: Int,
        var x: Float,
        var y: Float,
        val intent: PointerIntent
    )

    private val activePointers = LinkedHashMap<Int, ActivePointer>()
    private var activeStylusPointerId: Int? = null
    private var isDrawing = false
    private var isPanning = false
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var pinchStartDist = 0f
    private var gestureStartMs = 0L
    private var maxTouchesInGesture = 0
    private var gestureMoved = false

    var diagnostics = StylusDiagnostics()
        private set

    fun handleMotionEvent(
        event: MotionEvent,
        viewportWidth: Float,
        viewportHeight: Float,
        viewScale: Float,
        viewOffsetX: Float,
        viewOffsetY: Float,
        sPenGuardEnabled: Boolean
    ): Boolean {
        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex

        // Hover events (S-Pen hovering above Tab S9 Ultra screen)
        if (actionMasked == MotionEvent.ACTION_HOVER_MOVE || actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
            val intent = classifyToolType(event.getToolType(0))
            val wp = screenToWorld(event.x, event.y, viewportWidth, viewportHeight, viewScale, viewOffsetX, viewOffsetY)
            diagnostics = diagnostics.copy(
                lastIntent = intent,
                lastTiltDeg = Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT, 0).toDouble()).toFloat()
            )
            onHoverMove(wp, intent)
            return true
        }
        if (actionMasked == MotionEvent.ACTION_HOVER_EXIT) {
            onHoverMove(null, PointerIntent.TOUCH)
            return true
        }

        // Handle ACTION_CANCEL or Android 13+ FLAG_CANCELED (palm rejection / interrupted gesture)
        val isFlagCanceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (event.flags and MotionEvent.FLAG_CANCELED) != 0
        if (actionMasked == MotionEvent.ACTION_CANCEL || (isFlagCanceled && (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_POINTER_UP))) {
            rollbackAll(isCanceled = true)
            return true
        }

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = event.getPointerId(actionIndex)
                val px = event.getX(actionIndex)
                val py = event.getY(actionIndex)
                val intent = classifyToolType(event.getToolType(actionIndex))
                activePointers[pointerId] = ActivePointer(pointerId, px, py, intent)

                if (activePointers.size == 1) {
                    gestureStartMs = event.eventTime
                    maxTouchesInGesture = 1
                    gestureMoved = false
                } else {
                    maxTouchesInGesture = max(maxTouchesInGesture, activePointers.size)
                }

                // If stylus is already drawing and a palm/finger lands, ignore the resting finger!
                if (isDrawing && activeStylusPointerId != null && intent == PointerIntent.TOUCH) {
                    return true
                }

                // If 2+ non-stylus fingers land, switch to pan/zoom and cancel any provisional touch mark
                if (activePointers.size >= 2 && activeStylusPointerId == null) {
                    if (isDrawing) {
                        isDrawing = false
                        onCancelStroke()
                    }
                    isPanning = true
                    val pts = activePointers.values.toList()
                    lastPanX = (pts[0].x + pts[1].x) / 2f
                    lastPanY = (pts[0].y + pts[1].y) / 2f
                    pinchStartDist = hypot((pts[1].x - pts[0].x).toDouble(), (pts[1].y - pts[0].y).toDouble()).toFloat()
                    return true
                }

                // S-Pen Guard: single-finger touch pans while stylus/mouse draws
                if (sPenGuardEnabled && intent == PointerIntent.TOUCH) {
                    isPanning = true
                    lastPanX = px
                    lastPanY = py
                    return true
                }

                // Start single-pointer drawing
                if (activeStylusPointerId == null || intent == PointerIntent.STYLUS || intent == PointerIntent.ERASER) {
                    isDrawing = true
                    activeStylusPointerId = if (intent == PointerIntent.STYLUS || intent == PointerIntent.ERASER) pointerId else null
                    val sample = buildSample(
                        event, actionIndex, -1, viewportWidth, viewportHeight, viewScale, viewOffsetX, viewOffsetY, intent
                    )
                    diagnostics = diagnostics.copy(
                        lastIntent = intent,
                        lastPressure = sample.pressure,
                        lastTiltDeg = Math.toDegrees(sample.tiltRad.toDouble()).toFloat()
                    )
                    onBeginStroke(sample)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val prev = activePointers[pid]
                    val nx = event.getX(i)
                    val ny = event.getY(i)
                    if (prev != null) {
                        if (hypot((nx - prev.x).toDouble(), (ny - prev.y).toDouble()) > 6.0) {
                            gestureMoved = true
                        }
                        prev.x = nx
                        prev.y = ny
                    }
                }

                if (isPanning && activePointers.size >= 2) {
                    val pts = activePointers.values.toList()
                    val midX = (pts[0].x + pts[1].x) / 2f
                    val midY = (pts[0].y + pts[1].y) / 2f
                    val dist = hypot((pts[1].x - pts[0].x).toDouble(), (pts[1].y - pts[0].y).toDouble()).toFloat()
                    val dx = midX - lastPanX
                    val dy = midY - lastPanY
                    val scaleDelta = if (pinchStartDist > 10f && dist > 10f) dist / pinchStartDist else 1f
                    lastPanX = midX
                    lastPanY = midY
                    pinchStartDist = dist
                    onPanZoom(scaleDelta, dx, dy)
                    return true
                }

                if (isPanning && sPenGuardEnabled && activePointers.size == 1) {
                    val pt = activePointers.values.first()
                    if (pt.intent == PointerIntent.TOUCH) {
                        val dx = pt.x - lastPanX
                        val dy = pt.y - lastPanY
                        lastPanX = pt.x
                        lastPanY = pt.y
                        onPanZoom(1f, dx, dy)
                        return true
                    }
                }

                if (isDrawing) {
                    val targetIdx = activeStylusPointerId?.let { event.findPointerIndex(it) }?.takeIf { it >= 0 } ?: 0
                    val intent = classifyToolType(event.getToolType(targetIdx))
                    if (sPenGuardEnabled && intent == PointerIntent.TOUCH) return true

                    val samples = ArrayList<StrokeSample>(event.historySize + 1)
                    for (h in 0 until event.historySize) {
                        samples.add(
                            buildSample(event, targetIdx, h, viewportWidth, viewportHeight, viewScale, viewOffsetX, viewOffsetY, intent)
                        )
                    }
                    samples.add(
                        buildSample(event, targetIdx, -1, viewportWidth, viewportHeight, viewScale, viewOffsetX, viewOffsetY, intent)
                    )
                    val last = samples.last()
                    diagnostics = diagnostics.copy(
                        lastIntent = intent,
                        lastPressure = last.pressure,
                        lastTiltDeg = Math.toDegrees(last.tiltRad.toDouble()).toFloat(),
                        historicalSamplesConsumed = diagnostics.historicalSamplesConsumed + event.historySize
                    )
                    onMoveStroke(samples)
                }
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                activePointers.remove(pointerId)

                if (pointerId == activeStylusPointerId || (activePointers.isEmpty() && isDrawing)) {
                    isDrawing = false
                    activeStylusPointerId = null
                    onEndStroke()
                }

                if (activePointers.isEmpty()) {
                    val elapsed = event.eventTime - gestureStartMs
                    if (!gestureMoved && elapsed < 320L) {
                        if (maxTouchesInGesture == 2) onTwoFingerTapUndo()
                        else if (maxTouchesInGesture == 3) onThreeFingerTapRedo()
                    }
                    isPanning = false
                    pinchStartDist = 0f
                }
            }
        }
        return true
    }

    private fun rollbackAll(isCanceled: Boolean) {
        activePointers.clear()
        activeStylusPointerId = null
        isPanning = false
        pinchStartDist = 0f
        if (isDrawing) {
            isDrawing = false
            if (isCanceled) {
                diagnostics = diagnostics.copy(canceledTransactions = diagnostics.canceledTransactions + 1)
            }
            onCancelStroke()
        }
    }

    private fun buildSample(
        event: MotionEvent,
        pointerIndex: Int,
        historyPos: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        viewScale: Float,
        viewOffsetX: Float,
        viewOffsetY: Float,
        intent: PointerIntent
    ): StrokeSample {
        val sx = if (historyPos >= 0) event.getHistoricalX(pointerIndex, historyPos) else event.getX(pointerIndex)
        val sy = if (historyPos >= 0) event.getHistoricalY(pointerIndex, historyPos) else event.getY(pointerIndex)
        val rawP = if (historyPos >= 0) event.getHistoricalPressure(pointerIndex, historyPos) else event.getPressure(pointerIndex)
        val tilt = if (historyPos >= 0) {
            event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointerIndex, historyPos)
        } else {
            event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
        }
        val orient = if (historyPos >= 0) {
            event.getHistoricalOrientation(pointerIndex, historyPos)
        } else {
            event.getOrientation(pointerIndex)
        }
        val timeMs = if (historyPos >= 0) event.getHistoricalEventTime(historyPos) else event.eventTime
        val wp = screenToWorld(sx, sy, viewportWidth, viewportHeight, viewScale, viewOffsetX, viewOffsetY)
        val effectivePressure = when (intent) {
            PointerIntent.STYLUS, PointerIntent.ERASER -> rawP.coerceIn(0.05f, 1.0f)
            PointerIntent.TOUCH, PointerIntent.MOUSE -> 0.65f
        }
        return StrokeSample(
            screenX = sx,
            screenY = sy,
            worldX = wp.x,
            worldY = wp.y,
            pressure = effectivePressure,
            tiltRad = tilt,
            orientationRad = orient,
            eventTimeMs = timeMs,
            intent = intent
        )
    }

    companion object {
        fun classifyToolType(toolType: Int): PointerIntent = when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS -> PointerIntent.STYLUS
            MotionEvent.TOOL_TYPE_ERASER -> PointerIntent.ERASER
            MotionEvent.TOOL_TYPE_MOUSE -> PointerIntent.MOUSE
            else -> PointerIntent.TOUCH
        }

        fun screenToWorld(
            sx: Float,
            sy: Float,
            viewportWidth: Float,
            viewportHeight: Float,
            viewScale: Float,
            viewOffsetX: Float,
            viewOffsetY: Float
        ): PointF {
            val vw = max(1f, viewportWidth)
            val vh = max(1f, viewportHeight)
            return PointF(
                (sx - vw / 2f - viewOffsetX) / viewScale,
                (sy - vh / 2f - viewOffsetY) / viewScale
            )
        }
    }
}
