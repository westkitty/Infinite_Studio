package com.example.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.example.document.CanonicalDocument
import com.example.document.CustomBrushPreset
import com.example.document.DEFAULT_TILE_SIZE
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentBounds
import com.example.document.DocumentLayer
import com.example.document.MAX_DOCUMENT_SIDE
import com.example.document.SelectionMaskRecord
import com.example.document.TileKey
import com.example.document.Vec2
import com.example.model.PaperStyle
import com.example.model.ShapeGuide
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tile-native brush, shape, fill, and color-sampling engine for CanonicalDocument.
 * Mutates only touched 256x256 sparse PNG tiles, keeping untouched tiles structurally shared.
 */
object TileBrushEngine {

    data class StrokeRenderParams(
        val tool: StudioTool,
        val colorInt: Int,
        val preset: CustomBrushPreset,
        val symmetryMode: SymmetryMode,
        val activeMask: SelectionMaskRecord?
    )

    fun defaultPresetForTool(tool: StudioTool, brushSize: Float = 12f, opacity: Float = 1f): CustomBrushPreset {
        return CustomBrushPreset(
            id = "preset_${tool.name.lowercase()}",
            name = tool.label,
            baseKind = tool.name,
            radius = brushSize,
            flow = when (tool) {
                StudioTool.WATERCOLOR -> 0.45f
                StudioTool.AIRBRUSH -> 0.55f
                StudioTool.MARKER -> 0.65f
                else -> 1.0f
            },
            opacity = opacity,
            hardness = when (tool) {
                StudioTool.INK -> 0.98f
                StudioTool.WATERCOLOR, StudioTool.AIRBRUSH -> 0.25f
                else -> 0.8f
            },
            spacing = when (tool) {
                StudioTool.WATERCOLOR -> 0.35f
                else -> 0.22f
            },
            smoothing = 0.55f,
            pressureCurve = if (tool == StudioTool.INK) 1.3f else 1.0f,
            angleDeg = 0f,
            textureStrength = if (tool == StudioTool.PENCIL) 0.45f else 0.1f
        )
    }

    /**
     * Expands DocumentBounds in multiples of tileSize so existing sparse PNG tiles keep their exact
     * pixel contents by simply shifting their TileKey coordinates—zero bitmap decoding needed!
     */
    fun expandBoundsIfNeeded(
        doc: CanonicalDocument,
        worldX: Float,
        worldY: Float,
        margin: Float = 260f
    ): CanonicalDocument {
        val tileSize = doc.tileSize
        val padTiles = 3 // 768 px per expansion step
        val b = doc.bounds
        var leftTiles = 0
        var topTiles = 0
        var rightTiles = 0
        var bottomTiles = 0

        if (worldX - margin < b.minX) {
            leftTiles = max(padTiles, ceil(((b.minX - (worldX - margin)) / tileSize)).toInt())
        }
        if (worldY - margin < b.minY) {
            topTiles = max(padTiles, ceil(((b.minY - (worldY - margin)) / tileSize)).toInt())
        }
        if (worldX + margin > b.maxX) {
            rightTiles = max(padTiles, ceil((((worldX + margin) - b.maxX) / tileSize)).toInt())
        }
        if (worldY + margin > b.maxY) {
            bottomTiles = max(padTiles, ceil((((worldY + margin) - b.maxY) / tileSize)).toInt())
        }

        if (leftTiles == 0 && topTiles == 0 && rightTiles == 0 && bottomTiles == 0) {
            return doc
        }

        val newWidth = b.width + (leftTiles + rightTiles) * tileSize
        val newHeight = b.height + (topTiles + bottomTiles) * tileSize
        if (newWidth > MAX_DOCUMENT_SIDE || newHeight > MAX_DOCUMENT_SIDE) {
            return doc
        }

        val nextBounds = b.copy(
            minX = b.minX - leftTiles * tileSize,
            minY = b.minY - topTiles * tileSize,
            width = newWidth,
            height = newHeight
        )

        val shiftedLayers = if (leftTiles == 0 && topTiles == 0) {
            doc.layers
        } else {
            doc.layers.map { layer ->
                val newTiles = LinkedHashMap<String, ByteArray>(layer.tiles.size)
                val newHashes = LinkedHashMap<String, String>(layer.tileHashes.size)
                layer.tiles.forEach { (kStr, bytes) ->
                    val tk = TileKey.parse(kStr)
                    if (tk != null) {
                        val nk = TileKey(tk.tx + leftTiles, tk.ty + topTiles).storageKey()
                        newTiles[nk] = bytes
                        layer.tileHashes[kStr]?.let { newHashes[nk] = it }
                    }
                }
                layer.copy(tiles = newTiles, tileHashes = newHashes)
            }
        }

        return doc.copy(bounds = nextBounds, layers = shiftedLayers)
    }

