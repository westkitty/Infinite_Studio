package com.example

import android.graphics.Color
import android.graphics.RectF
import android.view.MotionEvent
import com.example.document.CanonicalDocument
import com.example.document.DocumentBounds
import com.example.document.DocumentLayer
import com.example.document.TileKey
import com.example.document.Vec2
import com.example.input.PointerIntent
import com.example.input.StylusInputRouter
import com.example.model.StudioTool
import com.example.model.SymmetryMode
import com.example.render.SparseTileRenderer
import com.example.tools.TileBrushEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StylusAndTileRendererTests {

    @Test
    fun `stylus input router distinguishes stylus touch eraser and consumes historical batched samples`() {
        var begun = 0
        var movedSamples = 0
        var canceled = 0
        var lastIntent: PointerIntent? = null
        var undoCalls = 0

        val router = StylusInputRouter(
            onBeginStroke = { begun++; lastIntent = it.intent },
            onMoveStroke = { movedSamples += it.size },
            onEndStroke = {},
            onCancelStroke = { canceled++ },
            onHoverMove = { _, _ -> },
            onPanZoom = { _, _, _ -> },
            onTwoFingerTapUndo = { undoCalls++ },
            onThreeFingerTapRedo = {}
        )

        val downProps = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        })
        val downCoords = arrayOf(MotionEvent.PointerCoords().apply {
            x = 400f; y = 300f; pressure = 0.72f
        })
        val downEv = MotionEvent.obtain(
            100L, 100L, MotionEvent.ACTION_DOWN,
            1, downProps, downCoords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        router.handleMotionEvent(downEv, 1200f, 800f, 1f, 0f, 0f, sPenGuardEnabled = true)
        downEv.recycle()

        assertEquals(1, begun)
        assertEquals(PointerIntent.STYLUS, lastIntent)

        // Batched historical move
        val c0 = arrayOf(MotionEvent.PointerCoords().apply { x = 410f; y = 310f; pressure = 0.75f })
        val c1 = arrayOf(MotionEvent.PointerCoords().apply { x = 420f; y = 320f; pressure = 0.80f })
        val moveEv = MotionEvent.obtain(100L, 105L, MotionEvent.ACTION_MOVE, 1, downProps, c0, 0, 0, 1f, 1f, 0, 0, 0, 0)
        moveEv.addBatch(110L, c1, 0)
        router.handleMotionEvent(moveEv, 1200f, 800f, 1f, 0f, 0f, sPenGuardEnabled = true)
        moveEv.recycle()

        assertEquals(2, movedSamples)
        assertEquals(1L, router.diagnostics.historicalSamplesConsumed)

        // ACTION_CANCEL triggers rollback
        val cancelEv = MotionEvent.obtain(100L, 115L, MotionEvent.ACTION_CANCEL, 1, downProps, c1, 0, 0, 1f, 1f, 0, 0, 0, 0)
        router.handleMotionEvent(cancelEv, 1200f, 800f, 1f, 0f, 0f, sPenGuardEnabled = true)
        cancelEv.recycle()

        assertEquals(1, canceled)
        assertEquals(1L, router.diagnostics.canceledTransactions)
    }

    @Test
    fun `sPenGuard routes single touch to pan instead of stroke`() {
        var begun = 0
        var panned = 0
        val router = StylusInputRouter(
            onBeginStroke = { begun++ },
            onMoveStroke = {},
            onEndStroke = {},
            onCancelStroke = {},
            onHoverMove = { _, _ -> },
            onPanZoom = { _, _, _ -> panned++ },
            onTwoFingerTapUndo = {},
            onThreeFingerTapRedo = {}
        )
        val touchProps = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val c0 = arrayOf(MotionEvent.PointerCoords().apply { x = 100f; y = 100f })
        val c1 = arrayOf(MotionEvent.PointerCoords().apply { x = 140f; y = 120f })
        val down = MotionEvent.obtain(10L, 10L, MotionEvent.ACTION_DOWN, 1, touchProps, c0, 0, 0, 1f, 1f, 0, 0, 0, 0)
        val move = MotionEvent.obtain(10L, 20L, MotionEvent.ACTION_MOVE, 1, touchProps, c1, 0, 0, 1f, 1f, 0, 0, 0, 0)
        router.handleMotionEvent(down, 1200f, 800f, 1f, 0f, 0f, sPenGuardEnabled = true)
        router.handleMotionEvent(move, 1200f, 800f, 1f, 0f, 0f, sPenGuardEnabled = true)
        down.recycle(); move.recycle()

        assertEquals(0, begun)
        assertEquals(1, panned)
    }

    @Test
    fun `sparse tile renderer lazily allocates only touched tiles and culls viewport cleanly`() {
        val renderer = SparseTileRenderer()
        val bounds = DocumentBounds(minX = -1024f, minY = -1024f, width = 2048, height = 2048)
        val layer = DocumentLayer(id = "l1", name = "Ink")
        var doc = CanonicalDocument(bounds = bounds, layers = listOf(layer), activeLayerId = "l1")

        // Commit stroke only near (0, 0)
        doc = TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "l1",
            worldPoints = listOf(Vec2(-10f, -10f) to 0.8f, Vec2(10f, 10f) to 0.8f),
            params = TileBrushEngine.StrokeRenderParams(
                tool = StudioTool.INK,
                colorInt = Color.BLUE,
                preset = TileBrushEngine.defaultPresetForTool(StudioTool.INK, 12f),
                symmetryMode = SymmetryMode.NONE,
                activeMask = null
            )
        )

        val tileCount = doc.layers.first().tiles.size
        // 2048x2048 = 64 tiles total, only 1-4 central tiles should be allocated!
        assertTrue("Expected sparse tile count in 1..4, got $tileCount", tileCount in 1..4)

        val visibleCenter = renderer.visibleTileKeysForWorldRect(bounds, 256, RectF(-100f, -100f, 100f, 100f))
        assertTrue(visibleCenter.size <= 4)
        assertFalse(visibleCenter.contains(TileKey(0, 0)))
    }
}
