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

        val maxSide = 1280f
        val scale = min(1f, maxSide / max(regionBounds.width(), regionBounds.height()))
        val raw = renderer.renderFlattenedRegion(doc, regionBounds, includePaper = true, includeVectors = true)

        val outW = max(1, (regionBounds.width() * scale).roundToInt())
        val outH = max(1, (regionBounds.height() * scale).roundToInt())
        val scaledOriginal = if (raw.width == outW && raw.height == outH) raw else {
            Bitmap.createScaledBitmap(raw, outW, outH, true).also { raw.recycle() }
        }

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
        val layerBitmap = Bitmap.createBitmap(doc.bounds.width, doc.bounds.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layerBitmap)
        canvas.save()

        val pts = session.selectionMask?.points
        if (pts != null && pts.size >= 3) {
            val clipPath = Path()
            pts.forEachIndexed { i, p ->
                val lp = TileBrushEngine.worldToLocal(doc.bounds, p.x, p.y)
                if (i == 0) clipPath.moveTo(lp.x, lp.y) else clipPath.lineTo(lp.x, lp.y)
            }
            clipPath.close()
            canvas.clipPath(clipPath)
        }

        val pos = TileBrushEngine.worldToLocal(doc.bounds, session.regionBounds.left, session.regionBounds.top)
        val dst = RectF(
            pos.x, pos.y,
            pos.x + session.regionBounds.width(),
            pos.y + session.regionBounds.height()
        )
        canvas.drawBitmap(branch.candidateBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()

        // Explicitly punch out any FORBIDDEN_REGION or PROTECTED_SILHOUETTE constraint locked by user
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        doc.constraints.filter {
            it.userLocked && (it.kind == ConstraintKind.FORBIDDEN_REGION || it.kind == ConstraintKind.PROTECTED_SILHOUETTE) && it.points.size >= 3
        }.forEach { constraint ->
            val p = Path()
            constraint.points.forEachIndexed { idx, v ->
                val lp = TileBrushEngine.worldToLocal(doc.bounds, v.x, v.y)
                if (idx == 0) p.moveTo(lp.x, lp.y) else p.lineTo(lp.x, lp.y)
            }
            p.close()
            canvas.drawPath(p, clearPaint)
        }

        val tiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(layerBitmap, doc.tileSize)
        layerBitmap.recycle()

        val prefix = when (branch.sourceLabel) {
            "LOCAL_STUDY" -> "Study: "
            "OFFLINE_MOCK_PROVIDER" -> "Mock Candidate: "
            else -> "Candidate: "
        }
        val newLayer = DocumentLayer(
            id = "layer_${UUID.randomUUID().toString().take(8)}",
            name = "$prefix${branch.label}",
            visible = true,
            opacity = 1f,
            tiles = tiles,
            tileHashes = tiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )

        val activeIdx = doc.layers.indexOfFirst { it.id == doc.activeLayerId }
        val updatedLayers = doc.layers.toMutableList()
        if (activeIdx >= 0) updatedLayers.add(activeIdx + 1, newLayer) else updatedLayers.add(newLayer)

        return doc.copy(layers = updatedLayers, activeLayerId = newLayer.id) to newLayer
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
        val ts = doc.tileSize.toFloat()
        val lt = leftTiles.coerceIn(0, 3)
        val rt = rightTiles.coerceIn(0, 3)
        val tt = topTiles.coerceIn(0, 3)
        val bt = bottomTiles.coerceIn(0, 3)
        if (lt + rt + tt + bt == 0) return doc

        var workDoc = doc
        if (lt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, workDoc.bounds.minX - lt * ts, 0f, 8f)
        if (rt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, workDoc.bounds.maxX + rt * ts, 0f, 8f)
        if (tt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, 0f, workDoc.bounds.minY - tt * ts, 8f)
        if (bt > 0) workDoc = TileBrushEngine.expandBoundsIfNeeded(workDoc, 0f, workDoc.bounds.maxY + bt * ts, 8f)

        val oldRect = RectF(doc.bounds.minX, doc.bounds.minY, doc.bounds.maxX, doc.bounds.maxY)
        val previewSample = renderer.renderFlattenedRegion(doc, oldRect, includePaper = true, includeVectors = false)
        val avgColor = sampleEdgeColor(previewSample)
        previewSample.recycle()

        val outLayerBmp = Bitmap.createBitmap(workDoc.bounds.width, workDoc.bounds.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outLayerBmp)
        val alphaInt = (blendStrength.coerceIn(0.15f, 1.0f) * 235f).roundToInt().coerceIn(20, 245)
        val fillPaint = Paint().apply {
            color = Color.argb(alphaInt, Color.red(avgColor), Color.green(avgColor), Color.blue(avgColor))
        }
        canvas.drawRect(0f, 0f, outLayerBmp.width.toFloat(), outLayerBmp.height.toFloat(), fillPaint)

        // Carve out original area with soft optional feather border so source artwork stays untouched
        val f = featherPx.coerceIn(0f, 96f)
        val oldLocal = RectF(
            doc.bounds.minX - workDoc.bounds.minX + f * 0.15f,
            doc.bounds.minY - workDoc.bounds.minY + f * 0.15f,
            doc.bounds.maxX - workDoc.bounds.minX - f * 0.15f,
            doc.bounds.maxY - workDoc.bounds.minY - f * 0.15f
        )
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawRect(oldLocal, clearPaint)

        val tiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(outLayerBmp, workDoc.tileSize)
        outLayerBmp.recycle()

        val labelSuffix = listOfNotNull(
            if (lt > 0) "L$lt" else null,
            if (rt > 0) "R$rt" else null,
            if (tt > 0) "T$tt" else null,
            if (bt > 0) "B$bt" else null
        ).joinToString("+")
        val newLayer = DocumentLayer(
            id = "outpaint_${UUID.randomUUID().toString().take(8)}",
            name = "Outpaint Study ($labelSuffix)",
            visible = true,
            opacity = 1f,
            tiles = tiles,
            tileHashes = tiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )
        return workDoc.copy(layers = workDoc.layers + newLayer, activeLayerId = newLayer.id)
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
        val targetRect = RectF(
            (pw - targetW * scale) / 2f,
            (ph - targetH * scale) / 2f,
            (pw + targetW * scale) / 2f,
            (ph + targetH * scale) / 2f
        )
        canvas.drawRect(targetRect, Paint().apply {
            color = Color.argb(120, 56, 189, 248)
            style = Paint.Style.FILL
        })
        val oldLeft = targetRect.left + leftTiles * ts * scale
        val oldTop = targetRect.top + topTiles * ts * scale
        val oldRect = RectF(oldLeft, oldTop, oldLeft + doc.bounds.width * scale, oldTop + doc.bounds.height * scale)

        val art = renderer.renderFlattenedRegion(doc, RectF(doc.bounds.minX, doc.bounds.minY, doc.bounds.maxX, doc.bounds.maxY), includePaper = true, includeVectors = true)
        canvas.drawBitmap(art, null, oldRect, Paint(Paint.FILTER_BITMAP_FLAG))
        art.recycle()
        canvas.drawRect(oldRect, Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        })

        return OutpaintPreviewInfo(
            previewBitmap = bmp,
            currentSizeLabel = "${doc.bounds.width}x${doc.bounds.height}",
            targetSizeLabel = "${targetW}x${targetH}",
            disclaimer = "DETERMINISTIC LOCAL EDGE STUDY · Remote AI disabled in APK"
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
        val baseLayer = doc.layers.find { it.id == baseLayerId } ?: doc.layers.first()
        val cmpLayer = doc.layers.find { it.id == candidateLayerId } ?: doc.layers.last()

        val bmpA = renderer.materializeLayerBitmap(doc, baseLayer)
        val bmpB = renderer.materializeLayerBitmap(doc, cmpLayer)
        val w = doc.bounds.width
        val h = doc.bounds.height

        val pxA = IntArray(w * h)
        val pxB = IntArray(w * h)
        bmpA.getPixels(pxA, 0, w, 0, 0, w, h)
        bmpB.getPixels(pxB, 0, w, 0, 0, w, h)

        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mc = Canvas(maskBmp)
        if (selectionMask != null && selectionMask.points.size >= 3) {
            val path = TileBrushEngine.buildMaskPath(doc.bounds, selectionMask)
            if (path != null) {
                mc.drawPath(path, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            }
        } else {
            mc.drawColor(Color.WHITE)
        }
        val maskPx = IntArray(w * h)
        maskBmp.getPixels(maskPx, 0, w, 0, 0, w, h)
        maskBmp.recycle()

        val heatPx = IntArray(w * h)
        var changedIn = 0
        var changedOut = 0
        var maxDelta = 0
        var sumDelta = 0L

        for (i in pxA.indices) {
            val c1 = pxA[i]
            val c2 = pxB[i]
            val da = abs(((c1 ushr 24) and 0xFF) - ((c2 ushr 24) and 0xFF))
            val dr = abs(((c1 ushr 16) and 0xFF) - ((c2 ushr 16) and 0xFF))
            val dg = abs(((c1 ushr 8) and 0xFF) - ((c2 ushr 8) and 0xFF))
            val db = abs((c1 and 0xFF) - (c2 and 0xFF))
            val dMax = max(max(da, dr), max(dg, db))
            if (dMax > maxDelta) maxDelta = dMax
            sumDelta += (da + dr + dg + db)

            val insideMask = ((maskPx[i] ushr 24) and 0xFF) > 0
            if (dMax > 2) {
                if (insideMask) changedIn++ else changedOut++
                val intensity = min(255, dMax * 2)
                heatPx[i] = if (insideMask) {
                    Color.argb(220, intensity, 180, 40) // Gold/amber inside mask
                } else {
                    Color.argb(245, 255, 40, 120) // Bright magenta alarm outside mask
                }
            } else {
                heatPx[i] = Color.argb(90, 20, 40, 56) // Calm slate for identical pixels
            }
        }

        val rawHeat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(heatPx, 0, w, 0, 0, w, h)
        }
        val thumbScale = min(1f, 640f / max(w, h))
        val previewHeat = Bitmap.createScaledBitmap(
            rawHeat,
            max(1, (w * thumbScale).roundToInt()),
            max(1, (h * thumbScale).roundToInt()),
            true
        )
        rawHeat.recycle()
        bmpA.recycle()
        bmpB.recycle()

        val meanDelta = sumDelta.toDouble() / (pxA.size.toDouble() * 4.0)
        return ForensicsReport(
            baseLabel = baseLayer.name,
            compareLabel = cmpLayer.name,
            heatmapBitmap = previewHeat,
            totalPixelsCompared = pxA.size,
            changedPixelsInsideMask = changedIn,
            changedPixelsOutsideMask = changedOut,
            maxChannelDelta = maxDelta,
            meanAbsoluteDelta = meanDelta,
            maskBoundarySafe = changedOut == 0,
            advisoryChecklist = listOf(
                if (changedOut == 0) "PASS: Zero pixels changed outside active selection mask."
                else "ALERT: $changedOut pixel(s) differ outside active selection mask.",
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
        val bmp = renderer.materializeLayerBitmap(doc, layer)
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()

        val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mc = Canvas(maskBmp)
        TileBrushEngine.buildMaskPath(doc.bounds, mask)?.let { p ->
            mc.drawPath(p, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
        }
        val mPx = IntArray(w * h)
        maskBmp.getPixels(mPx, 0, w, 0, 0, w, h)
        maskBmp.recycle()

        var outsideCount = 0
        for (i in px.indices) {
            val alpha = (px[i] ushr 24) and 0xFF
            val inMask = ((mPx[i] ushr 24) and 0xFF) > 0
            if (alpha > 0 && !inMask) outsideCount++
        }
        return outsideCount
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
