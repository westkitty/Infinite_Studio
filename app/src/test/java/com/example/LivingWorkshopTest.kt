package com.example

import android.app.Application
import android.graphics.Color
import android.graphics.PointF
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.document.CanonicalDocument
import com.example.document.CharacterSheet
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.LayerTransform
import com.example.document.RecipeNodeKind
import com.example.document.SelectionMaskRecord
import com.example.document.SampleProjectFactory
import com.example.document.Vec2
import com.example.features.CherryPickResolution
import com.example.features.FuturesAndConstraintsEngine
import com.example.features.ObjectSurgeryAndMaterialEngine
import com.example.features.TimeAndRecipeEngine
import com.example.features.VaultApprovalFilter
import com.example.features.VaultBranchOrchardEngine
import com.example.features.VectorAndFoundryEngine
import com.example.model.CommandRingAction
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.model.WorkspaceMode
import com.example.render.SparseTileRenderer
import com.example.services.ProviderMode
import com.example.tools.TileBrushEngine
import com.example.ui.theme.InfiniteStudioTheme
import com.example.viewmodel.StudioViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LivingWorkshopTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun seedCanvas(renderer: SparseTileRenderer): CanonicalDocument {
        val doc = CanonicalDocument(
            title = "Living Workshop Test",
            layers = listOf(DocumentLayer("layer_ink", "Ink Base")),
            activeLayerId = "layer_ink"
        )
        return TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "layer_ink",
            worldPoints = listOf(Vec2(-120f, -80f) to 0.8f, Vec2(120f, 80f) to 0.9f),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.INK,
                colorInt = Color.rgb(56, 189, 248),
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.INK, 22f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )
    }

    @Test
    fun `P07 and Futures Loom fork thumbnails compare diff overlay and conflict-aware cherry pick persist across v3 archive`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvas(renderer)
        doc = VaultBranchOrchardEngine.forkBranch(doc, "Warm Ink", "Initial ink study", renderer)
        val br1 = doc.activeBranchId
        val br1Node = doc.branches.first { it.branchId == br1 }
        assertNotNull(br1Node.thumbnailPng)
        assertTrue(br1Node.thumbnailPng!!.isNotEmpty())
        assertTrue(br1Node.ancestryPath.isNotEmpty())

        // Modify active branch before second fork so pixel comparison shows delta
        doc = doc.copy(
            layers = doc.layers + DocumentLayer("layer_accent", "Accent Wash")
        )
        doc = TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "layer_accent",
            worldPoints = listOf(Vec2(-40f, 100f) to 0.9f, Vec2(140f, -40f) to 0.9f),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.MARKER,
                colorInt = Color.rgb(245, 158, 11),
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.MARKER, 28f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )
        doc = VaultBranchOrchardEngine.forkBranch(doc, "Amber Overlay", "Accent added", renderer)
        val br2 = doc.activeBranchId

        val report = VaultBranchOrchardEngine.buildBranchComparisonReport(doc, br1, br2, renderer)
        assertNotNull(report)
        assertTrue(report!!.changedPixelPercent > 0f)
        assertTrue(report.onlyInB.contains("Accent Wash"))
        assertEquals(report.bitmapA.width, report.diffOverlayBitmap.width)

        // Cherry-pick colliding Ink Base as RENAME_NEW & REPLACE_MATCHING
        val preview = VaultBranchOrchardEngine.previewCherryPickLayer(doc, br1, "Ink Base")
        assertNotNull(preview)
        assertNotNull(preview!!.collidingCurrentLayerId)

        val renamedDoc = VaultBranchOrchardEngine.cherryPickLayerWithResolution(
            doc, br1, "Ink Base", CherryPickResolution.RENAME_NEW
        )!!
        assertTrue(renamedDoc.layers.any { it.name.contains("Ink Base (from") })

        val replacedDoc = VaultBranchOrchardEngine.cherryPickLayerWithResolution(
            doc, br1, "Ink Base", CherryPickResolution.REPLACE_MATCHING
        )!!
        assertEquals(doc.layers.size, replacedDoc.layers.size)

        // Save / reload archive preserves branches, thumbnails, and ancestry
        val archiveBytes = DocumentArchiveCodec.writeArchiveToBytes(replacedDoc)
        val reopened = DocumentArchiveCodec.readDocument(archiveBytes).getOrThrow()
        assertEquals(replacedDoc.branches.size, reopened.branches.size)
        assertEquals(replacedDoc.activeBranchId, reopened.activeBranchId)
        assertNotNull(reopened.branches.first { it.branchId == br1 }.thumbnailPng)
    }

    @Test
    fun `F01 F02 and F09 direct manipulation mask refine camera reset Bezier handles and SVG export`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvas(renderer)
        val originalSourceHash = doc.layers.first().pixelContentHash()

        val mask = SelectionMaskRecord(
            id = "m_obj",
            name = "Hero Box",
            points = listOf(Vec2(-140f, -100f), Vec2(140f, -100f), Vec2(140f, 100f), Vec2(-140f, 100f))
        )
        val (extractedDoc, semObj) = ObjectSurgeryAndMaterialEngine.extractObjectFromSelection(
            doc, renderer, "layer_ink", mask, "Extracted Hero"
        )
        assertEquals(originalSourceHash, extractedDoc.layers.first { it.id == "layer_ink" }.pixelContentHash())

        val refinedDoc = ObjectSurgeryAndMaterialEngine.refineObjectMaskAndReextract(extractedDoc, renderer, semObj.id, 12f)
        val refinedObj = refinedDoc.semanticObjects.first { it.id == semObj.id }
        assertTrue(refinedObj.boundsW > semObj.boundsW)
        assertEquals(originalSourceHash, refinedDoc.layers.first { it.id == "layer_ink" }.pixelContentHash())

        val transformedDoc = ObjectSurgeryAndMaterialEngine.applyTransformToLayerOrObject(
            refinedDoc, refinedObj.extractedLayerId, refinedObj.id, LayerTransform(offsetX = 32f, offsetY = -16f, scaleX = 1.2f, scaleY = 1.2f, rotationDeg = 15f)
        )
        assertEquals(32f, transformedDoc.layers.first { it.id == refinedObj.extractedLayerId }.transform.offsetX, 0.01f)

        // F02 Pitch + Yaw composite delta and Reset
        var camDoc = ObjectSurgeryAndMaterialEngine.assignLayerToDepthPlane(transformedDoc, refinedObj.extractedLayerId, "plane_fg")
        val pitchUp = ObjectSurgeryAndMaterialEngine.updateSpatialStage(camDoc) { it.copy(enabled = true, cameraPitchDeg = -18f, cameraYawDeg = 12f) }
        val pitchDown = ObjectSurgeryAndMaterialEngine.updateSpatialStage(camDoc) { it.copy(enabled = true, cameraPitchDeg = 18f, cameraYawDeg = -12f) }
        val bmpA = ObjectSurgeryAndMaterialEngine.renderSpatialStageComposite(pitchUp, renderer, 300, 200)
        val bmpB = ObjectSurgeryAndMaterialEngine.renderSpatialStageComposite(pitchDown, renderer, 300, 200)
        val pa = IntArray(300 * 200); val pb = IntArray(300 * 200)
        bmpA.getPixels(pa, 0, 300, 0, 0, 300, 200)
        bmpB.getPixels(pb, 0, 300, 0, 0, 300, 200)
        assertFalse(pa.contentEquals(pb))
        bmpA.recycle(); bmpB.recycle()

        val resetCam = ObjectSurgeryAndMaterialEngine.resetSpatialCamera(pitchUp)
        assertEquals(0f, resetCam.spatialStage.cameraYawDeg, 0.001f)
        assertEquals(0f, resetCam.spatialStage.cameraPitchDeg, 0.001f)
        assertEquals(1f, resetCam.spatialStage.cameraZoom, 0.001f)

        // F09 Vector resize, Bezier handle edit, balloon tail move, snap & SVG export
        var vecDoc = VectorAndFoundryEngine.addTextBox(resetCam, "LIVING WORKSHOP")
        val txtId = vecDoc.vectorObjects.last().id
        vecDoc = VectorAndFoundryEngine.resizeVectorObject(vecDoc, txtId, 10f, 10f, 320f, 140f, snapEnabled = true)
        assertEquals(320f, vecDoc.vectorObjects.last().width, 1f)

        vecDoc = VectorAndFoundryEngine.addSpeechBalloon(vecDoc, "TAIL TEST")
        val balId = vecDoc.vectorObjects.last().id
        vecDoc = VectorAndFoundryEngine.moveBalloonTailTip(vecDoc, balId, Vec2(99f, 142f), snapEnabled = true)
        assertEquals(96f, vecDoc.vectorObjects.last().tailTip!!.x, 0.5f)
        assertEquals(144f, vecDoc.vectorObjects.last().tailTip!!.y, 0.5f)

        vecDoc = VectorAndFoundryEngine.addBezierCurve(vecDoc, listOf(Vec2(-100f, 0f), Vec2(0f, -60f), Vec2(100f, 0f)))
        val pathId = vecDoc.vectorObjects.last().id
        vecDoc = VectorAndFoundryEngine.moveBezierNodeOrHandle(
            vecDoc, pathId, 1, VectorAndFoundryEngine.BezierHandlePart.ANCHOR, Vec2(16f, -96f), snapEnabled = true
        )
        assertEquals(-96f, vecDoc.vectorObjects.last().nodes[1].anchor.y, 0.5f)
        val svg = VectorAndFoundryEngine.exportDocumentToSvg(vecDoc)
        assertTrue(svg.contains("LIVING WORKSHOP"))
        assertTrue(svg.contains("TAIL TEST"))
        assertTrue(svg.contains("<path"))
    }

    @Test
    fun `F04 Recipe DAG editing cycle rejection invalidation validation error and presets`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvas(renderer)
        val recipeId = doc.recipes.first().id

        // Initial run computes all nodes
        val run1 = TimeAndRecipeEngine.executeRecipeGraph(doc, recipeId)
        assertEquals(4, run1.computedNodeIds.size)
        doc = doc.copy(recipes = listOf(run1.updatedGraph))

        // Tweak downstream n_mat -> upstream nodes reused from cache, n_mat invalidates & recomputes
        doc = TimeAndRecipeEngine.updateRecipeNodeParameter(doc, recipeId, "n_mat", "roughness", "0.65")
        val invalidated = TimeAndRecipeEngine.computeInvalidatedNodeIds(doc, recipeId)
        assertTrue(invalidated.contains("n_mat"))
        assertFalse(invalidated.contains("n_cleanup"))

        val run2 = TimeAndRecipeEngine.executeRecipeGraph(doc, recipeId)
        assertTrue(run2.reusedNodeIds.contains("n_cleanup"))
        assertTrue(run2.reusedNodeIds.contains("n_line"))
        assertTrue(run2.computedNodeIds.contains("n_mat"))
        doc = doc.copy(recipes = listOf(run2.updatedGraph))

        // Add node, connect valid & reject cycle
        doc = TimeAndRecipeEngine.addRecipeNode(doc, recipeId, RecipeNodeKind.EXPORT_PREP, "Final Atlas", listOf("n_mat"))
        val addedId = doc.recipes.first().nodes.last().id
        val cycleAttempt = TimeAndRecipeEngine.connectRecipeNodes(doc, recipeId, addedId, "n_cleanup")
        assertTrue("Cycle must be rejected", cycleAttempt.isFailure)

        // Parameter validation error
        val badParamDoc = TimeAndRecipeEngine.updateRecipeNodeParameter(doc, recipeId, "n_mat", "roughness", "4.5")
        val errRun = TimeAndRecipeEngine.executeRecipeGraph(badParamDoc, recipeId)
        assertTrue(errRun.updatedGraph.results["n_mat"]!!.status.startsWith("ERROR"))

        // Preset copy & select preserves existing presets
        val withPreset = TimeAndRecipeEngine.saveRecipePresetCopy(doc, recipeId, "My Export Preset")
        assertEquals(2, withPreset.recipes.size)
        assertEquals("My Export Preset", withPreset.recipes.first().name)
    }

    @Test
    fun `F05 Time Canvas F06 Material F10 Vault P04 Character P06 Outpaint P10 Health`() = runBlocking {
        val renderer = SparseTileRenderer()
        var doc = seedCanvas(renderer)

        // F05 duplicate, delete, loop range, dual onion skin, keyframe edit, sprite sheet
        doc = TimeAndRecipeEngine.addTimelineFrame(doc)
        val countAfterAdd = doc.timeline.frames.size
        doc = TimeAndRecipeEngine.duplicateTimelineFrame(doc, 0)
        assertEquals(countAfterAdd + 1, doc.timeline.frames.size)
        doc = TimeAndRecipeEngine.deleteTimelineFrame(doc, 1)
        assertEquals(countAfterAdd, doc.timeline.frames.size)
        doc = TimeAndRecipeEngine.setTimelineLoopRange(doc, 0, 1)
        assertEquals(0..1, TimeAndRecipeEngine.effectiveLoopRange(doc))
        doc = TimeAndRecipeEngine.updateOnionSkinConfig(doc, enabled = true, beforeOpacity = 0.35f, afterEnabled = true, afterOpacity = 0.25f)
        assertTrue(doc.timeline.onionSkinAfterEnabled)
        doc = TimeAndRecipeEngine.updateLayerKeyframe(doc, 0, "layer_ink", 24f, -12f, 0.8f, true)
        val (sheetBmp, sheetMeta) = TimeAndRecipeEngine.exportSpriteSheet(doc, renderer)
        assertEquals(doc.timeline.frames.size, sheetMeta.frames.size)
        sheetBmp.recycle()

        // F06 masked material lighting + non-destructive metadata save
        val mask = SelectionMaskRecord("m_mat", "Rim", listOf(Vec2(-60f, -60f), Vec2(60f, -60f), Vec2(60f, 60f), Vec2(-60f, 60f)))
        doc = doc.copy(masks = listOf(mask))
        val mat = com.example.document.MaterialRegion(
            id = "mat_gold",
            name = "Gold Trim",
            targetLayerId = "layer_ink",
            roughness = 0.2f,
            metalness = 0.9f,
            emissiveHex = "#F59E0B",
            emissionIntensity = 0.6f,
            lightElevationDeg = 60f,
            maskId = "m_mat"
        )
        val litBmp = ObjectSurgeryAndMaterialEngine.renderMaterialLitPreview(doc, renderer, mat)
        assertTrue(litBmp.width > 0)
        litBmp.recycle()
        doc = ObjectSurgeryAndMaterialEngine.upsertMaterial(doc, mat)
        assertEquals(1, doc.materials.size)

        // F10 multi-filter search & no-results
        val thumb = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        thumb.eraseColor(Color.rgb(56, 189, 248))
        doc = VaultBranchOrchardEngine.addVaultAsset(doc, "Sky Lantern", "local://sky", "sky_1", listOf("lantern", "sky"), thumb, userApproved = true).first
        thumb.recycle()
        assertEquals(1, VaultBranchOrchardEngine.searchVaultAssets(doc, queryName = "lantern", approvalFilter = VaultApprovalFilter.APPROVED_ONLY).size)
        assertTrue(VaultBranchOrchardEngine.searchVaultAssets(doc, queryName = "no_match_zzz").isEmpty())

        // P04 manual canon approval defaults false and updates cleanly
        assertFalse(CharacterSheet().userApprovedCanon)
        doc = VaultBranchOrchardEngine.updateCharacterSheetFull(
            doc, name = "Aiden", proportionNotes = "7.5 heads", poseNotes = "Standing",
            turnaroundNotes = "Front/Side", paletteHexes = listOf("#38BDF8"),
            expressionTags = listOf("Calm"), poseTags = listOf("Idle"),
            referenceAssetIds = listOf(doc.vaultAssets.first().id), referenceProvenanceNote = "Artist ref"
        )
        assertFalse(doc.characterSheets.first().userApprovedCanon)
        doc = VaultBranchOrchardEngine.setCharacterCanonApproval(doc, userApproved = true)
        assertTrue(doc.characterSheets.first().userApprovedCanon)

        // P06 custom L/R/T/B outpaint + preview disclaimer
        val info = FuturesAndConstraintsEngine.buildOutpaintContextPreview(doc, renderer, leftTiles = 1, rightTiles = 2, topTiles = 0, bottomTiles = 1)
        assertTrue(info.disclaimer.contains("DETERMINISTIC LOCAL EDGE STUDY"))
        info.previewBitmap.recycle()
        val oldW = doc.bounds.width
        doc = FuturesAndConstraintsEngine.generateCustomLocalOutpaint(doc, renderer, leftTiles = 1, rightTiles = 1)
        assertTrue(doc.bounds.width > oldW)

        // P10 loopback health ping
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            srv.createContext("/health") { ex ->
                val b = """{"providerId":"loopback-companion","status":"healthy","offlineSafe":true,"detail":"Ready"}""".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
            srv.start()
            val adapter = com.example.services.LoopbackCompanionAdapter("http://127.0.0.1:${srv.address.port}")
            val health = adapter.health()
            assertEquals("healthy", health.status)
        } finally {
            srv.stop(0)
        }
    }

    @Test
    fun `SampleProjectFactory buildLivingWorkshopJourney and Foundry export pass all invariants`() {
        val renderer = SparseTileRenderer()
        val doc = SampleProjectFactory.buildLivingWorkshopJourney(renderer)

        assertTrue(doc.layers.size >= 3)
        assertTrue(doc.branches.size >= 2)
        assertTrue(doc.semanticObjects.isNotEmpty())
        assertTrue(doc.constraints.isNotEmpty())
        assertTrue(doc.vectorObjects.isNotEmpty())
        assertTrue(doc.materials.isNotEmpty())
        assertTrue(doc.recipes.first().results.isNotEmpty())
        assertTrue(doc.timeline.frames.size >= 3)
        assertTrue(doc.vaultAssets.isNotEmpty())
        assertFalse("Canon must stay unapproved until artist toggles it", doc.characterSheets.first().userApprovedCanon)

        val zipBytes = VectorAndFoundryEngine.exportFoundryBundleZip(doc, renderer)
        val manifest = VectorAndFoundryEngine.verifyFoundryBundleZip(zipBytes).getOrThrow()
        assertTrue(manifest.sprites.isNotEmpty())

        val iastudio = DocumentArchiveCodec.writeArchiveToBytes(doc)
        val restored = DocumentArchiveCodec.readDocument(iastudio).getOrThrow()
        assertEquals(doc.branches.size, restored.branches.size)
        assertEquals(doc.recipes.first().nodes.size, restored.recipes.first().nodes.size)
    }

    @Test
    fun `Living Workshop Compose UI CUJs Loom Command Ring Modes Recipe DAG Vault filters and Diagnostics`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudioViewModel(app)
        vm.loadLivingWorkshopJourney()

        composeRule.setContent {
            InfiniteStudioTheme {
                InfiniteStudioApp(viewModel = vm)
            }
        }
        composeRule.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.document.branches.size >= 2
        }
        composeRule.waitForIdle()

        // 1. Futures Loom -> compare branches -> toggle diff overlay -> close
        composeRule.onNodeWithTag("btn_open_loom").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("futures_loom_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_compare_branches").performScrollTo().performClick()
        composeRule.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.branchComparison != null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("branch_comparison_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_toggle_diff_overlay").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_close_loom").performClick()
        composeRule.waitForIdle()

        // 2. Creative Command Ring -> radial -> accessible grid -> reduced motion -> trigger Vector action
        composeRule.onNodeWithTag("btn_command_ring").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("command_ring_radial").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_ring_toggle_accessible").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("command_ring_accessible_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_ring_reduced_motion").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(vm.uiState.value.reducedMotionEnabled)

        vm.customizeCommandRingSlot(0, CommandRingAction.VECTOR)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ring_slot_0").performClick()
        composeRule.waitForIdle()
        assertEquals(StudioTool.VECTOR_EDIT, vm.uiState.value.activeTool)

        // 3. WorkspaceMode switching + Back to Canvas preserves revision and document
        val revBefore = vm.uiState.value.document.revision
        composeRule.onNodeWithTag("mode_animate").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("animate_transport_bar").assertIsDisplayed()
        assertEquals(revBefore, vm.uiState.value.document.revision)
        composeRule.onNodeWithTag("btn_back_to_canvas").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(WorkspaceMode.PAINT, vm.uiState.value.workspaceMode)
        assertEquals(revBefore, vm.uiState.value.document.revision)

        // 4. Studio Hub Tab 3 (Recipe DAG) -> Add Node & Cycle Error Banner
        composeRule.onNodeWithTag("btn_studio_hub").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_tab_3").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recipe_graph_editor").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_add_recipe_node").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Self-connect triggers cycle rejection banner
        val firstNodeId = vm.uiState.value.document.recipes.first().nodes.first().id
        composeRule.onNodeWithTag("btn_link_node_$firstNodeId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_link_node_$firstNodeId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recipe_error_banner").performScrollTo().assertIsDisplayed()

        // 5. Studio Hub Tab 4 -> Vault search empty/no-results state
        composeRule.onNodeWithTag("hub_tab_4").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("input_vault_search_name").performScrollTo().performTextReplacement("zzz_nonexistent_zzz")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vault_no_results").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("btn_vault_clear_filters").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_close_hub").performClick()
        composeRule.waitForIdle()

        // 6. Diagnostics includes live render frame timing & hitRatioPercent
        val diag = vm.buildRedactedDiagnosticsReport()
        assertTrue(diag.contains("hitRatio="))
        assertTrue(diag.contains("Render Frame Timing:"))
        assertTrue(diag.contains("Client Secrets: NONE"))
    }
}
