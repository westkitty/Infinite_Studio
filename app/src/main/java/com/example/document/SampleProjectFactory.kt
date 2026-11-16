package com.example.document

import android.graphics.Color
import com.example.features.FuturesAndConstraintsEngine
import com.example.features.ObjectSurgeryAndMaterialEngine
import com.example.features.TimeAndRecipeEngine
import com.example.features.VaultBranchOrchardEngine
import com.example.features.VectorAndFoundryEngine
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.tools.TileBrushEngine

/**
 * Deterministically synthesizes a repeatable offline sample project exercising:
 * - Multiple raster layers (ink + wash) with preserved original hashes
 * - Active polygon selection & protected silhouette constraint
 * - Futures Table local study acceptance (zero pixels outside mask)
 * - Vector speech balloon with exact lettering + Bezier path
 * - Material intelligence shading region
 * - Creative Branch fork + provenance
 * - Creative Recipe DAG execution
 * - Time Canvas multi-frame keyframes + onion skin
 */
object SampleProjectFactory {

    fun buildSignatureDemo(renderer: SparseTileRenderer): CanonicalDocument {
        val base = CanonicalDocument(
            documentId = "doc_sample_futures",
            title = "One Mark, Three Futures",
            layers = listOf(
                DocumentLayer(id = "layer_ink", name = "Original Ink Mark"),
                DocumentLayer(id = "layer_wash", name = "Sky Underpaint")
            ),
            activeLayerId = "layer_ink"
        )

        var doc = TileBrushEngine.commitStrokeToDocument(
            doc = base,
            renderer = renderer,
            targetLayerId = "layer_ink",
            worldPoints = listOf(
                Vec2(-140f, 60f) to 0.7f,
                Vec2(-40f, -90f) to 0.9f,
                Vec2(80f, -40f) to 0.85f,
                Vec2(140f, 90f) to 0.75f
            ),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.INK,
                colorInt = Color.rgb(22, 32, 48),
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.INK, 14f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )

        doc = TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "layer_wash",
            worldPoints = listOf(
                Vec2(-110f, -20f) to 0.65f,
                Vec2(10f, -30f) to 0.8f,
                Vec2(110f, 20f) to 0.7f
            ),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.WATERCOLOR,
                colorInt = Color.rgb(56, 189, 248),
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.WATERCOLOR, 36f, 0.85f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )

        val mask = SelectionMaskRecord(
            id = "mask_sample_lantern",
            name = "Protected Study Region",
            points = listOf(
                Vec2(-120f, -110f),
                Vec2(120f, -110f),
                Vec2(120f, 110f),
                Vec2(-120f, 110f)
            )
        )
        doc = doc.copy(masks = listOf(mask))
        doc = FuturesAndConstraintsEngine.addConstraint(
            doc = doc,
            kind = ConstraintKind.PROTECTED_SILHOUETTE,
            label = "Lantern Silhouette Lock",
            points = listOf(
                Vec2(-160f, -150f),
                Vec2(-130f, -150f),
                Vec2(-130f, -120f),
                Vec2(-160f, -120f)
            ),
            note = "Preserve outer margin untouched"
        )

        // Explore Futures locally & accept Copper Light (index 2) onto a new layer
        val session = FuturesAndConstraintsEngine.buildFuturesSession(doc, renderer, mask, "warm twilight lantern")
        val (withFuture, _) = FuturesAndConstraintsEngine.acceptBranchToNewLayer(doc, session, 2)
        doc = withFuture
        session.originalBitmap.recycle()
        session.maskBitmap.recycle()
        session.branches.forEach { it.candidateBitmap.recycle(); it.previewBitmap.recycle() }

        // Vector speech balloon + Bezier ribbon
        doc = VectorAndFoundryEngine.addSpeechBalloon(
            doc = doc,
            text = "ONE MARK, THREE FUTURES",
            x = -190f,
            y = -270f
        )
        doc = VectorAndFoundryEngine.addBezierCurve(
            doc = doc,
            points = listOf(Vec2(-160f, 130f), Vec2(0f, 180f), Vec2(160f, 130f))
        )

        // Material region + lighting
        val material = MaterialRegion(
            id = "mat_copper",
            name = "Hammered Copper",
            targetLayerId = "layer_ink",
            baseColorHex = "#D4763E",
            roughness = 0.32f,
            metalness = 0.85f,
            reliefStrength = 0.55f
        )
        doc = ObjectSurgeryAndMaterialEngine.applyMaterialPreviewAsNewLayer(doc, renderer, material)

        // Creative branch fork
        doc = VaultBranchOrchardEngine.forkBranch(doc, "Copper Study Branch", "Sample offline fixture")

        // Recipe DAG execution
        val recipeRun = TimeAndRecipeEngine.executeRecipeGraph(doc)
        doc = doc.copy(recipes = listOf(recipeRun.updatedGraph))

