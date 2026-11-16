package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode

/**
 * Reusable zero-allocation checkerboard background backed by a cached ImageBitmap ShaderBrush
 * instead of per-frame nested Canvas drawRect loops.
 */
@Composable
fun CheckerboardBackground(
    darkColor: Color,
    lightColor: Color,
    cellPx: Int = 22,
    modifier: Modifier = Modifier
) {
    val brush = remember(darkColor, lightColor, cellPx) {
        val size = (cellPx.coerceAtLeast(8)) * 2
        val bmp = ImageBitmap(size, size)
        val c = Canvas(bmp)
        val darkPaint = Paint().apply { color = darkColor }
        val lightPaint = Paint().apply { color = lightColor }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), darkPaint)
        c.drawRect(0f, 0f, cellPx.toFloat(), cellPx.toFloat(), lightPaint)
        c.drawRect(cellPx.toFloat(), cellPx.toFloat(), size.toFloat(), size.toFloat(), lightPaint)
        ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
    }
    Box(modifier = modifier.background(brush))
}
