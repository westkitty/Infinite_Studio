package com.example.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android client execution modes.
 * Zero provider API credentials or direct third-party inference URLs ever reside inside the APK;
 * optional external inference is isolated strictly on a separately hosted loopback companion service.
 */
@Serializable
enum class ProviderMode {
    OFFLINE_LOCAL_ONLY,
    OFFLINE_MOCK_PROVIDER,
    LOOPBACK_COMPANION
}

@Serializable
enum class ProviderJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED
}

@Serializable
enum class ProviderErrorCode {
    NONE,
    OFFLINE_MODE_ACTIVE,
    PAID_EGRESS_DISABLED,
    CONSENT_REQUIRED,
    MISSING_API_KEY,
    QUOTA_CAP_REACHED,
    STALE_DOCUMENT_REVISION,
    NETWORK_ERROR,
    INVALID_RESPONSE
}

@Serializable
data class ProviderHealth(
    val providerId: String,
    val status: String,
    val offlineSafe: Boolean,
    val detail: String
)

@Serializable
data class ProviderCapabilities(
    val providerId: String,
    val supportsImageEdit: Boolean,
    val supportsOutpaint: Boolean,
    val maxResolutionPx: Int,
    val supportsCancel: Boolean
)

@Serializable
data class ProviderJobRequest(
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val documentId: String,
    val baseRevision: Long,
    val branchLabel: String,
    val directionSuffix: String,
    val prompt: String,
    val modelId: String = "gpt-image-2.6-flare",
    val cropPngBase64: String,
    val maskPngBase64: String? = null,
    val userConfirmedPaidEgress: Boolean = false
)

@Serializable
data class ProviderJobRecord(
    val jobId: String,
    val idempotencyKey: String,
    val providerId: String,
    val documentId: String,
    val baseRevision: Long,
    val status: ProviderJobStatus,
    val processingMode: String,
    val resultPngBase64: String? = null,
    val message: String,
    val errorCode: ProviderErrorCode = ProviderErrorCode.NONE
)

interface ProviderAdapter {
    val providerId: String
    suspend fun health(): ProviderHealth
    suspend fun capabilities(): ProviderCapabilities
    suspend fun submitJob(request: ProviderJobRequest, currentDocumentRevision: Long): ProviderJobRecord
    suspend fun getJob(jobId: String): ProviderJobRecord?
    suspend fun cancelJob(jobId: String): ProviderJobRecord?
}

/**
 * Deterministic, zero-network offline mock provider for testing full async job lifecycle,
 * idempotency keys, stale-revision guards, and cancellation without external network calls.
 */
class OfflineMockProviderAdapter : ProviderAdapter {
    override val providerId: String = "offline-mock-router"
    private val jobs = ConcurrentHashMap<String, ProviderJobRecord>()
    private val idempotencyIndex = ConcurrentHashMap<String, String>()

