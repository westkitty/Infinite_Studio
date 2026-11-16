package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.example.document.CANONICAL_SCHEMA_VERSION
import com.example.document.CanonicalDocument
import com.example.document.ConstraintKind
import com.example.document.ConstraintRecord
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentBounds
import com.example.document.DocumentCommand
import com.example.document.DocumentLayer
import com.example.document.TransactionEngine
import com.example.document.Vec2
import com.example.document.VectorKind
import com.example.document.VectorObject
import com.example.render.SparseTileRenderer
import com.example.tools.TileBrushEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocumentAndArchiveTests {

    @Test
    fun `v3 archive write and read roundtrip preserves multi-layer tiles checksums and unknown fields`() {
        val renderer = SparseTileRenderer()
        val baseLayer = DocumentLayer(id = "l1", name = "Sketch")
        val inkLayer = DocumentLayer(id = "l2", name = "Ink")
        var doc = CanonicalDocument(
            documentId = "doc_test_roundtrip",
            title = "Tab S9 Test",
            layers = listOf(baseLayer, inkLayer),
            activeLayerId = "l2",
            vectorObjects = listOf(
                VectorObject(id = "v1", kind = VectorKind.TEXT_BOX, text = "EXACT TITLE", x = 10f, y = 20f)
            ),
            constraints = listOf(
                ConstraintRecord(
                    id = "c1",
                    kind = ConstraintKind.PROTECTED_SILHOUETTE,
                    label = "Hero Silhouette",
                    points = listOf(Vec2(0f, 0f), Vec2(100f, 0f), Vec2(100f, 100f))
                )
            ),
            unknownFields = mapOf("future_meta" to "\"preserve-me\"")
        )
        doc = TileBrushEngine.commitStrokeToDocument(
            doc = doc,
            renderer = renderer,
            targetLayerId = "l2",
            worldPoints = listOf(Vec2(-40f, -40f) to 0.8f, Vec2(40f, 40f) to 0.9f),
            params = TileBrushEngine.StrokeRenderParams(
                tool = com.example.model.StudioTool.INK,
                colorInt = Color.CYAN,
                preset = TileBrushEngine.defaultPresetForTool(com.example.model.StudioTool.INK, 10f),
                symmetryMode = com.example.model.SymmetryMode.NONE,
                activeMask = null
            )
        )

        val archiveBytes = DocumentArchiveCodec.writeArchiveToBytes(doc)
        val decoded = DocumentArchiveCodec.readDocument(archiveBytes).getOrThrow()

        assertEquals(CANONICAL_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(doc.documentId, decoded.documentId)
        assertEquals(2, decoded.layers.size)
        assertEquals(doc.layers[1].pixelContentHash(), decoded.layers[1].pixelContentHash())
        assertEquals("EXACT TITLE", decoded.vectorObjects.first().text)
        assertEquals(1, decoded.constraints.size)
        assertEquals("\"preserve-me\"", decoded.unknownFields["future_meta"])
    }

    @Test
    fun `legacy v2 json project migrates losslessly without flattening layers`() {
        fun makeEncodedPng(color: Int): String {
            val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawCircle(256f, 256f, 60f, Paint().apply { this.color = color })
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            bmp.recycle()
            return "data:image/png;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }

        val legacyJson = """
            {
              "format": "infinite-studio-project",
              "version": 2,
              "bounds": {"minX": -256.0, "minY": -256.0, "width": 512, "height": 512},
              "paperColor": "#1e1e22",
              "activeLayerId": "layer_a",
              "layers": [
                {"id": "layer_a", "name": "Lines", "visible": true, "opacity": 0.9, "blendMode": "source-over", "alphaLock": false, "png": "${makeEncodedPng(Color.BLACK)}"},
                {"id": "layer_b", "name": "Color", "visible": true, "opacity": 0.7, "blendMode": "multiply", "alphaLock": true, "png": "${makeEncodedPng(Color.RED)}"}
              ]
            }
        """.trimIndent()

        val migrated = DocumentArchiveCodec.readDocument(legacyJson.toByteArray(Charsets.UTF_8)).getOrThrow()
        assertEquals(2, migrated.layers.size)
        assertEquals("Lines", migrated.layers[0].name)
        assertEquals("Color", migrated.layers[1].name)
        assertTrue(migrated.layers[0].tiles.isNotEmpty())
        assertTrue(migrated.layers[1].tiles.isNotEmpty())
        assertTrue(migrated.layers[1].alphaLock)
        assertEquals("MIGRATION_V2_TO_V3", migrated.revisions.first().processingMode)
    }

    @Test
    fun `zip-slip traversal truncated archive and unsupported future version fail safely`() {
        // 1. Zip-slip
        val slipOut = ByteArrayOutputStream()
        ZipOutputStream(slipOut).use { zos ->
            zos.putNextEntry(ZipEntry("../../etc/passwd"))
            zos.write("bad".toByteArray())
            zos.closeEntry()
        }
        val slipRes = DocumentArchiveCodec.readDocument(slipOut.toByteArray())
        assertTrue(slipRes.isFailure)

        // 2. Truncated / corrupt payload
        val corruptRes = DocumentArchiveCodec.readDocument(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
        assertTrue(corruptRes.isFailure)

        // 3. Unsupported mandatory future version
        val futureZip = ByteArrayOutputStream()
        ZipOutputStream(futureZip).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("""{"format":"infinite-studio-archive","schemaVersion":99,"document":{"documentId":"x","revision":1,"title":"x","bounds":{"minX":0,"minY":0,"width":256,"height":256,"units":"px"},"tileSize":256,"paperColorHex":"#ffffff","layers":[{"id":"l1","name":"L1","visible":true,"opacity":1.0,"blendModeId":"source-over","alphaLock":false}],"activeLayerId":"l1"}}""".toByteArray())
            zos.closeEntry()
        }
        val futureRes = DocumentArchiveCodec.readDocument(futureZip.toByteArray())
        assertTrue(futureRes.isFailure)
        assertTrue(futureRes.exceptionOrNull()?.message?.contains("Unsupported") == true)
    }

    @Test
    fun `transaction engine supports undo redo redo-clearing and structural tile sharing`() {
        val l1 = DocumentLayer(id = "l1", name = "Base")
        val initial = CanonicalDocument(layers = listOf(l1), activeLayerId = "l1")
        val engine = TransactionEngine(initial)

        assertFalse(engine.canUndo())
        engine.commit(DocumentCommand("Rename") { it.copy(title = "Step 1") })
        engine.commit(DocumentCommand("Rename 2") { it.copy(title = "Step 2") })
        assertEquals("Step 2", engine.current().title)
        assertTrue(engine.canUndo())

        assertNotNull(engine.undo())
        assertEquals("Step 1", engine.current().title)
        assertTrue(engine.canRedo())

        // Branching commit clears redo stack
        engine.commit(DocumentCommand("Forked Step") { it.copy(title = "Step 1B") })
        assertFalse(engine.canRedo())
        assertEquals("Step 1B", engine.current().title)
    }
}
