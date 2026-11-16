package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CURATED_SWATCHES
import com.example.ui.theme.StudioColors

@Composable
fun ColorStudioPopover(
    currentColorHex: String,
    currentColorInt: Int,
    onSelectColorHex: (String) -> Unit,
    onSelectColorInt: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = remember(currentColorInt) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentColorInt, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember(currentColorHex) { mutableStateOf(currentColorHex) }

    LaunchedEffect(currentColorInt) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(currentColorInt, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
    }

    fun emitHsv(h: Float, s: Float, v: Float) {
        val colorInt = AndroidColor.HSVToColor(floatArrayOf(h, s, v))
        onSelectColorInt(colorInt)
    }

    Column(
        modifier = modifier
            .width(268.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(StudioColors.GlassPanel)
            .border(1.dp, StudioColors.GlassPanelBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .testTag("popover_color_studio"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COLOR STUDIO",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = StudioColors.TextSecondary,
                    letterSpacing = 1.sp
                )
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("btn_close_color_studio")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Color Studio",
                    tint = StudioColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Saturation/Value Pad
        val pureHueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(14.dp))
                .pointerInput(hue) {
                    detectTapGestures { pos ->
                        sat = (pos.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        emitHsv(hue, sat, value)
                    }
                }
                .pointerInput(hue) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        sat = (change.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        emitHsv(hue, sat, value)
                    }
                }
        ) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pureHueColor)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Hue Spectrum Bar
        val hueColors = remember {
            listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(10.dp))
                .pointerInput(sat, value) {
                    detectTapGestures { pos ->
                        hue = ((pos.x / size.width).coerceIn(0f, 1f)) * 360f
                        emitHsv(hue, sat, value)
                    }
                }
                .pointerInput(sat, value) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        hue = ((change.position.x / size.width).coerceIn(0f, 1f)) * 360f
                        emitHsv(hue, sat, value)
                    }
                }
        ) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
            val hx = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = Offset(hx, size.height / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Active Color Preview + Hex Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(currentColorInt))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    val trimmed = raw.take(7)
                    hexInput = trimmed
                    onSelectColorHex(trimmed)
                },
                singleLine = true,
                label = { Text("HEX CODE", fontSize = 10.sp) },
                textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StudioColors.SkyAccent,
                    unfocusedBorderColor = Color(0xFF3F3F46),
                    focusedTextColor = StudioColors.TextPrimary,
                    unfocusedTextColor = StudioColors.TextPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_hex_color")
            )
        }

        // Curated Artist Swatches (6x4 grid)
        Text(
            text = "STUDIO SWATCHES",
            style = MaterialTheme.typography.labelMedium.copy(
                color = StudioColors.TextMuted,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CURATED_SWATCHES.chunked(6).forEach { rowSwatches ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowSwatches.forEach { hex ->
                        val swColor = Color(AndroidColor.parseColor(hex))
                        val isSelected = hex.equals(currentColorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(swColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF3F3F46),
                                    shape = RoundedCornerShape(9.dp)
                                )
                                .clickable { onSelectColorHex(hex) }
                                .testTag("swatch_${hex.drop(1)}")
                        )
                    }
                }
            }
        }
    }
}
