package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.ExportFormat
import com.example.ui.theme.StudioColors
import kotlin.math.roundToInt

@Composable
fun ExportDialog(
    previewBitmap: Bitmap?,
    selectedFormat: ExportFormat,
    jpgQuality: Int,
    onSelectFormat: (ExportFormat) -> Unit,
    onJpgQualityChange: (Int) -> Unit,
    onSaveFile: () -> Unit,
    onShareFile: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .safeDrawingPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.GlassPanel)
                    .border(1.dp, Color(0xCC3F3F46), RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .testTag("export_dialog"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilePresent,
                            contentDescription = null,
                            tint = StudioColors.SkyAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.export_title),
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("btn_export_close")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close export dialog",
                            tint = StudioColors.TextMuted
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF27272A))

                // Live Preview with Checkerboard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xCC3F3F46), RoundedCornerShape(16.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CheckerboardBackground(
                        darkColor = StudioColors.CanvasCheckerDark,
                        lightColor = StudioColors.CanvasCheckerLight,
                        cellPx = 20,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (previewBitmap != null) {
                        val img = remember(previewBitmap) { previewBitmap.asImageBitmap() }
                        Image(
                            bitmap = img,
                            contentDescription = "Export Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Format Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FormatCard(
                        title = "Transparent PNG",
                        badge = "ALPHA",
                        subtitle = "Preserves transparency without background paper.",
                        selected = selectedFormat == ExportFormat.PNG,
                        testTag = "btn_fmt_png",
                        onClick = { onSelectFormat(ExportFormat.PNG) },
                        modifier = Modifier.weight(1f)
                    )
                    FormatCard(
                        title = "Standard JPG",
                        badge = "FLAT",
                        subtitle = "Flattens onto paper background for sharing.",
                        selected = selectedFormat == ExportFormat.JPG,
                        testTag = "btn_fmt_jpg",
                        onClick = { onSelectFormat(ExportFormat.JPG) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedFormat == ExportFormat.JPG) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xB318181B))
                            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "JPG Quality",
                            style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted)
                        )
                        Slider(
                            value = jpgQuality.toFloat(),
                            onValueChange = { onJpgQualityChange(it.roundToInt()) },
                            valueRange = 50f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = StudioColors.SkyAccent,
                                activeTrackColor = StudioColors.SkyAccent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("slider_jpg_quality")
                        )
                        Text(
                            text = "$jpgQuality%",
                            style = MaterialTheme.typography.labelSmall.copy(color = StudioColors.TextPrimary)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSaveFile,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(StudioColors.SkyAccent, StudioColors.IndigoAccent)
                                )
                            )
                            .testTag("btn_export_confirm")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save File", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onShareFile,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F3F46)),
                        modifier = Modifier.testTag("btn_export_share")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = StudioColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share", color = StudioColors.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatCard(
    title: String,
    badge: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) StudioColors.SkyAccent.copy(alpha = 0.2f) else Color(0xCC18181B))
            .border(
                1.dp,
                if (selected) StudioColors.SkyAccent.copy(alpha = 0.55f) else Color(0xFF27272A),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (selected) StudioColors.SkySoft else StudioColors.TextMuted,
                    fontSize = 9.sp
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (selected) StudioColors.SkyAccent.copy(alpha = 0.25f) else Color(0xFF27272A))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(color = StudioColors.TextMuted, fontSize = 11.sp)
        )
    }
}