    override suspend fun health(): ProviderHealth = ProviderHealth(
        providerId = providerId,
        status = "healthy",
        offlineSafe = true,
        detail = "Deterministic in-process mock provider active (zero network egress)."
    )

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = providerId,
        supportsImageEdit = true,
        supportsOutpaint = true,
        maxResolutionPx = 2048,
        supportsCancel = true
    )

    override suspend fun submitJob(request: ProviderJobRequest, currentDocumentRevision: Long): ProviderJobRecord {
        if (request.baseRevision != currentDocumentRevision) {
            return ProviderJobRecord(
                jobId = "job_rejected_${UUID.randomUUID().toString().take(6)}",
                idempotencyKey = request.idempotencyKey,
                providerId = providerId,
                documentId = request.documentId,
                baseRevision = request.baseRevision,
                status = ProviderJobStatus.FAILED,
                processingMode = "OFFLINE_MOCK_PROVIDER",
                message = "Rejected stale revision ${request.baseRevision} (current is $currentDocumentRevision).",
                errorCode = ProviderErrorCode.STALE_DOCUMENT_REVISION
            )
        }

        val existingJobId = idempotencyIndex[request.idempotencyKey]
        if (existingJobId != null) {
            jobs[existingJobId]?.let { return it }
        }

        val jobId = "mock_${UUID.randomUUID().toString().take(8)}"
        val cropBytes = Base64.decode(request.cropPngBase64, Base64.DEFAULT)
        val srcBmp = BitmapFactory.decodeByteArray(cropBytes, 0, cropBytes.size)
        val outBase64 = if (srcBmp != null) {
            val tinted = synthesizeMockCandidate(srcBmp, request.directionSuffix)
            val baos = ByteArrayOutputStream()
            tinted.compress(Bitmap.CompressFormat.PNG, 100, baos)
            srcBmp.recycle()
            tinted.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } else {
            request.cropPngBase64
        }

        val record = ProviderJobRecord(
            jobId = jobId,
            idempotencyKey = request.idempotencyKey,
            providerId = providerId,
            documentId = request.documentId,
            baseRevision = request.baseRevision,
            status = ProviderJobStatus.SUCCEEDED,
            processingMode = "OFFLINE_MOCK_PROVIDER",
            resultPngBase64 = outBase64,
            message = "Mock provider candidate generated locally (${request.branchLabel}). Not an external AI call."
        )
        jobs[jobId] = record
        idempotencyIndex[request.idempotencyKey] = jobId
        return record
    }

    override suspend fun getJob(jobId: String): ProviderJobRecord? = jobs[jobId]

    override suspend fun cancelJob(jobId: String): ProviderJobRecord? {
        val current = jobs[jobId] ?: return null
        val canceled = current.copy(
            status = ProviderJobStatus.CANCELED,
            message = "Job canceled cleanly by user."
        )
        jobs[jobId] = canceled
        return canceled
    }

    private fun synthesizeMockCandidate(src: Bitmap, direction: String): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val lowerDir = direction.lowercase()
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.29f * r + 0.59f * g + 0.12f * b
            val (nr, ng, nb) = when {
                "nocturnal" in lowerDir || "blue" in lowerDir -> Triple(
                    (18 + lum * 0.42f).roundToInt().coerceIn(0, 255),
                    (32 + lum * 0.68f).roundToInt().coerceIn(0, 255),
                    (60 + lum * 0.92f).roundToInt().coerceIn(0, 255)
                )
                "copper" in lowerDir || "amber" in lowerDir -> Triple(
                    (46 + lum * 0.92f).roundToInt().coerceIn(0, 255),
                    (20 + lum * 0.72f).roundToInt().coerceIn(0, 255),
                    (12 + lum * 0.45f).roundToInt().coerceIn(0, 255)
                )
                else -> Triple(
                    ((lum - 110f) * 1.45f + 132f).roundToInt().coerceIn(0, 255),
                    ((lum - 110f) * 1.38f + 128f).roundToInt().coerceIn(0, 255),
                    ((lum - 110f) * 1.25f + 122f).roundToInt().coerceIn(0, 255)
                )
            }
            px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(px, 0, w, 0, 0, w, h)
        }
    }
}

/**
 * Connects strictly to the separately hosted companion server on loopback (127.0.0.1:8787) when opted in.
 * All provider credentials remain isolated on the companion host, never inside the Android client.
 */
class LoopbackCompanionAdapter(
    private val baseUrl: String = "http://127.0.0.1:8787"
) : ProviderAdapter {
    override val providerId: String = "loopback-companion"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    json.decodeFromString<ProviderHealth>(resp.body?.string().orEmpty())
                } else {
                    ProviderHealth(providerId, "unreachable", true, "HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            ProviderHealth(providerId, "unreachable", true, e.message ?: "Loopback companion offline")
        }
    }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = providerId,
        supportsImageEdit = true,
        supportsOutpaint = true,
        maxResolutionPx = 2048,
        supportsCancel = true
    )

    override suspend fun submitJob(request: ProviderJobRequest, currentDocumentRevision: Long): ProviderJobRecord =
        withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(request).toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$baseUrl/jobs").post(body).build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        json.decodeFromString<ProviderJobRecord>(raw)
                    } else {
                        runCatching { json.decodeFromString<ProviderJobRecord>(raw) }.getOrElse {
                            ProviderJobRecord(
                                jobId = "err_${UUID.randomUUID().toString().take(6)}",
                                idempotencyKey = request.idempotencyKey,
                                providerId = providerId,
                                documentId = request.documentId,
                                baseRevision = request.baseRevision,
                                status = ProviderJobStatus.FAILED,
                                processingMode = "LOOPBACK_COMPANION",
                                message = "Companion returned HTTP ${resp.code}",
                                errorCode = ProviderErrorCode.NETWORK_ERROR
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                ProviderJobRecord(
                    jobId = "err_${UUID.randomUUID().toString().take(6)}",
                    idempotencyKey = request.idempotencyKey,
                    providerId = providerId,
                    documentId = request.documentId,
                    baseRevision = request.baseRevision,
                    status = ProviderJobStatus.FAILED,
                    processingMode = "LOOPBACK_COMPANION",
                    message = "Companion unreachable: ${e.message}",
                    errorCode = ProviderErrorCode.NETWORK_ERROR
                )
            }
        }

    override suspend fun getJob(jobId: String): ProviderJobRecord? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/jobs/$jobId").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) json.decodeFromString<ProviderJobRecord>(resp.body?.string().orEmpty()) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun cancelJob(jobId: String): ProviderJobRecord? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/jobs/$jobId/cancel")
                .post("{}".toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) json.decodeFromString<ProviderJobRecord>(resp.body?.string().orEmpty()) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Central Secure Provider Router managing offline-first enforcement, explicit consent gating,
 * per-session companion call cap, stale revision checks, and cancellation.
 * Holds zero external API secrets and makes zero direct paid-API calls from Android.
 */
