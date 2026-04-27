package com.pqnas.mobile.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinnedTlsNormalizationTest {

    // 32 zero-bytes → canonical base64 (NO_WRAP)
    private val canonical32 = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    @Test
    fun `accepts canonical pin`() {
        val result = PinnedTls.normalizeSpkiSha256Pin(canonical32)
        assertNotNull(result)
        assertEquals(canonical32, result)
    }

    @Test
    fun `rejects empty string`() {
        assertNull(PinnedTls.normalizeSpkiSha256Pin(""))
    }

    @Test
    fun `rejects blank string`() {
        assertNull(PinnedTls.normalizeSpkiSha256Pin("   "))
    }

    @Test
    fun `rejects missing sha256 prefix`() {
        assertNull(PinnedTls.normalizeSpkiSha256Pin("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))
    }

    @Test
    fun `rejects sha256 prefix with blank body`() {
        assertNull(PinnedTls.normalizeSpkiSha256Pin("sha256/"))
    }

    @Test
    fun `rejects sha256 prefix with whitespace-only body`() {
        assertNull(PinnedTls.normalizeSpkiSha256Pin("sha256/   "))
    }

    @Test
    fun `rejects invalid base64`() {
        assertNull(PinnedTls.normalizeSpkiSha256Pin("sha256/!!!garbage!!!"))
    }

    @Test
    fun `rejects wrong decoded length - too short`() {
        // "AAAA" decodes to 3 bytes
        assertNull(PinnedTls.normalizeSpkiSha256Pin("sha256/AAAA"))
    }

    @Test
    fun `rejects wrong decoded length - too long`() {
        // 48 zero-bytes base64 = 64 chars
        val fortyEightBytes = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        assertNull(PinnedTls.normalizeSpkiSha256Pin("sha256/$fortyEightBytes"))
    }

    @Test
    fun `converts spaces to plus signs`() {
        // Spaces in QR data sometimes replace '+' during scanning
        val withSpaces = canonical32.replace("+", " ")
        val result = PinnedTls.normalizeSpkiSha256Pin(withSpaces)
        assertNotNull(result)
        assertEquals(canonical32, result)
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        val result = PinnedTls.normalizeSpkiSha256Pin("  $canonical32  ")
        assertNotNull(result)
        assertEquals(canonical32, result)
    }

    @Test
    fun `re-encodes to NO_WRAP canonical form`() {
        // Even if input has slightly different base64 encoding, output is canonical
        val result = PinnedTls.normalizeSpkiSha256Pin(canonical32)
        assertNotNull(result)
        // Must not contain newlines or trailing whitespace
        assertEquals(-1, result!!.indexOf('\n'))
        assertEquals(-1, result.indexOf('\r'))
    }
}
