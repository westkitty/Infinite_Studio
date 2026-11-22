package com.example.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.document.BezierNode
import com.example.document.CanonicalDocument
import com.example.document.CanonPassportCodec
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.TimelineFrame
import com.example.document.Vec2
import com.example.document.VectorKind
import com.example.document.VectorObject
import com.example.render.SparseTileRenderer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Serializable
data class FoundrySpriteEntry(
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val componentFile: String
)

@Serializable
data class FoundryManifest(
    val format: String = "infinite-studio-foundry-bundle",
    val version: Int = 1,
    val documentId: String,
    val revision: Long,
    val title: String,
    val exportedAtIso: String,
    val provenanceSummary: List<String>,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val sprites: List<FoundrySpriteEntry>,
    val svgFile: String = "artwork.svg"
)

data class WorkshopTemplate(
    val id: String,
    val title: String,
    val description: String,
    val vectors: List<VectorObject>,
    val frameCount: Int,
    val starterCaption: String
)

/**
 * Implements:
 * - F09 Vector & Typography Studio (Bezier paths, shapes, exact wrapped text boxes, speech balloons, SVG export)
 * - F08 Production Foundry (transparent components + packed atlas + provenance JSON + SVG bundle export & verification)
 * - P09 Asset Workshop (reusable templates wired into document & Foundry)
 */
object VectorAndFoundryEngine {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    val WORKSHOP_TEMPLATES = listOf(
        WorkshopTemplate(
            id = "tpl_comic_panel",
            title = "Comic Page Frame + Balloons",
            description = "Clean ink panel border with two editable speech balloons and caption box.",
            vectors = listOf(
                VectorObject(
                    id = "tpl_panel_1",
                    kind = VectorKind.RECT,
                    x = -380f, y = -240f, width = 760f, height = 480f,
                    strokeColorHex = "#0F172A", fillColorHex = "#00000000", strokeWidth = 4f
                ),
                VectorObject(
                    id = "tpl_balloon_1",
                    kind = VectorKind.SPEECH_BALLOON,
                    x = -320f, y = -200f, width = 260f, height = 110f,
                    strokeColorHex = "#09090B", fillColorHex = "#FFFFFF", strokeWidth = 3f,
                    text = "ONE MARK, THREE FUTURES",
                    fontSizeSp = 15f,
                    tailTip = Vec2(-180f, -45f)
                )
            ),
            frameCount = 2,
            starterCaption = "Comic panel & dialogue ready."
        ),
        WorkshopTemplate(
            id = "tpl_character_turnaround",
            title = "Character Turnaround Guides",
            description = "Proportion horizon guides and callout labels for Front / 3-4 / Back poses.",
            vectors = listOf(
                VectorObject(
                    id = "tpl_char_label",
                    kind = VectorKind.TEXT_BOX,
                    x = -240f, y = -290f, width = 480f, height = 64f,
                    strokeColorHex = "#38BDF8", fillColorHex = "#FFFFFF", strokeWidth = 2f,
                    text = "CHARACTER TURNAROUND · 1:7.5 HEAD RATIO",
                    fontSizeSp = 16f
                )
            ),
            frameCount = 2,
            starterCaption = "Turnaround guides placed."
        ),
        WorkshopTemplate(
            id = "tpl_sprite_pack",
            title = "Game Sprite & Emblem Kit",
            description = "2-frame animation timing + crisp Bezier emblem arc for atlas export.",
            vectors = listOf(
                VectorObject(
                    id = "tpl_bezier_arc",
                    kind = VectorKind.PATH,
                    x = -180f, y = -80f, width = 360f, height = 160f,
                    strokeColorHex = "#38BDF8", fillColorHex = "#00000000", strokeWidth = 5f,
                    nodes = listOf(
                        BezierNode(Vec2(-160f, 40f), handleOut = Vec2(-60f, -120f)),
                        BezierNode(Vec2(160f, 40f), handleIn = Vec2(60f, -120f))
                    )
                )
            ),
            frameCount = 2,
            starterCaption = "Sprite kit initialized."
        )
    )

