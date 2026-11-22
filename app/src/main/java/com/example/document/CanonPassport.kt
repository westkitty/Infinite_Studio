package com.example.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Portable reference notes, not a certificate of canonical approval. */
@Serializable
data class CanonPassportReference(
    val sourceId: String,
    val sha256: String,
    val mediaType: String = "image/png",
    val rightsNote: String = "",
    val provenanceLabel: String = "",
    val userSuppliedUriHint: String? = null
)

@Serializable
enum class CanonPassportLockKind {
    SILHOUETTE, TOPOLOGY, COLOR, MARKING, ANATOMY, LITERAL, PROHIBITION, OTHER
}

@Serializable
data class CanonPassportLock(
    val id: String,
    val kind: CanonPassportLockKind,
    val claim: String,
    val authorityNote: String,
    val requiresVisualReview: Boolean = true
)

@Serializable
data class CanonPassport(
    val format: String = "infinite-studio-canon-passport",
    val version: Int = 1,
    val id: String,
    val characterSheetId: String,
    val displayName: String,
    val references: List<CanonPassportReference>,
    val locks: List<CanonPassportLock> = emptyList(),
    val artistNotes: String = "",
    val claimedApprovalState: String = "UNAPPROVED_DRAFT",
    val verifiedByCurrentUser: Boolean = false
)

object CanonPassportCodec {
    private const val LIMIT = 65536
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; prettyPrint = true }
    private val shaPattern = Regex("[a-f0-9]{64}")

    /** Fingerprint actual bytes separately; a filename never constitutes proof. */
    fun sha256Hex(originalBytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(originalBytes).joinToString("") { "%02x".format(it) }

    fun validate(passport: CanonPassport) {
        require(passport.format == "infinite-studio-canon-passport" && passport.version == 1)
        require(passport.id.isNotBlank() && passport.id.length <= 128)
        require(passport.characterSheetId.isNotBlank() && passport.displayName.isNotBlank())
        require(passport.references.size in 1..16 && passport.locks.size <= 100)
        require(passport.references.map { it.sourceId }.distinct().size == passport.references.size)
        require(passport.locks.map { it.id }.distinct().size == passport.locks.size)
        passport.references.forEach {
            require(it.sourceId.isNotBlank() && shaPattern.matches(it.sha256))
            require(it.userSuppliedUriHint == null || it.userSuppliedUriHint.length <= 2048)
        }
        passport.locks.forEach {
            require(it.id.isNotBlank() && it.claim.isNotBlank() && it.authorityNote.isNotBlank())
        }
        require(passport.artistNotes.length <= 8000)
    }

    /** Verifies an explicitly supplied reference's actual bytes, with no disk/network read. */
    fun referenceMatches(passport: CanonPassport, sourceId: String, bytes: ByteArray): Boolean {
        validate(passport)
        val expected = passport.references.find { it.sourceId == sourceId } ?: return false
        return sha256Hex(bytes) == expected.sha256
    }

    fun exportPortable(passport: CanonPassport): ByteArray {
        validate(passport)
        // A portable export is evidence, never a transferable canon approval.
        val encoded = json.encodeToString(passport.copy(verifiedByCurrentUser = false))
            .toByteArray(Charsets.UTF_8)
        require(encoded.size <= LIMIT) { "Canon Passport exceeds portable size limit" }
        return encoded
    }

    fun importUntrusted(raw: ByteArray): CanonPassport {
        require(raw.size in 1..LIMIT) { "Invalid passport byte length" }
        val decoded = json.decodeFromString<CanonPassport>(raw.toString(Charsets.UTF_8))
        validate(decoded)
        return decoded.copy(verifiedByCurrentUser = false)
    }
}
