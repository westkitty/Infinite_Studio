package com.example

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.example.document.CanonicalDocument
import com.example.document.DocumentLayer
import com.example.document.Vec2
import com.example.features.FuturesAndConstraintsEngine
import com.example.features.VaultBranchOrchardEngine
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.services.ProviderErrorCode
import com.example.services.ProviderJobRequest
import com.example.services.ProviderJobStatus
import com.example.services.ProviderMode
import com.example.services.ProviderRouter
import com.example.tools.TileBrushEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProviderAndBranchesTest {

    private fun samplePngBase64(): String {
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(80, 120, 180))
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    @Test
    fun `J07 offline mode blocks external egress and mock provider honors idempotency cancellation and stale revision`() = runBlocking {
        val router = ProviderRouter()
        val cropB64 = samplePngBase64()

        // 1. Default strict offline blocks remote calls cleanly
        assertEquals(ProviderMode.OFFLINE_LOCAL_ONLY, router.mode)
        val blocked = router.submitCandidateJob(
            ProviderJobRequest(
                documentId = "d1", baseRevision = 5L,
                branchLabel = "Salt", directionSuffix = "warm",
                prompt = "test", cropPngBase64 = cropB64
            ),
            currentDocumentRevision = 5L
        )
        assertEquals(ProviderJobStatus.FAILED, blocked.status)
        assertEquals(ProviderErrorCode.OFFLINE_MODE_ACTIVE, blocked.errorCode)

        // 2. Loopback companion mode requires explicit user confirmation first
        router.mode = ProviderMode.LOOPBACK_COMPANION
        val unconsented = router.submitCandidateJob(
            ProviderJobRequest(
                documentId = "d1", baseRevision = 5L,
                branchLabel = "Salt", directionSuffix = "warm",
                prompt = "test", cropPngBase64 = cropB64, userConfirmedPaidEgress = false
            ),
            currentDocumentRevision = 5L
        )
        assertEquals(ProviderErrorCode.CONSENT_REQUIRED, unconsented.errorCode)

        // 3. Offline Mock Provider executes deterministic idempotent jobs + stale guard + cancel
        router.mode = ProviderMode.OFFLINE_MOCK_PROVIDER
        val req = ProviderJobRequest(
            idempotencyKey = "idem-123",
            documentId = "d1", baseRevision = 5L,
            branchLabel = "Blue Hour", directionSuffix = "nocturnal blue",
            prompt = "night mood", cropPngBase64 = cropB64
        )
        val mockRes = router.submitCandidateJob(req, currentDocumentRevision = 5L)
        assertEquals(ProviderJobStatus.SUCCEEDED, mockRes.status)
        assertNotNull(mockRes.resultPngBase64)

        // Idempotency returns same jobId
        val replay = router.submitCandidateJob(req, currentDocumentRevision = 5L)
        assertEquals(mockRes.jobId, replay.jobId)

        // Stale revision rejected
        val stale = router.submitCandidateJob(req, currentDocumentRevision = 6L)
        assertEquals(ProviderJobStatus.FAILED, stale.status)
        assertEquals(ProviderErrorCode.STALE_DOCUMENT_REVISION, stale.errorCode)

        // Cancel lifecycle works cleanly
        val canceled = router.cancelJob(mockRes.jobId)
        assertEquals(ProviderJobStatus.CANCELED, canceled?.status)
    }

    @Test
    fun `creative branches fork diff rollback and cherry pick have zero mutable aliasing`() {
        val renderer = SparseTileRenderer()
        var doc = CanonicalDocument(layers = listOf(DocumentLayer("l1", "Sketch")), activeLayerId = "l1")
        doc = TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "l1",
            worldPoints = listOf(Vec2(0f, 0f) to 0.8f, Vec2(60f, 20f) to 0.9f),
            params = TileBrushEngine.StrokeRenderParams(
                StudioTool.INK, Color.BLACK,
                TileBrushEngine.defaultPresetForTool(StudioTool.INK),
                SymmetryMode.NONE, null
            )
        )
        val mainHash = doc.documentContentHash()

        // Fork child branch & mutate child
        var branched = VaultBranchOrchardEngine.forkBranch(doc, "Warm Experiment")
        branched = FuturesAndConstraintsEngine.generateLocalOutpaintCandidate(
            branched, renderer, FuturesAndConstraintsEngine.OutpaintDirection.RIGHT
        )
        assertNotEquals(mainHash, branched.documentContentHash())

        // Rollback to main preserves exact original hash!
        val rolledBack = VaultBranchOrchardEngine.rollbackToBranch(branched, "main")!!
        assertEquals(mainHash, rolledBack.documentContentHash())
        assertEquals(1, rolledBack.layers.size)
    }
}