class ProviderRouter(
    private val mockAdapter: OfflineMockProviderAdapter = OfflineMockProviderAdapter(),
    private val companionAdapter: LoopbackCompanionAdapter = LoopbackCompanionAdapter()
) {
    companion object {
        const val MAX_COMPANION_CALLS_PER_SESSION = 3
    }

    var mode: ProviderMode = ProviderMode.OFFLINE_LOCAL_ONLY
    var companionCallsUsed: Int = 0
        private set

    fun remainingCompanionCalls(): Int = max(0, MAX_COMPANION_CALLS_PER_SESSION - companionCallsUsed)

    suspend fun checkHealth(): ProviderHealth {
        return when (mode) {
            ProviderMode.OFFLINE_LOCAL_ONLY -> ProviderHealth(
                providerId = "local-deterministic",
                status = "healthy",
                offlineSafe = true,
                detail = "Strict offline mode active. Zero network calls allowed."
            )
            ProviderMode.OFFLINE_MOCK_PROVIDER -> mockAdapter.health()
            ProviderMode.LOOPBACK_COMPANION -> companionAdapter.health()
        }
    }

    suspend fun submitCandidateJob(
        request: ProviderJobRequest,
        currentDocumentRevision: Long
    ): ProviderJobRecord {
        if (request.baseRevision != currentDocumentRevision) {
            return ProviderJobRecord(
                jobId = "stale_${UUID.randomUUID().toString().take(6)}",
                idempotencyKey = request.idempotencyKey,
                providerId = mode.name,
                documentId = request.documentId,
                baseRevision = request.baseRevision,
                status = ProviderJobStatus.FAILED,
                processingMode = mode.name,
                message = "Document changed while preparing job (rev ${request.baseRevision} vs $currentDocumentRevision).",
                errorCode = ProviderErrorCode.STALE_DOCUMENT_REVISION
            )
        }

        return when (mode) {
            ProviderMode.OFFLINE_LOCAL_ONLY -> ProviderJobRecord(
                jobId = "blocked_${UUID.randomUUID().toString().take(6)}",
                idempotencyKey = request.idempotencyKey,
                providerId = "local-deterministic",
                documentId = request.documentId,
                baseRevision = request.baseRevision,
                status = ProviderJobStatus.FAILED,
                processingMode = "OFFLINE_LOCAL_ONLY",
                message = "External inference disabled in Offline Local mode. Use local color studies or Mock Provider.",
                errorCode = ProviderErrorCode.OFFLINE_MODE_ACTIVE
            )

            ProviderMode.OFFLINE_MOCK_PROVIDER -> mockAdapter.submitJob(request, currentDocumentRevision)

            ProviderMode.LOOPBACK_COMPANION -> {
                if (!request.userConfirmedPaidEgress) {
                    return ProviderJobRecord(
                        jobId = "consent_${UUID.randomUUID().toString().take(6)}",
                        idempotencyKey = request.idempotencyKey,
                        providerId = companionAdapter.providerId,
                        documentId = request.documentId,
                        baseRevision = request.baseRevision,
                        status = ProviderJobStatus.FAILED,
                        processingMode = "LOOPBACK_COMPANION",
                        message = "Explicit user confirmation required before dispatching job to companion service.",
                        errorCode = ProviderErrorCode.CONSENT_REQUIRED
                    )
                }
                if (companionCallsUsed >= MAX_COMPANION_CALLS_PER_SESSION) {
                    return ProviderJobRecord(
                        jobId = "cap_${UUID.randomUUID().toString().take(6)}",
                        idempotencyKey = request.idempotencyKey,
                        providerId = companionAdapter.providerId,
                        documentId = request.documentId,
                        baseRevision = request.baseRevision,
                        status = ProviderJobStatus.FAILED,
                        processingMode = "LOOPBACK_COMPANION",
                        message = "Session companion-call safety cap ($MAX_COMPANION_CALLS_PER_SESSION) reached.",
                        errorCode = ProviderErrorCode.QUOTA_CAP_REACHED
                    )
                }
                companionCallsUsed++
                companionAdapter.submitJob(request, currentDocumentRevision)
            }
        }
    }

    suspend fun cancelJob(jobId: String): ProviderJobRecord? {
        return when (mode) {
            ProviderMode.OFFLINE_MOCK_PROVIDER -> mockAdapter.cancelJob(jobId)
            ProviderMode.LOOPBACK_COMPANION -> companionAdapter.cancelJob(jobId)
            ProviderMode.OFFLINE_LOCAL_ONLY -> null
        }
    }
}
