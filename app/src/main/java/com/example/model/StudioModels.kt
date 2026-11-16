package com.example.model

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import com.example.document.CANONICAL_SCHEMA_VERSION
import com.example.document.CanonicalDocument
import com.example.document.CustomBrushPreset
import com.example.document.DocumentLayer
import com.example.document.LayerTransform
import com.example.document.SelectionMaskRecord
import com.example.features.BranchComparisonReport
import com.example.features.CherryPickPreview
import com.example.features.ForensicsReport
import com.example.features.FuturesTableSessionV3
import com.example.features.LivingPreviewSnapshot
import com.example.features.SpriteSheetExportResult
import com.example.input.StylusDiagnostics
import com.example.render.TileCacheStats
import com.example.services.ProviderMode
import kotlinx.serialization.Serializable

enum class StudioTool(val label: String) {
    PENCIL("6B Pencil"),
    INK("Tech Ink"),
    MARKER("Marker"),
    WATERCOLOR("Soft Wash"),
    AIRBRUSH("Airbrush"),
    SMUDGE("Smudge"),
    FILL("Fill"),
    ERASER("Eraser"),
    TRANSFORM("Transform"),
    VECTOR_EDIT("Vector"),
    LASSO("AI Lasso"),
    RECT_SELECT("AI Box"),
    EYEDROPPER("Eyedropper"),
    AI_GUIDANCE("AI Brush")
}

enum class WorkspaceMode(val label: String, val subtitle: String, val defaultHubTab: Int) {
    PAINT("Paint", "Brushes, ink & canvas", 0),
    EDIT("Edit", "Surgery, stage & vectors", 0),
    EXPLORE("Explore", "Futures Loom, branches & vault", 4),
    ANIMATE("Animate", "Time Canvas & onion skin", 2),
    PRODUCE("Produce", "Materials, recipes & Foundry", 1)
}

enum class CommandRingAction(val label: String) {
    BRUSH("Brush"),
    ERASER("Eraser"),
    SELECT("Select"),
    TRANSFORM("Transform"),
    VECTOR("Vector"),
    COLOR_PICKER("Color"),
    UNDO("Undo"),
    REDO("Redo"),
    FUTURES("Futures"),
    LOOM("Loom"),
    LAYERS("Layers"),
    EYEDROPPER("Picker")
}

data class InteractiveTransformState(
    val targetLayerId: String,
    val semanticObjectId: String? = null,
    val label: String,
    val initialTransform: LayerTransform,
    val snapEnabled: Boolean = true
)

enum class SymmetryMode(val label: String) {
    NONE("Sym: Off"),
    VERTICAL("Sym: Vertical"),
    HORIZONTAL("Sym: Horizontal"),
    QUADRANT("Sym: Quadrant"),
    KALEIDOSCOPE("Sym: Radial 8x")
}

enum class ShapeGuide(val label: String) {
    FREEHAND("Freehand"),
    LINE("Line Guide"),
    RECT("Box Guide"),
    ELLIPSE("Circle Guide")
}

enum class LayerBlendMode(
    val id: String,
    val displayName: String,
    val composeBlendMode: BlendMode,
    val porterDuffMode: PorterDuff.Mode
) {
    NORMAL("source-over", "Normal", BlendMode.SrcOver, PorterDuff.Mode.SRC_OVER),
    MULTIPLY("multiply", "Multiply", BlendMode.Multiply, PorterDuff.Mode.MULTIPLY),
    SCREEN("screen", "Screen", BlendMode.Screen, PorterDuff.Mode.SCREEN),
    OVERLAY("overlay", "Overlay", BlendMode.Overlay, PorterDuff.Mode.OVERLAY),
    DARKEN("darken", "Darken", BlendMode.Darken, PorterDuff.Mode.DARKEN),
    LIGHTEN("lighten", "Lighten", BlendMode.Lighten, PorterDuff.Mode.LIGHTEN);

    companion object {
        fun fromId(id: String): LayerBlendMode =
            entries.find { it.id == id } ?: NORMAL
    }
}

enum class PaperStyle(val id: String, val label: String, val colorInt: Int, val composeColor: Color) {
    BRIGHT_WHITE("#ffffff", "Bright White", 0xFFFFFFFF.toInt(), Color(0xFFFFFFFF)),
    WARM_SKETCHBOOK("#f4f1ea", "Warm Sketchbook", 0xFFF4F1EA.toInt(), Color(0xFFF4F1EA)),
    DARK_SLATE("#1e1e22", "Dark Slate", 0xFF1E1E22.toInt(), Color(0xFF1E1E22)),
    TRANSPARENT("transparent", "Transparent Grid", 0x00000000, Color.Transparent);

    companion object {
        fun fromId(id: String): PaperStyle =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: WARM_SKETCHBOOK
    }
}

@Serializable
data class CanvasBounds(
    val minX: Float = -1200f,
    val minY: Float = -800f,
    val width: Int = 2400,
    val height: Int = 1600
)

enum class SelectionType { LASSO, RECT, FULL }

data class SelectionState(
    val active: Boolean = false,
    val type: SelectionType? = null,
    val points: List<PointF> = emptyList(),
    val bounds: RectF? = null
)

enum class FutureBranchId(val id: String, val label: String, val directionSuffix: String) {
    SALT("salt", "Salt Print", " graphic ink and warm grain"),
    NIGHT("night", "Blue Hour", " nocturnal blue and silver"),
    EMBER("ember", "Copper Light", " warm copper and amber")
}

enum class FutureSource(val badge: String) {
    LOCAL("LOCAL STUDY"),
    API("API CANDIDATE")
}

