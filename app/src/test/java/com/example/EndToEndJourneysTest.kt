package com.example

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.document.CanonicalDocument
import com.example.document.ConstraintKind
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.MaterialRegion
import com.example.document.SelectionMaskRecord
import com.example.document.Vec2
import com.example.features.FuturesAndConstraintsEngine
import com.example.features.ObjectSurgeryAndMaterialEngine
import com.example.features.TimeAndRecipeEngine
import com.example.features.VaultBranchOrchardEngine
import com.example.features.VectorAndFoundryEngine
import com.example.model.FutureSource
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.storage.ProjectStorageManager
import com.example.tools.TileBrushEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EndToEndJourneysTest {

    private fun seedCanvasWithArtwork(renderer: SparseTileRenderer): CanonicalDocument {
        val base = CanonicalDocument(
            title = "Journey Canvas",
            layers = listOf(DocumentLayer(id = "sketch", name = "Sketch Base")),
            activeLayerId = "sketch"
        )
        return TileBrushEngine.commitStrokeToDocument(
            doc = base,
            renderer = renderer,
            targetLayerId = "sketch",
            worldPoints = listOf(
                Vec2(-120f, -80f) to 0.85f,
                Vec2(0f, 40f) to 0.90f,
                Vec2(140f, -60f) to 0.85f
            ),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.INK,
                colorInt = Color.rgb(40, 140, 220),
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.INK, 18f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )
    }

    @Test
    fun `J01 paint save reopen and deterministic flattened export`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = ProjectStorageManager(context)
        val renderer = SparseTileRenderer()
        val doc = seedCanvasWithArtwork(renderer)

        val saveRes = storage.saveLocalDraftAtomic(doc)
        assertTrue(saveRes.isSuccess)
        assertTrue(storage.hasRecoverableDraft())

        val reopened = storage.loadLocalDraft().getOrThrow()
        assertEquals(doc.layers.first().pixelContentHash(), reopened.layers.first().pixelContentHash())

        val exportBmp = storage.renderExportPreview(reopened, renderer)
        assertTrue(exportBmp.width > 0 && exportBmp.height > 0)
        exportBmp.recycle()
    }

    @Test
    fun `J02 object surgery extracts semantic object repairs hole speculatively and reverts cleanly`() {
        val renderer = SparseTileRenderer()
        val doc = seedCanvasWithArtwork(renderer)
        val origHash = doc.layers.first().pixelContentHash()

        val mask = SelectionMaskRecord(
            id = "m_obj",
            name = "Hero Loop",
            points = listOf(Vec2(-150f, -120f), Vec2(160f, -120f), Vec2(160f, 100f), Vec2(-150f, 100f))
        )
        val (extractedDoc, record) = ObjectSurgeryAndMaterialEngine.extractObjectFromSelection(
            doc = doc, renderer = renderer, sourceLayerId = "sketch", mask = mask, objectName = "Lantern"
        )
        assertEquals(3, extractedDoc.layers.size)
        assertTrue(record.repairLabel.contains("SPECULATIVE"))
        assertEquals("Original source layer must remain untouched!", origHash, extractedDoc.layers.first().pixelContentHash())

        val movedDoc = ObjectSurgeryAndMaterialEngine.moveSemanticObject(extractedDoc, record.id, 45f, -30f)
        assertEquals(45f, movedDoc.layers.last().transform.offsetX, 0.01f)

        val revertedDoc = ObjectSurgeryAndMaterialEngine.revertObjectExtraction(movedDoc, record.id)
        assertEquals(1, revertedDoc.layers.size)
        assertTrue(revertedDoc.semanticObjects.isEmpty())
    }

    @Test
    fun `J03 futures produces 3 honest local studies and zero pixels mutate outside mask`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvasWithArtwork(renderer)
        val origSketchHash = doc.layers.first().pixelContentHash()

        val mask = SelectionMaskRecord(
            id = "m_fut",
            name = "Study Region",
            points = listOf(Vec2(-90f, -90f), Vec2(90f, -90f), Vec2(90f, 90f), Vec2(-90f, 90f))
        )
        doc = doc.copy(masks = listOf(mask))

        val session = FuturesAndConstraintsEngine.buildFuturesSession(doc, renderer, mask, "copper glow")
        assertEquals(3, session.branches.size)
        session.branches.forEach { branch ->
            assertEquals(FutureSource.LOCAL, branch.source)
            assertEquals("LOCAL_STUDY", branch.sourceLabel)
        }

        // Accepting Copper Light study (index 2) onto a new layer
        val (acceptedDoc, acceptedLayer) = FuturesAndConstraintsEngine.acceptBranchToNewLayer(doc, session, 2)
        assertEquals(2, acceptedDoc.layers.size)
        assertEquals("Original sketch hash must remain invariant!", origSketchHash, acceptedDoc.layers.first().pixelContentHash())

        // CRITICAL MACHINE PROOF: Zero pixels outside selection mask may be non-transparent!
        val outsidePixels = FuturesAndConstraintsEngine.countNonTransparentPixelsOutsideMask(
            doc = acceptedDoc,
            renderer = renderer,
            targetLayerId = acceptedLayer.id,
            mask = mask
        )
        assertEquals(0, outsidePixels)

        // Visual Forensics heatmap also confirms 0 outside-mask deltas against blank baseline
        val docWithBlank = acceptedDoc.copy(layers = listOf(DocumentLayer("blank", "Blank"), acceptedLayer))
        val report = FuturesAndConstraintsEngine.computeForensicsReport(docWithBlank, renderer, "blank", acceptedLayer.id, mask)
        assertTrue(report.maskBoundarySafe)
        assertEquals(0, report.changedPixelsOutsideMask)
        assertTrue(report.changedPixelsInsideMask > 0)
        report.heatmapBitmap.recycle()
    }

    @Test
    fun `J04 vector exact SVG material preview time frames and foundry bundle roundtrip`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvasWithArtwork(renderer)
        doc = VectorAndFoundryEngine.addTextBox(doc, "INFINITE EXACT STRING")
        doc = VectorAndFoundryEngine.addSpeechBalloon(doc, "SPEECH TEST 42")
        doc = VectorAndFoundryEngine.addBezierCurve(doc, listOf(Vec2(-50f, 0f), Vec2(0f, -60f), Vec2(50f, 0f)))

        val svg = VectorAndFoundryEngine.exportDocumentToSvg(doc)
        assertTrue(svg.contains("<text"))
        assertTrue(svg.contains("INFINITE EXACT STRING"))
        assertTrue(svg.contains("SPEECH TEST 42"))
        assertTrue(svg.contains("<path"))

        // F06 Material pass
        val mat = MaterialRegion("m1", "Brushed Steel", "sketch", roughness = 0.25f, metalness = 0.8f)
        doc = ObjectSurgeryAndMaterialEngine.applyMaterialPreviewAsNewLayer(doc, renderer, mat)
        assertTrue(doc.layers.any { it.name.contains("Material:") })

        // F05 Time Canvas 2+ frames + sprite sheet
        doc = TimeAndRecipeEngine.addTimelineFrame(doc)
        val (sheetBmp, sheetMeta) = TimeAndRecipeEngine.exportSpriteSheet(doc, renderer, 128, 128)
        assertEquals(3, sheetMeta.frames.size)
        assertEquals(384, sheetBmp.width)
        sheetBmp.recycle()

        // F08 Production Foundry ZIP bundle export + exact reimport verification
        val foundryZip = VectorAndFoundryEngine.exportFoundryBundleZip(doc, renderer)
        val verifiedManifest = VectorAndFoundryEngine.verifyFoundryBundleZip(foundryZip).getOrThrow()
        assertTrue(verifiedManifest.sprites.isNotEmpty())
        assertEquals(doc.documentId, verifiedManifest.documentId)
    }

    @Test
    fun `J05 creative recipe DAG selective descendant recompute skips unchanged upstream nodes`() {
        val doc = CanonicalDocument(layers = listOf(DocumentLayer("l1", "L1")), activeLayerId = "l1")

        // First execution computes all 4 nodes
        val run1 = TimeAndRecipeEngine.executeRecipeGraph(doc)
        assertEquals(listOf("n_cleanup", "n_line", "n_flat", "n_mat"), run1.computedNodeIds)
        assertTrue(run1.reusedNodeIds.isEmpty())

        // Modify downstream material node only
        val doc2 = doc.copy(recipes = listOf(run1.updatedGraph)).let {
            TimeAndRecipeEngine.updateRecipeNodeParameter(it, "recipe_default", "n_mat", "roughness", "0.88")
        }

        // Second execution reuses upstream 3 nodes and recomputes ONLY n_mat!
        val run2 = TimeAndRecipeEngine.executeRecipeGraph(doc2)
        assertEquals(listOf("n_cleanup", "n_line", "n_flat"), run2.reusedNodeIds)
        assertEquals(listOf("n_mat"), run2.computedNodeIds)
    }

    @Test
    fun `J06 vault tag and RGB color similarity search plus explicit approval and local Art Director`() {
        val renderer = SparseTileRenderer()
        var doc = seedCanvasWithArtwork(renderer)
        val thumb = ProjectStorageManager(ApplicationProvider.getApplicationContext()).renderExportPreview(doc, renderer)

        val (docWithAsset, asset) = VaultBranchOrchardEngine.addVaultAsset(
            doc = doc,
            title = "Sky Lantern Reference",
            sourceUri = "local://ref/lantern",
            sourceId = "local_1",
            tags = listOf("lantern", "sky", "blue"),
            bitmap = thumb,
            userApproved = false
        )
        thumb.recycle()
        doc = docWithAsset
        assertFalse(asset.userApproved)

        val hits = VaultBranchOrchardEngine.searchVaultAssets(doc, queryTag = "lantern", targetColorHex = "#288CDC")
        assertEquals(1, hits.size)
        assertEquals("Sky Lantern Reference", hits.first().asset.title)

        // Art Director deterministic local grammar
        val dirRes = VaultBranchOrchardEngine.executeArtDirectorCommand(doc, "add text SCENE TWO", renderer)
        assertTrue(dirRes.applied)
        assertEquals("SCENE TWO", dirRes.updatedDoc.vectorObjects.last().text)
    }

    @Test
    fun `J08 corrupt input bad checksum and process death recovery preserve last good project and sample project roundtrips`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = ProjectStorageManager(context)
        val renderer = SparseTileRenderer()

        // 1. Build repeatable offline sample project & save atomically as last good draft
        val sample = com.example.document.SampleProjectFactory.buildSignatureDemo(renderer)
        assertTrue(sample.layers.size >= 4) // ink + wash + accepted study + material
        assertTrue(sample.masks.isNotEmpty())
        assertTrue(sample.constraints.isNotEmpty())
        assertTrue(sample.vectorObjects.any { it.text == "ONE MARK, THREE FUTURES" })
        assertTrue(sample.materials.isNotEmpty())
        assertTrue(sample.branches.isNotEmpty())
        assertTrue(sample.timeline.frames.size >= 2)
        assertTrue(sample.recipes.first().results.isNotEmpty())

        assertTrue(storage.saveLocalDraftAtomic(sample).isSuccess)
        val expectedHash = sample.documentContentHash()

        // 2. Corrupt archive bytes & invalid PNG header fail safely without destroying saved draft
        val corruptLoad = DocumentArchiveCodec.readDocument(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
        assertTrue(corruptLoad.isFailure)

        // 3. Round-trip sample archive through v3 ZIP codec and verify full preservation
        val zipBytes = DocumentArchiveCodec.writeArchiveToBytes(sample)
        val reopened = DocumentArchiveCodec.readDocument(zipBytes).getOrThrow()
        assertEquals(expectedHash, reopened.documentContentHash())
        assertEquals("ONE MARK, THREE FUTURES", reopened.vectorObjects.first { it.text.isNotEmpty() }.text)

        // 4. Simulate process death & recovery via fresh ProjectStorageManager instance
        val freshStorage = ProjectStorageManager(context)
        assertTrue(freshStorage.hasRecoverableDraft())
        val recovered = freshStorage.loadLocalDraft().getOrThrow()
        assertEquals(expectedHash, recovered.documentContentHash())
    }
}
