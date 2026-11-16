package com.example.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import com.example.document.CanonicalDocument
import com.example.document.DEFAULT_TILE_SIZE
import com.example.document.DocumentBounds
import com.example.document.DocumentLayer
import com.example.document.TileKey
import com.example.document.VectorKind
import com.example.document.VectorObject
import com.example.model.LayerBlendMode
import com.example.model.PaperStyle
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class TileCacheStats(
    val cachedTiles: Int,
    val allocatedBytes: Long,
    val maxBytesBudget: Long,
    val hits: Long,
    val misses: Long,
    val lastVisibleTilesRendered: Int,
    val lastFrameDrawMicros: Long = 0L,
    val emaFrameDrawMicros: Long = 0L
) {
    val hitRatioPercent: Int
        get() {
            val total = hits + misses
            return if (total <= 0L) 100 else ((hits * 100L) / total).toInt().coerceIn(0, 100)
        }
}

/**
 * Sparse 256x256 tile renderer with:
 * - Lazy tile allocation (blank tiles are never allocated)
 * - Viewport culling (only tiles intersecting visible world rect are decoded/drawn)
 * - Finite LRU Bitmap cache budget (default 48 MB)
 * - Deterministic compositing of layers, blend modes, opacity, transforms, vector/text, and onion skins
 */