    // --- F09 Vector & Typography Studio ---
    fun addTextBox(doc: CanonicalDocument, text: String, x: Float = -140f, y: Float = -60f): CanonicalDocument {
        val obj = VectorObject(
            id = "vec_txt_${UUID.randomUUID().toString().take(8)}",
            kind = VectorKind.TEXT_BOX,
            x = x, y = y, width = 280f, height = 96f,
            strokeColorHex = "#0F172A", fillColorHex = "#FFFFFF", strokeWidth = 2f,
            text = text,
            fontSizeSp = 18f
        )
        return doc.copy(vectorObjects = doc.vectorObjects + obj)
    }

    fun addSpeechBalloon(doc: CanonicalDocument, text: String, x: Float = -150f, y: Float = -180f): CanonicalDocument {
        val obj = VectorObject(
            id = "vec_bal_${UUID.randomUUID().toString().take(8)}",
            kind = VectorKind.SPEECH_BALLOON,
            x = x, y = y, width = 280f, height = 115f,
            strokeColorHex = "#09090B", fillColorHex = "#FFFFFF", strokeWidth = 3f,
            text = text,
            fontSizeSp = 16f,
            tailTip = Vec2(x + 90f, y + 165f)
        )
        return doc.copy(vectorObjects = doc.vectorObjects + obj)
    }

    fun addBezierCurve(doc: CanonicalDocument, points: List<Vec2>, strokeHex: String = "#38BDF8"): CanonicalDocument {
        if (points.size < 2) return doc
        val nodes = points.mapIndexed { idx, p ->
            val prev = points.getOrNull(idx - 1)
            val next = points.getOrNull(idx + 1)
            BezierNode(
                anchor = p,
                handleIn = prev?.let { Vec2((p.x + it.x) / 2f, p.y) },
                handleOut = next?.let { Vec2((p.x + it.x) / 2f, p.y) }
            )
        }
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val obj = VectorObject(
            id = "vec_path_${UUID.randomUUID().toString().take(8)}",
            kind = VectorKind.PATH,
            x = minX, y = minY,
            width = max(20f, maxX - minX),
            height = max(20f, maxY - minY),
            strokeColorHex = strokeHex,
            strokeWidth = 4f,
            nodes = nodes
        )
        return doc.copy(vectorObjects = doc.vectorObjects + obj)
    }

    enum class BezierHandlePart { ANCHOR, HANDLE_IN, HANDLE_OUT }

    fun snapCoord(value: Float, snapEnabled: Boolean, step: Float = 16f): Float =
        if (!snapEnabled) value else (value / step).roundToInt() * step

    fun findVectorAt(doc: CanonicalDocument, worldX: Float, worldY: Float, hitPad: Float = 24f): VectorObject? {
        return doc.vectorObjects.asReversed().firstOrNull { obj ->
            if (!obj.visible) return@firstOrNull false
            val inBox = worldX in (obj.x - hitPad)..(obj.x + obj.width + hitPad) &&
                worldY in (obj.y - hitPad)..(obj.y + obj.height + hitPad)
            val nearNode = obj.nodes.any { n ->
                hypotDist(n.anchor.x - worldX, n.anchor.y - worldY) <= hitPad * 1.4f ||
                    n.handleIn?.let { hypotDist(it.x - worldX, it.y - worldY) <= hitPad * 1.3f } == true ||
                    n.handleOut?.let { hypotDist(it.x - worldX, it.y - worldY) <= hitPad * 1.3f } == true
            }
            val nearTail = obj.tailTip?.let { hypotDist(it.x - worldX, it.y - worldY) <= hitPad * 1.4f } == true
            inBox || nearNode || nearTail
        }
    }

