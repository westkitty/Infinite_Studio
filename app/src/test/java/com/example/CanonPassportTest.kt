package com.example

import com.example.document.*
import org.junit.Assert.*
import org.junit.Test

class CanonPassportTest {
    private val bytes = "approved-source-bytes".toByteArray()
    private fun sample() = CanonPassport(
        id = "subject_1", characterSheetId = "char_1", displayName = "Artist's Character",
        references = listOf(CanonPassportReference(
            sourceId = "ref-1", sha256 = CanonPassportCodec.sha256Hex(bytes))),
        locks = listOf(CanonPassportLock("face", CanonPassportLockKind.TOPOLOGY,
            "Preserve the exact reference head topology", "User-supplied design requirement"))
    )

    @Test fun `portable roundtrip never imports approval`() {
        val exported = CanonPassportCodec.exportPortable(sample().copy(
            verifiedByCurrentUser = true, claimedApprovalState = "APPROVED_IN_SOURCE_WORKSPACE"))
        val loaded = CanonPassportCodec.importUntrusted(exported)
        assertFalse(loaded.verifiedByCurrentUser)
        assertEquals("APPROVED_IN_SOURCE_WORKSPACE", loaded.claimedApprovalState)
    }

    @Test fun `reference hash requires matching bytes`() {
        assertTrue(CanonPassportCodec.referenceMatches(sample(), "ref-1", bytes))
        assertFalse(CanonPassportCodec.referenceMatches(sample(), "ref-1", "fake".toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate identifiers are rejected`() {
        val ref = sample().references.single()
        CanonPassportCodec.validate(sample().copy(references = listOf(ref, ref)))
    }
}
