package com.example.features

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.document.CanonicalDocument
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.LayerTransform
import com.example.document.MaterialRegion
import com.example.document.SelectionMaskRecord
import com.example.document.SemanticObjectRecord
import com.example.document.SpatialStageState
import com.example.document.Vec2
import com.example.render.SparseTileRenderer
import com.example.tools.TileBrushEngine
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Implements:
 * - F01 Object Surgery (non-destructive polygon extraction, speculative background repair layer, transform, revert)
 * - F06 Material Intelligence (roughness/metalness/alpha/emissive/relief shading & deterministic preview)
 * - F02 Spatial Stage (2.5D depth planes, parallax compositing & vanishing guides)
 */
object ObjectSurgeryAndMaterialEngine {

    // --- F01 Object Surgery ---
    /**
     * Non-destructively extracts the selected polygon from [sourceLayerId] into a brand-new
     * [DocumentLayer] plus a speculative background repair preview layer (`LOCAL INFERRED REPAIR (SPECULATIVE)`).
     * Never modifies the original [sourceLayerId] pixels!
     */
    fun extractObjectFromSelection(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        sourceLayerId: String,
        mask: SelectionMaskRecord,
        objectName: String
    ): Pair<CanonicalDocument, SemanticObjectRecord> {
        val srcLayer = doc.layers.find { it.id == sourceLayerId } ?: doc.layers.first()
        val srcBmp = renderer.materializeLayerBitmap(doc, srcLayer)
        val w = doc.bounds.width
        val h = doc.bounds.height

        val extractedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val exCanvas = Canvas(extractedBmp)
        val clipPath = TileBrushEngine.buildMaskPath(doc.bounds, mask)
        if (clipPath != null) {
            exCanvas.save()
            exCanvas.clipPath(clipPath)
            exCanvas.drawBitmap(srcBmp, 0f, 0f, null)
            exCanvas.restore()
        }

        // Synthesize speculative background infill strictly labeled LOCAL INFERRED REPAIR (SPECULATIVE)
        val repairBmp = synthesizeSpeculativeHoleInfill(srcBmp, mask, doc)
        srcBmp.recycle()

        val extractedTiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(extractedBmp, doc.tileSize)
        val repairTiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(repairBmp, doc.tileSize)
        extractedBmp.recycle()

        // Create small thumbnail of repair for metadata audit
        val thumbScale = min(1f, 256f / max(w, h))
        val thumb = Bitmap.createScaledBitmap(
            repairBmp,
            max(1, (w * thumbScale).roundToInt()),
            max(1, (h * thumbScale).roundToInt()),
            true
        )
        val thumbBaos = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.PNG, 100, thumbBaos)
        thumb.recycle()
        repairBmp.recycle()

        val extractedLayerId = "obj_layer_${UUID.randomUUID().toString().take(8)}"
        val repairLayerId = "repair_layer_${UUID.randomUUID().toString().take(8)}"
        val cleanName = objectName.ifBlank { "Object ${doc.semanticObjects.size + 1}" }

        val extractedLayer = DocumentLayer(
            id = extractedLayerId,
            name = "Extracted: $cleanName",
            visible = true,
            opacity = 1f,
            tiles = extractedTiles,
            tileHashes = extractedTiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )
        val repairLayer = DocumentLayer(
            id = repairLayerId,
            name = "Speculative Repair: $cleanName",
            visible = true,
            opacity = 0.92f,
            tiles = repairTiles,
            tileHashes = repairTiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )

        val bounds = FuturesAndConstraintsEngine.computePolygonBounds(mask.points)
        val record = SemanticObjectRecord(
            id = "sem_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            sourceLayerId = srcLayer.id,
            extractedLayerId = extractedLayerId,
            boundsX = bounds.left,
            boundsY = bounds.top,
            boundsW = bounds.width(),
            boundsH = bounds.height(),
            pivotX = bounds.centerX(),
            pivotY = bounds.centerY(),
            maskPoints = mask.points,
            inferredBgRepairPng = thumbBaos.toByteArray(),
            repairLabel = "LOCAL INFERRED REPAIR (SPECULATIVE)"
        )

