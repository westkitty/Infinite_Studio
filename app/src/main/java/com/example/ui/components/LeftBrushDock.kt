package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StudioTool
import com.example.model.StudioUiState
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun LeftBrushDock(
    state: StudioUiState,
    compact: Boolean,
    onToggleColorPicker: () -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Float) -> Unit,
    onSelectEyedropper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackHeight = if (compact) 76.dp else 96.dp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(StudioColors.GlassPanel)
            .border(1.dp, StudioColors.GlassPanelBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        // Color Swatch Trigger
        Box(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onToggleColorPicker)
                .testTag("btn_color_picker"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(state.brushColorInt))
                    .border(2.dp, Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF18181B))
                    .border(1.dp, Color(0xFF3F3F46), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Palette,
                    contentDescription = "Color Studio",
                    tint = StudioColors.TextSecondary,
                    modifier = Modifier.size(9.dp)
                )
            }
        }

        // Size Slider
        VerticalStudioSlider(
            label = "SIZE",
            valueText = "${state.brushSize.roundToInt()}px",
            fraction = ((state.brushSize - 1f) / 119f).coerceIn(0f, 1f),
            trackHeight = trackHeight,
            testTag = "slider_brush_size",
            onFractionChange = { f -> onBrushSizeChange(1f + f * 119f) }
        )

        Box(
            modifier = Modifier
                .width(24.dp)
                .height(1.dp)
                .background(Color(0xB33F3F46))
        )

        // Flow / Opacity Slider
        VerticalStudioSlider(
            label = "FLOW",
            valueText = "${(state.brushOpacity * 100f).roundToInt()}%",
            fraction = ((state.brushOpacity - 0.05f) / 0.95f).coerceIn(0f, 1f),
            trackHeight = trackHeight,
            testTag = "slider_brush_opacity",
            onFractionChange = { f -> onBrushOpacityChange(0.05f + f * 0.95f) }
        )

        // Eyedropper
        val isEyedropper = state.activeTool == StudioTool.EYEDROPPER
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isEyedropper) StudioColors.SkyAccent.copy(alpha = 0.22f) else Color.Transparent)
                .border(
                    width = if (isEyedropper) 1.dp else 0.dp,
                    color = if (isEyedropper) StudioColors.SkyAccent.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onSelectEyedropper)
                .testTag("btn_eyedropper"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Colorize,
                contentDescription = "Eyedropper",
                tint = if (isEyedropper) StudioColors.SkyAccent else StudioColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun VerticalStudioSlider(
    label: String,
    valueText: String,
    fraction: Float,
    trackHeight: androidx.compose.ui.unit.Dp,
    testTag: String,
    onFractionChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    val trackPx = with(density) { trackHeight.toPx() }
    val thumbSize = 18.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = StudioColors.TextMuted,
                fontSize = 9.5.sp,
                letterSpacing = 0.8.sp
            )
        )

        Box(
            modifier = Modifier
                .width(36.dp)
                .height(trackHeight)
                .testTag(testTag)
                .pointerInput(trackPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val f = 1f - (offset.y / trackPx).coerceIn(0f, 1f)
                            onFractionChange(f)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val f = 1f - (change.position.y / trackPx).coerceIn(0f, 1f)
                            onFractionChange(f)
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Track
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF27272A))
            ) {
                // Active fill from bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(6.dp)
                        .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(StudioColors.SkyAccent.copy(alpha = 0.65f))
                )
            }

            // Thumb
            val thumbOffsetPx = ((1f - fraction) * (trackPx - with(density) { thumbSize.toPx() })).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetPx) }
                    .size(thumbSize)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(StudioColors.SkyAccent)
                    .border(2.dp, Color.White, CircleShape)
            )
        }

        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall.copy(
                color = StudioColors.TextSecondary,
                fontSize = 10.5.sp
            )
        )
    }
}
