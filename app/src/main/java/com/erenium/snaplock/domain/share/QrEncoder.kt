package com.erenium.snaplock.domain.share

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR symbol as a square grid of modules. The quiet zone around it is left to whoever draws it. */
class QrCode internal constructor(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

object QrEncoder {
    /** Modules of empty margin a scanner needs on every side. */
    const val QUIET_ZONE = 4

    // Longest text any QR symbol can hold at all (digits only, lowest correction). Longer input is
    // refused up front instead of asking the encoder to chew on it.
    private const val MAX_CHARS = 7089

    /** Returns null when the text is empty or does not fit into a QR code. */
    fun encode(text: String): QrCode? {
        if (text.isEmpty() || text.length > MAX_CHARS) return null
        // Medium correction survives glare and a dirty screen, low is the fallback for long texts.
        return listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L).firstNotNullOfOrNull { level ->
            try {
                write(text, level)
            } catch (_: WriterException) {
                null
            }
        }
    }

    private fun write(text: String, level: ErrorCorrectionLevel): QrCode {
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.ERROR_CORRECTION, level)
            put(EncodeHintType.MARGIN, 0)
            // Plain ASCII needs no character set marker. Anything else is sent as UTF-8 with the
            // matching ECI header so Turkish letters survive the trip.
            if (text.any { it.code > 127 }) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = matrix.width
        val dark = BooleanArray(size * size)
        for (y in 0 until size) for (x in 0 until size) dark[y * size + x] = matrix.get(x, y)
        return QrCode(size, dark)
    }
}