        // Timeline frame capture + second frame
        doc = TimeAndRecipeEngine.captureCurrentLayersToFrame(doc)
        doc = TimeAndRecipeEngine.addTimelineFrame(doc)
        return doc
    }

    /**
     * Synthesizes a rich offline "Living Workshop Journey" exercising connected Futures Loom,
     * Creative Branches with PNG thumbnails, Object Surgery, Spatial Stage depth planes,
     * Masked Material Intelligence, Vector & Typography, Timeline onion skins,
     * Creative Recipe DAG cache, Visual Memory Vault, and Character Study notes.
     */
    fun buildLivingWorkshopJourney(renderer: SparseTileRenderer): CanonicalDocument {
        var doc = buildSignatureDemo(renderer).copy(
            documentId = "doc_living_workshop",
            title = "Living Workshop Journey"
        )

        // F01: Extract lantern study region non-destructively (source layer pixels remain intact)
        val studyMask = doc.masks.firstOrNull() ?: SelectionMaskRecord(
            id = "mask_lantern_core",
            name = "Lantern Core",
            points = listOf(Vec2(-120f, -110f), Vec2(120f, -110f), Vec2(120f, 110f), Vec2(-120f, 110f))
        )
        val (extractedDoc, semObj) = ObjectSurgeryAndMaterialEngine.extractObjectFromSelection(
            doc = doc,
            renderer = renderer,
            sourceLayerId = "layer_ink",
            mask = studyMask,
            objectName = "Lantern Study"
        )
        doc = ObjectSurgeryAndMaterialEngine.moveSemanticObject(extractedDoc, semObj.id, 18f, -12f)

        // F02: Assign layers to 2.5D depth planes & modest parallax camera
        doc = ObjectSurgeryAndMaterialEngine.assignLayerToDepthPlane(doc, "layer_wash", "plane_bg")
        doc = ObjectSurgeryAndMaterialEngine.assignLayerToDepthPlane(doc, "layer_ink", "plane_mid")
        doc = ObjectSurgeryAndMaterialEngine.assignLayerToDepthPlane(doc, semObj.extractedLayerId, "plane_fg")
        doc = ObjectSurgeryAndMaterialEngine.updateSpatialStage(doc) {
            it.copy(enabled = false, cameraYawDeg = 4f, cameraPitchDeg = 2f, cameraZoom = 1.0f)
        }

        // F09: Add crisp Text Box alongside existing Speech Balloon & Bezier Path
        doc = VectorAndFoundryEngine.addTextBox(doc, "STUDIO WORKSHOP STUDY", x = -140f, y = 210f)

        // F06: Masked Material region with explicit emission & elevation
        val maskedMat = MaterialRegion(
            id = "mat_rim_emissive",
            name = "Rim Warm Glow",
            targetLayerId = "layer_ink",
            baseColorHex = "#38BDF8",
            roughness = 0.25f,
            metalness = 0.65f,
            emissiveHex = "#F59E0B",
            emissionIntensity = 0.45f,
            reliefStrength = 0.6f,
            lightAzimuthDeg = 48f,
            lightElevationDeg = 60f,
            maskId = studyMask.id
        )
        doc = ObjectSurgeryAndMaterialEngine.upsertMaterial(doc, maskedMat)

        // F10 & P04: Add local reference into Visual Memory Vault & artist-authored character study (never auto-canon)
        val thumbBmp = renderer.renderFlattenedRegion(doc, android.graphics.RectF(-260f, -200f, 260f, 200f))
        val (vaultDoc, asset) = VaultBranchOrchardEngine.addVaultAsset(
            doc = doc,
            title = "Lantern Ink Reference",
            sourceUri = "local://sample/lantern_ink",
            sourceId = "sample_fixture",
            tags = listOf("lantern", "ink", "copper"),
            bitmap = thumbBmp,
            rightsNote = "Offline synthetic fixture (Zero external canon)",
            userApproved = true
        )
        thumbBmp.recycle()
        doc = VaultBranchOrchardEngine.updateCharacterSheetFull(
            doc = vaultDoc,
            name = "Workshop Figure Study",
            proportionNotes = "Head-to-body 1:7.5; balanced center of gravity",
            poseNotes = "Three-quarter silhouette exploration",
            turnaroundNotes = "Front / 3-4 / Back reference frame",
            paletteHexes = listOf("#162030", "#38BDF8", "#D4763E"),
            expressionTags = listOf("Focused", "Calm"),
            poseTags = listOf("Standing 3/4", "Lantern Hold"),
            referenceAssetIds = listOf(asset.id),
            referenceProvenanceNote = "Local procedural fixture; userApprovedCanon remains false until user toggles"
        )

        // F05: Configure multi-frame keyframe delta & onion skin
        doc = TimeAndRecipeEngine.updateLayerKeyframe(
            doc = doc,
            frameIndex = 1,
            layerId = semObj.extractedLayerId,
            offsetX = 28f,
            offsetY = -18f,
            opacity = 0.95f,
            visible = true
        )
        doc = TimeAndRecipeEngine.updateOnionSkinConfig(
            doc = doc,
            enabled = true,
            beforeOpacity = 0.25f,
            afterEnabled = true,
            afterOpacity = 0.18f
        )

        // P07 & P08: Fork a second branch with PNG thumbnail + add Orchard scene node
        doc = VaultBranchOrchardEngine.forkBranch(
            doc = doc,
            label = "Blue Hour Alt",
            note = "Alternative silhouette & lighting study",
            renderer = renderer
        )
        doc = VaultBranchOrchardEngine.addOrchardNode(
            doc = doc,
            title = "Lantern Beat 02",
            branchId = doc.activeBranchId,
            x = 180f,
            y = 75f
        )

        // F04: Ensure layout and execute DAG so cache hashes are populated
        val laidOut = TimeAndRecipeEngine.ensureNodeLayout(doc.recipes.first())
        doc = doc.copy(recipes = listOf(laidOut))
        val recipeReport = TimeAndRecipeEngine.executeRecipeGraph(doc)
        doc = doc.copy(recipes = listOf(recipeReport.updatedGraph))
        return doc
    }
}