class SparseTileRenderer(
    maxCacheBytes: Int = 48 * 1024 * 1024
) {
    private var cacheHits = 0L
    private var cacheMisses = 0L
    private var lastVisibleTiles = 0
    private var lastFrameDrawMicros = 0L
    private var emaFrameDrawMicros = 0L

    private val tileCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun stats(): TileCacheStats = TileCacheStats(
        cachedTiles = tileCache.snapshot().size,
        allocatedBytes = tileCache.size().toLong(),
        maxBytesBudget = tileCache.maxSize().toLong(),
        hits = cacheHits,
        misses = cacheMisses,
        lastVisibleTilesRendered = lastVisibleTiles,
        lastFrameDrawMicros = lastFrameDrawMicros,
        emaFrameDrawMicros = emaFrameDrawMicros
    )

    fun clearCache() {
        tileCache.evictAll()
    }

    fun getDecodedTile(layerId: String, tileKey: String, pngBytes: ByteArray, contentHash: String?): Bitmap {
        val cacheId = "$layerId:$tileKey:${contentHash ?: pngBytes.size}"
        val cached = tileCache.get(cacheId)
        if (cached != null && !cached.isRecycled) {
            cacheHits++
            return cached
        }
        cacheMisses++
        val decoded = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            ?: Bitmap.createBitmap(DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE, Bitmap.Config.ARGB_8888)
        tileCache.put(cacheId, decoded)
        return decoded
    }

    fun visibleTileKeysForWorldRect(bounds: DocumentBounds, tileSize: Int, visibleWorldRect: RectF): List<TileKey> {
        val localLeft = max(0f, visibleWorldRect.left - bounds.minX)
        val localTop = max(0f, visibleWorldRect.top - bounds.minY)
        val localRight = min(bounds.width.toFloat(), visibleWorldRect.right - bounds.minX)
        val localBottom = min(bounds.height.toFloat(), visibleWorldRect.bottom - bounds.minY)
        if (localRight <= localLeft || localBottom <= localTop) return emptyList()

        val txMin = floor(localLeft / tileSize).toInt().coerceAtLeast(0)
        val tyMin = floor(localTop / tileSize).toInt().coerceAtLeast(0)
        val txMax = floor((localRight - 0.001f) / tileSize).toInt().coerceAtLeast(txMin)
        val tyMax = floor((localBottom - 0.001f) / tileSize).toInt().coerceAtLeast(tyMin)

        val keys = ArrayList<TileKey>()
        for (ty in tyMin..tyMax) {
            for (tx in txMin..txMax) {
                keys.add(TileKey(tx, ty))
            }
        }
        return keys
    }

    fun renderDocumentViewport(
        nativeCanvas: Canvas,
        doc: CanonicalDocument,
        viewportWidth: Float,
        viewportHeight: Float,
        viewScale: Float,
        viewOffsetX: Float,
        viewOffsetY: Float,
        provisionalBitmap: Bitmap? = null,
        provisionalOriginWorldX: Float = 0f,
        provisionalOriginWorldY: Float = 0f
    ) {
        val startNs = System.nanoTime()
        val worldLeft = (-viewportWidth / 2f - viewOffsetX) / viewScale
        val worldTop = (-viewportHeight / 2f - viewOffsetY) / viewScale
        val worldRight = (viewportWidth / 2f - viewOffsetX) / viewScale
        val worldBottom = (viewportHeight / 2f - viewOffsetY) / viewScale
        val visibleWorld = RectF(worldLeft, worldTop, worldRight, worldBottom)

        var renderedTiles = 0
        val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Optional onion skin ghosts (previous and next timeline frames)
        val timeline = doc.timeline
        if (timeline.onionSkinEnabled && timeline.currentFrameIndex > 0 && timeline.frames.size > 1) {
            val prevFrame = timeline.frames[timeline.currentFrameIndex - 1]
            val ghostFactor = timeline.onionSkinBeforeOpacity.coerceIn(0.05f, 0.7f)
            prevFrame.layerKeyframes.forEach { kf ->
                val layer = doc.layers.find { it.id == kf.layerId } ?: return@forEach
                if (!kf.visible) return@forEach
                val ghostLayer = layer.copy(
                    opacity = (kf.opacity * ghostFactor).coerceIn(0.05f, 0.6f),
                    transform = layer.transform.copy(offsetX = kf.offsetX, offsetY = kf.offsetY)
                )
                renderedTiles += drawLayerTiles(nativeCanvas, doc.bounds, doc.tileSize, ghostLayer, visibleWorld, tilePaint)
            }
        }
        if (timeline.onionSkinAfterEnabled && timeline.currentFrameIndex + 1 in timeline.frames.indices) {
            val nextFrame = timeline.frames[timeline.currentFrameIndex + 1]
            val afterFactor = timeline.onionSkinAfterOpacity.coerceIn(0.05f, 0.7f)
            nextFrame.layerKeyframes.forEach { kf ->
                val layer = doc.layers.find { it.id == kf.layerId } ?: return@forEach
                if (!kf.visible) return@forEach
                val ghostLayer = layer.copy(
                    opacity = (kf.opacity * afterFactor).coerceIn(0.05f, 0.55f),
                    transform = layer.transform.copy(offsetX = kf.offsetX, offsetY = kf.offsetY)
                )
                renderedTiles += drawLayerTiles(nativeCanvas, doc.bounds, doc.tileSize, ghostLayer, visibleWorld, tilePaint)
            }
        }

        // Main layer stack
        doc.layers.forEach { layer ->
            if (!layer.visible) return@forEach
            renderedTiles += drawLayerTiles(nativeCanvas, doc.bounds, doc.tileSize, layer, visibleWorld, tilePaint)

            // Draw low-latency provisional stroke buffer right above the active layer
            if (provisionalBitmap != null && layer.id == doc.activeLayerId) {
                nativeCanvas.drawBitmap(provisionalBitmap, provisionalOriginWorldX, provisionalOriginWorldY, null)
            }
        }

        // Vector & Typography Objects
        doc.vectorObjects.forEach { vec ->
            if (vec.visible) drawVectorObject(nativeCanvas, vec)
        }

        lastVisibleTiles = renderedTiles
        val elapsedMicros = max(1L, (System.nanoTime() - startNs) / 1000L)
        lastFrameDrawMicros = elapsedMicros
        emaFrameDrawMicros = if (emaFrameDrawMicros == 0L) elapsedMicros else ((emaFrameDrawMicros * 7L + elapsedMicros * 3L) / 10L)
    }

    private fun drawLayerTiles(
        canvas: Canvas,
        bounds: DocumentBounds,
        tileSize: Int,
        layer: DocumentLayer,
        visibleWorld: RectF,
        tilePaint: Paint
    ): Int {
        if (layer.tiles.isEmpty()) return 0
        val layerPaint = Paint(tilePaint).apply {
            alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            xfermode = PorterDuffXfermode(LayerBlendMode.fromId(layer.blendModeId).porterDuffMode)
        }

        val t = layer.transform
        val hasTransform = t.offsetX != 0f || t.offsetY != 0f || t.scaleX != 1f || t.scaleY != 1f || t.rotationDeg != 0f
        if (hasTransform) {
            canvas.save()
            val cx = bounds.minX + bounds.width / 2f
            val cy = bounds.minY + bounds.height / 2f
            canvas.translate(t.offsetX, t.offsetY)
            canvas.rotate(t.rotationDeg, cx, cy)
            canvas.scale(t.scaleX, t.scaleY, cx, cy)
        }

        var count = 0
        val visibleKeys = if (hasTransform) {
            layer.tiles.keys.mapNotNull { TileKey.parse(it) }
        } else {
            visibleTileKeysForWorldRect(bounds, tileSize, visibleWorld)
        }

        for (key in visibleKeys) {
            val kStr = key.storageKey()
            val bytes = layer.tiles[kStr] ?: continue
            val bmp = getDecodedTile(layer.id, kStr, bytes, layer.tileHashes[kStr])
            val worldX = bounds.minX + key.tx * tileSize
            val worldY = bounds.minY + key.ty * tileSize
            canvas.drawBitmap(bmp, worldX, worldY, layerPaint)
            count++
        }

        if (hasTransform) canvas.restore()
        return count
    }

    fun drawVectorObject(canvas: Canvas, obj: VectorObject) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = parseSafeColor(obj.fillColorHex, Color.WHITE)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = obj.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = parseSafeColor(obj.strokeColorHex, Color.BLACK)
        }

        canvas.save()
        if (obj.rotationDeg != 0f) {
            canvas.rotate(obj.rotationDeg, obj.x + obj.width / 2f, obj.y + obj.height / 2f)
        }

        when (obj.kind) {
            VectorKind.RECT -> {
                val rect = RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)
                canvas.drawRoundRect(rect, 12f, 12f, fillPaint)
                canvas.drawRoundRect(rect, 12f, 12f, strokePaint)
            }
            VectorKind.ELLIPSE -> {
                val rect = RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)
                canvas.drawOval(rect, fillPaint)
                canvas.drawOval(rect, strokePaint)
            }
            VectorKind.PATH -> {
                if (obj.nodes.size >= 2) {
                    val path = Path()
                    val first = obj.nodes.first()
                    path.moveTo(first.anchor.x, first.anchor.y)
                    for (i in 1 until obj.nodes.size) {
                        val prev = obj.nodes[i - 1]
                        val curr = obj.nodes[i]
                        val c1 = prev.handleOut ?: prev.anchor
                        val c2 = curr.handleIn ?: curr.anchor
                        path.cubicTo(c1.x, c1.y, c2.x, c2.y, curr.anchor.x, curr.anchor.y)
                    }
                    canvas.drawPath(path, strokePaint)
                }
            }
            VectorKind.SPEECH_BALLOON -> {
                val rect = RectF(obj.x, obj.y, obj.x + obj.width, obj.y + obj.height)
                val balloonPath = Path().apply {
                    addRoundRect(rect, 28f, 28f, Path.Direction.CW)
                    val tip = obj.tailTip
                    if (tip != null) {
                        val baseCenterX = (obj.x + obj.width * 0.35f).coerceIn(obj.x + 24f, obj.x + obj.width - 24f)
                        val baseY = obj.y + obj.height - 2f
                        moveTo(baseCenterX - 16f, baseY)
                        lineTo(tip.x, tip.y)
                        lineTo(baseCenterX + 16f, baseY)
                        close()
                    }
                }
                canvas.drawPath(balloonPath, fillPaint)
                canvas.drawPath(balloonPath, strokePaint)
                drawWrappedText(canvas, obj, padding = 14f)
            }
            VectorKind.TEXT_BOX -> {
                drawWrappedText(canvas, obj, padding = 6f)
            }
        }
        canvas.restore()
    }

    private fun drawWrappedText(canvas: Canvas, obj: VectorObject, padding: Float) {
        if (obj.text.isEmpty()) return
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseSafeColor(obj.strokeColorHex, Color.BLACK)
            textSize = obj.fontSizeSp * 1.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val availWidth = max(40, (obj.width - padding * 2f).roundToInt())
        val layout = StaticLayout.Builder.obtain(obj.text, 0, obj.text.length, tp, availWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .build()
        canvas.save()
        val textOffsetY = obj.y + max(padding, (obj.height - layout.height) / 2f)
        canvas.translate(obj.x + padding, textOffsetY)
        layout.draw(canvas)
        canvas.restore()
    }

    /**
     * Renders a deterministic flattened region (cropped to worldRegion) directly from sparse tiles
     * without allocating an oversized full-document RGBA bitmap!
     */
    fun renderFlattenedRegion(
        doc: CanonicalDocument,
        worldRegion: RectF,
        includePaper: Boolean = true,
        includeVectors: Boolean = true
    ): Bitmap {
        val outW = max(1, worldRegion.width().roundToInt())
        val outH = max(1, worldRegion.height().roundToInt())
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (includePaper) {
            val paper = PaperStyle.fromId(doc.paperColorHex)
            canvas.drawColor(if (paper == PaperStyle.TRANSPARENT) Color.WHITE else paper.colorInt)
        }

        canvas.translate(-worldRegion.left, -worldRegion.top)
        val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        doc.layers.forEach { layer ->
            if (layer.visible) {
                drawLayerTiles(canvas, doc.bounds, doc.tileSize, layer, worldRegion, tilePaint)
            }
        }
        if (includeVectors) {
            doc.vectorObjects.forEach { vec ->
                if (vec.visible) drawVectorObject(canvas, vec)
            }
        }
        return out
    }

    fun computeArtworkBoundingBox(doc: CanonicalDocument, padding: Int = 40): RectF? {
        var minWx = Float.POSITIVE_INFINITY
        var minWy = Float.POSITIVE_INFINITY
        var maxWx = Float.NEGATIVE_INFINITY
        var maxWy = Float.NEGATIVE_INFINITY
        var found = false

        val tileSize = doc.tileSize
        val px = IntArray(tileSize * tileSize)

        doc.layers.forEach { layer ->
            if (!layer.visible) return@forEach
            layer.tiles.forEach { (keyStr, bytes) ->
                val tk = TileKey.parse(keyStr) ?: return@forEach
                val bmp = getDecodedTile(layer.id, keyStr, bytes, layer.tileHashes[keyStr])
                bmp.getPixels(px, 0, tileSize, 0, 0, tileSize, tileSize)
                var y = 0
                while (y < tileSize) {
                    var x = 0
                    while (x < tileSize) {
                        if ((px[y * tileSize + x] ushr 24) > 4) {
                            found = true
                            val wx = doc.bounds.minX + tk.tx * tileSize + x + layer.transform.offsetX
                            val wy = doc.bounds.minY + tk.ty * tileSize + y + layer.transform.offsetY
                            if (wx < minWx) minWx = wx
                            if (wy < minWy) minWy = wy
                            if (wx > maxWx) maxWx = wx
                            if (wy > maxWy) maxWy = wy
                        }
                        x += 2
                    }
                    y += 2
                }
            }
        }

        doc.vectorObjects.forEach { v ->
            if (!v.visible) return@forEach
            found = true
            minWx = min(minWx, v.x)
            minWy = min(minWy, v.y)
            maxWx = max(maxWx, v.x + v.width)
            maxWy = max(maxWy, v.y + v.height)
        }

        if (!found) return null
        return RectF(
            minWx - padding,
            minWy - padding,
            max(minWx - padding + 64f, maxWx + padding),
            max(minWy - padding + 64f, maxWy + padding)
        )
    }

    /**
     * Mutates only the sparse tiles touched by [dirtyWorldRect] on [layer] and returns a new immutable
     * DocumentLayer with updated PNG bytes and SHA-256 tile hashes.
     */
    fun applyRegionMutationToLayer(
        doc: CanonicalDocument,
        layer: DocumentLayer,
        dirtyWorldRect: RectF,
        drawBlock: (Canvas, DocumentBounds) -> Unit
    ): DocumentLayer {
        val tileSize = doc.tileSize
        val keys = visibleTileKeysForWorldRect(doc.bounds, tileSize, dirtyWorldRect)
        if (keys.isEmpty()) return layer

        val updatedTiles = layer.tiles.toMutableMap()
        val updatedHashes = layer.tileHashes.toMutableMap()
        val px = IntArray(tileSize * tileSize)

        for (tk in keys) {
            val kStr = tk.storageKey()
            val existingBytes = updatedTiles[kStr]
            val workBmp = if (existingBytes != null) {
                getDecodedTile(layer.id, kStr, existingBytes, updatedHashes[kStr])
                    .copy(Bitmap.Config.ARGB_8888, true)
            } else {
                Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
            }

            val tileWorldMinX = doc.bounds.minX + tk.tx * tileSize
            val tileWorldMinY = doc.bounds.minY + tk.ty * tileSize
            val tileBounds = DocumentBounds(
                minX = tileWorldMinX,
                minY = tileWorldMinY,
                width = tileSize,
                height = tileSize
            )
            val tileCanvas = Canvas(workBmp)
            drawBlock(tileCanvas, tileBounds)

            workBmp.getPixels(px, 0, tileSize, 0, 0, tileSize, tileSize)
            val hasInk = px.any { (it ushr 24) != 0 }
            if (hasInk) {
                val baos = ByteArrayOutputStream()
                workBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                val png = baos.toByteArray()
                updatedTiles[kStr] = png
                updatedHashes[kStr] = com.example.document.DocumentArchiveCodec.sha256Hex(png)
            } else {
                updatedTiles.remove(kStr)
                updatedHashes.remove(kStr)
            }
            workBmp.recycle()
        }

        return layer.copy(tiles = updatedTiles, tileHashes = updatedHashes)
    }

    /**
     * Reconstructs a full contiguous layer bitmap (used when running pixel-level operations such as
     * flood fill, object extraction, or legacy export).
     */
    fun materializeLayerBitmap(doc: CanonicalDocument, layer: DocumentLayer): Bitmap {
        val bmp = Bitmap.createBitmap(doc.bounds.width, doc.bounds.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        layer.tiles.forEach { (kStr, bytes) ->
            val tk = TileKey.parse(kStr) ?: return@forEach
            val tileBmp = getDecodedTile(layer.id, kStr, bytes, layer.tileHashes[kStr])
            c.drawBitmap(tileBmp, (tk.tx * doc.tileSize).toFloat(), (tk.ty * doc.tileSize).toFloat(), null)
        }
        return bmp
    }

    private fun parseSafeColor(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
