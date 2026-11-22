package com.example.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.min

/**
 * Handles both:
 * 1. Canonical v3 ZIP-archive `.iastudio` container (manifest.json + typed binary tile PNGs + SHA-256 checksums)
 * 2. Legacy v2 JSON `.iastudio` import (from original browser studio) with lossless multi-layer tile slicing!
 *
 * Security & Integrity:
 * - Rejects zip-slip path traversal (`..` / leading `/`)
 * - Enforces max uncompressed byte budget and entry count
 * - Rejects truncated/corrupt archives and unsupported mandatory future schema versions
 * - Validates PNG signatures on all decoded tile assets
 */
object DocumentArchiveCodec {
    const val MAX_SUPPORTED_SCHEMA_VERSION = 3
    const val MAX_ARCHIVE_UNCOMPRESSED_BYTES = 192L * 1024L * 1024L
    const val MAX_ARCHIVE_ENTRIES = 8192

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Serializable
    data class ArchiveManifest(
        val format: String = "infinite-studio-archive",
        val schemaVersion: Int = CANONICAL_SCHEMA_VERSION,
        val document: CanonicalDocumentMeta,
        val assetChecksums: Map<String, String> = emptyMap(),
        val unknownFields: Map<String, String> = emptyMap()
    )

    @Serializable
    data class CanonicalDocumentMeta(
        val documentId: String,
        val revision: Long,
        val title: String,
        val bounds: DocumentBounds,
        val tileSize: Int,
        val paperColorHex: String,
        val layers: List<ArchiveLayerMeta>,
        val activeLayerId: String?,
        val vectorObjects: List<VectorObject> = emptyList(),
        val masks: List<SelectionMaskRecord> = emptyList(),
        val semanticObjects: List<SemanticObjectRecord> = emptyList(),
        val constraints: List<ConstraintRecord> = emptyList(),
        val materials: List<MaterialRegion> = emptyList(),
        val spatialStage: SpatialStageState = SpatialStageState(),
        val timeline: TimelineState = TimelineState(),
        val recipes: List<RecipeGraph> = emptyList(),
        val vaultAssets: List<VaultAssetRecord> = emptyList(),
        val characterSheets: List<CharacterSheet> = emptyList(),
        val canonPassports: List<CanonPassport> = emptyList(),
        val guidanceMarks: List<AiGuidanceMark> = emptyList(),
        val customPresets: List<CustomBrushPreset> = emptyList(),
        val branches: List<BranchNode> = emptyList(),
        val activeBranchId: String = "main",
        val revisions: List<RevisionProvenance> = emptyList(),
        val artDirectorHistory: List<ArtDirectorEntry> = emptyList()
    )

    @Serializable
    data class ArchiveLayerMeta(
        val id: String,
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val blendModeId: String,
        val alphaLock: Boolean,
        val transform: LayerTransform = LayerTransform(),
        val depthPlaneId: String? = null,
        val tileKeys: List<String> = emptyList()
    )

    @Serializable
    private data class LegacyV2Project(
        val format: String,
        val version: Int,
        val bounds: DocumentBounds,
        val paperColor: String = "#f4f1ea",
        val activeLayerId: String? = null,
        val layers: List<LegacyV2Layer>
    )

    @Serializable
    private data class LegacyV2Layer(
        val id: String,
        val name: String,
        val visible: Boolean,
        val opacity: Float,
        val blendMode: String = "source-over",
        val alphaLock: Boolean = false,
        val png: String
    )

