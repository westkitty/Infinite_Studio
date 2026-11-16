package com.example.features

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.document.ArtDirectorEntry
import com.example.document.BranchNode
import com.example.document.CanonicalDocument
import com.example.document.CharacterSheet
import com.example.document.ConstraintKind
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.VaultAssetRecord
import com.example.model.FutureBranchId
import com.example.model.PaperStyle
import com.example.render.SparseTileRenderer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Serializable
data class OrchardSceneNode(
    val id: String,
    val title: String,
    val branchId: String,
    val x: Float,
    val y: Float,
    val linkedNodeIds: List<String> = emptyList()
)

data class VaultSearchHit(
    val asset: VaultAssetRecord,
    val colorDistance: Double,
    val matchedTags: List<String>
)

data class ArtDirectorExecutionResult(
    val updatedDoc: CanonicalDocument,
    val applied: Boolean,
    val message: String
)

data class BranchDiffSummary(
    val branchAId: String,
    val branchBId: String,
    val sharedLayerCount: Int,
    val onlyInA: List<String>,
    val onlyInB: List<String>,
    val identicalContent: Boolean
)

data class BranchComparisonReport(
    val branchAId: String,
    val branchBId: String,
    val branchALabel: String,
    val branchBLabel: String,
    val sharedLayerCount: Int,
    val onlyInA: List<String>,
    val onlyInB: List<String>,
    val modifiedSharedLayers: List<String>,
    val vectorCountDiff: String,
    val materialCountDiff: String,
    val identicalContent: Boolean,
    val changedPixelPercent: Float,
    val protectedRegionWarnings: List<String>,
    val bitmapA: Bitmap,
    val bitmapB: Bitmap,
    val diffOverlayBitmap: Bitmap
)

enum class CherryPickResolution { RENAME_NEW, REPLACE_MATCHING }

data class CherryPickPreview(
    val fromBranchId: String,
    val fromBranchLabel: String,
    val sourceLayerId: String,
    val sourceLayerName: String,
    val sourceLayerContentHash: String,
    val tileCount: Int,
    val collidingCurrentLayerId: String?,
    val collidingCurrentLayerName: String?,
    val proposedRenamedName: String,
    val protectedConstraintWarnings: List<String>
)

enum class VaultApprovalFilter { ALL, APPROVED_ONLY, UNAPPROVED_ONLY }

/**
 * Implements:
 * - F10 Visual Memory Vault (user assets, tag & RGB color similarity search, explicit approval flag)
 * - P04 Character Studio (sheets, proportion rulers, palette consistency)
 * - P05 Conversational Art Director (deterministic local command grammar with transparent provenance)
 * - P07 Creative Branches (immutable archive snapshots: fork, diff, rollback, cherry-pick with zero aliasing)
 * - P08 World Orchard (spatial story/branch map navigation)
 */
object VaultBranchOrchardEngine {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // --- F10 Visual Memory Vault ---
    fun addVaultAsset(
        doc: CanonicalDocument,
        title: String,
        sourceUri: String,
        sourceId: String,
        tags: List<String>,
        bitmap: Bitmap?,
        rightsNote: String = "User-selected local reference",
        userApproved: Boolean = false
    ): Pair<CanonicalDocument, VaultAssetRecord> {
        val dominantColors = if (bitmap != null) extractDominantColors(bitmap) else listOf("#38BDF8")
        val thumbBytes = if (bitmap != null) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
            scaled.recycle()
            baos.toByteArray()
        } else null