    private fun hypotDist(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    fun moveVectorObject(
        doc: CanonicalDocument,
        vectorId: String,
        dx: Float,
        dy: Float,
        snapEnabled: Boolean = false
    ): CanonicalDocument {
        return doc.copy(vectorObjects = doc.vectorObjects.map { obj ->
            if (obj.id != vectorId) obj
            else {
                val targetX = snapCoord(obj.x + dx, snapEnabled)
                val targetY = snapCoord(obj.y + dy, snapEnabled)
                val adx = targetX - obj.x
                val ady = targetY - obj.y
                obj.copy(
                    x = targetX,
                    y = targetY,
                    nodes = obj.nodes.map { n ->
                        BezierNode(
                            anchor = Vec2(n.anchor.x + adx, n.anchor.y + ady),
                            handleIn = n.handleIn?.let { Vec2(it.x + adx, it.y + ady) },
                            handleOut = n.handleOut?.let { Vec2(it.x + adx, it.y + ady) }
                        )
                    },
                    tailTip = obj.tailTip?.let { Vec2(it.x + adx, it.y + ady) }
                )
            }
        })
    }

    fun resizeVectorObject(
        doc: CanonicalDocument,
        vectorId: String,
        newX: Float,
        newY: Float,
        newWidth: Float,
        newHeight: Float,
        snapEnabled: Boolean = false
    ): CanonicalDocument {
        val sx = snapCoord(newX, snapEnabled)
        val sy = snapCoord(newY, snapEnabled)
        val sw = max(48f, snapCoord(newWidth, snapEnabled))
        val sh = max(36f, snapCoord(newHeight, snapEnabled))
        return doc.copy(vectorObjects = doc.vectorObjects.map { obj ->
            if (obj.id != vectorId) obj
            else obj.copy(x = sx, y = sy, width = sw, height = sh)
        })
    }

    fun moveBezierNodeOrHandle(
        doc: CanonicalDocument,
        vectorId: String,
        nodeIndex: Int,
        part: BezierHandlePart,
        worldPos: Vec2,
        snapEnabled: Boolean = false
    ): CanonicalDocument {
        val sp = Vec2(snapCoord(worldPos.x, snapEnabled), snapCoord(worldPos.y, snapEnabled))
        return doc.copy(vectorObjects = doc.vectorObjects.map { obj ->
            if (obj.id != vectorId || nodeIndex !in obj.nodes.indices) obj
            else {
                val updatedNodes = obj.nodes.mapIndexed { idx, node ->
                    if (idx != nodeIndex) node
                    else when (part) {
                        BezierHandlePart.ANCHOR -> {
                            val adx = sp.x - node.anchor.x
                            val ady = sp.y - node.anchor.y
                            node.copy(
                                anchor = sp,
                                handleIn = node.handleIn?.let { Vec2(it.x + adx, it.y + ady) },
                                handleOut = node.handleOut?.let { Vec2(it.x + adx, it.y + ady) }
                            )
                        }
                        BezierHandlePart.HANDLE_IN -> node.copy(handleIn = sp)
                        BezierHandlePart.HANDLE_OUT -> node.copy(handleOut = sp)
                    }
                }
                val pts = updatedNodes.flatMap { n -> listOfNotNull(n.anchor, n.handleIn, n.handleOut) }
                val minX = pts.minOfOrNull { it.x } ?: obj.x
                val minY = pts.minOfOrNull { it.y } ?: obj.y
                val maxX = pts.maxOfOrNull { it.x } ?: (obj.x + obj.width)
                val maxY = pts.maxOfOrNull { it.y } ?: (obj.y + obj.height)
                obj.copy(
                    x = minX,
                    y = minY,
                    width = max(24f, maxX - minX),
                    height = max(24f, maxY - minY),
                    nodes = updatedNodes
                )
            }
        })
    }

    fun moveBalloonTailTip(
        doc: CanonicalDocument,
        vectorId: String,
        tipPos: Vec2,
        snapEnabled: Boolean = false
    ): CanonicalDocument {
        val sp = Vec2(snapCoord(tipPos.x, snapEnabled), snapCoord(tipPos.y, snapEnabled))
        return doc.copy(vectorObjects = doc.vectorObjects.map { obj ->
            if (obj.id != vectorId) obj else obj.copy(tailTip = sp)
        })
    }

    fun updateVectorObjectText(doc: CanonicalDocument, vectorId: String, newText: String): CanonicalDocument =
        doc.copy(vectorObjects = doc.vectorObjects.map { if (it.id == vectorId) it.copy(text = newText) else it })

    fun updateVectorStyle(
        doc: CanonicalDocument,
        vectorId: String,
        text: String? = null,
        fontSizeSp: Float? = null,
        strokeHex: String? = null,
        fillHex: String? = null
    ): CanonicalDocument =
        doc.copy(vectorObjects = doc.vectorObjects.map { obj ->
            if (obj.id != vectorId) obj
            else obj.copy(
                text = text ?: obj.text,
                fontSizeSp = fontSizeSp?.coerceIn(8f, 72f) ?: obj.fontSizeSp,
                strokeColorHex = strokeHex ?: obj.strokeColorHex,
                fillColorHex = fillHex ?: obj.fillColorHex
            )
        })

    fun deleteVectorObject(doc: CanonicalDocument, vectorId: String): CanonicalDocument =
        doc.copy(vectorObjects = doc.vectorObjects.filterNot { it.id == vectorId })

    fun applyWorkshopTemplate(doc: CanonicalDocument, templateId: String): CanonicalDocument {
        val tpl = WORKSHOP_TEMPLATES.find { it.id == templateId } ?: return doc
        val stampedVectors = tpl.vectors.map { v ->
            v.copy(id = "vec_${UUID.randomUUID().toString().take(8)}")
        }
        val newFrames = if (doc.timeline.frames.size < tpl.frameCount) {
            doc.timeline.frames + TimelineFrame("frame_${doc.timeline.frames.size + 1}", "Frame ${doc.timeline.frames.size + 1}")
        } else doc.timeline.frames
        return doc.copy(
            vectorObjects = doc.vectorObjects + stampedVectors,
            timeline = doc.timeline.copy(frames = newFrames)
        )
    }

    /**
     * Generates deterministic SVG 1.1 vector + typography output preserving exact `<text>` strings,
     * cubic Bezier `<path>` curves, rounded shapes, and speech balloon tails.
     */
    fun exportDocumentToSvg(doc: CanonicalDocument): String {
        val b = doc.bounds
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="${b.minX} ${b.minY} ${b.width} ${b.height}" width="${b.width}" height="${b.height}">""")
        sb.appendLine("""  <!-- Infinite Studio SVG Export | doc=${escapeXml(doc.documentId)} | rev=${doc.revision} -->""")
        val paper = if (doc.paperColorHex.equals("transparent", ignoreCase = true)) "none" else doc.paperColorHex
        sb.appendLine("""  <rect x="${b.minX}" y="${b.minY}" width="${b.width}" height="${b.height}" fill="${escapeXml(paper)}" />""")

        doc.vectorObjects.filter { it.visible }.forEach { obj ->
            val rotAttr = if (obj.rotationDeg != 0f) {
                val cx = obj.x + obj.width / 2f
                val cy = obj.y + obj.height / 2f
                """ transform="rotate(${obj.rotationDeg} $cx $cy)""""
            } else ""

            when (obj.kind) {
                VectorKind.RECT -> {
                    sb.appendLine("""  <rect id="${escapeXml(obj.id)}" x="${obj.x}" y="${obj.y}" width="${obj.width}" height="${obj.height}" rx="12" fill="${escapeXml(obj.fillColorHex)}" stroke="${escapeXml(obj.strokeColorHex)}" stroke-width="${obj.strokeWidth}"$rotAttr />""")
                }
                VectorKind.ELLIPSE -> {
                    val cx = obj.x + obj.width / 2f
                    val cy = obj.y + obj.height / 2f
                    sb.appendLine("""  <ellipse id="${escapeXml(obj.id)}" cx="$cx" cy="$cy" rx="${obj.width / 2f}" ry="${obj.height / 2f}" fill="${escapeXml(obj.fillColorHex)}" stroke="${escapeXml(obj.strokeColorHex)}" stroke-width="${obj.strokeWidth}"$rotAttr />""")
                }
                VectorKind.PATH -> {
                    if (obj.nodes.size >= 2) {
                        val d = StringBuilder("M ${obj.nodes[0].anchor.x} ${obj.nodes[0].anchor.y}")
                        for (i in 1 until obj.nodes.size) {
                            val prev = obj.nodes[i - 1]
                            val curr = obj.nodes[i]
                            val c1 = prev.handleOut ?: prev.anchor
                            val c2 = curr.handleIn ?: curr.anchor
                            d.append(" C ${c1.x} ${c1.y}, ${c2.x} ${c2.y}, ${curr.anchor.x} ${curr.anchor.y}")
                        }
                        sb.appendLine("""  <path id="${escapeXml(obj.id)}" d="$d" fill="none" stroke="${escapeXml(obj.strokeColorHex)}" stroke-width="${obj.strokeWidth}" stroke-linecap="round"$rotAttr />""")
                    }
                }
                VectorKind.SPEECH_BALLOON -> {
                    sb.appendLine("""  <g id="${escapeXml(obj.id)}"$rotAttr>""")
                    sb.appendLine("""    <rect x="${obj.x}" y="${obj.y}" width="${obj.width}" height="${obj.height}" rx="28" fill="${escapeXml(obj.fillColorHex)}" stroke="${escapeXml(obj.strokeColorHex)}" stroke-width="${obj.strokeWidth}" />""")
                    val tip = obj.tailTip
                    if (tip != null) {
                        val bx = (obj.x + obj.width * 0.35f).coerceIn(obj.x + 24f, obj.x + obj.width - 24f)
                        val by = obj.y + obj.height - 2f
                        sb.appendLine("""    <polygon points="${bx - 16f},$by ${tip.x},${tip.y} ${bx + 16f},$by" fill="${escapeXml(obj.fillColorHex)}" stroke="${escapeXml(obj.strokeColorHex)}" stroke-width="${obj.strokeWidth}" />""")
                    }
                    val tx = obj.x + obj.width / 2f
                    val ty = obj.y + obj.height / 2f + obj.fontSizeSp * 0.4f
                    sb.appendLine("""    <text x="$tx" y="$ty" text-anchor="middle" font-family="Inter, sans-serif" font-weight="bold" font-size="${obj.fontSizeSp * 1.5f}" fill="${escapeXml(obj.strokeColorHex)}">${escapeXml(obj.text)}</text>""")
                    sb.appendLine("""  </g>""")
                }
                VectorKind.TEXT_BOX -> {
                    val tx = obj.x + obj.width / 2f
                    val ty = obj.y + obj.height / 2f + obj.fontSizeSp * 0.4f
                    sb.appendLine("""  <text id="${escapeXml(obj.id)}" x="$tx" y="$ty" text-anchor="middle" font-family="Inter, sans-serif" font-weight="bold" font-size="${obj.fontSizeSp * 1.5f}" fill="${escapeXml(obj.strokeColorHex)}"$rotAttr>${escapeXml(obj.text)}</text>""")
                }
            }
        }
        sb.appendLine("</svg>")
        return sb.toString()
    }

    // --- F08 Production Foundry ---
    /**
     * Packages transparent layer/object components, a packed sprite atlas PNG, artwork SVG,
     * and full provenance metadata into a self-contained ZIP archive.
     */
    fun exportFoundryBundleZip(doc: CanonicalDocument, renderer: SparseTileRenderer): ByteArray {
        val visibleLayers = doc.layers.filter { it.visible && it.tiles.isNotEmpty() }.ifEmpty { doc.layers.take(1) }
        val componentPairs = visibleLayers.map { layer ->
            val raw = renderer.materializeLayerBitmap(doc, layer)
            val cropped = cropToNonTransparentOrSmall(raw)
            if (cropped !== raw) raw.recycle()
            layer to cropped
        }

        // Pack into square-ish grid atlas
        val count = max(1, componentPairs.size)
        val cols = ceil(sqrt(count.toDouble())).toInt()
        val cellW = max(64, componentPairs.maxOfOrNull { it.second.width } ?: 64)
        val cellH = max(64, componentPairs.maxOfOrNull { it.second.height } ?: 64)
        val rows = ceil(count / cols.toDouble()).toInt()
        val atlasW = cols * cellW
        val atlasH = rows * cellH

        val atlasBmp = Bitmap.createBitmap(atlasW, atlasH, Bitmap.Config.ARGB_8888)
        val atlasCanvas = Canvas(atlasBmp)
        val spriteEntries = mutableListOf<FoundrySpriteEntry>()
        val componentBytes = LinkedHashMap<String, ByteArray>()

        componentPairs.forEachIndexed { idx, (layer, compBmp) ->
            val col = idx % cols
            val row = idx / cols
            val px = col * cellW
            val py = row * cellH
            atlasCanvas.drawBitmap(compBmp, px.toFloat(), py.toFloat(), null)

            val fileName = "components/${layer.id}.png"
            val compOut = ByteArrayOutputStream()
            compBmp.compress(Bitmap.CompressFormat.PNG, 100, compOut)
            componentBytes[fileName] = compOut.toByteArray()

            spriteEntries.add(
                FoundrySpriteEntry(
                    id = layer.id,
                    name = layer.name,
                    x = px,
                    y = py,
                    width = compBmp.width,
                    height = compBmp.height,
                    componentFile = fileName
                )
            )
            compBmp.recycle()
        }

        val atlasBaos = ByteArrayOutputStream()
        atlasBmp.compress(Bitmap.CompressFormat.PNG, 100, atlasBaos)
        atlasBmp.recycle()

        val manifest = FoundryManifest(
            documentId = doc.documentId,
            revision = doc.revision,
            title = doc.title,
            exportedAtIso = Instant.now().toString(),
            provenanceSummary = doc.revisions.takeLast(16).map { "rev ${it.revision}: [${it.processingMode}] ${it.summary}" },
            atlasWidth = atlasW,
            atlasHeight = atlasH,
            sprites = spriteEntries
        )

        val svgBytes = exportDocumentToSvg(doc).toByteArray(Charsets.UTF_8)
        val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("atlas.png"))
            zos.write(atlasBaos.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("artwork.svg"))
            zos.write(svgBytes)
            zos.closeEntry()

            doc.canonPassports.forEachIndexed { index, passport ->
                val path = "canon/passport_${index}.json"
                val payload = CanonPassportCodec.exportPortable(passport)
                zos.putNextEntry(ZipEntry(path))
                zos.write(payload)
                zos.closeEntry()
            }

            componentBytes.forEach { (path, data) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
            zos.finish()
        }
        return zipOut.toByteArray()
    }

    /**
     * Reimports and validates a Foundry ZIP bundle, verifying atlas bitmap, SVG text, and all component PNGs.
     */
    fun verifyFoundryBundleZip(zipBytes: ByteArray): Result<FoundryManifest> = runCatching {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                DocumentArchiveCodec.validateSafeEntryPath(entry.name)
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
            }
        }
        val manifestBytes = entries["manifest.json"] ?: error("Foundry ZIP missing manifest.json")
        val manifest = json.decodeFromString<FoundryManifest>(manifestBytes.toString(Charsets.UTF_8))
        val atlasBytes = entries["atlas.png"] ?: error("Foundry ZIP missing atlas.png")
        val atlasBmp = BitmapFactory.decodeByteArray(atlasBytes, 0, atlasBytes.size)
            ?: error("Corrupt atlas.png in Foundry ZIP")
        require(atlasBmp.width == manifest.atlasWidth && atlasBmp.height == manifest.atlasHeight) {
            "Atlas dimensions mismatch"
        }
        atlasBmp.recycle()
        require(entries.containsKey(manifest.svgFile)) { "Missing ${manifest.svgFile}" }
        manifest.sprites.forEach { s ->
            val comp = entries[s.componentFile] ?: error("Missing component ${s.componentFile}")
            val cb = BitmapFactory.decodeByteArray(comp, 0, comp.size) ?: error("Corrupt ${s.componentFile}")
            cb.recycle()
        }
        manifest
    }

    private fun cropToNonTransparentOrSmall(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val row = IntArray(w)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h step 2) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w step 2) {
                if ((row[x] ushr 24) > 2) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        }
        val cw = max(16, min(512, maxX - minX + 16))
        val ch = max(16, min(512, maxY - minY + 16))
        val sx = max(0, min(w - cw, minX - 8))
        val sy = max(0, min(h - ch, minY - 8))
        return Bitmap.createBitmap(src, sx, sy, min(cw, w - sx), min(ch, h - sy))
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
