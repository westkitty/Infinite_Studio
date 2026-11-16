package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.document.CanonicalDocument
import com.example.document.DocumentLayer
import com.example.document.Vec2
import com.example.features.RecipePixelKernels
import com.example.features.TimeAndRecipeEngine
import com.example.model.StudioTool
import com.example.model.StudioUiState
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.tools.TileBrushEngine
import com.example.ui.canvas.StudioNativeCanvasView
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EngineRepairRegressionTest {

    private fun paintedDocument(renderer: SparseTileRenderer): CanonicalDocument {
        val blank = CanonicalDocument(
            layers = listOf(DocumentLayer("ink", "Original Ink")),
            activeLayerId = "ink"
        )
        return TileBrushEngine.commitStrokeToDocument(
            blank, renderer, "ink",
            listOf(Vec2(-20f, -10f) to 1f, Vec2(20f, 10f) to 1f),
            TileBrushEngine.StrokeRenderParams(
                StudioTool.INK, Color.rgb(35, 70, 160),
                TileBrushEngine.defaultPresetForTool(StudioTool.INK, 32f),
                SymmetryMode.NONE, null
            )
        )
    }

    @Test
    fun `F04 stages produce pixels, preserve original, and reuse actual cached upstream artifacts`() {
        val doc = paintedDocument(SparseTileRenderer())
        val hash = doc.layers.first().pixelContentHash()
        val initial = TimeAndRecipeEngine.executeRecipeGraph(doc)
        assertFalse(initial.canceled)
        assertEquals(4, initial.computedNodeIds.size)
        assertNotNull(initial.producedLayer)
        assertTrue(initial.producedLayer!!.tiles.isNotEmpty())
        assertNotEquals(hash, initial.producedLayer!!.pixelContentHash())
        assertTrue(initial.updatedGraph.results.values.all { it.status == "SUCCEEDED" })
        assertEquals(hash, doc.layers.first().pixelContentHash())

        var rerunDoc = doc.copy(recipes = listOf(initial.updatedGraph))
        val second = TimeAndRecipeEngine.executeRecipeGraph(rerunDoc)
        assertEquals(4, second.reusedNodeIds.size)

        rerunDoc = TimeAndRecipeEngine.updateRecipeNodeParameter(
            rerunDoc, rerunDoc.recipes.first().id, "n_mat", "roughness", "0.85"
        )
        val third = TimeAndRecipeEngine.executeRecipeGraph(rerunDoc)
        assertTrue(third.reusedNodeIds.contains("n_cleanup"))
        assertTrue(third.reusedNodeIds.contains("n_line"))
        assertTrue(third.reusedNodeIds.contains("n_flat"))
        assertTrue(third.computedNodeIds.contains("n_mat"))
        assertNotEquals(
            initial.updatedGraph.results.getValue("n_mat").outputHash,
            third.updatedGraph.results.getValue("n_mat").outputHash
        )
        assertEquals(hash, doc.layers.first().pixelContentHash())
    }

    @Test
    fun `F04 cancellation and invalid parameters cannot produce an accepted layer`() {
        val doc = paintedDocument(SparseTileRenderer())
        val hash = doc.layers.first().pixelContentHash()
        val canceled = TimeAndRecipeEngine.executeRecipeGraph(doc, shouldCancel = { true })
        assertTrue(canceled.canceled)
        assertTrue(canceled.producedLayer == null)
        val invalid = TimeAndRecipeEngine.updateRecipeNodeParameter(
            doc, doc.recipes.first().id, "n_mat", "roughness", "9"
        )
        val failed = TimeAndRecipeEngine.executeRecipeGraph(invalid)
        assertTrue(failed.updatedGraph.results.getValue("n_mat").status.startsWith("ERROR"))
        assertTrue(failed.producedLayer == null)
        assertEquals(hash, doc.layers.first().pixelContentHash())
    }

    @Test
    fun `F04 deterministic pixel kernels change real image data and bound cancellation`() {
        val original = intArrayOf(0, 0xFF1E5FC0.toInt(), 0x80AB2255.toInt(), 0x01FF0000)
        for (operator in listOf("CLEANUP", "LINE_WORK", "FLAT_COLOR", "MATERIAL_PREVIEW", "EXPORT_PREP")) {
            val result = RecipePixelKernels.transform(original, 2, 2, operator, emptyMap(), 20, 11)
            assertEquals(4, result.size)
            assertEquals(0, result[0])
        }
        val export = RecipePixelKernels.transform(original, 2, 2, "EXPORT_PREP", emptyMap())
        assertEquals(0, export[3])
        var sawCancellation = false
        try {
            RecipePixelKernels.transform(original, 2, 2, "CLEANUP", emptyMap(), shouldCancel = { true })
        } catch (_: RecipePixelKernels.Canceled) {
            sawCancellation = true
        }
        assertTrue(sawCancellation)
    }

    private fun canvasView(
        state: StudioUiState,
        renderer: SparseTileRenderer
    ): StudioNativeCanvasView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return StudioNativeCanvasView(
            context = context,
            tileRenderer = renderer,
            getState = { state },
            onUpdateViewport = { _, _, _ -> },
            onBeginPointer = { _, _ -> null to null },
            onMovePointer = { _, _ -> null to null },
            onEndPointer = {},
            onCancelStroke = {},
            onTwoFingerTapUndo = {},
            onThreeFingerTapRedo = {},
            onDiagnostics = {},
            onSelectVector = {},
            onMoveVectorLive = { _, _, _ -> },
            onResizeVectorLive = { _, _, _ -> },
            onMoveBezierPartLive = { _, _, _, _ -> },
            onMoveBalloonTailLive = { _, _ -> },
            onCommitVectorEdits = {},
            onUpdateTransformLive = { _, _, _, _ -> },
            onCommitTransform = {}
        ).apply {
            layout(0, 0, 600, 400)
        }
    }

    private fun render(view: StudioNativeCanvasView): Bitmap =
        Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun touch(view: StudioNativeCanvasView, action: Int) {
        val event = MotionEvent.obtain(0, 12, action, 300f, 200f, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }

    @Test
    fun `G03 provisional ink renders at world origin at view center before commit`() {
        val doc = CanonicalDocument(layers = listOf(DocumentLayer("ink", "Ink")), activeLayerId = "ink", paperColorHex = "#ffffff")
        val state = StudioUiState(
            document = doc,
            activeTool = StudioTool.INK,
            activeBrushPreset = TileBrushEngine.defaultPresetForTool(StudioTool.INK, 28f),
            brushColorInt = Color.RED,
            sPenOnly = false
        )
        val view = canvasView(state, SparseTileRenderer())
        touch(view, MotionEvent.ACTION_DOWN)
        val during = render(view)
        try {
            val center = during.getPixel(306, 206)
            assertTrue("Ink must appear at center, not shifted by document minX/minY", Color.red(center) > 180 && Color.blue(center) < 160)
        } finally {
            during.recycle()
        }
        touch(view, MotionEvent.ACTION_CANCEL)
        val afterCancel = render(view)
        try {
            assertEquals(Color.WHITE, afterCancel.getPixel(306, 206))
        } finally {
            afterCancel.recycle()
        }
        assertTrue(doc.layers.first().tiles.isEmpty())
    }

    @Test
    fun `G03 eraser previews removal of active layer only and cancel restores source`() {
        val renderer = SparseTileRenderer()
        val doc = paintedDocument(renderer).copy(paperColorHex = "#ffffff")
        val hash = doc.layers.first().pixelContentHash()
        val state = StudioUiState(
            document = doc,
            activeTool = StudioTool.ERASER,
            activeBrushPreset = TileBrushEngine.defaultPresetForTool(StudioTool.ERASER, 50f),
            sPenOnly = false
        )
        val view = canvasView(state, renderer)
        val before = render(view)
        val originalPixel = before.getPixel(306, 206)
        before.recycle()
        assertNotEquals(Color.WHITE, originalPixel)
        touch(view, MotionEvent.ACTION_DOWN)
        val preview = render(view)
        try {
            assertEquals(Color.WHITE, preview.getPixel(306, 206))
        } finally {
            preview.recycle()
        }
        assertEquals(hash, doc.layers.first().pixelContentHash())
        touch(view, MotionEvent.ACTION_CANCEL)
        val restored = render(view)
        try {
            assertEquals(originalPixel, restored.getPixel(306, 206))
        } finally {
            restored.recycle()
        }
    }
}
