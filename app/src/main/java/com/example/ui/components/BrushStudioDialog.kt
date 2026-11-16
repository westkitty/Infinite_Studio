package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.document.CustomBrushPreset
import com.example.ui.theme.StudioColors
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun BrushStudioDialog(
    currentPreset: CustomBrushPreset,
    savedPresets: List<CustomBrushPreset>,
    onUpdatePreset: (CustomBrushPreset) -> Unit,
    onSavePreset: (CustomBrushPreset) -> Unit,
    onClose: () -> Unit
) {
    var presetName by remember(currentPreset.id) { mutableStateOf(currentPreset.name) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.FuturesDialogBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("brush_studio_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Brush Dynamics Studio", style = MaterialTheme.typography.titleLarge.copy(color = StudioColors.TextPrimary))
                    Icon(Icons.Filled.Close, "Close Brush Studio", tint = StudioColors.TextMuted, modifier = Modifier.size(22.dp).clickable(onClick = onClose))
                }

                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                BrushSlider("Radius", currentPreset.radius, 1f..120f, "${currentPreset.radius.roundToInt()} px") {
                    onUpdatePreset(currentPreset.copy(radius = it))
                }
                BrushSlider("Flow", currentPreset.flow, 0.1f..1f, "${(currentPreset.flow * 100).roundToInt()}%") {
                    onUpdatePreset(currentPreset.copy(flow = it))
                }
                BrushSlider("Hardness", currentPreset.hardness, 0.1f..1f, "${(currentPreset.hardness * 100).roundToInt()}%") {
                    onUpdatePreset(currentPreset.copy(hardness = it))
                }
                BrushSlider("Spacing", currentPreset.spacing, 0.1f..0.8f, "${(currentPreset.spacing * 100).roundToInt()}%") {
                    onUpdatePreset(currentPreset.copy(spacing = it))
                }
                BrushSlider("Pressure Curve", currentPreset.pressureCurve, 0.4f..2.2f, String.format("%.2f", currentPreset.pressureCurve)) {
                    onUpdatePreset(currentPreset.copy(pressureCurve = it))
                }
                BrushSlider("Grain Texture", currentPreset.textureStrength, 0f..1f, "${(currentPreset.textureStrength * 100).roundToInt()}%") {
                    onUpdatePreset(currentPreset.copy(textureStrength = it))
                }

                if (savedPresets.isNotEmpty()) {
                    Text("Saved Presets", style = MaterialTheme.typography.labelMedium.copy(color = StudioColors.SkySoft))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedPresets.take(4).forEach { p ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F2B35))
                                    .clickable { onUpdatePreset(p) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(p.name, style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSavePreset(currentPreset.copy(id = "custom_${UUID.randomUUID().toString().take(6)}", name = presetName.ifBlank { "Custom Brush" }))
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText),
                    modifier = Modifier.fillMaxWidth().testTag("btn_save_brush_preset")
                ) {
                    Text("Save Custom Preset", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BrushSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextSecondary))
            Text(readout, style = MaterialTheme.typography.labelSmall.copy(color = StudioColors.SkyAccent))
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = StudioColors.SkyAccent, activeTrackColor = StudioColors.SkyAccent)
        )
    }
}