    /**
     * Draws a complete stroke path onto [targetLayerId] using only dirty overlapping tiles.
     */
    fun commitStrokeToDocument(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        targetLayerId: String,
        worldPoints: List<Pair<Vec2, Float>>, // (point, pressure)
        params: StrokeRenderParams
    ): CanonicalDocument {
        if (worldPoints.isEmpty()) return doc
        var workDoc = doc
        worldPoints.forEach { (pt, _) ->
            workDoc = expandBoundsIfNeeded(workDoc, pt.x, pt.y, params.preset.radius * 2f + 160f)
        }
        val layer = workDoc.layers.find { it.id == targetLayerId } ?: return workDoc

        val dirtyRect = computeDirtyRectForPoints(
            points = worldPoints.map { it.first },
            margin = params.preset.radius * 2.5f + 24f,
            symmetryMode = params.symmetryMode
        )

        // Note: For SMUDGE we materialize the affected region first so neighbor sampling works across tile seams
        val updatedLayer = if (params.tool == StudioTool.SMUDGE) {
            applySmudgeStrokeToLayer(workDoc, renderer, layer, worldPoints, params)
        } else {
            renderer.applyRegionMutationToLayer(workDoc, layer, dirtyRect) { tileCanvas, tileBounds ->
                renderStrokeOnCanvas(
                    canvas = tileCanvas,
                    canvasBounds = tileBounds,
                    worldPoints = worldPoints,
                    params = params,
                    alphaLock = layer.alphaLock
                )
            }
        }

        return workDoc.copy(
            layers = workDoc.layers.map { if (it.id == targetLayerId) updatedLayer else it }
        )
    }

    /**
     * Draws a stroke into an arbitrary Canvas (used both for dirty sparse tiles AND provisional buffer rendering).
     */
    fun renderStrokeOnCanvas(
        canvas: Canvas,
        canvasBounds: DocumentBounds,
        worldPoints: List<Pair<Vec2, Float>>,
        params: StrokeRenderParams,
        alphaLock: Boolean
    ) {
        if (worldPoints.isEmpty()) return
        canvas.save()
        buildMaskPath(canvasBounds, params.activeMask)?.let { canvas.clipPath(it) }

        val alphaLockXfer = if (alphaLock) PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) else null
        val clearXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val matrices = symmetryMatrices(canvasBounds, params.symmetryMode)

        val r = Color.red(params.colorInt)
        val g = Color.green(params.colorInt)
        val b = Color.blue(params.colorInt)
        val preset = params.preset
        val effectiveOpacity = (preset.opacity * preset.flow).coerceIn(0.02f, 1.0f)

