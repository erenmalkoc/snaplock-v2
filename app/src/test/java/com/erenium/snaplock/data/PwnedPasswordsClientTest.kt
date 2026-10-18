package com.erenium.snaplock.data

import com.erenium.snaplock.data.network.PwnedPasswordsClient
import org.junit.Assert.assertEquals
import org.junit.Test

class PwnedPasswordsClientTest {

    @Test
    fun hashesLikeTheApiExpects() {
        // The SHA-1 of "password", as used in HIBP's own documentation.
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", PwnedPasswordsClient.sha1Hex("password"))
    }

    @Test
    fun findsTheSuffixInARangeResponse() {
        val body = """
            003D68EB55068C33ACE09247EE4C639306B:3
            1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824
            1E4C9B93F3F0682250B6CF8331B7EE68FD9:0
        """.trimIndent()

        assertEquals(9545824, PwnedPasswordsClient.countIn(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
        assertEquals(9545824, PwnedPasswordsClient.countIn(body, "1e4c9b93f3f0682250b6cf8331b7ee68fd8"))
    }

    @Test
    fun paddingAndMissingSuffixesCountAsZero() {
        val body = "1E4C9B93F3F0682250B6CF8331B7EE68FD9:0\r\nABCDEF:12"

        assertEquals(0, PwnedPasswordsClient.countIn(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD9"))
        assertEquals(0, PwnedPasswordsClient.countIn(body, "0000000000000000000000000000000000"))
    }
}
