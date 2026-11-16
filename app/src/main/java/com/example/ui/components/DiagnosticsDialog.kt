package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.StudioColors

/**
 * Hidden-by-default Developer Diagnostics Sheet (unlocked via 5-tap on Brand Badge).
 * Exposes redacted document telemetry, tile cache stats, stylus motion counters, and provider mode.
 */
@Composable
fun DiagnosticsDialog(
    reportText: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
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
                    .widthIn(max = 580.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioColors.FuturesDialogBg)
                    .border(1.dp, StudioColors.FuturesDialogBorder, RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("diagnostics_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Developer Diagnostics (Redacted)", style = MaterialTheme.typography.titleMedium.copy(color = StudioColors.SkySoft))
                    Icon(Icons.Filled.Close, "Close diagnostics", tint = StudioColors.TextMuted, modifier = Modifier.size(22.dp).clickable(onClick = onClose))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A1016))
                        .border(1.dp, Color(0xFF283542), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = reportText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFCCE2EE),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )
                }

                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("InfiniteStudioDiagnostics", reportText))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioColors.SkySoft, contentColor = StudioColors.SkyDarkText),
                    modifier = Modifier.fillMaxWidth().testTag("btn_copy_diagnostics")
                ) {
                    Text("Copy Redacted Report")
                }
            }
        }
    }
}