        val updatedDoc = doc.copy(
            layers = doc.layers + repairLayer + extractedLayer,
            activeLayerId = extractedLayerId,
            semanticObjects = doc.semanticObjects + record
        )
        return updatedDoc to record
    }

    private fun synthesizeSpeculativeHoleInfill(
        srcBmp: Bitmap,
        mask: SelectionMaskRecord,
        doc: CanonicalDocument
    ): Bitmap {
        val w = srcBmp.width
        val h = srcBmp.height
        val repairBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val polyBounds = FuturesAndConstraintsEngine.computePolygonBounds(mask.points)
        val localLeft = (polyBounds.left - doc.bounds.minX).roundToInt().coerceIn(0, w - 1)
        val localTop = (polyBounds.top - doc.bounds.minY).roundToInt().coerceIn(0, h - 1)
        val localRight = (polyBounds.right - doc.bounds.minX).roundToInt().coerceIn(localLeft + 1, w)
        val localBottom = (polyBounds.bottom - doc.bounds.minY).roundToInt().coerceIn(localTop + 1, h)

        // Average non-transparent border pixels just outside the polygon bounds
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        val pad = 8
        val sL = max(0, localLeft - pad)
        val sT = max(0, localTop - pad)
        val sR = min(w - 1, localRight + pad)
        val sB = min(h - 1, localBottom + pad)
        for (x in sL..sR step 4) {
            listOf(srcBmp.getPixel(x, sT), srcBmp.getPixel(x, sB)).forEach { c ->
                if (Color.alpha(c) > 20) {
                    rSum += Color.red(c); gSum += Color.green(c); bSum += Color.blue(c); count++
                }
            }
        }
        for (y in sT..sB step 4) {
            listOf(srcBmp.getPixel(sL, y), srcBmp.getPixel(sR, y)).forEach { c ->
                if (Color.alpha(c) > 20) {
                    rSum += Color.red(c); gSum += Color.green(c); bSum += Color.blue(c); count++
                }
            }
        }

        val fillColor = if (count > 0) {
            Color.argb(235, (rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
        } else {
            try {
                Color.parseColor(doc.paperColorHex)
            } catch (_: Exception) {
                Color.rgb(220, 216, 208)
            }
        }

        val canvas = Canvas(repairBmp)
        TileBrushEngine.buildMaskPath(doc.bounds, mask)?.let { p ->
            canvas.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fillColor
            })
        }
        return repairBmp
    }

    fun moveSemanticObject(
        doc: CanonicalDocument,
        objectId: String,
        dx: Float,
        dy: Float,
        scaleFactor: Float = 1f,
        rotationDeltaDeg: Float = 0f
    ): CanonicalDocument {
        val obj = doc.semanticObjects.find { it.id == objectId } ?: return doc
        val updatedLayers = doc.layers.map { layer ->
            if (layer.id == obj.extractedLayerId) {
                layer.copy(
                    transform = layer.transform.copy(
                        offsetX = layer.transform.offsetX + dx,
                        offsetY = layer.transform.offsetY + dy,
                        scaleX = (layer.transform.scaleX * scaleFactor).coerceIn(0.1f, 6f),
                        scaleY = (layer.transform.scaleY * scaleFactor).coerceIn(0.1f, 6f),
                        rotationDeg = layer.transform.rotationDeg + rotationDeltaDeg
                    )
                )
            } else layer
        }
        val updatedObjs = doc.semanticObjects.map {
            if (it.id == objectId) it.copy(
                boundsX = it.boundsX + dx,
                boundsY = it.boundsY + dy,
                pivotX = it.pivotX + dx,
                pivotY = it.pivotY + dy
            ) else it
        }
        return doc.copy(layers = updatedLayers, semanticObjects = updatedObjs)
    }

    fun applyTransformToLayerOrObject(
        doc: CanonicalDocument,
        targetLayerId: String,
        semanticObjectId: String?,
        newTransform: LayerTransform
    ): CanonicalDocument {
        val prevLayer = doc.layers.find { it.id == targetLayerId } ?: return doc
        val dx = newTransform.offsetX - prevLayer.transform.offsetX
        val dy = newTransform.offsetY - prevLayer.transform.offsetY
        val updatedLayers = doc.layers.map { layer ->
            if (layer.id == targetLayerId) {
                layer.copy(
                    transform = newTransform.copy(
                        scaleX = newTransform.scaleX.let { if (it < 0f) it.coerceIn(-6f, -0.1f) else it.coerceIn(0.1f, 6f) },
                        scaleY = newTransform.scaleY.let { if (it < 0f) it.coerceIn(-6f, -0.1f) else it.coerceIn(0.1f, 6f) }
                    )
                )
            } else layer
        }
        val updatedObjs = if (semanticObjectId != null) {
            doc.semanticObjects.map { obj ->
                if (obj.id == semanticObjectId) {
                    val sxRatio = if (prevLayer.transform.scaleX != 0f) abs(newTransform.scaleX / prevLayer.transform.scaleX) else 1f
                    val syRatio = if (prevLayer.transform.scaleY != 0f) abs(newTransform.scaleY / prevLayer.transform.scaleY) else 1f
                    obj.copy(
                        boundsX = obj.boundsX + dx,
                        boundsY = obj.boundsY + dy,
                        boundsW = max(16f, obj.boundsW * sxRatio),
                        boundsH = max(16f, obj.boundsH * syRatio),
                        pivotX = obj.pivotX + dx,
                        pivotY = obj.pivotY + dy
                    )
                } else obj
            }
        } else doc.semanticObjects
        return doc.copy(layers = updatedLayers, semanticObjects = updatedObjs)
    }

    fun refineObjectMaskAndReextract(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        objectId: String,
        expandPx: Float
    ): CanonicalDocument {
        val obj = doc.semanticObjects.find { it.id == objectId } ?: return doc
        if (obj.maskPoints.size < 3) return doc
        val cx = obj.pivotX
        val cy = obj.pivotY
        val refinedPoints = obj.maskPoints.map { pt ->
            val vx = pt.x - cx
            val vy = pt.y - cy
            val len = max(1f, sqrt(vx * vx + vy * vy))
            val nextLen = max(8f, len + expandPx)
            Vec2(cx + (vx / len) * nextLen, cy + (vy / len) * nextLen)
        }
        val srcLayer = doc.layers.find { it.id == obj.sourceLayerId } ?: return doc
        val srcBmp = renderer.materializeLayerBitmap(doc, srcLayer)
        val w = doc.bounds.width
        val h = doc.bounds.height
        val maskRecord = SelectionMaskRecord("mask_ref_${obj.id}", "Refined ${obj.name}", refinedPoints)
        val extractedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val exCanvas = Canvas(extractedBmp)
        TileBrushEngine.buildMaskPath(doc.bounds, maskRecord)?.let { clipPath ->
            exCanvas.save()
            exCanvas.clipPath(clipPath)
            exCanvas.drawBitmap(srcBmp, 0f, 0f, null)
            exCanvas.restore()
        }
        srcBmp.recycle()
        val extractedTiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(extractedBmp, doc.tileSize)
        extractedBmp.recycle()

        val bounds = FuturesAndConstraintsEngine.computePolygonBounds(refinedPoints)
        return doc.copy(
            layers = doc.layers.map { l ->
                if (l.id == obj.extractedLayerId) {
                    l.copy(
                        tiles = extractedTiles,
                        tileHashes = extractedTiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
                    )
                } else l
            },
            semanticObjects = doc.semanticObjects.map { o ->
                if (o.id == objectId) {
                    o.copy(
                        maskPoints = refinedPoints,
                        boundsX = bounds.left,
                        boundsY = bounds.top,
                        boundsW = bounds.width(),
                        boundsH = bounds.height()
                    )
                } else o
            }
        )
    }

    fun revertObjectExtraction(doc: CanonicalDocument, objectId: String): CanonicalDocument {
        val obj = doc.semanticObjects.find { it.id == objectId } ?: return doc
        val repairPrefix = "Speculative Repair: ${obj.name}"
        val filteredLayers = doc.layers.filterNot {
            it.id == obj.extractedLayerId || it.name == repairPrefix
        }.ifEmpty { doc.layers }
        return doc.copy(
            layers = filteredLayers,
            activeLayerId = obj.sourceLayerId.takeIf { id -> filteredLayers.any { it.id == id } } ?: filteredLayers.last().id,
            semanticObjects = doc.semanticObjects.filterNot { it.id == objectId }
        )
    }

    // --- F06 Material Intelligence ---
    fun upsertMaterial(doc: CanonicalDocument, material: MaterialRegion): CanonicalDocument {
        val existing = doc.materials.any { it.id == material.id }
        val updated = if (existing) {
            doc.materials.map { if (it.id == material.id) material else it }
        } else {
            doc.materials + material
        }
        return doc.copy(materials = updated)
    }

    fun removeMaterial(doc: CanonicalDocument, materialId: String): CanonicalDocument =
        doc.copy(materials = doc.materials.filterNot { it.id == materialId })

    /**
     * Deterministic Blinn-Phong + Fresnel relief shader pass over non-transparent pixels of targetLayerId,
     * optionally clipped strictly to [MaterialRegion.maskId].
     * Supports Material Intelligence preview and non-destructive baked lighting layer creation.
     */
    fun renderMaterialLitPreview(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        material: MaterialRegion
    ): Bitmap {
        val layer = doc.layers.find { it.id == material.targetLayerId } ?: doc.layers.last()
        val src = renderer.materializeLayerBitmap(doc, layer)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val maskRecord = material.maskId?.let { id -> doc.masks.find { it.id == id } }
        val maskPx = if (maskRecord != null && maskRecord.points.size >= 3) {
            val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val mc = Canvas(maskBmp)
            TileBrushEngine.buildMaskPath(doc.bounds, maskRecord)?.let { p ->
                mc.drawPath(p, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL })
            }
            IntArray(w * h).also { arr ->
                maskBmp.getPixels(arr, 0, w, 0, 0, w, h)
                maskBmp.recycle()
            }
        } else null

        val az = Math.toRadians(material.lightAzimuthDeg.toDouble())
        val el = Math.toRadians(material.lightElevationDeg.toDouble())
        val lx = (cos(el) * cos(az)).toFloat()
        val ly = (cos(el) * sin(az)).toFloat()
        val lz = sin(el).toFloat()

        // Halfway vector with view = (0, 0, 1)
        val hx = lx
        val hy = ly
        val hz = lz + 1f
        val hLen = max(0.001f, sqrt(hx * hx + hy * hy + hz * hz))
        val hnx = hx / hLen
        val hny = hy / hLen
        val hnz = hz / hLen

        val shininess = (2f + (1f - material.roughness.coerceIn(0f, 1f)) * 90f)
        val baseTint = parseHexRgb(material.baseColorHex, Color.rgb(56, 189, 248))
        val emissive = parseHexRgb(material.emissiveHex, Color.BLACK)
        val emStrength = material.emissionIntensity.coerceIn(0f, 1.5f)

        val outPx = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val c = px[idx]
                val a = (c ushr 24) and 0xFF
                if (a == 0) {
                    outPx[idx] = 0
                    continue
                }
                if (maskPx != null && ((maskPx[idx] ushr 24) and 0xFF) == 0) {
                    outPx[idx] = c
                    continue
                }
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF

                // Simple 4-neighbor gradient normal approximation for relief height
                val lLeft = if (x > 0) luma(px[idx - 1]) else luma(c)
                val lRight = if (x < w - 1) luma(px[idx + 1]) else luma(c)
                val lUp = if (y > 0) luma(px[idx - w]) else luma(c)
                val lDown = if (y < h - 1) luma(px[idx + w]) else luma(c)

                var nx = (lLeft - lRight) * material.reliefStrength * 0.02f
                var ny = (lUp - lDown) * material.reliefStrength * 0.02f
                var nz = 1.0f
                val nLen = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= nLen; ny /= nLen; nz /= nLen

                val nDotL = max(0.15f, nx * lx + ny * ly + nz * lz)
                val nDotH = max(0f, nx * hnx + ny * hny + nz * hnz)
                val spec = nDotH.pow(shininess) * (0.2f + 0.8f * material.metalness)

                val tintBlend = 0.25f
                val br = r * (1f - tintBlend) + Color.red(baseTint) * tintBlend
                val bg = g * (1f - tintBlend) + Color.green(baseTint) * tintBlend
                val bb = b * (1f - tintBlend) + Color.blue(baseTint) * tintBlend

                val fr = (br * (0.3f + 0.7f * nDotL) + spec * 220f + Color.red(emissive) * emStrength).roundToInt().coerceIn(0, 255)
                val fg = (bg * (0.3f + 0.7f * nDotL) + spec * 220f + Color.green(emissive) * emStrength).roundToInt().coerceIn(0, 255)
                val fb = (bb * (0.3f + 0.7f * nDotL) + spec * 220f + Color.blue(emissive) * emStrength).roundToInt().coerceIn(0, 255)
                val fa = (a * material.alpha.coerceIn(0.05f, 1f)).roundToInt().coerceIn(0, 255)

                outPx[idx] = (fa shl 24) or (fr shl 16) or (fg shl 8) or fb
            }
        }
        src.recycle()

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPx, 0, w, 0, 0, w, h)
        }
    }

    fun applyMaterialPreviewAsNewLayer(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        material: MaterialRegion
    ): CanonicalDocument {
        val lit = renderMaterialLitPreview(doc, renderer, material)
        val tiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(lit, doc.tileSize)
        lit.recycle()
        val newLayer = DocumentLayer(
            id = "mat_layer_${UUID.randomUUID().toString().take(8)}",
            name = "Material: ${material.name}",
            visible = true,
            opacity = 1f,
            tiles = tiles,
            tileHashes = tiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
        )
        return upsertMaterial(doc, material).copy(
            layers = doc.layers + newLayer,
            activeLayerId = newLayer.id
        )
    }

    // --- F02 Spatial Stage (2.5D) ---
    fun assignLayerToDepthPlane(doc: CanonicalDocument, layerId: String, planeId: String): CanonicalDocument {
        val updatedPlanes = doc.spatialStage.depthPlanes.map { p ->
            val cleaned = p.assignedLayerIds.filterNot { it == layerId }
            if (p.id == planeId) p.copy(assignedLayerIds = cleaned + layerId) else p.copy(assignedLayerIds = cleaned)
        }
        val updatedLayers = doc.layers.map { l ->
            if (l.id == layerId) l.copy(depthPlaneId = planeId) else l
        }
        return doc.copy(
            layers = updatedLayers,
            spatialStage = doc.spatialStage.copy(depthPlanes = updatedPlanes)
        )
    }

    fun updateSpatialStage(doc: CanonicalDocument, transform: (SpatialStageState) -> SpatialStageState): CanonicalDocument =
        doc.copy(spatialStage = transform(doc.spatialStage))

    fun resetSpatialCamera(doc: CanonicalDocument): CanonicalDocument =
        doc.copy(spatialStage = doc.spatialStage.copy(cameraYawDeg = 0f, cameraPitchDeg = 0f, cameraZoom = 1f))

    fun renderSpatialStageComposite(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        outWidth: Int = 800,
        outHeight: Int = 520
    ): Bitmap {
        val bmp = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(14, 21, 30))

        val stage = doc.spatialStage
        val yawRad = stage.cameraYawDeg * 3.5f
        val pitchRad = stage.cameraPitchDeg * 2.5f

        // Sort planes farthest (largest zDepth) to nearest
        val planeMap = stage.depthPlanes.associateBy { it.id }
        val sortedLayers = doc.layers.filter { it.visible }.sortedByDescending { l ->
            planeMap[l.depthPlaneId]?.zDepth ?: 1.0f
        }

        val scale = min(outWidth.toFloat() / doc.bounds.width, outHeight.toFloat() / doc.bounds.height) * stage.cameraZoom
        canvas.save()
        canvas.translate(outWidth / 2f, outHeight / 2f)
        canvas.scale(scale, scale)

        // Draw perspective grid lines toward vanishing point
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 56, 189, 248)
            strokeWidth = 2f
        }
        val vp = stage.vanishingPoint
        listOf(-900f, -450f, 0f, 450f, 900f).forEach { gx ->
            canvas.drawLine(vp.x, vp.y, gx, 700f, gridPaint)
        }

        sortedLayers.forEach { layer ->
            val plane = planeMap[layer.depthPlaneId]
            val parallax = plane?.parallaxFactor ?: 1.0f
            val dx = yawRad * parallax
            val dy = pitchRad * parallax
            val bmpLayer = renderer.materializeLayerBitmap(doc, layer)
            canvas.save()
            canvas.translate(-doc.bounds.width / 2f + dx, -doc.bounds.height / 2f + dy)
            canvas.drawBitmap(bmpLayer, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            })
            canvas.restore()
            bmpLayer.recycle()
        }

        // Light node indicator
        canvas.drawCircle(stage.lightPos.x, stage.lightPos.y, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 250, 204, 21)
        })
        canvas.restore()
        return bmp
    }

    private fun luma(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun parseHexRgb(hex: String, fallback: Int): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        fallback
    }
}