        for (m in matrices) {
            canvas.save()
            canvas.concat(m)

            if (worldPoints.size == 1) {
                val (wp, p) = worldPoints[0]
                val lp = worldToLocal(canvasBounds, wp.x, wp.y)
                drawSingleDab(canvas, lp.x, lp.y, p, params.tool, preset, effectiveOpacity, r, g, b, alphaLockXfer, clearXfer)
            } else {
                var prev = worldPoints[0].first
                var lastMid = prev
                for (i in 1 until worldPoints.size) {
                    val (curr, rawPressure) = worldPoints[i]
                    val pressure = rawPressure.coerceIn(0.05f, 1.0f).pow(preset.pressureCurve)
                    val mid = Vec2((prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                    val pPrev = worldToLocal(canvasBounds, prev.x, prev.y)
                    val pMid1 = worldToLocal(canvasBounds, lastMid.x, lastMid.y)
                    val pMid2 = worldToLocal(canvasBounds, mid.x, mid.y)
                    drawSegment(
                        canvas = canvas,
                        p1 = pPrev,
                        mid1 = pMid1,
                        mid2 = pMid2,
                        pressure = pressure,
                        tool = params.tool,
                        preset = preset,
                        effectiveOpacity = effectiveOpacity,
                        r = r, g = g, b = b,
                        alphaLockXfer = alphaLockXfer,
                        clearXfer = clearXfer
                    )
                    prev = curr
                    lastMid = mid
                }
                // Finish final tail to exact end point
                val (lastPt, lastP) = worldPoints.last()
                val pLastMid = worldToLocal(canvasBounds, lastMid.x, lastMid.y)
                val pEnd = worldToLocal(canvasBounds, lastPt.x, lastPt.y)
                drawSegment(
                    canvas = canvas,
                    p1 = pEnd,
                    mid1 = pLastMid,
                    mid2 = pEnd,
                    pressure = lastP.coerceIn(0.05f, 1.0f).pow(preset.pressureCurve),
                    tool = params.tool,
                    preset = preset,
                    effectiveOpacity = effectiveOpacity,
                    r = r, g = g, b = b,
                    alphaLockXfer = alphaLockXfer,
                    clearXfer = clearXfer
                )
            }

            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawSingleDab(
        canvas: Canvas,
        lx: Float,
        ly: Float,
        pressure: Float,
        tool: StudioTool,
        preset: CustomBrushPreset,
        effectiveOpacity: Float,
        r: Int,
        g: Int,
        b: Int,
        alphaLockXfer: PorterDuffXfermode?,
        clearXfer: PorterDuffXfermode
    ) {
        val radius = max(0.8f, preset.radius * (0.35f + pressure * 0.65f) / 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            if (tool == StudioTool.ERASER) {
                color = Color.argb((effectiveOpacity * 255f).roundToInt().coerceIn(0, 255), 0, 0, 0)
                xfermode = clearXfer
            } else {
                color = Color.argb((effectiveOpacity * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                xfermode = alphaLockXfer
            }
        }
        canvas.drawCircle(lx, ly, radius, paint)
    }

    private fun drawSegment(
        canvas: Canvas,
        p1: PointF,
        mid1: PointF,
        mid2: PointF,
        pressure: Float,
        tool: StudioTool,
        preset: CustomBrushPreset,
        effectiveOpacity: Float,
        r: Int,
        g: Int,
        b: Int,
        alphaLockXfer: PorterDuffXfermode?,
        clearXfer: PorterDuffXfermode
    ) {
        val path = Path().apply {
            moveTo(mid1.x, mid1.y)
            quadTo(p1.x, p1.y, mid2.x, mid2.y)
        }

        when (tool) {
            StudioTool.PENCIL -> {
                val w = max(0.8f, preset.radius * (0.3f + pressure * 0.85f))
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = w
                    color = Color.argb((effectiveOpacity * (0.35f + pressure * 0.65f) * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                    xfermode = alphaLockXfer
                }
                canvas.drawPath(path, strokePaint)
                if (preset.textureStrength > 0.1f && pressure > 0.25f) {
                    val grainPaint = Paint().apply {
                        style = Paint.Style.FILL
                        color = Color.argb((effectiveOpacity * preset.textureStrength * 0.5f * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                        xfermode = alphaLockXfer
                    }
                    val rng = Random((mid2.x * 31f + mid2.y * 17f).roundToInt())
                    repeat(3) {
                        val rx = mid2.x + (rng.nextFloat() - 0.5f) * w * 1.15f
                        val ry = mid2.y + (rng.nextFloat() - 0.5f) * w * 1.15f
                        canvas.drawRect(rx, ry, rx + 1.4f, ry + 1.4f, grainPaint)
                    }
                }
            }

            StudioTool.INK -> {
                val w = max(0.6f, preset.radius * pressure * 1.35f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = w
                    color = Color.argb((preset.opacity * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                    xfermode = alphaLockXfer
                }
                canvas.drawPath(path, paint)
            }

            StudioTool.MARKER -> {
                val w = preset.radius * (0.85f + pressure * 0.25f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.SQUARE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = w
                    color = Color.argb((effectiveOpacity * 0.28f * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                    xfermode = alphaLockXfer
                }
                canvas.drawPath(path, paint)
            }

            StudioTool.WATERCOLOR -> {
                val radius = max(2f, (preset.radius * (0.5f + pressure * 0.8f)) / 2f)
                val dist = hypot((mid2.x - mid1.x).toDouble(), (mid2.y - mid1.y).toDouble()).toFloat()
                val steps = max(1, ceil(dist / max(1f, radius * preset.spacing)).toInt())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    xfermode = alphaLockXfer
                }
                val cInner = Color.argb((effectiveOpacity * 0.22f * pressure * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                val cMid = Color.argb((effectiveOpacity * 0.11f * pressure * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                val cEdge = Color.argb(0, r, g, b)
                for (i in 0..steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val x = mid1.x + (mid2.x - mid1.x) * t
                    val y = mid1.y + (mid2.y - mid1.y) * t
                    paint.shader = RadialGradient(
                        x, y, radius,
                        intArrayOf(cInner, cMid, cEdge),
                        floatArrayOf(0.1f, max(0.3f, preset.hardness), 1.0f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawCircle(x, y, radius, paint)
                }
            }

            StudioTool.AIRBRUSH -> {
                val radius = max(3f, preset.radius * (0.6f + pressure * 0.6f))
                val cInner = Color.argb((effectiveOpacity * 0.35f * pressure * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                val cEdge = Color.argb(0, r, g, b)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = RadialGradient(
                        mid2.x, mid2.y, radius,
                        intArrayOf(cInner, cEdge),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    xfermode = alphaLockXfer
                }
                canvas.drawCircle(mid2.x, mid2.y, radius, paint)
            }

            StudioTool.ERASER -> {
                val w = max(2f, preset.radius * (0.4f + pressure * 0.8f))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = w
                    color = Color.argb((preset.opacity * 255f).roundToInt().coerceIn(0, 255), 0, 0, 0)
                    xfermode = clearXfer
                }
                canvas.drawPath(path, paint)
            }

            else -> {
                val w = max(1f, preset.radius * (0.4f + pressure * 0.7f))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = w
                    color = Color.argb((effectiveOpacity * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                    xfermode = alphaLockXfer
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun applySmudgeStrokeToLayer(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        layer: DocumentLayer,
        worldPoints: List<Pair<Vec2, Float>>,
        params: StrokeRenderParams
    ): DocumentLayer {
        val fullBmp = renderer.materializeLayerBitmap(doc, layer)
        val canvas = Canvas(fullBmp)
        canvas.save()
        buildMaskPath(doc.bounds, params.activeMask)?.let { canvas.clipPath(it) }
        val radius = max(4f, params.preset.radius * 0.75f)
        for (i in 1 until worldPoints.size) {
            val prev = worldToLocal(doc.bounds, worldPoints[i - 1].first.x, worldPoints[i - 1].first.y)
            val curr = worldToLocal(doc.bounds, worldPoints[i].first.x, worldPoints[i].first.y)
            val p = worldPoints[i].second
            val sx = max(0f, min(fullBmp.width - radius * 2f, prev.x - radius)).roundToInt()
            val sy = max(0f, min(fullBmp.height - radius * 2f, prev.y - radius)).roundToInt()
            val sampleSize = max(1, (radius * 2f).roundToInt())
            if (sx + sampleSize <= fullBmp.width && sy + sampleSize <= fullBmp.height) {
                val srcRect = Rect(sx, sy, sx + sampleSize, sy + sampleSize)
                val dstRect = RectF(curr.x - radius, curr.y - radius, curr.x + radius, curr.y + radius)
                val smudgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (min(0.65f, params.preset.opacity * 0.55f * p) * 255f).roundToInt().coerceIn(0, 255)
                    xfermode = if (layer.alphaLock) PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) else null
                }
                canvas.save()
                canvas.clipPath(Path().apply { addCircle(curr.x, curr.y, radius, Path.Direction.CW) })
                canvas.drawBitmap(fullBmp, srcRect, dstRect, smudgePaint)
                canvas.restore()
            }
        }
        canvas.restore()
        val updatedTiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(fullBmp, doc.tileSize)
        fullBmp.recycle()
        return layer.copy(
            tiles = updatedTiles,
            tileHashes = updatedTiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )
    }

    fun commitAssistedShape(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        targetLayerId: String,
        shapeGuide: ShapeGuide,
        isEraser: Boolean,
        colorInt: Int,
        brushSize: Float,
        brushOpacity: Float,
        symmetryMode: SymmetryMode,
        activeMask: SelectionMaskRecord?,
        wStart: Vec2,
        wEnd: Vec2
    ): CanonicalDocument {
        if (shapeGuide == ShapeGuide.FREEHAND) return doc
        var workDoc = expandBoundsIfNeeded(doc, wStart.x, wStart.y, brushSize + 180f)
        workDoc = expandBoundsIfNeeded(workDoc, wEnd.x, wEnd.y, brushSize + 180f)
        val layer = workDoc.layers.find { it.id == targetLayerId } ?: return workDoc

        val dirty = computeDirtyRectForPoints(listOf(wStart, wEnd), brushSize * 2f + 24f, symmetryMode)
        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)

        val updated = renderer.applyRegionMutationToLayer(workDoc, layer, dirty) { canvas, bounds ->
            val start = worldToLocal(bounds, wStart.x, wStart.y)
            val end = worldToLocal(bounds, wEnd.x, wEnd.y)
            canvas.save()
            buildMaskPath(bounds, activeMask)?.let { canvas.clipPath(it) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = brushSize
                color = Color.argb((brushOpacity * 255f).roundToInt().coerceIn(0, 255), r, g, b)
                xfermode = when {
                    isEraser -> PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    layer.alphaLock -> PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    else -> null
                }
            }
            for (m in symmetryMatrices(bounds, symmetryMode)) {
                canvas.save()
                canvas.concat(m)
                when (shapeGuide) {
                    ShapeGuide.LINE -> canvas.drawLine(start.x, start.y, end.x, end.y, paint)
                    ShapeGuide.RECT -> canvas.drawRect(
                        min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y), paint
                    )
                    ShapeGuide.ELLIPSE -> canvas.drawOval(
                        RectF(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y)), paint
                    )
                    ShapeGuide.FREEHAND -> Unit
                }
                canvas.restore()
            }
            canvas.restore()
        }
        return workDoc.copy(layers = workDoc.layers.map { if (it.id == targetLayerId) updated else it })
    }

    /**
     * Scanline flood fill operating across layer tiles.
     */
    fun floodFillOnLayer(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        targetLayerId: String,
        worldX: Float,
        worldY: Float,
        fillColorInt: Int,
        opacity: Float
    ): Pair<CanonicalDocument, String?> {
        val layer = doc.layers.find { it.id == targetLayerId } ?: return doc to "No active layer"
        val lx = floor(worldX - doc.bounds.minX).toInt()
        val ly = floor(worldY - doc.bounds.minY).toInt()
        if (lx < 0 || lx >= doc.bounds.width || ly < 0 || ly >= doc.bounds.height) {
            return doc to "Tap inside the canvas bounds to fill"
        }

        val bmp = renderer.materializeLayerBitmap(doc, layer)
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val fillA = (opacity * 255f).roundToInt().coerceIn(0, 255)
        val fillR = Color.red(fillColorInt)
        val fillG = Color.green(fillColorInt)
        val fillB = Color.blue(fillColorInt)
        val fillArgb = Color.argb(fillA, fillR, fillG, fillB)

        val startPixel = pixels[ly * w + lx]
        val targetA = Color.alpha(startPixel)
        val targetR = Color.red(startPixel)
        val targetG = Color.green(startPixel)
        val targetB = Color.blue(startPixel)

        if (targetA == 0) {
            bmp.recycle()
            return doc to "Draw a closed outline first before filling"
        }
        if (targetR == fillR && targetG == fillG && targetB == fillB && abs(targetA - fillA) < 5) {
            bmp.recycle()
            return doc to null
        }

        val tolerance = 32
        fun matches(idx: Int): Boolean {
            val px = pixels[idx]
            return abs(Color.red(px) - targetR) <= tolerance &&
                abs(Color.green(px) - targetG) <= tolerance &&
                abs(Color.blue(px) - targetB) <= tolerance &&
                abs(Color.alpha(px) - targetA) <= tolerance
        }

        val visited = BooleanArray(w * h)
        val stack = ArrayDeque<Int>(1024)
        stack.addLast(ly * w + lx)
        while (stack.isNotEmpty()) {
            val pos = stack.removeLast()
            val cy = pos / w
            var x = pos % w
            while (x >= 0 && !visited[cy * w + x] && matches(cy * w + x)) x--
            x++
            var spanUp = false
            var spanDown = false
            while (x < w && !visited[cy * w + x] && matches(cy * w + x)) {
                val pIdx = cy * w + x
                visited[pIdx] = true
                pixels[pIdx] = fillArgb
                if (cy > 0) {
                    val upIdx = (cy - 1) * w + x
                    val upMatch = !visited[upIdx] && matches(upIdx)
                    if (!spanUp && upMatch) { stack.addLast(upIdx); spanUp = true }
                    else if (spanUp && !upMatch) spanUp = false
                }
                if (cy < h - 1) {
                    val downIdx = (cy + 1) * w + x
                    val downMatch = !visited[downIdx] && matches(downIdx)
                    if (!spanDown && downMatch) { stack.addLast(downIdx); spanDown = true }
                    else if (spanDown && !downMatch) spanDown = false
                }
                x++
            }
        }

        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        val updatedTiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(bmp, doc.tileSize)
        bmp.recycle()
        val updatedLayer = layer.copy(
            tiles = updatedTiles,
            tileHashes = updatedTiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )
        return doc.copy(layers = doc.layers.map { if (it.id == targetLayerId) updatedLayer else it }) to null
    }

    fun sampleColorAt(doc: CanonicalDocument, renderer: SparseTileRenderer, worldX: Float, worldY: Float): Int {
        val region = RectF(floor(worldX), floor(worldY), floor(worldX) + 1f, floor(worldY) + 1f)
        val single = renderer.renderFlattenedRegion(doc, region, includePaper = true, includeVectors = true)
        val sampled = single.getPixel(0, 0)
        single.recycle()
        return sampled or (0xFF shl 24)
    }

    fun bakeLayerTransformAndClearMatrix(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        layerId: String,
        configureMatrix: Matrix.(pivotX: Float, pivotY: Float) -> Unit
    ): CanonicalDocument {
        val layer = doc.layers.find { it.id == layerId } ?: return doc
        val srcBmp = renderer.materializeLayerBitmap(doc, layer)
        val dstBmp = Bitmap.createBitmap(doc.bounds.width, doc.bounds.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(dstBmp)
        val pivotX = doc.bounds.width / 2f
        val pivotY = doc.bounds.height / 2f
        val m = Matrix().apply { configureMatrix(pivotX, pivotY) }
        c.drawBitmap(srcBmp, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        srcBmp.recycle()
        val tiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(dstBmp, doc.tileSize)
        dstBmp.recycle()
        val updatedLayer = layer.copy(
            tiles = tiles,
            tileHashes = tiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )
        return doc.copy(layers = doc.layers.map { if (it.id == layerId) updatedLayer else it })
    }

    fun worldToLocal(bounds: DocumentBounds, wx: Float, wy: Float): PointF =
        PointF(wx - bounds.minX, wy - bounds.minY)

    fun buildMaskPath(bounds: DocumentBounds, mask: SelectionMaskRecord?): Path? {
        if (mask == null || mask.points.size < 3) return null
        val path = Path()
        val p0 = worldToLocal(bounds, mask.points[0].x, mask.points[0].y)
        path.moveTo(p0.x, p0.y)
        for (i in 1 until mask.points.size) {
            val pi = worldToLocal(bounds, mask.points[i].x, mask.points[i].y)
            path.lineTo(pi.x, pi.y)
        }
        path.close()
        return path
    }

    fun symmetryMatrices(bounds: DocumentBounds, mode: SymmetryMode): List<Matrix> {
        val originLocal = worldToLocal(bounds, 0f, 0f)
        val list = mutableListOf(Matrix())
        when (mode) {
            SymmetryMode.NONE -> Unit
            SymmetryMode.VERTICAL -> list.add(Matrix().apply { setScale(-1f, 1f, originLocal.x, originLocal.y) })
            SymmetryMode.HORIZONTAL -> list.add(Matrix().apply { setScale(1f, -1f, originLocal.x, originLocal.y) })
            SymmetryMode.QUADRANT -> {
                list.add(Matrix().apply { setScale(-1f, 1f, originLocal.x, originLocal.y) })
                list.add(Matrix().apply { setScale(1f, -1f, originLocal.x, originLocal.y) })
                list.add(Matrix().apply { setScale(-1f, -1f, originLocal.x, originLocal.y) })
            }
            SymmetryMode.KALEIDOSCOPE -> {
                for (k in 1 until 8) {
                    list.add(Matrix().apply { setRotate(k * 45f, originLocal.x, originLocal.y) })
                }
            }
        }
        return list
    }

    fun computeDirtyRectForPoints(points: List<Vec2>, margin: Float, symmetryMode: SymmetryMode): RectF {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        points.forEach { pt ->
            val candidates = expandPointForSymmetry(pt, symmetryMode)
            candidates.forEach { c ->
                if (c.x < minX) minX = c.x
                if (c.y < minY) minY = c.y
                if (c.x > maxX) maxX = c.x
                if (c.y > maxY) maxY = c.y
            }
        }
        if (!minX.isFinite()) return RectF(-margin, -margin, margin, margin)
        return RectF(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }

    private fun expandPointForSymmetry(p: Vec2, mode: SymmetryMode): List<Vec2> {
        return when (mode) {
            SymmetryMode.NONE -> listOf(p)
            SymmetryMode.VERTICAL -> listOf(p, Vec2(-p.x, p.y))
            SymmetryMode.HORIZONTAL -> listOf(p, Vec2(p.x, -p.y))
            SymmetryMode.QUADRANT -> listOf(p, Vec2(-p.x, p.y), Vec2(p.x, -p.y), Vec2(-p.x, -p.y))
            SymmetryMode.KALEIDOSCOPE -> {
                (0 until 8).map { k ->
                    val rad = Math.toRadians(k * 45.0)
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    Vec2(p.x * cosA - p.y * sinA, p.x * sinA + p.y * cosA)
                }
            }
        }
    }

    fun bitmapToPngBytes(bmp: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }
}