enum class AiEditModel(val id: String, val label: String) {
    FLARE("gpt-image-2.6-flare", "Flare · fast exploration"),
    SUNBURST("gpt-image-2.6-sunburst", "Sunburst · deliberate detail")
}

enum class ExportFormat { PNG, JPG }

data class StudioUiState(
    val document: CanonicalDocument = CanonicalDocument(schemaVersion = CANONICAL_SCHEMA_VERSION),
    val activeTool: StudioTool = StudioTool.PENCIL,
    val activeBrushPreset: CustomBrushPreset = CustomBrushPreset(
        id = "preset_pencil",
        name = "6B Pencil",
        baseKind = "PENCIL",
        radius = 12f
    ),
    val brushColorHex: String = "#38BDF8",
    val brushColorInt: Int = 0xFF38BDF8.toInt(),
    val brushSize: Float = 12f,
    val brushOpacity: Float = 1.0f,
    val symmetryMode: SymmetryMode = SymmetryMode.NONE,
    val shapeGuide: ShapeGuide = ShapeGuide.FREEHAND,
    val sPenOnly: Boolean = false,
    val viewScale: Float = 1.0f,
    val viewOffsetX: Float = 0f,
    val viewOffsetY: Float = 0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selection: SelectionState = SelectionState(),
    val showColorPopover: Boolean = false,
    val showLayersPanel: Boolean = false,
    val showAiPromptDock: Boolean = false,
    val aiPromptBadge: String = "AI Selection Transform",
    val aiPromptText: String = "",
    val selectedAiModel: AiEditModel = AiEditModel.FLARE,
    val futuresSession: FuturesTableSessionV3? = null,
    val showProjectDialog: Boolean = false,
    val projectStatusMessage: String = "",
    val hasLocalDraft: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.PNG,
    val jpgQuality: Int = 95,
    val exportPreviewBitmap: Bitmap? = null,
    val statusBannerText: String = "Offline local canvas · zero network",
    val remainingCompanionCalls: Int = 3,
    val providerMode: ProviderMode = ProviderMode.OFFLINE_LOCAL_ONLY,
    val providerHealthSummary: String = "Strict offline mode active.",
    val showBrushStudio: Boolean = false,
    val showWorkspaceHub: Boolean = false,
    val workspaceTab: Int = 0,
    val workspaceMode: WorkspaceMode = WorkspaceMode.PAINT,
    val showFuturesLoom: Boolean = false,
    val branchComparison: BranchComparisonReport? = null,
    val cherryPickPreview: CherryPickPreview? = null,
    val selectedVectorId: String? = null,
    val vectorSnapEnabled: Boolean = true,
    val transformSession: InteractiveTransformState? = null,
    val spatialOrbitActive: Boolean = false,
    val timelinePlaying: Boolean = false,
    val activeProviderJobId: String? = null,
    val showCommandRing: Boolean = false,
    val commandRingAccessibleStrip: Boolean = false,
    val reducedMotionEnabled: Boolean = false,
    val commandRingSlots: List<CommandRingAction> = listOf(
        CommandRingAction.BRUSH,
        CommandRingAction.ERASER,
        CommandRingAction.SELECT,
        CommandRingAction.TRANSFORM,
        CommandRingAction.COLOR_PICKER,
        CommandRingAction.UNDO,
        CommandRingAction.FUTURES,
        CommandRingAction.LAYERS
    ),
    val showDiagnostics: Boolean = false,
    val stylusDiagnostics: StylusDiagnostics = StylusDiagnostics(),
    val tileCacheStats: TileCacheStats = TileCacheStats(0, 0, 48 * 1024 * 1024L, 0, 0, 0),
    val historyEstimatedBytes: Long = 0L,
    val historyStackSize: Int = 1,
    val forensicsReport: ForensicsReport? = null,
    val materialPreviewBitmap: Bitmap? = null,
    val spatialPreviewBitmap: Bitmap? = null,
    val spriteSheetPreviewBitmap: Bitmap? = null,
    val spriteSheetMeta: SpriteSheetExportResult? = null,
    val lastRecipeReportSummary: String = "Not executed yet.",
    val lastRecipeError: String? = null,
    val livingPreview: LivingPreviewSnapshot? = null,
    val recentErrors: List<String> = emptyList(),
    val toastMessage: String? = null,
    val toastNonce: Long = 0L,
    val renderTick: Long = 0L
) {
    val layers: List<DocumentLayer> get() = document.layers
    val activeLayerId: String? get() = document.activeLayerId
    val paperStyle: PaperStyle get() = PaperStyle.fromId(document.paperColorHex)
    val activeSelectionMask: SelectionMaskRecord?
        get() = document.masks.lastOrNull()
}

val CURATED_SWATCHES = listOf(
    "#FFFFFF", "#D4D4D8", "#71717A", "#27272A", "#09090B", "#78350F",
    "#EF4444", "#F97316", "#F59E0B", "#EAB308", "#84CC16", "#22C55E",
    "#10B981", "#14B8A6", "#06B6D4", "#38BDF8", "#3B82F6", "#6366F1",
    "#8B5CF6", "#A855F7", "#D946EF", "#EC4899", "#F43F5E", "#FDA4AF"
)

val QUICK_STYLE_CHIPS = listOf(
    "✨ Refine Sketch" to "Refine sketch into clean polished digital illustration, vibrant lighting",
    "📸 Photorealistic" to "Photorealistic studio render, detailed textures, dramatic rim light",
    "🎨 Watercolor" to "Lush watercolor and gouache painting on textured paper",
    "🌸 Anime Render" to "Anime key visual art style, cel shaded, crisp linework"
)