        val record = VaultAssetRecord(
            id = "vault_${UUID.randomUUID().toString().take(8)}",
            title = title.ifBlank { "Reference ${doc.vaultAssets.size + 1}" },
            sourceUri = sourceUri,
            sourceId = sourceId,
            tags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() },
            dominantColorHexes = dominantColors,
            rightsNote = rightsNote,
            userApproved = userApproved,
            thumbnailPng = thumbBytes,
            linkedToDocument = true
        )
        return doc.copy(vaultAssets = doc.vaultAssets + record) to record
    }

    fun setVaultAssetApproval(doc: CanonicalDocument, assetId: String, approved: Boolean): CanonicalDocument {
        val updatedAssets = doc.vaultAssets.map { if (it.id == assetId) it.copy(userApproved = approved) else it }
        return doc.copy(vaultAssets = updatedAssets)
    }

    fun searchVaultAssets(
        doc: CanonicalDocument,
        queryTag: String? = null,
        targetColorHex: String? = null,
        maxColorDistance: Double = 220.0,
        queryName: String? = null,
        sourceFilter: String? = null,
        assetTypeFilter: String? = null,
        approvalFilter: VaultApprovalFilter = VaultApprovalFilter.ALL
    ): List<VaultSearchHit> {
        val normTag = queryTag?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val normName = queryName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val normSource = sourceFilter?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "all" }
        val normType = assetTypeFilter?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != "ALL" }
        val targetRgb = targetColorHex?.takeIf { it.isNotBlank() }?.let { parseHexSafe(it) }

        return doc.vaultAssets.mapNotNull { asset ->
            if (approvalFilter == VaultApprovalFilter.APPROVED_ONLY && !asset.userApproved) return@mapNotNull null
            if (approvalFilter == VaultApprovalFilter.UNAPPROVED_ONLY && asset.userApproved) return@mapNotNull null
            if (normSource != null && !asset.sourceLabel.lowercase().contains(normSource) &&
                !asset.sourceUri.lowercase().contains(normSource) && !asset.sourceId.lowercase().contains(normSource)
            ) return@mapNotNull null
            if (normType != null && asset.assetType.uppercase() != normType) return@mapNotNull null
            if (normName != null && !asset.title.lowercase().contains(normName)) return@mapNotNull null

            val matchedTags = if (normTag != null) {
                asset.tags.filter { it.contains(normTag) || asset.title.lowercase().contains(normTag) }
            } else emptyList()

            if (normTag != null && matchedTags.isEmpty()) return@mapNotNull null

            val bestDist = if (targetRgb != null && asset.dominantColorHexes.isNotEmpty()) {
                asset.dominantColorHexes.minOf { hex ->
                    colorDistanceRgb(targetRgb, parseHexSafe(hex))
                }
            } else 0.0

            if (targetRgb != null && bestDist > maxColorDistance) return@mapNotNull null

            VaultSearchHit(asset = asset, colorDistance = bestDist, matchedTags = matchedTags)
        }.sortedBy { it.colorDistance }
    }

    fun extractDominantColors(bitmap: Bitmap, maxColors: Int = 3): List<String> {
        val bins = LinkedHashMap<Int, Int>()
        val stepX = maxOf(1, bitmap.width / 16)
        val stepY = maxOf(1, bitmap.height / 16)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val c = bitmap.getPixel(x, y)
                if (Color.alpha(c) < 64) continue
                // Quantize to 32-step grid
                val qr = (Color.red(c) / 32) * 32 + 16
                val qg = (Color.green(c) / 32) * 32 + 16
                val qb = (Color.blue(c) / 32) * 32 + 16
                val key = Color.rgb(qr.coerceAtMost(255), qg.coerceAtMost(255), qb.coerceAtMost(255))
                bins[key] = (bins[key] ?: 0) + 1
            }
        }
        if (bins.isEmpty()) return listOf("#38BDF8")
        return bins.entries.sortedByDescending { it.value }.take(maxColors).map {
            String.format("#%06X", 0xFFFFFF and it.key)
        }
    }

    // --- P04 Character Studio ---
    fun toggleProportionRuler(doc: CanonicalDocument, sheetId: String = doc.characterSheets.firstOrNull()?.id ?: "char_default"): CanonicalDocument {
        val sheets = doc.characterSheets.ifEmpty { listOf(CharacterSheet()) }
        return doc.copy(characterSheets = sheets.map {
            if (it.id == sheetId) it.copy(showProportionRuler = !it.showProportionRuler) else it
        })
    }

    fun updateCharacterSheetNotes(
        doc: CanonicalDocument,
        sheetId: String,
        name: String,
        proportionNotes: String,
        poseNotes: String
    ): CanonicalDocument {
        return doc.copy(characterSheets = doc.characterSheets.map {
            if (it.id == sheetId) it.copy(name = name, proportionNotes = proportionNotes, poseNotes = poseNotes) else it
        })
    }

    fun updateCharacterSheetFull(
        doc: CanonicalDocument,
        sheetId: String = doc.characterSheets.firstOrNull()?.id ?: "char_default",
        name: String,
        proportionNotes: String,
        poseNotes: String,
        turnaroundNotes: String,
        paletteHexes: List<String>,
        expressionTags: List<String>,
        poseTags: List<String>,
        referenceAssetIds: List<String>,
        referenceProvenanceNote: String
    ): CanonicalDocument {
        val sheets = doc.characterSheets.ifEmpty { listOf(CharacterSheet()) }
        return doc.copy(characterSheets = sheets.map { sheet ->
            if (sheet.id == sheetId) {
                sheet.copy(
                    name = name.ifBlank { "Character Study" },
                    proportionNotes = proportionNotes,
                    poseNotes = poseNotes,
                    turnaroundNotes = turnaroundNotes,
                    paletteHexes = paletteHexes.ifEmpty { sheet.paletteHexes },
                    expressionTags = expressionTags,
                    poseTags = poseTags,
                    referenceAssetIds = referenceAssetIds,
                    referenceProvenanceNote = referenceProvenanceNote
                )
            } else sheet
        })
    }

    fun setCharacterCanonApproval(
        doc: CanonicalDocument,
        sheetId: String = doc.characterSheets.firstOrNull()?.id ?: "char_default",
        userApproved: Boolean
    ): CanonicalDocument {
        val sheets = doc.characterSheets.ifEmpty { listOf(CharacterSheet()) }
        return doc.copy(characterSheets = sheets.map {
            if (it.id == sheetId) it.copy(userApprovedCanon = userApproved) else it
        })
    }

    // --- P07 Creative Branches (Immutable archive snapshots — zero mutable aliasing!) ---
    fun forkBranch(
        doc: CanonicalDocument,
        label: String,
        note: String = "",
        renderer: SparseTileRenderer? = null
    ): CanonicalDocument {
        val branchId = "br_${UUID.randomUUID().toString().take(8)}"
        val snapshotBytes = DocumentArchiveCodec.writeArchiveToBytes(doc.copy(branches = emptyList()))
        val thumbPng = renderer?.let { captureBranchThumbnailPng(doc, it) }
        val parentNode = doc.branches.find { it.branchId == doc.activeBranchId }
        val ancestry = if (parentNode != null) {
            (parentNode.ancestryPath + parentNode.branchId).distinct()
        } else {
            listOf("main")
        }
        val node = BranchNode(
            branchId = branchId,
            parentBranchId = doc.activeBranchId,
            label = label.ifBlank { "Branch ${doc.branches.size + 1}" },
            note = note,
            sourceHash = doc.documentContentHash().take(16),
            processingMode = "LOCAL_DETERMINISTIC",
            snapshotJsonBytes = snapshotBytes,
            thumbnailPng = thumbPng,
            ancestryPath = ancestry,
            revisionAtFork = doc.revision
        )
        // Ensure "main" checkpoint also exists if branches was empty
        val baseList = if (doc.branches.isEmpty()) {
            listOf(
                BranchNode(
                    branchId = "main",
                    parentBranchId = null,
                    label = "Main Trunk",
                    note = "Initial trunk baseline",
                    sourceHash = doc.documentContentHash().take(16),
                    snapshotJsonBytes = snapshotBytes,
                    thumbnailPng = thumbPng,
                    ancestryPath = emptyList(),
                    revisionAtFork = doc.revision
                )
            )
        } else doc.branches

        return doc.copy(
            branches = baseList + node,
            activeBranchId = branchId
        )
    }

    private fun captureBranchThumbnailPng(doc: CanonicalDocument, renderer: SparseTileRenderer): ByteArray? {
        return runCatching {
            val region = renderer.computeArtworkBoundingBox(doc, padding = 24)
                ?: RectF(-400f, -260f, 400f, 260f)
            val full = renderer.renderFlattenedRegion(doc, region, includePaper = true, includeVectors = true)
            val scaled = Bitmap.createScaledBitmap(full, 160, 100, true)
            if (scaled !== full) full.recycle()
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 95, baos)
            scaled.recycle()
            baos.toByteArray()
        }.getOrNull()
    }

    fun rollbackToBranch(doc: CanonicalDocument, targetBranchId: String): CanonicalDocument? {
        val node = doc.branches.find { it.branchId == targetBranchId } ?: return null
        val snapBytes = node.snapshotJsonBytes ?: return null
        val restored = DocumentArchiveCodec.readDocument(snapBytes).getOrNull() ?: return null
        // Preserve branch registry and switch activeBranchId cleanly
        return restored.copy(
            branches = doc.branches,
            activeBranchId = targetBranchId,
            revision = doc.revision + 1L
        )
    }

    fun previewCherryPickLayer(
        doc: CanonicalDocument,
        fromBranchId: String,
        layerNameOrId: String
    ): CherryPickPreview? {
        val node = doc.branches.find { it.branchId == fromBranchId } ?: return null
        val snapBytes = node.snapshotJsonBytes ?: return null
        val sourceDoc = DocumentArchiveCodec.readDocument(snapBytes).getOrNull() ?: return null
        val srcLayer = sourceDoc.layers.find { it.id == layerNameOrId || it.name == layerNameOrId } ?: return null

        val colliding = doc.layers.find { it.name.equals(srcLayer.name, ignoreCase = true) || it.id == srcLayer.id }
        val warnings = mutableListOf<String>()
        if (colliding != null) {
            warnings.add("Collision: current document already contains '${colliding.name}'. Choose Rename New or Replace Matching.")
        }
        val lockedConstraints = doc.constraints.filter { it.userLocked && (it.kind == ConstraintKind.PROTECTED_SILHOUETTE || it.kind == ConstraintKind.FORBIDDEN_REGION) }
        if (lockedConstraints.isNotEmpty() && srcLayer.tiles.isNotEmpty()) {
            warnings.add("Active doc has ${lockedConstraints.size} locked constraint(s); inspect Forensics after cherry-pick.")
        }

        return CherryPickPreview(
            fromBranchId = fromBranchId,
            fromBranchLabel = node.label,
            sourceLayerId = srcLayer.id,
            sourceLayerName = srcLayer.name,
            sourceLayerContentHash = srcLayer.pixelContentHash().take(16),
            tileCount = srcLayer.tiles.size,
            collidingCurrentLayerId = colliding?.id,
            collidingCurrentLayerName = colliding?.name,
            proposedRenamedName = "${srcLayer.name} (from ${node.label})",
            protectedConstraintWarnings = warnings
        )
    }

    fun cherryPickLayerWithResolution(
        doc: CanonicalDocument,
        fromBranchId: String,
        layerNameOrId: String,
        resolution: CherryPickResolution = CherryPickResolution.RENAME_NEW
    ): CanonicalDocument? {
        val node = doc.branches.find { it.branchId == fromBranchId } ?: return null
        val snapBytes = node.snapshotJsonBytes ?: return null
        val sourceDoc = DocumentArchiveCodec.readDocument(snapBytes).getOrNull() ?: return null
        val srcLayer = sourceDoc.layers.find { it.id == layerNameOrId || it.name == layerNameOrId } ?: return null

        // Deep-copy ByteArrays & assign fresh hashes so zero mutable aliasing can ever happen
        val copiedTiles = srcLayer.tiles.mapValues { (_, bytes) -> bytes.copyOf() }
        val copiedHashes = copiedTiles.mapValues { (_, bytes) -> DocumentArchiveCodec.sha256Hex(bytes) }

        if (resolution == CherryPickResolution.REPLACE_MATCHING) {
            val target = doc.layers.find { it.name.equals(srcLayer.name, ignoreCase = true) || it.id == srcLayer.id }
            if (target != null) {
                val replaced = target.copy(
                    visible = srcLayer.visible,
                    opacity = srcLayer.opacity,
                    blendModeId = srcLayer.blendModeId,
                    alphaLock = srcLayer.alphaLock,
                    transform = srcLayer.transform,
                    tiles = copiedTiles,
                    tileHashes = copiedHashes
                )
                return doc.copy(
                    layers = doc.layers.map { if (it.id == target.id) replaced else it },
                    activeLayerId = replaced.id
                )
            }
        }

        val cloned = srcLayer.copy(
            id = "pick_${UUID.randomUUID().toString().take(8)}",
            name = "${srcLayer.name} (from ${node.label})",
            tiles = copiedTiles,
            tileHashes = copiedHashes
        )
        return doc.copy(layers = doc.layers + cloned, activeLayerId = cloned.id)
    }

    fun cherryPickLayerFromBranch(doc: CanonicalDocument, fromBranchId: String, layerNameOrId: String): CanonicalDocument? =
        cherryPickLayerWithResolution(doc, fromBranchId, layerNameOrId, CherryPickResolution.RENAME_NEW)

    fun readBranchSnapshotDocument(doc: CanonicalDocument, branchId: String): CanonicalDocument? {
        val bBytes = doc.branches.find { it.branchId == branchId }?.snapshotJsonBytes ?: return null
        return DocumentArchiveCodec.readDocument(bBytes).getOrNull()
    }

    fun compareBranches(doc: CanonicalDocument, branchAId: String, branchBId: String): BranchDiffSummary? {
        val docA = readBranchSnapshotDocument(doc, branchAId) ?: return null
        val docB = readBranchSnapshotDocument(doc, branchBId) ?: return null

        val aNames = docA.layers.map { it.name }.toSet()
        val bNames = docB.layers.map { it.name }.toSet()
        return BranchDiffSummary(
            branchAId = branchAId,
            branchBId = branchBId,
            sharedLayerCount = aNames.intersect(bNames).size,
            onlyInA = (aNames - bNames).toList(),
            onlyInB = (bNames - aNames).toList(),
            identicalContent = docA.documentContentHash() == docB.documentContentHash()
        )
    }

    fun buildBranchComparisonReport(
        doc: CanonicalDocument,
        branchAId: String,
        branchBId: String,
        renderer: SparseTileRenderer,
        previewWidth: Int = 360,
        previewHeight: Int = 220
    ): BranchComparisonReport? {
        val nodeA = doc.branches.find { it.branchId == branchAId } ?: return null
        val nodeB = doc.branches.find { it.branchId == branchBId } ?: return null
        val docA = readBranchSnapshotDocument(doc, branchAId) ?: return null
        val docB = readBranchSnapshotDocument(doc, branchBId) ?: return null

        val aByName = docA.layers.associateBy { it.name }
        val bByName = docB.layers.associateBy { it.name }
        val sharedNames = aByName.keys.intersect(bByName.keys)
        val modifiedShared = sharedNames.filter { name ->
            aByName[name]?.pixelContentHash() != bByName[name]?.pixelContentHash()
        }.sorted()

        val regionA = renderer.computeArtworkBoundingBox(docA, padding = 24)
        val regionB = renderer.computeArtworkBoundingBox(docB, padding = 24)
        val unionRegion = when {
            regionA != null && regionB != null -> RectF(
                min(regionA.left, regionB.left),
                min(regionA.top, regionB.top),
                max(regionA.right, regionB.right),
                max(regionA.bottom, regionB.bottom)
            )
            regionA != null -> regionA
            regionB != null -> regionB
            else -> RectF(-400f, -260f, 400f, 260f)
        }

        val rawA = renderer.renderFlattenedRegion(docA, unionRegion, includePaper = true, includeVectors = true)
        val rawB = renderer.renderFlattenedRegion(docB, unionRegion, includePaper = true, includeVectors = true)
        val bmpA = Bitmap.createScaledBitmap(rawA, previewWidth, previewHeight, true).also { rawA.recycle() }
        val bmpB = Bitmap.createScaledBitmap(rawB, previewWidth, previewHeight, true).also { rawB.recycle() }

        val totalPx = previewWidth * previewHeight
        val pxA = IntArray(totalPx)
        val pxB = IntArray(totalPx)
        val diffPx = IntArray(totalPx)
        bmpA.getPixels(pxA, 0, previewWidth, 0, 0, previewWidth, previewHeight)
        bmpB.getPixels(pxB, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        var changedCount = 0
        for (i in 0 until totalPx) {
            val c1 = pxA[i]
            val c2 = pxB[i]
            val dMax = max(
                max(abs(((c1 ushr 16) and 0xFF) - ((c2 ushr 16) and 0xFF)), abs(((c1 ushr 8) and 0xFF) - ((c2 ushr 8) and 0xFF))),
                abs((c1 and 0xFF) - (c2 and 0xFF))
            )
            if (dMax > 8) {
                changedCount++
                val intensity = min(255, 80 + dMax * 2)
                diffPx[i] = Color.argb(230, 56, intensity, 248)
            } else {
                // Desaturated dim underlay for unchanged pixels
                val luma = ((((c1 ushr 16) and 0xFF) * 3 + ((c1 ushr 8) and 0xFF) * 6 + (c1 and 0xFF)) / 10).coerceIn(0, 255)
                val dim = (luma * 0.35f).roundToInt()
                diffPx[i] = Color.argb(255, dim, dim, dim)
            }
        }
        val diffBmp = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(diffPx, 0, previewWidth, 0, 0, previewWidth, previewHeight)
        }

        val warnings = mutableListOf<String>()
        val lockedConstraints = (docA.constraints + docB.constraints).distinctBy { it.label }.filter { it.userLocked }
        if (lockedConstraints.isNotEmpty()) {
            warnings.add("${lockedConstraints.size} locked constraint(s) active across compared branches.")
        }

        return BranchComparisonReport(
            branchAId = branchAId,
            branchBId = branchBId,
            branchALabel = nodeA.label,
            branchBLabel = nodeB.label,
            sharedLayerCount = sharedNames.size,
            onlyInA = (aByName.keys - bByName.keys).toList(),
            onlyInB = (bByName.keys - aByName.keys).toList(),
            modifiedSharedLayers = modifiedShared,
            vectorCountDiff = "${docA.vectorObjects.size} vs ${docB.vectorObjects.size} vectors",
            materialCountDiff = "${docA.materials.size} vs ${docB.materials.size} materials",
            identicalContent = docA.documentContentHash() == docB.documentContentHash(),
            changedPixelPercent = (changedCount * 100f) / max(1, totalPx),
            protectedRegionWarnings = warnings,
            bitmapA = bmpA,
            bitmapB = bmpB,
            diffOverlayBitmap = diffBmp
        )
    }

    // --- P08 World Orchard ---
    fun readOrchardNodes(doc: CanonicalDocument): List<OrchardSceneNode> {
        val raw = doc.unknownFields["world_orchard"] ?: return defaultOrchardNodes(doc)
        return runCatching { json.decodeFromString<List<OrchardSceneNode>>(raw) }
            .getOrDefault(defaultOrchardNodes(doc))
    }

    fun addOrchardNode(doc: CanonicalDocument, title: String, branchId: String, x: Float, y: Float): CanonicalDocument {
        val existing = readOrchardNodes(doc)
        val node = OrchardSceneNode(
            id = "orch_${UUID.randomUUID().toString().take(6)}",
            title = title.ifBlank { "Scene ${existing.size + 1}" },
            branchId = branchId,
            x = x,
            y = y,
            linkedNodeIds = listOfNotNull(existing.lastOrNull()?.id)
        )
        val encoded = json.encodeToString(existing + node)
        return doc.copy(unknownFields = doc.unknownFields + ("world_orchard" to encoded))
    }

    private fun defaultOrchardNodes(doc: CanonicalDocument): List<OrchardSceneNode> = listOf(
        OrchardSceneNode("orch_root", doc.title, doc.activeBranchId, 0f, 0f)
    )

    // --- P05 Conversational Art Director (Local deterministic command grammar) ---
    fun executeArtDirectorCommand(
        doc: CanonicalDocument,
        instruction: String,
        renderer: SparseTileRenderer
    ): ArtDirectorExecutionResult {
        val trimmed = instruction.trim()
        val lower = trimmed.lowercase()
        val activeLayer = doc.layers.find { it.id == doc.activeLayerId }
        val selSummary = if (doc.masks.isEmpty()) "Full canvas" else "${doc.masks.last().name}"

        fun record(nextDoc: CanonicalDocument, op: String, applied: Boolean, msg: String): ArtDirectorExecutionResult {
            val entry = ArtDirectorEntry(
                id = "ad_${UUID.randomUUID().toString().take(8)}",
                instruction = trimmed,
                targetLayerId = activeLayer?.id,
                selectionSummary = selSummary,
                proposedOperation = op,
                applied = applied
            )
            return ArtDirectorExecutionResult(
                updatedDoc = nextDoc.copy(artDirectorHistory = nextDoc.artDirectorHistory + entry),
                applied = applied,
                message = msg
            )
        }

        return when {
            lower.startsWith("add text ") -> {
                val txt = trimmed.substring(9).trim()
                val next = VectorAndFoundryEngine.addTextBox(doc, txt)
                record(next, "VectorAndFoundry.addTextBox('$txt')", true, "Added wrapped text box '$txt'.")
            }
            lower.startsWith("add balloon ") -> {
                val txt = trimmed.substring(12).trim()
                val next = VectorAndFoundryEngine.addSpeechBalloon(doc, txt)
                record(next, "VectorAndFoundry.addSpeechBalloon('$txt')", true, "Added speech balloon '$txt'.")
            }
            "duplicate layer" in lower && activeLayer != null -> {
                val copy = activeLayer.copy(
                    id = "layer_${UUID.randomUUID().toString().take(8)}",
                    name = "${activeLayer.name} Copy",
                    tiles = activeLayer.tiles.mapValues { (_, b) -> b.copyOf() }
                )
                val next = doc.copy(layers = doc.layers + copy, activeLayerId = copy.id)
                record(next, "DuplicateLayer('${activeLayer.name}')", true, "Duplicated '${activeLayer.name}'.")
            }
            lower.startsWith("add layer") -> {
                val name = trimmed.removePrefix("add layer").removePrefix("Add layer").trim().ifBlank { "Layer ${doc.layers.size + 1}" }
                val newLayer = DocumentLayer(id = "layer_${UUID.randomUUID().toString().take(8)}", name = name)
                val next = doc.copy(layers = doc.layers + newLayer, activeLayerId = newLayer.id)
                record(next, "AddLayer('$name')", true, "Added empty layer '$name'.")
            }
            "alpha lock on" in lower && activeLayer != null -> {
                val next = doc.copy(layers = doc.layers.map { if (it.id == activeLayer.id) it.copy(alphaLock = true) else it })
                record(next, "SetAlphaLock(true)", true, "Alpha Lock enabled on '${activeLayer.name}'.")
            }
            "alpha lock off" in lower && activeLayer != null -> {
                val next = doc.copy(layers = doc.layers.map { if (it.id == activeLayer.id) it.copy(alphaLock = false) else it })
                record(next, "SetAlphaLock(false)", true, "Alpha Lock disabled on '${activeLayer.name}'.")
            }
            Regex("opacity\\s+(\\d+)").find(lower) != null && activeLayer != null -> {
                val pct = Regex("opacity\\s+(\\d+)").find(lower)!!.groupValues[1].toInt().coerceIn(0, 100)
                val next = doc.copy(layers = doc.layers.map { if (it.id == activeLayer.id) it.copy(opacity = pct / 100f) else it })
                record(next, "SetLayerOpacity($pct%)", true, "Set '${activeLayer.name}' opacity to $pct%.")
            }
            lower.startsWith("fork branch") -> {
                val label = trimmed.substringAfter("fork branch", "").trim().ifBlank { "Director Branch" }
                val next = forkBranch(doc, label, "Created by Art Director command")
                record(next, "ForkBranch('$label')", true, "Forked branch '$label'.")
            }
            "ruler on" in lower || "proportion ruler" in lower -> {
                val next = toggleProportionRuler(doc)
                record(next, "ToggleProportionRuler", true, "Toggled Character Studio proportion ruler.")
            }
            lower.startsWith("paper ") -> {
                val style = when {
                    "dark" in lower -> PaperStyle.DARK_SLATE
                    "white" in lower -> PaperStyle.BRIGHT_WHITE
                    "transparent" in lower -> PaperStyle.TRANSPARENT
                    else -> PaperStyle.WARM_SKETCHBOOK
                }
                val next = doc.copy(paperColorHex = style.id)
                record(next, "SetPaper('${style.label}')", true, "Paper set to ${style.label}.")
            }
            "run study" in lower || "salt" in lower || "blue hour" in lower || "copper" in lower -> {
                val branchIdx = when {
                    "blue" in lower || "night" in lower -> 1
                    "copper" in lower || "ember" in lower -> 2
                    else -> 0
                }
                val session = FuturesAndConstraintsEngine.buildFuturesSession(doc, renderer, doc.masks.lastOrNull())
                val (next, layer) = FuturesAndConstraintsEngine.acceptBranchToNewLayer(doc, session, branchIdx)
                record(next, "ApplyLocalColorStudy(${FutureBranchId.entries[branchIdx].label})", true, "Created study layer '${layer.name}'.")
            }
            else -> record(
                doc,
                "UNSUPPORTED_COMMAND",
                false,
                "Supported local commands: 'add text <txt>', 'add balloon <txt>', 'add layer <name>', 'duplicate layer', 'opacity 50', 'alpha lock on/off', 'fork branch <name>', 'paper <dark|warm|white>', 'run study <salt|blue|copper>'."
            )
        }
    }

    private fun parseHexSafe(hex: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.GRAY
    }

    private fun colorDistanceRgb(c1: Int, c2: Int): Double {
        val dr = (Color.red(c1) - Color.red(c2)).toDouble()
        val dg = (Color.green(c1) - Color.green(c2)).toDouble()
        val db = (Color.blue(c1) - Color.blue(c2)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
