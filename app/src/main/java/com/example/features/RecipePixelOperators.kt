package com.example.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import com.example.document.CanonicalDocument
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.document.RecipeNode
import com.example.document.TileKey
import java.io.ByteArrayOutputStream

/**
 * Actual deterministic F04 recipe execution over sparse PNG tiles. Intermediate artifacts
 * remain isolated from the canonical document until the user accepts the completed run.
 * No full 16k x 16k bitmap is ever materialized.
 */
internal object RecipePixelOperators {
    fun apply(
        source: DocumentLayer,
        node: RecipeNode,
        doc: CanonicalDocument,
        shouldCancel: () -> Boolean
    ): DocumentLayer {
        val outTiles = linkedMapOf<String, ByteArray>()
        val outHashes = linkedMapOf<String, String>()
        for ((tileKey, bytes) in source.tiles.toSortedMap()) {
            if (shouldCancel()) throw RecipePixelKernels.Canceled()
            val key = TileKey.parse(tileKey)
                ?: throw IllegalArgumentException("Invalid recipe tile key $tileKey")
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalArgumentException("Unrecognized PNG in recipe input $tileKey")
            try {
                require(bmp.width <= doc.tileSize && bmp.height <= doc.tileSize) {
                    "Oversized recipe tile: $tileKey"
                }
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val transformed = RecipePixelKernels.transform(
                    pixels, bmp.width, bmp.height, node.kind.name, node.parameters,
                    (doc.bounds.minX + key.tx * doc.tileSize).toInt(),
                    (doc.bounds.minY + key.ty * doc.tileSize).toInt(),
                    shouldCancel
                )
                if (transformed.any { (it ushr 24) != 0 }) {
                    val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    try {
                        out.setPixels(transformed, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                        val stream = ByteArrayOutputStream()
                        check(out.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        val png = stream.toByteArray()
                        outTiles[tileKey] = png
                        outHashes[tileKey] = DocumentArchiveCodec.sha256Hex(png)
                    } finally {
                        out.recycle()
                    }
                }
            } finally {
                bmp.recycle()
            }
        }
        return source.copy(
            id = "recipe_stage_${node.id}",
            name = "Stage: ${node.title}",
            visible = false,
            tiles = outTiles,
            tileHashes = outHashes
        )
    }

    /** Stable alpha-over composition for DAG nodes with two or more source ports. */
    fun composite(
        inputs: List<DocumentLayer>,
        doc: CanonicalDocument,
        shouldCancel: () -> Boolean
    ): DocumentLayer {
        require(inputs.isNotEmpty())
        if (inputs.size == 1) return inputs.first()
        require(inputs.all { it.transform == inputs.first().transform }) {
            "Recipe input transforms differ; align the layers before combining"
        }
        val keys = inputs.flatMap { it.tiles.keys }.toSortedSet()
        val tiles = linkedMapOf<String, ByteArray>()
        val hashes = linkedMapOf<String, String>()
        for (key in keys) {
            if (shouldCancel()) throw RecipePixelKernels.Canceled()
            val target = Bitmap.createBitmap(doc.tileSize, doc.tileSize, Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(target)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                for (layer in inputs) {
                    val bytes = layer.tiles[key] ?: continue
                    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw IllegalArgumentException("Invalid recipe input PNG: $key")
                    try {
                        c.drawBitmap(src, 0f, 0f, paint)
                    } finally {
                        src.recycle()
                    }
                }
                val px = IntArray(doc.tileSize * doc.tileSize)
                target.getPixels(px, 0, doc.tileSize, 0, 0, doc.tileSize, doc.tileSize)
                if (px.any { (it ushr 24) != 0 }) {
                    val stream = ByteArrayOutputStream()
                    check(target.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    val png = stream.toByteArray()
                    tiles[key] = png
                    hashes[key] = DocumentArchiveCodec.sha256Hex(png)
                }
            } finally {
                target.recycle()
            }
        }
        return inputs.first().copy(
            id = "recipe_composited",
            visible = false,
            tiles = tiles,
            tileHashes = hashes
        )
    }
}
