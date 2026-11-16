package com.example.document

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

const val CANONICAL_SCHEMA_VERSION = 3
const val DEFAULT_TILE_SIZE = 256
const val MAX_DOCUMENT_SIDE = 16384

@Serializable
data class Vec2(
    val x: Float,
    val y: Float
)

@Serializable
data class DocumentBounds(
    val minX: Float = -1200f,
    val minY: Float = -800f,
    val width: Int = 2400,
    val height: Int = 1600,
    val units: String = "px"
) {
    val maxX: Float get() = minX + width
    val maxY: Float get() = minY + height
}

@Serializable
data class TileKey(
    val tx: Int,
    val ty: Int
) {
    fun storageKey(): String = "${tx}_${ty}"

    companion object {
        fun parse(raw: String): TileKey? {
            val parts = raw.split('_')
            if (parts.size != 2) return null
            val x = parts[0].toIntOrNull() ?: return null
            val y = parts[1].toIntOrNull() ?: return null
            return TileKey(x, y)
        }
    }
}

@Serializable
data class LayerTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f
)

@Serializable
data class DocumentLayer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blendModeId: String = "source-over",
    val alphaLock: Boolean = false,
    val transform: LayerTransform = LayerTransform(),
    val depthPlaneId: String? = null,
    val tiles: Map<String, ByteArray> = emptyMap(),
    val tileHashes: Map<String, String> = emptyMap()
) {
    fun pixelContentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        tiles.toSortedMap().forEach { (k, bytes) ->
            digest.update(k.toByteArray(Charsets.UTF_8))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@Serializable
enum class VectorKind { PATH, RECT, ELLIPSE, TEXT_BOX, SPEECH_BALLOON }

@Serializable
data class BezierNode(
    val anchor: Vec2,
    val handleIn: Vec2? = null,
    val handleOut: Vec2? = null
)

@Serializable
data class VectorObject(
    val id: String = "vec_${UUID.randomUUID().toString().take(8)}",
    val kind: VectorKind,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 240f,
    val height: Float = 120f,
    val strokeColorHex: String = "#09090B",
    val fillColorHex: String = "#FFFFFF",
    val strokeWidth: Float = 3f,
    val text: String = "",
    val fontSizeSp: Float = 18f,
    val nodes: List<BezierNode> = emptyList(),
    val tailTip: Vec2? = null,
    val rotationDeg: Float = 0f,
    val visible: Boolean = true
)

@Serializable
data class SelectionMaskRecord(
    val id: String,
    val name: String,
    val points: List<Vec2>,
    val inverted: Boolean = false,
    val createdAtIso: String = Instant.now().toString()
)

@Serializable
data class SemanticObjectRecord(
    val id: String,
    val name: String,
    val sourceLayerId: String,
    val extractedLayerId: String,
    val boundsX: Float,
    val boundsY: Float,
    val boundsW: Float,
    val boundsH: Float,
    val pivotX: Float,
    val pivotY: Float,
    val maskPoints: List<Vec2>,
    val inferredBgRepairPng: ByteArray? = null,
    val repairLabel: String = "LOCAL INFERRED REPAIR (SPECULATIVE)"
)

@Serializable
enum class ConstraintKind {
    PROTECTED_SILHOUETTE,
    FORBIDDEN_REGION,
    MATERIAL_NOTE,
    REQUIRED_TRAIT
}

@Serializable
data class ConstraintRecord(
    val id: String,
    val kind: ConstraintKind,
    val label: String,
    val note: String = "",
    val points: List<Vec2> = emptyList(),
    val userLocked: Boolean = true,
    val referenceId: String? = null
)

@Serializable
data class MaterialRegion(
    val id: String,
    val name: String,
    val targetLayerId: String,
    val baseColorHex: String = "#38BDF8",
    val roughness: Float = 0.35f,
    val metalness: Float = 0.2f,
    val alpha: Float = 1.0f,
    val emissiveHex: String = "#000000",
    val emissionIntensity: Float = 0.6f,
    val reliefStrength: Float = 0.4f,
    val lightAzimuthDeg: Float = 45f,
    val lightElevationDeg: Float = 55f,
    val maskId: String? = null
)

@Serializable
data class DepthPlane(
    val id: String,
    val name: String,
    val zDepth: Float,
    val parallaxFactor: Float,
    val assignedLayerIds: List<String> = emptyList()
)

@Serializable
data class SpatialStageState(
    val enabled: Boolean = false,
    val vanishingPoint: Vec2 = Vec2(0f, -120f),
    val cameraYawDeg: Float = 0f,
    val cameraPitchDeg: Float = 0f,
    val cameraZoom: Float = 1f,
    val lightPos: Vec2 = Vec2(280f, -260f),
    val lightIntensity: Float = 1.0f,
    val depthPlanes: List<DepthPlane> = listOf(
        DepthPlane("plane_bg", "Background", zDepth = 2.0f, parallaxFactor = 0.45f),
        DepthPlane("plane_mid", "Midground", zDepth = 1.0f, parallaxFactor = 1.0f),
        DepthPlane("plane_fg", "Foreground", zDepth = 0.35f, parallaxFactor = 1.35f)
    )
)

@Serializable
data class LayerKeyframe(
    val layerId: String,
    val visible: Boolean = true,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val opacity: Float = 1f
)

@Serializable
data class TimelineFrame(
    val id: String,
    val label: String,
    val durationMs: Int = 125,
    val layerKeyframes: List<LayerKeyframe> = emptyList()
)

@Serializable
data class TimelineState(
    val fps: Int = 8,
    val currentFrameIndex: Int = 0,
    val onionSkinEnabled: Boolean = true,
    val onionSkinBeforeOpacity: Float = 0.25f,
    val onionSkinAfterEnabled: Boolean = false,
    val onionSkinAfterOpacity: Float = 0.18f,
    val loopStartIndex: Int = 0,
    val loopEndIndex: Int = -1,
    val frames: List<TimelineFrame> = listOf(
        TimelineFrame("frame_1", "Frame 1", 125),
        TimelineFrame("frame_2", "Frame 2", 125)
    )
)

@Serializable
enum class RecipeNodeKind { CLEANUP, LINE_WORK, FLAT_COLOR, MATERIAL_PREVIEW, EXPORT_PREP }

@Serializable
data class RecipeNode(
    val id: String,
    val kind: RecipeNodeKind,
    val title: String,
    val parameters: Map<String, String> = emptyMap(),
    val inputNodeIds: List<String> = emptyList(),
    val posX: Float = 0f,
    val posY: Float = 0f
)

@Serializable
data class RecipeStageResult(
    val nodeId: String,
    val cacheKey: String,
    val status: String,
    val outputHash: String,
    val computedAtIso: String
)

@Serializable
data class RecipeGraph(
    val id: String = "recipe_default",
    val name: String = "Standard Ink & Shading Pipeline",
    val nodes: List<RecipeNode> = listOf(
        RecipeNode("n_cleanup", RecipeNodeKind.CLEANUP, "Paper & Contrast Cleanup", mapOf("threshold" to "118")),
        RecipeNode("n_line", RecipeNodeKind.LINE_WORK, "Ink Line Extraction", mapOf("weight" to "1.4"), listOf("n_cleanup")),
        RecipeNode("n_flat", RecipeNodeKind.FLAT_COLOR, "Flat Color Study", mapOf("palette" to "salt"), listOf("n_line")),
        RecipeNode("n_mat", RecipeNodeKind.MATERIAL_PREVIEW, "Material Shade Pass", mapOf("roughness" to "0.35"), listOf("n_flat"))
    ),
    val results: Map<String, RecipeStageResult> = emptyMap()
)

@Serializable
data class VaultAssetRecord(
    val id: String,
    val title: String,
    val sourceUri: String,
    val sourceId: String,
    val tags: List<String> = emptyList(),
    val dominantColorHexes: List<String> = emptyList(),
    val rightsNote: String = "User-supplied local reference",
    val userApproved: Boolean = false,
    val thumbnailPng: ByteArray? = null,
    val linkedToDocument: Boolean = false,
    val assetType: String = "REFERENCE",
    val sourceLabel: String = "Local Capture"
)

@Serializable
data class CharacterSheet(
    val id: String = "char_default",
    val name: String = "Character Study",
    val referenceAssetIds: List<String> = emptyList(),
    val paletteHexes: List<String> = listOf("#38BDF8", "#F4F1EA", "#1E1E22"),
    val proportionNotes: String = "Head-to-body ratio 1:7.5; eye line mid-cranium",
    val poseNotes: String = "Three-quarter dynamic stance",
    val turnaroundNotes: String = "Front / 3-4 / Side / Back aligned on horizon",
    val expressionTags: List<String> = listOf("Neutral", "Focused"),
    val poseTags: List<String> = listOf("3/4 Turnaround", "Idle Stance"),
    val referenceProvenanceNote: String = "Artist-authored notes & explicit local references only",
    val showProportionRuler: Boolean = false,
    val userApprovedCanon: Boolean = false
)

@Serializable
data class BranchNode(
    val branchId: String,
    val parentBranchId: String? = null,
    val label: String,
    val note: String = "",
    val sourceHash: String,
    val processingMode: String = "LOCAL_DETERMINISTIC",
    val createdAtIso: String = Instant.now().toString(),
    val snapshotJsonBytes: ByteArray? = null,
    val thumbnailPng: ByteArray? = null,
    val ancestryPath: List<String> = emptyList(),
    val revisionAtFork: Long = 1L
)

@Serializable
data class RevisionProvenance(
    val revision: Long,
    val summary: String,
    val sourceHash: String,
    val processingMode: String = "LOCAL_EDIT",
    val providerId: String? = null,
    val timestampIso: String = Instant.now().toString()
)

@Serializable
data class ArtDirectorEntry(
    val id: String,
    val instruction: String,
    val targetLayerId: String?,
    val selectionSummary: String,
    val proposedOperation: String,
    val applied: Boolean,
    val timestampIso: String = Instant.now().toString()
)

@Serializable
data class AiGuidanceMark(
    val id: String,
    val kind: String, // "STROKE", "ARROW", "COLOR_HINT"
    val points: List<Vec2>,
    val colorHex: String,
    val note: String = ""
)

@Serializable
data class CustomBrushPreset(
    val id: String,
    val name: String,
    val baseKind: String,
    val radius: Float = 12f,
    val flow: Float = 1.0f,
    val opacity: Float = 1.0f,
    val hardness: Float = 0.85f,
    val spacing: Float = 0.25f,
    val smoothing: Float = 0.5f,
    val pressureCurve: Float = 1.0f,
    val angleDeg: Float = 0f,
    val textureStrength: Float = 0.3f
)

@Serializable
data class CanonicalDocument(
    val documentId: String = "doc_${UUID.randomUUID().toString().take(8)}",
    val schemaVersion: Int = CANONICAL_SCHEMA_VERSION,
    val revision: Long = 1L,
    val title: String = "Untitled Studio",
    val bounds: DocumentBounds = DocumentBounds(),
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val paperColorHex: String = "#f4f1ea",
    val layers: List<DocumentLayer> = emptyList(),
    val activeLayerId: String? = null,
    val vectorObjects: List<VectorObject> = emptyList(),
    val masks: List<SelectionMaskRecord> = emptyList(),
    val semanticObjects: List<SemanticObjectRecord> = emptyList(),
    val constraints: List<ConstraintRecord> = emptyList(),
    val materials: List<MaterialRegion> = emptyList(),
    val spatialStage: SpatialStageState = SpatialStageState(),
    val timeline: TimelineState = TimelineState(),
    val recipes: List<RecipeGraph> = listOf(RecipeGraph()),
    val vaultAssets: List<VaultAssetRecord> = emptyList(),
    val characterSheets: List<CharacterSheet> = listOf(CharacterSheet()),
    val guidanceMarks: List<AiGuidanceMark> = emptyList(),
    val customPresets: List<CustomBrushPreset> = emptyList(),
    val branches: List<BranchNode> = emptyList(),
    val activeBranchId: String = "main",
    val revisions: List<RevisionProvenance> = emptyList(),
    val artDirectorHistory: List<ArtDirectorEntry> = emptyList(),
    val unknownFields: Map<String, String> = emptyMap()
) {
    fun documentContentHash(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        layers.forEach { layer ->
            digest.update(layer.id.toByteArray())
            digest.update(layer.pixelContentHash().toByteArray())
        }
        vectorObjects.forEach { v ->
            digest.update("${v.id}:${v.text}:${v.x}:${v.y}".toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
