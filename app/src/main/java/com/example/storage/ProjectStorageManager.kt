package com.example.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.document.CanonicalDocument
import com.example.document.DocumentArchiveCodec
import com.example.document.DocumentLayer
import com.example.model.ExportFormat
import com.example.render.SparseTileRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
data class RecoveryJournal(
    val activeDocumentId: String,
    val lastRevision: Long,
    val draftFileName: String,
    val cleanShutdown: Boolean,
    val updatedAtIso: String
)

/**
 * Atomic, crash-safe project storage manager supporting:
 * - Local draft & journal persistence in filesDir/projects/ (write-to-temp + rename)
 * - SAF .iastudio v3 ZIP export and v2/v3 import via DocumentArchiveCodec
 * - Zero-permission Photo Picker image import into a new sparse-tile layer
 * - Flattened PNG / JPG / SVG / Foundry ZIP export and FileProvider share
 */
class ProjectStorageManager(private val context: Context) {

    private val projectsDir: File by lazy {
        File(context.filesDir, "projects").apply { mkdirs() }
    }
    private val draftFile: File by lazy { File(projectsDir, "autosave_draft.iastudio") }
    private val journalFile: File by lazy { File(projectsDir, "recovery_journal.json") }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun hasRecoverableDraft(): Boolean = draftFile.exists() && draftFile.length() > 16L

    fun readJournal(): RecoveryJournal? {
        if (!journalFile.exists()) return null
        return runCatching {
            json.decodeFromString<RecoveryJournal>(journalFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    suspend fun saveLocalDraftAtomic(doc: CanonicalDocument): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val archiveBytes = DocumentArchiveCodec.writeArchiveToBytes(doc)
            writeBytesAtomic(draftFile, archiveBytes)

            val journal = RecoveryJournal(
                activeDocumentId = doc.documentId,
                lastRevision = doc.revision,
                draftFileName = draftFile.name,
                cleanShutdown = false,
                updatedAtIso = Instant.now().toString()
            )
            writeBytesAtomic(journalFile, json.encodeToString(journal).toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun loadLocalDraft(): Result<CanonicalDocument> = withContext(Dispatchers.IO) {
        if (!draftFile.exists()) {
            Result.failure(IllegalStateException("No local draft archive found."))
        } else {
            DocumentArchiveCodec.readDocument(draftFile.readBytes())
        }
    }

    suspend fun saveArchiveToUri(uri: Uri, doc: CanonicalDocument): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = DocumentArchiveCodec.writeArchiveToBytes(doc)
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(bytes)
                os.flush()
            } ?: error("Unable to open destination URI for writing.")
        }
    }

    suspend fun openArchiveFromUri(uri: Uri): Result<CanonicalDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to open project file URI.")
            DocumentArchiveCodec.readDocument(bytes).getOrThrow()
        }
    }

    suspend fun saveTextOrBytesToUri(uri: Uri, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(bytes)
                os.flush()
            } ?: error("Unable to open output stream for export.")
        }
    }

    /**
     * Decodes a Photo Picker URI at bounded tablet resolution and creates a new sparse-tile DocumentLayer
     * centered in the current document bounds.
     */
    suspend fun decodePhotoToLayer(
        uri: Uri,
        doc: CanonicalDocument,
        layerName: String = "Imported Photo"
    ): Result<DocumentLayer> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected image.")
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
            var sampleSize = 1
            val maxDim = 1600
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                ?: error("Selected file is not a supported bitmap image.")

            val targetBmp = Bitmap.createBitmap(doc.bounds.width, doc.bounds.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(targetBmp)
            val maxFitW = doc.bounds.width * 0.65f
            val maxFitH = doc.bounds.height * 0.65f
            val scale = min(1f, min(maxFitW / decoded.width, maxFitH / decoded.height))
            val drawW = (decoded.width * scale).roundToInt()
            val drawH = (decoded.height * scale).roundToInt()
            val left = (doc.bounds.width - drawW) / 2f
            val top = (doc.bounds.height - drawH) / 2f
            canvas.drawBitmap(decoded, null, RectF(left, top, left + drawW, top + drawH), null)
            decoded.recycle()

            val tiles = DocumentArchiveCodec.sliceBitmapToSparseTiles(targetBmp, doc.tileSize)
            targetBmp.recycle()

            DocumentLayer(
                id = "layer_${UUID.randomUUID().toString().take(8)}",
                name = layerName,
                visible = true,
                opacity = 1f,
                tiles = tiles,
                tileHashes = tiles.mapValues { (_, b) -> DocumentArchiveCodec.sha256Hex(b) }
            )
        }
    }

    suspend fun exportFlattenedBitmapToUri(
        uri: Uri,
        bitmap: Bitmap,
        format: ExportFormat,
        jpgQuality: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                val compressFormat = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val q = if (format == ExportFormat.PNG) 100 else jpgQuality.coerceIn(50, 100)
                if (!bitmap.compress(compressFormat, q, os)) error("Bitmap compression failed.")
                os.flush()
            } ?: error("Unable to write to export destination.")
        }
    }

    suspend fun buildShareIntentForBitmap(
        bitmap: Bitmap,
        format: ExportFormat,
        jpgQuality: Int
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val ext = if (format == ExportFormat.PNG) "png" else "jpg"
            val mime = if (format == ExportFormat.PNG) "image/png" else "image/jpeg"
            val file = File(exportDir, "infinite_studio_${System.currentTimeMillis()}.$ext")
            FileOutputStream(file).use { os ->
                val compressFormat = if (format == ExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                val q = if (format == ExportFormat.PNG) 100 else jpgQuality.coerceIn(50, 100)
                bitmap.compress(compressFormat, q, os)
                os.flush()
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun renderExportPreview(doc: CanonicalDocument, renderer: SparseTileRenderer): Bitmap {
        val region = renderer.computeArtworkBoundingBox(doc, padding = 45)
            ?: RectF(-500f, -350f, 500f, 350f)
        return renderer.renderFlattenedRegion(doc, region, includePaper = true, includeVectors = true)
    }

    fun writeBytesAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { fos ->
            fos.write(bytes)
            fos.fd.sync()
        }
        if (target.exists() && !target.delete()) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        } else if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
