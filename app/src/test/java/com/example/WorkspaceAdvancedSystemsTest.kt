package com.example

import android.app.Application
import android.graphics.Color
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.document.CanonicalDocument
import com.example.document.CustomBrushPreset
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.Vec2
import com.example.features.LivingPreviewEngine
import com.example.features.ObjectSurgeryAndMaterialEngine
import com.example.features.OrchardSceneNode
import com.example.features.TimeAndRecipeEngine
import com.example.features.VaultBranchOrchardEngine
import com.example.features.VectorAndFoundryEngine
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.services.LoopbackCompanionAdapter
import com.example.services.ProviderJobRequest
import com.example.services.ProviderJobStatus
import com.example.tools.TileBrushEngine
import com.example.viewmodel.StudioViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspaceAdvancedSystemsTest {

    private fun seedCanvas(renderer: SparseTileRenderer): CanonicalDocument {
        val doc = CanonicalDocument(
            title = "Advanced Engine Canvas",
            layers = listOf(DocumentLayer("layer_ink", "Ink Base")),
            activeLayerId = "layer_ink"
        )
        return TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "layer_ink",
            worldPoints = listOf(Vec2(-80f, -60f) to 0.8f, Vec2(80f, 60f) to 0.9f),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.INK,
                colorInt = Color.rgb(32, 160, 240),
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.INK, 20f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )
    }

    @Test
    fun `F02 spatial stage assigns depth planes renders distinct parallax composites and roundtrips v3`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvas(renderer)
        doc = ObjectSurgeryAndMaterialEngine.assignLayerToDepthPlane(doc, "layer_ink", "plane_fg")
        assertEquals("plane_fg", doc.layers.first().depthPlaneId)
        assertTrue(doc.spatialStage.depthPlanes.first { it.id == "plane_fg" }.assignedLayerIds.contains("layer_ink"))

        val leftCam = ObjectSurgeryAndMaterialEngine.updateSpatialStage(doc) {
            it.copy(enabled = true, cameraYawDeg = -22f, cameraZoom = 1.15f)
        }
        val rightCam = ObjectSurgeryAndMaterialEngine.updateSpatialStage(doc) {
            it.copy(enabled = true, cameraYawDeg = 22f, cameraZoom = 1.15f)
        }

        val bmpLeft = ObjectSurgeryAndMaterialEngine.renderSpatialStageComposite(leftCam, renderer, 400, 260)
        val bmpRight = ObjectSurgeryAndMaterialEngine.renderSpatialStageComposite(rightCam, renderer, 400, 260)
        assertEquals(400, bmpLeft.width)
        assertEquals(260, bmpLeft.height)

        // Compare pixel streams to prove yaw shifts rendered foreground layer pixels
        val pLeft = IntArray(400 * 260)
        val pRight = IntArray(400 * 260)
        bmpLeft.getPixels(pLeft, 0, 400, 0, 0, 400, 260)
        bmpRight.getPixels(pRight, 0, 400, 0, 0, 400, 260)
        assertFalse("Left and right parallax renders must differ!", pLeft.contentEquals(pRight))
        bmpLeft.recycle()
        bmpRight.recycle()

        // Verify v3 archive serialization preserves spatial stage camera and depth plane assignment
        val roundtrip = DocumentArchiveCodec.readDocument(DocumentArchiveCodec.writeArchiveToBytes(rightCam)).getOrThrow()
        assertEquals(22f, roundtrip.spatialStage.cameraYawDeg, 0.01f)
        assertEquals("plane_fg", roundtrip.layers.first().depthPlaneId)
    }

    @Test
    fun `F03 living preview coalesces rapid revisions and honors low power battery pause`() = runBlocking {
        val renderer = SparseTileRenderer()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val engine = LivingPreviewEngine(scope, renderer)
            val base = seedCanvas(renderer)

            // Burst 3 rapid revision updates -> stale jobs 1 & 2 should cancel, settling on rev 3
            engine.schedulePreview(base.copy(revision = 1L))
            engine.schedulePreview(base.copy(revision = 2L))
            engine.schedulePreview(base.copy(revision = 3L))

            val deadline = System.currentTimeMillis() + 5000L
            while (System.currentTimeMillis() < deadline && engine.state.value.revision != 3L) {
                delay(40L)
            }
            val snap = engine.state.value
            assertEquals(3L, snap.revision)
            assertNotNull(snap.bitmap)
            assertFalse(snap.isPausedForBattery)

            // Toggle battery pause
            engine.setLowPowerPaused(true)
            engine.schedulePreview(base.copy(revision = 4L))
            delay(180L)
            assertTrue(engine.state.value.isPausedForBattery)
            assertEquals(4L, engine.state.value.revision)
            assertTrue(engine.state.value.statusText.contains("Paused", ignoreCase = true))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `P04 character studio ruler toggle and notes persist cleanly across v3 archive`() {
        var doc = CanonicalDocument(layers = listOf(DocumentLayer("l1", "Character")), activeLayerId = "l1")
        assertFalse(doc.characterSheets.first().showProportionRuler)

        doc = VaultBranchOrchardEngine.toggleProportionRuler(doc)
        doc = VaultBranchOrchardEngine.updateCharacterSheetNotes(
            doc = doc,
            sheetId = doc.characterSheets.first().id,
            name = "Aiden Skyguard",
            proportionNotes = "7.5 heads; cloak hem at ankle",
            poseNotes = "Dynamic 3/4 turnaround"
        )
        assertTrue(doc.characterSheets.first().showProportionRuler)

        val restored = DocumentArchiveCodec.readDocument(DocumentArchiveCodec.writeArchiveToBytes(doc)).getOrThrow()
        assertTrue(restored.characterSheets.first().showProportionRuler)
        assertEquals("Aiden Skyguard", restored.characterSheets.first().name)
        assertEquals("7.5 heads; cloak hem at ankle", restored.characterSheets.first().proportionNotes)
    }

    @Test
    fun `P08 world orchard and P09 asset workshop integrate with StudioViewModel and Foundry`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudioViewModel(app)

        // Apply P09 Workshop template
        vm.applyWorkshopTemplate("tpl_comic_panel")
        val afterTpl = vm.uiState.value.document
        assertTrue(afterTpl.vectorObjects.any { it.text == "ONE MARK, THREE FUTURES" })
        assertTrue(afterTpl.timeline.frames.size >= 2)

        // Verify Foundry bundle export with template assets
        val foundryZip = VectorAndFoundryEngine.exportFoundryBundleZip(afterTpl, vm.tileRenderer)
        val manifest = VectorAndFoundryEngine.verifyFoundryBundleZip(foundryZip).getOrThrow()
        assertTrue(manifest.sprites.isNotEmpty())

        // Fork branch + add P08 Orchard scene nodes + navigate directly to Orchard node
        val rootBranch = vm.uiState.value.document.activeBranchId
        vm.forkCreativeBranch("Nocturnal Branch")
        val childBranch = vm.uiState.value.document.activeBranchId
        assertNotEquals(rootBranch, childBranch)

        vm.addOrchardSceneNode("Act II Harbor")
        val nodes = VaultBranchOrchardEngine.readOrchardNodes(vm.uiState.value.document)
        assertTrue(nodes.size >= 2)
        val rootNode = OrchardSceneNode("orch_jump_main", "Act I Square", branchId = "main", x = 320f, y = -180f)
        vm.navigateToOrchardNode(rootNode)

        val jumpedState = vm.uiState.value
        assertEquals("main", jumpedState.document.activeBranchId)
        assertEquals(-320f * jumpedState.viewScale, jumpedState.viewOffsetX, 0.5f)
        assertEquals(180f * jumpedState.viewScale, jumpedState.viewOffsetY, 0.5f)
    }

    @Test
    fun `P10 loopback companion adapter executes real OkHttp HTTP calls against loopback server`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/health") { ex ->
                val body = """{"providerId":"loopback-companion","status":"healthy","offlineSafe":true,"detail":"Mock loop OK"}"""
                    .toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            server.createContext("/jobs") { ex ->
                val raw = ex.requestBody.bufferedReader().readText()
                if (raw.contains("\"baseRevision\":99")) {
                    val err = """{"error":"stale"}""".toByteArray(Charsets.UTF_8)
                    ex.sendResponseHeaders(409, err.size.toLong())
                    ex.responseBody.use { it.write(err) }
                } else {
                    val body = """{"jobId":"job_loop_1","idempotencyKey":"k1","providerId":"loopback-companion","documentId":"d1","baseRevision":5,"status":"SUCCEEDED","processingMode":"LOOPBACK_COMPANION","resultPngBase64":"AAAA","message":"Done"}"""
                        .toByteArray(Charsets.UTF_8)
                    ex.responseHeaders.add("Content-Type", "application/json")
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
            }
            server.createContext("/jobs/job_loop_1/cancel") { ex ->
                val body = """{"jobId":"job_loop_1","idempotencyKey":"k1","providerId":"loopback-companion","documentId":"d1","baseRevision":5,"status":"CANCELED","processingMode":"LOOPBACK_COMPANION","message":"Canceled"}"""
                    .toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            server.createContext("/jobs/job_loop_1") { ex ->
                val body = """{"jobId":"job_loop_1","idempotencyKey":"k1","providerId":"loopback-companion","documentId":"d1","baseRevision":5,"status":"SUCCEEDED","processingMode":"LOOPBACK_COMPANION","resultPngBase64":"AAAA","message":"Done"}"""
                    .toByteArray(Charsets.UTF_8)
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            server.start()

            val adapter = LoopbackCompanionAdapter("http://127.0.0.1:${server.address.port}")
            val health = adapter.health()
            assertEquals("healthy", health.status)
            assertTrue(health.offlineSafe)

            val okJob = adapter.submitJob(
                ProviderJobRequest(
                    idempotencyKey = "k1",
                    documentId = "d1",
                    baseRevision = 5L,
                    branchLabel = "Copper",
                    directionSuffix = "warm",
                    prompt = "copper rim",
                    cropPngBase64 = "AAAA"
                ),
                currentDocumentRevision = 5L
            )
            assertEquals(ProviderJobStatus.SUCCEEDED, okJob.status)
            assertEquals("job_loop_1", okJob.jobId)

            val polled = adapter.getJob("job_loop_1")
            assertEquals(ProviderJobStatus.SUCCEEDED, polled?.status)

            val canceled = adapter.cancelJob("job_loop_1")
            assertEquals(ProviderJobStatus.CANCELED, canceled?.status)

            val conflictJob = adapter.submitJob(
                ProviderJobRequest(
                    idempotencyKey = "k2",
                    documentId = "d1",
                    baseRevision = 99L,
                    branchLabel = "Salt",
                    directionSuffix = "warm",
                    prompt = "test",
                    cropPngBase64 = "AAAA"
                ),
                currentDocumentRevision = 99L
            )
            assertEquals(ProviderJobStatus.FAILED, conflictJob.status)
            assertTrue(conflictJob.message.contains("409"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `StudioViewModel core CUJ paint undo redo layers AI guidance preset and redacted diagnostics`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudioViewModel(app)

        // Paint stroke
        vm.selectTool(StudioTool.INK)
        vm.beginPointerAction(PointF(0f, 0f), 0.7f)
        vm.movePointerAction(PointF(40f, 20f), 0.85f)
        vm.endPointerAction()

        val afterPaint = vm.uiState.value
        assertTrue(afterPaint.canUndo)
        val paintedHash = afterPaint.document.layers.first { it.id == afterPaint.document.activeLayerId }.pixelContentHash()

        // Undo & Redo
        vm.undo()
        assertTrue(vm.uiState.value.canRedo)
        vm.redo()
        assertEquals(
            paintedHash,
            vm.uiState.value.document.layers.first { it.id == vm.uiState.value.document.activeLayerId }.pixelContentHash()
        )

        // Layers: add, move, toggle visibility
        val countBefore = vm.uiState.value.document.layers.size
        vm.addLayer()
        assertEquals(countBefore + 1, vm.uiState.value.document.layers.size)
        val addedId = vm.uiState.value.document.activeLayerId!!
        vm.toggleLayerVisibility(addedId)
        assertFalse(vm.uiState.value.document.layers.first { it.id == addedId }.visible)
        vm.moveLayerDown(addedId)

        // P02 AI Brush guidance stroke
        vm.selectTool(StudioTool.AI_GUIDANCE)
        vm.beginPointerAction(PointF(-30f, 0f), 0.8f)
        vm.movePointerAction(PointF(30f, 40f), 0.8f)
        vm.endPointerAction()
        assertEquals(1, vm.uiState.value.document.guidanceMarks.size)

        // Brush preset persistence
        vm.saveCustomBrushPreset(CustomBrushPreset(id = "custom_1", name = "Hard Nib", baseKind = "INK", radius = 14f))
        assertTrue(vm.uiState.value.document.customPresets.any { it.name == "Hard Nib" })

        // Diagnostics excludes API secrets
        val diag = vm.buildRedactedDiagnosticsReport()
        assertTrue(diag.contains("INFINITE STUDIO DIAGNOSTICS"))
        assertTrue(diag.contains("strictly excluded", ignoreCase = true))
    }
}
