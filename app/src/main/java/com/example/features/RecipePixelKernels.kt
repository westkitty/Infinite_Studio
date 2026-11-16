package com.example.features

import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure, deterministic per-tile raster operators. No Android, network, or document mutations.
 * ARGB arrays are unpremultiplied. Pixel processing is bounded to one sparse tile at a time.
 */
internal object RecipePixelKernels {
    class Canceled : RuntimeException("Recipe processing canceled")

    fun transform(
        input: IntArray,
        width: Int,
        height: Int,
        kind: String,
        params: Map<String, String>,
        tileOffsetX: Int = 0,
        tileOffsetY: Int = 0,
        shouldCancel: () -> Boolean = { false }
    ): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height <= 262144L)
        require(input.size == width * height)
        require(kind in setOf("CLEANUP", "LINE_WORK", "FLAT_COLOR", "MATERIAL_PREVIEW", "EXPORT_PREP")) {
            "Unsupported recipe operator: $kind"
        }
        val threshold = (params["threshold"]?.toIntOrNull() ?: 118).coerceIn(1, 255)
        val weight = (params["weight"]?.toFloatOrNull() ?: 1.4f).coerceIn(0.01f, 20f)
        val roughness = (params["roughness"]?.toFloatOrNull() ?: 0.35f).coerceIn(0f, 1f)
        val palette = when (params["palette"]?.lowercase()) {
            "copper" -> intArrayOf(48, 25, 25, 213, 115, 56)
            "blue", "night" -> intArrayOf(19, 48, 99, 93, 170, 244)
            else -> intArrayOf(72, 61, 68, 236, 221, 198)
        }
        val output = IntArray(input.size)
        for (y in 0 until height) {
            if (shouldCancel()) throw Canceled()
            for (x in 0 until width) {
                val idx = y * width + x
                val c = input[idx]
                val a = c ushr 24
                if (a == 0) continue
                val r = c ushr 16 and 255
                val g = c ushr 8 and 255
                val b = c and 255
                val lum = (77 * r + 150 * g + 29 * b) ushr 8
                var oa = a
                var nr = r
                var ng = g
                var nb = b
                when (kind) {
                    "CLEANUP" -> {
                        // Remove nearly white imported paper; strengthen remaining marks.
                        if (lum >= 250) oa = 0
                        val contrast = ((lum - threshold) * 1.35f + threshold).roundToInt().coerceIn(0, 255)
                        nr = contrast; ng = contrast; nb = contrast
                    }
                    "LINE_WORK" -> {
                        // Isolate dark lines into an ink-alpha layer without a generative model.
                        val intensity = ((255 - lum) * weight).roundToInt().coerceIn(0, 255)
                        oa = (a * intensity / 255).coerceIn(0, 255)
                        nr = 25; ng = 28; nb = 34
                    }
                    "FLAT_COLOR" -> {
                        val t = (lum / 255f).coerceIn(0f, 1f)
                        nr = (palette[0] + (palette[3] - palette[0]) * t).roundToInt()
                        ng = (palette[1] + (palette[4] - palette[1]) * t).roundToInt()
                        nb = (palette[2] + (palette[5] - palette[2]) * t).roundToInt()
                    }
                    "MATERIAL_PREVIEW" -> {
                        // A modest local directional surface-light study, not PBR.
                        val phase = (tileOffsetX + x) * 0.012 + (tileOffsetY + y) * 0.007
                        val illumination = (sin(phase) * (1f - roughness) * 38f).roundToInt()
                        nr = (r + illumination).coerceIn(0, 255)
                        ng = (g + illumination).coerceIn(0, 255)
                        nb = (b + illumination).coerceIn(0, 255)
                    }
                    "EXPORT_PREP" -> {
                        // Remove near-transparent residue before downstream asset packaging.
                        if (a <= 2) oa = 0
                    }
                }
                output[idx] = if (oa == 0) 0 else
                    (oa shl 24) or (nr.coerceIn(0, 255) shl 16) or
                    (ng.coerceIn(0, 255) shl 8) or nb.coerceIn(0, 255)
            }
        }
        return output
    }
}
