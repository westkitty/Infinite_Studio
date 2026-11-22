package com.example.features

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.example.document.CanonicalDocument
import com.example.document.ConstraintKind
import com.example.document.ConstraintRecord
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.SelectionMaskRecord
import com.example.document.TileKey
import com.example.document.Vec2
import com.example.model.FutureBranchId
import com.example.model.FutureSource
import com.example.render.SparseTileRenderer
import com.example.services.ProviderJobRecord
import com.example.services.ProviderJobStatus
import com.example.tools.TileBrushEngine
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class FuturesCandidateBranch(
    val branchId: FutureBranchId,
    val label: String,
    val sourceLabel: String, // "LOCAL_STUDY", "MOCK_PROVIDER_CANDIDATE", "API_CANDIDATE"
    val source: FutureSource,
    val candidateBitmap: Bitmap,
    val previewBitmap: Bitmap
)

data class FuturesTableSessionV3(
    val docId: String,
    val baseRevision: Long,
    val regionBounds: RectF,
    val selectionMask: SelectionMaskRecord?,
    val originalBitmap: Bitmap,
    val maskBitmap: Bitmap,
    val scale: Float,
    val branches: List<FuturesCandidateBranch>,
    val selectedIndex: Int = 0,
    val prompt: String = "",
    val splitPercent: Int = 50,
    val isBusy: Boolean = false,
    val constraintWarnings: List<String> = emptyList(),
    val statusMessage: String = "Three local color studies ready. Original canvas layers untouched."
)

data class ForensicsReport(
    val baseLabel: String,
    val compareLabel: String,
    val heatmapBitmap: Bitmap,
    val totalPixelsCompared: Int,
    val changedPixelsInsideMask: Int,
    val changedPixelsOutsideMask: Int,
    val maxChannelDelta: Int,
    val meanAbsoluteDelta: Double,
    val maskBoundarySafe: Boolean,
    val advisoryChecklist: List<String>
)

/**
 * Implements:
 * - P01 The Futures Table (local color studies + non-destructive clipped acceptance)
 * - P02 AI Brush (coordinate-bound guidance strokes/arrows/color hints)
 * - P03 Constraint Constellation (protected silhouettes, forbidden zones, 0 outside-mask proof)
 * - P06 Outpainting (bounds extension + local deterministic border bleed candidate)
 * - F07 Visual Forensics (pixel diff heatmap + outside-mask mutation verification + advisory checklist)
 */
object FuturesAndConstraintsEngine {

