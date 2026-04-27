package com.pqnas.mobile.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairQrParserTest {

    // 32 zero-bytes encoded as base64
    private val validPin = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private fun qr(
        origin: String = "https://nas.example.com",
        pt: String = "tok123",
        pin: String = validPin,
        v: Int = 1,
        app: String = "MyNAS"
    ): String =
        "dna://pair?v=$v&pt=$pt&origin=$origin&app=$app&tls_pin_sha256=$pin"

    // ---- happy path ----

    @Test
    fun `valid QR parses successfully`() {
        val r = PairQrParser.parse(qr())
        assertNotNull(r)
        assertEquals("https://nas.example.com", r!!.origin)
        assertEquals("tok123", r.pairToken)
        assertEquals("MyNAS", r.appName)
        assertEquals(1, r.version)
    }

    @Test
    fun `strips trailing slash from origin`() {
        val r = PairQrParser.parse(qr(origin = "https://nas.example.com/"))
        assertNotNull(r)
        assertEquals("https://nas.example.com", r!!.origin)
    }

    @Test
    fun `accepts explicit port`() {
        val r = PairQrParser.parse(qr(origin = "https://nas.example.com:8443"))
        assertNotNull(r)
        assertEquals("https://nas.example.com:8443", r!!.origin)
    }

    @Test
    fun `accepts IP address origin`() {
        val r = PairQrParser.parse(qr(origin = "https://192.168.1.100"))
        assertNotNull(r)
    }

    @Test
    fun `accepts IP with port`() {
        val r = PairQrParser.parse(qr(origin = "https://192.168.1.100:8443"))
        assertNotNull(r)
    }

    @Test
    fun `defaults appName when blank`() {
        val r = PairQrParser.parse(qr(app = ""))
        assertNotNull(r)
        assertEquals("DNA-Nexus", r!!.appName)
    }

    @Test
    fun `trims whitespace from raw input`() {
        val r = PairQrParser.parse("  ${qr()}  \n")
        assertNotNull(r)
    }

    @Test
    fun `mixed case HTTPS scheme accepted`() {
        val r = PairQrParser.parse(qr(origin = "HTTPS://nas.example.com"))
        assertNotNull(r)
    }

    // ---- origin bypass vectors ----

    @Test
    fun `rejects userinfo attack - good at evil`() {
        assertNull(
            "userinfo in origin must be rejected",
            PairQrParser.parse(qr(origin = "https://good.com@evil.com"))
        )
    }

    @Test
    fun `rejects userinfo with credentials`() {
        assertNull(
            "userinfo with user:pass must be rejected",
            PairQrParser.parse(qr(origin = "https://user:pass@evil.com"))
        )
    }

    @Test
    fun `rejects origin with fragment`() {
        assertNull(
            "origin with fragment must be rejected",
            PairQrParser.parse(qr(origin = "https://nas.example.com#frag"))
        )
    }

    @Test
    fun `rejects http scheme`() {
        assertNull(
            "http must be rejected",
            PairQrParser.parse(qr(origin = "http://nas.example.com"))
        )
    }

    @Test
    fun `rejects ftp scheme`() {
        assertNull(
            "ftp must be rejected",
            PairQrParser.parse(qr(origin = "ftp://nas.example.com"))
        )
    }

    @Test
    fun `rejects javascript scheme`() {
        assertNull(
            "javascript: must be rejected",
            PairQrParser.parse(qr(origin = "javascript://nas.example.com"))
        )
    }

    // ---- pin validation ----

    @Test
    fun `rejects missing pin`() {
        assertNull(PairQrParser.parse(qr(pin = "")))
    }

    @Test
    fun `rejects pin without sha256 prefix`() {
        assertNull(PairQrParser.parse(qr(pin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
    }

    @Test
    fun `rejects pin with wrong decoded length`() {
        // "AAAA" decodes to 3 bytes, not 32
        assertNull(PairQrParser.parse(qr(pin = "sha256/AAAA")))
    }

    @Test
    fun `rejects pin with invalid base64`() {
        assertNull(PairQrParser.parse(qr(pin = "sha256/!!!not-base64!!!")))
    }

    // ---- required fields ----

    @Test
    fun `rejects blank pair token`() {
        assertNull(PairQrParser.parse(qr(pt = "")))
    }

    @Test
    fun `rejects blank origin`() {
        assertNull(PairQrParser.parse(qr(origin = "")))
    }

    // ---- outer URI validation ----

    @Test
    fun `rejects wrong outer scheme`() {
        val raw = "https://pair?v=1&pt=tok&origin=https://nas.example.com&tls_pin_sha256=$validPin"
        assertNull(PairQrParser.parse(raw))
    }

    @Test
    fun `rejects wrong outer host`() {
        val raw = "dna://notpair?v=1&pt=tok&origin=https://nas.example.com&tls_pin_sha256=$validPin"
        assertNull(PairQrParser.parse(raw))
    }
}
