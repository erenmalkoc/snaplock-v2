package com.erenium.snaplock.domain.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrEncoderTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun read(code: QrCode, scale: Int = 6) = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(pixels(code, scale))))

    // Draws the symbol the way the screen does: black modules, white quiet zone around them.
    private fun pixels(code: QrCode, scale: Int): RGBLuminanceSource {
        val side = (code.size + 2 * QrEncoder.QUIET_ZONE) * scale
        val pixels = IntArray(side * side) { white }
        for (y in 0 until code.size) for (x in 0 until code.size) {
            if (!code[x, y]) continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + QrEncoder.QUIET_ZONE) * scale + dy) * side + (x + QrEncoder.QUIET_ZONE) * scale + dx] = black
            }
        }
        return RGBLuminanceSource(side, side, pixels)
    }

    private fun encoded(text: String): QrCode {
        val code = QrEncoder.encode(text)
        assertNotNull("not encodable: ${text.take(40)}", code)
        return code!!
    }

    // First four bits of the data stream: 4 is plain byte mode, 7 announces a character set (ECI).
    private fun mode(code: QrCode) = (read(code).rawBytes[0].toInt() and 0xFF) shr 4

    @Test
    fun aWifiLoginReadsBackExactly() {
        val text = WifiQr.payload("Home;Net", "pa:ss,word\"1")

        assertEquals(text, read(encoded(text)).text)
    }

    @Test
    fun theSmallestSymbolIs21ModulesWide() {
        assertEquals(21, encoded("A").size)
    }

    @Test
    fun turkishLettersSurviveTheTrip() {
        val text = "Şifre: çğıöşü ÇĞİÖŞÜ 123"

        assertEquals(text, read(encoded(text)).text)
    }

    @Test
    fun plainAsciiIsWrittenWithoutACharacterSetMarker() {
        assertEquals(4, mode(encoded("hello world")))
        assertEquals(7, mode(encoded("héllo wörld")))
    }

    @Test
    fun aLongSecretFallsBackToLowCorrectionInsteadOfFailing() {
        // More than medium correction can hold at the largest size (2331 bytes), still fits with low (2953).
        val text = "key-" + "a1b2c3".repeat(420)
        assertTrue(text.length in 2332..2953)

        assertEquals(text, read(encoded(text), scale = 4).text)
    }

    @Test
    fun theLargestTextThatFitsIsEncodedAndOneMoreCharacterIsRefused() {
        assertNotNull(QrEncoder.encode("x".repeat(2953)))
        assertNull(QrEncoder.encode("x".repeat(2954)))
    }

    @Test
    fun emptyAndHugeTextProduceNothing() {
        assertNull(QrEncoder.encode(""))
        assertNull(QrEncoder.encode("x".repeat(100_000)))
    }

    @Test
    fun threeCornersCarryFinderPatterns() {
        val code = encoded("https://example.com")

        // Every QR symbol has a dark 7x7 finder ring in three corners.
        for (i in 0 until 7) {
            assertTrue(code[i, 0] && code[i, 6] && code[0, i] && code[6, i])
            assertTrue(code[code.size - 1 - i, 0] && code[0, code.size - 1 - i])
        }
    }
}