    fun buildFuturesSession(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        activeSelection: SelectionMaskRecord?,
        initialPrompt: String = ""
    ): FuturesTableSessionV3 {
        val hasPolygon = activeSelection != null && activeSelection.points.size >= 3
        val baseRect = if (hasPolygon) {
            computePolygonBounds(activeSelection!!.points)
        } else {
            renderer.computeArtworkBoundingBox(doc, padding = 55) ?: RectF(-500f, -350f, 500f, 350f)
        }

        val margin = if (hasPolygon) {
            min(220f, max(60f, ceil(max(baseRect.width(), baseRect.height()) * 0.18f)))
        } else 0f

        val regionBounds = RectF(
            floor(baseRect.left - margin),
            floor(baseRect.top - margin),
            floor(baseRect.left - margin) + ceil(baseRect.width() + 2f * margin),
            floor(baseRect.top - margin) + ceil(baseRect.height() + 2f * margin)
        )

        val scaledOriginal = renderer.renderBoundedPreview(
            doc, regionBounds, maxSide = 1280, includePaper = true, includeVectors = true
        )
        val outW = scaledOriginal.width
        val outH = scaledOriginal.height
        val scale = outW.toFloat() / regionBounds.width()

        val maskBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        if (hasPolygon) {
            maskCanvas.drawColor(Color.BLACK)
            val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            maskCanvas.drawPath(pathInRegion(regionBounds, activeSelection!!.points, outW / regionBounds.width()), clearPaint)
        } else {
            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        val constraintsWarnings = evaluateConstraintConflicts(doc.constraints, regionBounds)

        val branches = FutureBranchId.entries.map { id ->
            val study = generateLocalColorStudy(scaledOriginal, id)
            val preview = composeMaskedPreview(scaledOriginal, study, regionBounds, activeSelection?.points, scale)
            FuturesCandidateBranch(
                branchId = id,
                label = id.label,
                sourceLabel = "LOCAL_STUDY",
                source = FutureSource.LOCAL,
                candidateBitmap = study,
                previewBitmap = preview
            )
        }

        val guidanceSummary = if (doc.guidanceMarks.isNotEmpty()) {
            " ${doc.guidanceMarks.size} AI Brush mark(s) recorded."
        } else ""

        return FuturesTableSessionV3(
            docId = doc.documentId,
            baseRevision = doc.revision,
            regionBounds = regionBounds,
            selectionMask = activeSelection,
            originalBitmap = scaledOriginal,
            maskBitmap = maskBitmap,
            scale = scale,
            branches = branches,
            selectedIndex = 0,
            prompt = initialPrompt,
            splitPercent = 50,
            isBusy = false,
            constraintWarnings = constraintsWarnings,
            statusMessage = "Three local color studies ready (Salt Print, Blue Hour, Copper Light).$guidanceSummary Originals untouched."
        )
    }

    fun generateLocalColorStudy(base: Bitmap, kind: FutureBranchId): Bitmap {
        val w = base.width
        val h = base.height
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, 0, 0, w, h)

        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.29f * r + 0.59f * g + 0.12f * b
            val (nr, ng, nb) = when (kind) {
                FutureBranchId.SALT -> {
                    val x = ((lum - 118f) * 1.55f + 128f).coerceIn(0f, 255f)
                    Triple(
                        (x * 1.01f + 6f).roundToInt().coerceIn(0, 255),
                        (x * 0.94f + 10f).roundToInt().coerceIn(0, 255),
                        (x * 0.82f + 16f).roundToInt().coerceIn(0, 255)
                    )
                }
                FutureBranchId.NIGHT -> {
                    val sh = max(0f, lum - 55f)
                    Triple(
                        (17f + sh * 0.38f).roundToInt().coerceIn(0, 255),
                        (27f + sh * 0.64f).roundToInt().coerceIn(0, 255),
                        (45f + sh * 0.90f).roundToInt().coerceIn(0, 255)
                    )
                }
                FutureBranchId.EMBER -> {
                    Triple(
                        (38f + lum * 0.91f).roundToInt().coerceIn(0, 255),
                        (14f + lum * 0.77f).roundToInt().coerceIn(0, 255),
                        (10f + lum * 0.49f).roundToInt().coerceIn(0, 255)
                    )
                }
            }
            px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(px, 0, w, 0, 0, w, h)
        }
    }

    fun applyProviderJobToSession(
        session: FuturesTableSessionV3,
        branchIndex: Int,
        jobRecord: ProviderJobRecord
    ): FuturesTableSessionV3 {
        if (jobRecord.status != ProviderJobStatus.SUCCEEDED || jobRecord.resultPngBase64 == null) {
            return session.copy(isBusy = false, statusMessage = jobRecord.message)
        }
        val bytes = android.util.Base64.decode(jobRecord.resultPngBase64, android.util.Base64.DEFAULT)
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return session.copy(isBusy = false, statusMessage = "Failed to decode provider candidate PNG.")

        val scaledCandidate = if (decoded.width == session.originalBitmap.width && decoded.height == session.originalBitmap.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, session.originalBitmap.width, session.originalBitmap.height, true)
                .also { decoded.recycle() }
        }
        val preview = composeMaskedPreview(
            original = session.originalBitmap,
            proposal = scaledCandidate,
            regionBounds = session.regionBounds,
            points = session.selectionMask?.points,
            scale = session.scale
        )
        val cur = session.branches.getOrNull(branchIndex) ?: return session
        val isApi = jobRecord.processingMode.contains("OPENAI", ignoreCase = true)
        val updatedBranches = session.branches.toMutableList()
        updatedBranches[branchIndex] = cur.copy(
            sourceLabel = jobRecord.processingMode,
            source = if (isApi) FutureSource.API else FutureSource.LOCAL,
            candidateBitmap = scaledCandidate,
            previewBitmap = preview
        )
        return session.copy(
            branches = updatedBranches,
            selectedIndex = branchIndex,
            isBusy = false,
            statusMessage = jobRecord.message
        )
    }

    /**
     * Conservation invariant: renders the accepted study ONLY inside the selection polygon (and outside
     * any FORBIDDEN_REGION constraint!) onto a brand-new DocumentLayer. All existing layers remain unmodified.
     */
    fun acceptBranchToNewLayer(
        doc: CanonicalDocument,
        session: FuturesTableSessionV3,
        branchIndex: Int
    ): Pair<CanonicalDocument, DocumentLayer> {
        val branch = session.branches[branchIndex.coerceIn(0, session.branches.lastIndex)]
        val ts = doc.tileSize
        require(ts in 1..1024)
        val bounds = doc.bounds
        val x0 = floor((session.regionBounds.left - bounds.minX) / ts).toInt().coerceIn(0, (bounds.width - 1) / ts)
        val y0 = floor((session.regionBounds.top - bounds.minY) / ts).toInt().coerceIn(0, (bounds.height - 1) / ts)
        val x1 = floor((session.regionBounds.right - bounds.minX) / ts).toInt().coerceIn(0, (bounds.width - 1) / ts)
        val y1 = floor((session.regionBounds.bottom - bounds.minY) / ts).toInt().coerceIn(0, (bounds.height - 1) / ts)
        val acceptedTiles = linkedMapOf<String, ByteArray>()
        val acceptedHashes = linkedMapOf<String, String>()
        val clipPath = Path().apply {
            val points = session.selectionMask?.points
            if (points != null && points.size >= 3) {
                points.forEachIndexed { idx, p -> if (idx == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            } else addRect(session.regionBounds, Path.Direction.CW)
        }
        val forbiddenPaths = doc.constraints.asSequence().filter {
            it.userLocked && (it.kind == ConstraintKind.FORBIDDEN_REGION || it.kind == ConstraintKind.PROTECTED_SILHOUETTE) && it.points.size >= 3
        }.map { c ->
            Path().apply {
                c.points.forEachIndexed { index, v -> if (index == 0) moveTo(v.x, v.y) else lineTo(v.x, v.y) }
                close()
            }
        }.toList()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val area = session.regionBounds
        if (x0 <= x1 && y0 <= y1) {
            for (ty in y0..y1) for (tx in x0..x1) {
                val left = bounds.minX + tx * ts
                val top = bounds.minY + ty * ts
                val bmp = Bitmap.createBitmap(ts, ts, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bmp)
                    canvas.translate(-left, -top)
                    canvas.save()
                    canvas.clipPath(clipPath)
                    canvas.drawBitmap(branch.candidateBitmap, null, area, paint)
                    canvas.restore()
                    forbiddenPaths.forEach { canvas.drawPath(it, clear) }
                    val px = IntArray(ts * ts)
                    bmp.getPixels(px, 0, ts, 0, 0, ts, ts)
                    if (px.any { (it ushr 24) != 0 }) {
                        val stream = ByteArrayOutputStream()
                        check(bmp.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        val data = stream.toByteArray()
                        val key = "${tx}_${ty}"
                        acceptedTiles[key] = data
                        acceptedHashes[key] = DocumentArchiveCodec.sha256Hex(data)
                    }
                } finally { bmp.recycle() }
            }
        }
        val label = when (branch.sourceLabel) {
            "LOCAL_STUDY" -> "Study: "
            "OFFLINE_MOCK_PROVIDER" -> "Mock Candidate: "
            else -> "Candidate: "
        }
        val newLayer = DocumentLayer(
            id = "layer_${UUID.randomUUID().toString().take(8)}",
            name = "$label${branch.label}",
            visible = true,
            opacity = 1f,
            tiles = acceptedTiles,
            tileHashes = acceptedHashes
        )
        val insertAt = doc.layers.indexOfFirst { it.id == doc.activeLayerId }
        val layers = doc.layers.toMutableList()
        if (insertAt >= 0) layers.add(insertAt + 1, newLayer) else layers.add(newLayer)
        return doc.copy(layers = layers, activeLayerId = newLayer.id) to newLayer
    }

    // --- P02 AI Brush Guidance Marks ---
    fun addGuidanceMark(
        doc: CanonicalDocument,
        kind: String,
        points: List<Vec2>,
        colorHex: String,
        note: String = ""
    ): CanonicalDocument {
        if (points.isEmpty()) return doc
        val mark = com.example.document.AiGuidanceMark(
            id = "guide_${UUID.randomUUID().toString().take(8)}",
            kind = kind,
            points = points,
            colorHex = colorHex,
            note = note
        )
        return doc.copy(guidanceMarks = doc.guidanceMarks + mark)
    }

    fun clearGuidanceMarks(doc: CanonicalDocument): CanonicalDocument =
        doc.copy(guidanceMarks = emptyList())

    // --- P03 Constraint Constellation ---
    fun addConstraint(
        doc: CanonicalDocument,
        kind: ConstraintKind,
        label: String,
        points: List<Vec2>,
        note: String = ""
    ): CanonicalDocument {
        val record = ConstraintRecord(
            id = "const_${UUID.randomUUID().toString().take(8)}",
            kind = kind,
            label = label.ifBlank { kind.name },
            note = note,
            points = points,
            userLocked = true
        )
        return doc.copy(constraints = doc.constraints + record)
    }

    fun toggleConstraintLock(doc: CanonicalDocument, constraintId: String): CanonicalDocument =
        doc.copy(constraints = doc.constraints.map {
            if (it.id == constraintId) it.copy(userLocked = !it.userLocked) else it
        })

    fun removeConstraint(doc: CanonicalDocument, constraintId: String): CanonicalDocument =
        doc.copy(constraints = doc.constraints.filterNot { it.id == constraintId })

    fun evaluateConstraintConflicts(constraints: List<ConstraintRecord>, region: RectF): List<String> {
        val warnings = mutableListOf<String>()
        constraints.filter { it.userLocked && it.points.size >= 3 }.forEach { c ->
            val cb = computePolygonBounds(c.points)
            if (RectF.intersects(cb, region)) {
                warnings.add("${c.kind.name}: '${c.label}' overlaps selection (enforced protected upon accept).")
            }
        }
        return warnings
    }

    // --- P06 Outpainting ---
    enum class OutpaintDirection { RIGHT, LEFT, TOP, BOTTOM, ALL }

    data class OutpaintPreviewInfo(
        val previewBitmap: Bitmap,
        val currentSizeLabel: String,
        val targetSizeLabel: String,
        val disclaimer: String
    )

    /**
     * Expands canvas bounds cleanly by tile multiples and synthesizes a deterministic local
     * edge-bleed outpainting candidate layer without modifying any existing layer's tiles.
     */
    fun generateLocalOutpaintCandidate(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        direction: OutpaintDirection,
        extendTiles: Int = 1
    ): CanonicalDocument {
        val t = extendTiles.coerceIn(1, 3)
        return when (direction) {
            OutpaintDirection.LEFT -> generateCustomLocalOutpaint(doc, renderer, leftTiles = t)
            OutpaintDirection.RIGHT -> generateCustomLocalOutpaint(doc, renderer, rightTiles = t)
            OutpaintDirection.TOP -> generateCustomLocalOutpaint(doc, renderer, topTiles = t)
            OutpaintDirection.BOTTOM -> generateCustomLocalOutpaint(doc, renderer, bottomTiles = t)
            OutpaintDirection.ALL -> generateCustomLocalOutpaint(doc, renderer, leftTiles = t, rightTiles = t, topTiles = t, bottomTiles = t)
        }
    }

    fun generateCustomLocalOutpaint(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        leftTiles: Int = 0,
        rightTiles: Int = 0,
        topTiles: Int = 0,
        bottomTiles: Int = 0,
        blendStrength: Float = 0.78f,
        featherPx: Float = 36f
    ): CanonicalDocument {
        val ts = doc.tileSize
        val lt = leftTiles.coerceIn(0, 3)
        val rt = rightTiles.coerceIn(0, 3)
        val tt = topTiles.coerceIn(0, 3)
        val bt = bottomTiles.coerceIn(0, 3)
        if (lt + rt + tt + bt == 0) return doc
        var workDoc = doc
        if (lt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, workDoc.bounds.minX - lt * ts.toFloat(), 0f, 8f)
        if (rt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, workDoc.bounds.maxX + rt * ts.toFloat(), 0f, 8f)
        if (tt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, 0f, workDoc.bounds.minY - tt * ts.toFloat(), 8f)
        if (bt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, 0f, workDoc.bounds.maxY + bt * ts.toFloat(), 8f)

        val oldRect = RectF(doc.bounds.minX, doc.bounds.minY, doc.bounds.maxX, doc.bounds.maxY)
        val edgePreview = renderer.renderBoundedPreview(doc, oldRect, maxSide = 256, includePaper = true, includeVectors = false)
        val sampledColor = try { sampleEdgeColor(edgePreview) } finally { edgePreview.recycle() }
        val opacity = (blendStrength.coerceIn(0.15f, 1f) * 235f).roundToInt().coerceIn(20, 245)
        val fill = Paint().apply {
            color = Color.argb(opacity, Color.red(sampledColor), Color.green(sampledColor), Color.blue(sampledColor))
        }
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        val inset = featherPx.coerceIn(0f, 96f) * 0.15f
        val protectedRect = RectF(oldRect.left + inset, oldRect.top + inset,
            oldRect.right - inset, oldRect.bottom - inset)
        val tiles = linkedMapOf<String, ByteArray>()
        val hashes = linkedMapOf<String, String>()
        val maxTx = (workDoc.bounds.width - 1) / ts
        val maxTy = (workDoc.bounds.height - 1) / ts
        for (ty in 0..maxTy) for (tx in 0..maxTx) {
            val wx = workDoc.bounds.minX + tx * ts
            val wy = workDoc.bounds.minY + ty * ts
            val worldTile = RectF(wx, wy, wx + ts, wy + ts)
            if (protectedRect.contains(worldTile)) continue
            val tile = Bitmap.createBitmap(ts, ts, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(tile)
                canvas.translate(-wx, -wy)
                canvas.drawRect(worldTile, fill)
                canvas.drawRect(protectedRect, clear)
                val buffer = IntArray(ts * ts)
                tile.getPixels(buffer, 0, ts, 0, 0, ts, ts)
                if (buffer.any { (it ushr 24) != 0 }) {
                    val stream = ByteArrayOutputStream()
                    check(tile.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    val bytes = stream.toByteArray()
                    val key = "${tx}_${ty}"
                    tiles[key] = bytes
                    hashes[key] = DocumentArchiveCodec.sha256Hex(bytes)
                }
            } finally { tile.recycle() }
        }
        val suffix = listOfNotNull(if (lt > 0) "L$lt" else null, if (rt > 0) "R$rt" else null,
            if (tt > 0) "T$tt" else null, if (bt > 0) "B$bt" else null).joinToString("+")
        val layer = DocumentLayer(
            id = "outpaint_${UUID.randomUUID().toString().take(8)}",
            name = "Outpaint Study ($suffix)",
            visible = true,
            opacity = 1f,
            tiles = tiles,
            tileHashes = hashes
        )
        return workDoc.copy(layers = workDoc.layers + layer, activeLayerId = layer.id)
    }

    fun buildOutpaintContextPreview(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        leftTiles: Int,
        rightTiles: Int,
        topTiles: Int,
        bottomTiles: Int
    ): OutpaintPreviewInfo {
        val ts = doc.tileSize
        val targetW = doc.bounds.width + (leftTiles + rightTiles) * ts
        val targetH = doc.bounds.height + (topTiles + bottomTiles) * ts
        val pw = 320
        val ph = 200
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(16, 24, 34))
        val scale = min((pw - 24f) / max(1, targetW), (ph - 24f) / max(1, targetH))
        val targetRect = RectF((pw - targetW * scale) / 2f, (ph - targetH * scale) / 2f,
            (pw + targetW * scale) / 2f, (ph + targetH * scale) / 2f)
        canvas.drawRect(targetRect, Paint().apply { color = Color.argb(120, 56, 189, 248) })
        val oldLeft = targetRect.left + leftTiles * ts * scale
        val oldTop = targetRect.top + topTiles * ts * scale
        val oldRect = RectF(oldLeft, oldTop, oldLeft + doc.bounds.width * scale, oldTop + doc.bounds.height * scale)
        val sourceRect = RectF(doc.bounds.minX, doc.bounds.minY, doc.bounds.maxX, doc.bounds.maxY)
        val art = renderer.renderBoundedPreview(doc, sourceRect, maxSide = 320, includePaper = true, includeVectors = true)
        try { canvas.drawBitmap(art, null, oldRect, Paint(Paint.FILTER_BITMAP_FLAG)) }
        finally { art.recycle() }
        canvas.drawRect(oldRect, Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f })
        return OutpaintPreviewInfo(
            previewBitmap = bmp,
            currentSizeLabel = "${doc.bounds.width}x${doc.bounds.height}",
            targetSizeLabel = "${targetW}x${targetH}",
            disclaimer = "DETERMINISTIC LOCAL EDGE STUDY - Remote AI disabled in APK"
        )
    }

    // --- F07 Visual Forensics ---
    /**
     * Compares [candidateLayerId] against an empty baseline or [baseLayerId] inside vs outside [selectionMask],
     * generating an RGB diff heatmap Bitmap and exact outside-mask pixel mutation proof!
     */
    fun computeForensicsReport(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        baseLayerId: String,
        candidateLayerId: String,
        selectionMask: SelectionMaskRecord?
    ): ForensicsReport {
        val base = doc.layers.find { it.id == baseLayerId } ?: doc.layers.first()
        val candidate = doc.layers.find { it.id == candidateLayerId } ?: doc.layers.last()
        val width = doc.bounds.width
        val height = doc.bounds.height
        val previewScale = min(1f, 640f / max(width, height))
        val thumbW = max(1, (width * previewScale).roundToInt())
        val thumbH = max(1, (height * previewScale).roundToInt())
        val preview = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
        val previewCanvas = Canvas(preview)
        val selectionPath = selectionMask?.let { TileBrushEngine.buildMaskPath(doc.bounds, it) }
        var changedInside = 0
        var changedOutside = 0
        var maxDelta = 0
        var totalDelta = 0L
        val step = 256
        for (y in 0 until height step step) for (x in 0 until width step step) {
            val w = min(step, width - x)
            val h = min(step, height - y)
            val rect = RectF(doc.bounds.minX + x, doc.bounds.minY + y,
                doc.bounds.minX + x + w, doc.bounds.minY + y + h)
            val bmpA = renderer.renderSingleLayerRegion(doc, base, rect)
            val bmpB = renderer.renderSingleLayerRegion(doc, candidate, rect)
            val pxA = IntArray(w * h)
            val pxB = IntArray(w * h)
            bmpA.getPixels(pxA, 0, w, 0, 0, w, h)
            bmpB.getPixels(pxB, 0, w, 0, 0, w, h)
            bmpA.recycle()
            bmpB.recycle()
            val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val maskCanvas = Canvas(mask)
            if (selectionPath == null) maskCanvas.drawColor(Color.WHITE) else {
                maskCanvas.translate(-x.toFloat(), -y.toFloat())
                maskCanvas.drawPath(selectionPath, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            }
            val maskPx = IntArray(w * h)
            mask.getPixels(maskPx, 0, w, 0, 0, w, h)
            mask.recycle()
            val heatPx = IntArray(w * h)
            for (i in pxA.indices) {
                val a = pxA[i]
                val b = pxB[i]
                val da = abs(((a ushr 24) and 255) - ((b ushr 24) and 255))
                val dr = abs(((a ushr 16) and 255) - ((b ushr 16) and 255))
                val dg = abs(((a ushr 8) and 255) - ((b ushr 8) and 255))
                val db = abs((a and 255) - (b and 255))
                val dMax = max(max(da, dr), max(dg, db))
                maxDelta = max(maxDelta, dMax)
                totalDelta += da + dr + dg + db
                val inside = ((maskPx[i] ushr 24) and 255) > 0
                if (dMax > 2) {
                    if (inside) changedInside++ else changedOutside++
                    val intensity = min(255, dMax * 2)
                    heatPx[i] = if (inside) Color.argb(220, intensity, 180, 40)
                        else Color.argb(245, 255, 40, 120)
                } else heatPx[i] = Color.argb(90, 20, 40, 56)
            }
            val heatTile = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                heatTile.setPixels(heatPx, 0, w, 0, 0, w, h)
                previewCanvas.drawBitmap(heatTile, null,
                    RectF(x * previewScale, y * previewScale,
                        (x + w) * previewScale, (y + h) * previewScale),
                    Paint(Paint.FILTER_BITMAP_FLAG))
            } finally { heatTile.recycle() }
        }
        return ForensicsReport(
            baseLabel = base.name,
            compareLabel = candidate.name,
            heatmapBitmap = preview,
            totalPixelsCompared = width * height,
            changedPixelsInsideMask = changedInside,
            changedPixelsOutsideMask = changedOutside,
            maxChannelDelta = maxDelta,
            meanAbsoluteDelta = totalDelta.toDouble() / (width.toDouble() * height.toDouble() * 4.0),
            maskBoundarySafe = changedOutside == 0,
            advisoryChecklist = listOf(
                if (changedOutside == 0) "PASS: Zero pixels changed outside active selection mask."
                else "ALERT: $changedOutside pixel(s) differ outside active selection mask.",
                "Inspect anatomy/silhouette continuity at mask seam manually.",
                "Verify specular highlight direction matches canvas light source.",
                "Confirm layer naming & source provenance badge accuracy."
            )
        )
    }

    /**
     * Checks how many non-transparent pixels in [targetLayerId] fall strictly outside [mask].
     * Returns 0 when candidate acceptance adhered strictly to mask clipping.
     */
    fun countNonTransparentPixelsOutsideMask(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        targetLayerId: String,
        mask: SelectionMaskRecord
    ): Int {
        val layer = doc.layers.find { it.id == targetLayerId } ?: return 0
        val path = TileBrushEngine.buildMaskPath(doc.bounds, mask) ?: return Int.MAX_VALUE
        val width = doc.bounds.width
        val height = doc.bounds.height
        var count = 0
        for (y in 0 until height step 256) for (x in 0 until width step 256) {
            val w = min(256, width - x)
            val h = min(256, height - y)
            val rect = RectF(doc.bounds.minX + x, doc.bounds.minY + y,
                doc.bounds.minX + x + w, doc.bounds.minY + y + h)
            val bitmap = renderer.renderSingleLayerRegion(doc, layer, rect)
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()
            val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(maskBitmap)
            canvas.translate(-x.toFloat(), -y.toFloat())
            canvas.drawPath(path, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            val maskPixels = IntArray(w * h)
            maskBitmap.getPixels(maskPixels, 0, w, 0, 0, w, h)
            maskBitmap.recycle()
            for (i in pixels.indices) {
                if ((pixels[i] ushr 24) > 0 && (maskPixels[i] ushr 24) == 0) count++
            }
        }
        return count
    }

    fun composeMaskedPreview(
        original: Bitmap,
        proposal: Bitmap,
        regionBounds: RectF,
        points: List<Vec2>?,
        scale: Float
    ): Bitmap {
        val out = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(original, 0f, 0f, null)
        canvas.save()
        canvas.clipPath(pathInRegion(regionBounds, points, scale))
        canvas.drawBitmap(proposal, null, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        return out
    }

    fun pathInRegion(regionBounds: RectF, points: List<Vec2>?, scale: Float): Path {
        val path = Path()
        if (points == null || points.isEmpty()) {
            path.addRect(0f, 0f, regionBounds.width() * scale, regionBounds.height() * scale, Path.Direction.CW)
            return path
        }
        points.forEachIndexed { idx, pt ->
            val x = (pt.x - regionBounds.left) * scale
            val y = (pt.y - regionBounds.top) * scale
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    fun computePolygonBounds(points: List<Vec2>): RectF {
        if (points.isEmpty()) return RectF(-300f, -300f, 300f, 300f)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return RectF(floor(minX), floor(minY), max(floor(minX) + 20f, ceil(maxX)), max(floor(minY) + 20f, ceil(maxY)))
    }

    private fun sampleEdgeColor(bmp: Bitmap): Int {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (x in 0 until bmp.width step 8) {
            val c1 = bmp.getPixel(x, 0)
            val c2 = bmp.getPixel(x, bmp.height - 1)
            rSum += Color.red(c1) + Color.red(c2)
            gSum += Color.green(c1) + Color.green(c2)
            bSum += Color.blue(c1) + Color.blue(c2)
            count += 2
        }
        if (count == 0) return Color.DKGRAY
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }
}