    fun writeArchive(doc: CanonicalDocument, outputStream: OutputStream) {
        val checksums = LinkedHashMap<String, String>()
        val layerMetas = doc.layers.map { layer ->
            ArchiveLayerMeta(
                id = layer.id,
                name = layer.name,
                visible = layer.visible,
                opacity = layer.opacity,
                blendModeId = layer.blendModeId,
                alphaLock = layer.alphaLock,
                transform = layer.transform,
                depthPlaneId = layer.depthPlaneId,
                tileKeys = layer.tiles.keys.sorted()
            )
        }

        val zipEntries = LinkedHashMap<String, ByteArray>()
        doc.layers.forEach { layer ->
            layer.tiles.forEach { (tileKey, pngBytes) ->
                val path = "tiles/${sanitizeId(layer.id)}/$tileKey.png"
                zipEntries[path] = pngBytes
                checksums[path] = sha256Hex(pngBytes)
            }
        }

        val meta = CanonicalDocumentMeta(
            documentId = doc.documentId,
            revision = doc.revision,
            title = doc.title,
            bounds = doc.bounds,
            tileSize = doc.tileSize,
            paperColorHex = doc.paperColorHex,
            layers = layerMetas,
            activeLayerId = doc.activeLayerId,
            vectorObjects = doc.vectorObjects,
            masks = doc.masks,
            semanticObjects = doc.semanticObjects,
            constraints = doc.constraints,
            materials = doc.materials,
            spatialStage = doc.spatialStage,
            timeline = doc.timeline,
            recipes = doc.recipes,
            vaultAssets = doc.vaultAssets,
            characterSheets = doc.characterSheets,
            canonPassports = doc.canonPassports,
            guidanceMarks = doc.guidanceMarks,
            customPresets = doc.customPresets,
            branches = doc.branches,
            activeBranchId = doc.activeBranchId,
            revisions = doc.revisions,
            artDirectorHistory = doc.artDirectorHistory
        )

        val manifest = ArchiveManifest(
            document = meta,
            assetChecksums = checksums,
            unknownFields = doc.unknownFields
        )
        val manifestBytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)

        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zipEntries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
            zos.finish()
        }
    }

    fun writeArchiveToBytes(doc: CanonicalDocument): ByteArray {
        val baos = ByteArrayOutputStream()
        writeArchive(doc, baos)
        return baos.toByteArray()
    }

    fun readDocument(bytes: ByteArray): Result<CanonicalDocument> {
        return runCatching {
            require(bytes.size >= 4) { "Project file is empty or truncated." }
            // Check ZIP magic bytes PK\x03\x04
            if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                readZipArchive(ByteArrayInputStream(bytes))
            } else {
                val text = bytes.toString(Charsets.UTF_8)
                readLegacyOrJsonDocument(text)
            }
        }
    }

    fun readDocument(inputStream: InputStream): Result<CanonicalDocument> {
        val raw = inputStream.readBytes()
        return readDocument(raw)
    }

    private fun readZipArchive(inputStream: InputStream): CanonicalDocument {
        val entries = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L
        var entryCount = 0

        ZipInputStream(inputStream).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ARCHIVE_ENTRIES) { "Archive exceeds maximum entry count." }
                validateSafeEntryPath(entry.name)

                if (!entry.isDirectory) {
                    val buf = ByteArray(8192)
                    val out = ByteArrayOutputStream()
                    while (true) {
                        val read = zis.read(buf)
                        if (read <= 0) break
                        totalBytes += read
                        require(totalBytes <= MAX_ARCHIVE_UNCOMPRESSED_BYTES) {
                            "Archive exceeds uncompressed memory limit."
                        }
                        out.write(buf, 0, read)
                    }
                    entries[entry.name] = out.toByteArray()
                }
                zis.closeEntry()
            }
        }

        val manifestBytes = entries["manifest.json"]
            ?: error("Missing manifest.json in .iastudio archive.")
        val manifestText = manifestBytes.toString(Charsets.UTF_8)
        val rootObj = json.parseToJsonElement(manifestText).jsonObject
        val schemaVersion = rootObj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: error("Missing schemaVersion in manifest.")
        require(schemaVersion in 1..MAX_SUPPORTED_SCHEMA_VERSION) {
            "Unsupported mandatory document schema version: $schemaVersion (max supported: $MAX_SUPPORTED_SCHEMA_VERSION)."
        }

        val unknownTopLevel = extractUnknownStringFields(
            rootObj,
            setOf("format", "schemaVersion", "document", "assetChecksums", "unknownFields")
        )
        val manifest = json.decodeFromString<ArchiveManifest>(manifestText)
        require(manifest.format == "infinite-studio-archive") {
            "Invalid archive format identifier: ${manifest.format}"
        }
        val meta = manifest.document
        validateBounds(meta.bounds)
        require(meta.layers.isNotEmpty() && meta.layers.size <= 64) { "Invalid layer count." }

        val layers = meta.layers.map { lm ->
            val tileMap = LinkedHashMap<String, ByteArray>()
            val hashMap = LinkedHashMap<String, String>()
            lm.tileKeys.forEach { key ->
                require(TileKey.parse(key) != null) { "Invalid tile key: $key" }
                val path = "tiles/${sanitizeId(lm.id)}/$key.png"
                val tileBytes = entries[path] ?: error("Missing tile asset: $path")
                validatePngHeader(tileBytes, path)
                val actualSha = sha256Hex(tileBytes)
                val expectedSha = manifest.assetChecksums[path]
                if (expectedSha != null) {
                    require(actualSha == expectedSha) { "Checksum mismatch for $path" }
                }
                tileMap[key] = tileBytes
                hashMap[key] = actualSha
            }
            DocumentLayer(
                id = lm.id,
                name = lm.name,
                visible = lm.visible,
                opacity = lm.opacity.coerceIn(0f, 1f),
                blendModeId = lm.blendModeId,
                alphaLock = lm.alphaLock,
                transform = lm.transform,
                depthPlaneId = lm.depthPlaneId,
                tiles = tileMap,
                tileHashes = hashMap
            )
        }

        return CanonicalDocument(
            documentId = meta.documentId,
            schemaVersion = schemaVersion,
            revision = meta.revision,
            title = meta.title,
            bounds = meta.bounds,
            tileSize = meta.tileSize,
            paperColorHex = meta.paperColorHex,
            layers = layers,
            activeLayerId = if (layers.any { it.id == meta.activeLayerId }) meta.activeLayerId else layers.first().id,
            vectorObjects = meta.vectorObjects,
            masks = meta.masks,
            semanticObjects = meta.semanticObjects,
            constraints = meta.constraints,
            materials = meta.materials,
            spatialStage = meta.spatialStage,
            timeline = meta.timeline,
            recipes = meta.recipes.ifEmpty { listOf(RecipeGraph()) },
            vaultAssets = meta.vaultAssets,
            characterSheets = meta.characterSheets.ifEmpty { listOf(CharacterSheet()) },
            canonPassports = meta.canonPassports.map { it.copy(verifiedByCurrentUser = false) },
            guidanceMarks = meta.guidanceMarks,
            customPresets = meta.customPresets,
            branches = meta.branches,
            activeBranchId = meta.activeBranchId,
            revisions = meta.revisions,
            artDirectorHistory = meta.artDirectorHistory,
            unknownFields = manifest.unknownFields + unknownTopLevel
        )
    }

    /**
     * Migrates a legacy v2 `.iastudio` JSON document into CanonicalDocument v3
     * while preserving every layer separately (never silently flattening!).
     */
    private fun readLegacyOrJsonDocument(rawJson: String): CanonicalDocument {
        val rootObj = json.parseToJsonElement(rawJson).jsonObject
        val format = rootObj["format"]?.jsonPrimitive?.content ?: error("Missing project format.")
        if (format == "infinite-studio-project") {
            val version = rootObj["version"]?.jsonPrimitive?.intOrNull ?: 0
            require(version == 2) { "Unsupported legacy project version: $version" }
            val legacy = json.decodeFromString<LegacyV2Project>(rawJson)
            validateBounds(legacy.bounds)
            require(legacy.layers.isNotEmpty() && legacy.layers.size <= 40) { "Invalid legacy layer count." }

            val migratedLayers = legacy.layers.map { l ->
                require(l.png.startsWith("data:image/png;base64,")) { "Invalid legacy layer PNG payload." }
                val pngBytes = Base64.decode(l.png.substringAfter("data:image/png;base64,"), Base64.DEFAULT)
                validatePngHeader(pngBytes, l.id)
                val bmp = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                    ?: error("Corrupt PNG in legacy layer ${l.id}")
                require(bmp.width == legacy.bounds.width && bmp.height == legacy.bounds.height) {
                    "Legacy layer ${l.id} dimensions mismatch."
                }
                val tiles = sliceBitmapToSparseTiles(bmp, DEFAULT_TILE_SIZE)
                bmp.recycle()
                DocumentLayer(
                    id = l.id,
                    name = l.name,
                    visible = l.visible,
                    opacity = l.opacity.coerceIn(0f, 1f),
                    blendModeId = l.blendMode,
                    alphaLock = l.alphaLock,
                    tiles = tiles,
                    tileHashes = tiles.mapValues { (_, b) -> sha256Hex(b) }
                )
            }

            return CanonicalDocument(
                schemaVersion = CANONICAL_SCHEMA_VERSION,
                bounds = legacy.bounds,
                paperColorHex = legacy.paperColor,
                layers = migratedLayers,
                activeLayerId = if (migratedLayers.any { it.id == legacy.activeLayerId }) {
                    legacy.activeLayerId
                } else {
                    migratedLayers.first().id
                },
                revisions = listOf(
                    RevisionProvenance(
                        revision = 1L,
                        summary = "Migrated lossless from legacy .iastudio v2 (${migratedLayers.size} layers preserved)",
                        sourceHash = "legacy-v2",
                        processingMode = "MIGRATION_V2_TO_V3"
                    )
                )
            )
        }
        error("Unknown project format: $format")
    }

    fun sliceBitmapToSparseTiles(bitmap: Bitmap, tileSize: Int = DEFAULT_TILE_SIZE): Map<String, ByteArray> {
        val cols = ceil(bitmap.width / tileSize.toFloat()).toInt()
        val rows = ceil(bitmap.height / tileSize.toFloat()).toInt()
        val out = LinkedHashMap<String, ByteArray>()
        val rowPixels = IntArray(tileSize * tileSize)

        for (ty in 0 until rows) {
            for (tx in 0 until cols) {
                val x = tx * tileSize
                val y = ty * tileSize
                val w = min(tileSize, bitmap.width - x)
                val h = min(tileSize, bitmap.height - y)
                if (w <= 0 || h <= 0) continue

                bitmap.getPixels(rowPixels, 0, w, x, y, w, h)
                var hasNonZero = false
                for (i in 0 until w * h) {
                    if ((rowPixels[i] ushr 24) != 0) {
                        hasNonZero = true
                        break
                    }
                }
                if (!hasNonZero) continue

                val tileBmp = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
                tileBmp.setPixels(rowPixels, 0, w, 0, 0, w, h)
                val baos = ByteArrayOutputStream()
                tileBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                tileBmp.recycle()
                out[TileKey(tx, ty).storageKey()] = baos.toByteArray()
            }
        }
        return out
    }

    fun validateSafeEntryPath(entryName: String) {
        require(entryName.isNotEmpty() && !entryName.startsWith("/") && !entryName.startsWith("\\")) {
            "Unsafe absolute archive path: $entryName"
        }
        require(!entryName.contains("..")) {
            "Zip-slip path traversal detected: $entryName"
        }
        require(!entryName.contains('\u0000')) {
            "Invalid NUL byte in archive entry: $entryName"
        }
    }

    private fun validateBounds(bounds: DocumentBounds) {
        require(bounds.width in 1..MAX_DOCUMENT_SIDE && bounds.height in 1..MAX_DOCUMENT_SIDE) {
            "Document bounds out of supported limits (${bounds.width}x${bounds.height})."
        }
    }

    private fun validatePngHeader(bytes: ByteArray, label: String) {
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        require(bytes.size >= 24 && bytes.sliceArray(0..7).contentEquals(sig)) {
            "Corrupt or non-PNG asset in $label"
        }
    }

    private fun extractUnknownStringFields(obj: JsonObject, known: Set<String>): Map<String, String> {
        val extras = LinkedHashMap<String, String>()
        obj.entries.forEach { (k, v: JsonElement) ->
            if (k !in known) extras[k] = v.toString()
        }
        return extras
    }

    private fun sanitizeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
