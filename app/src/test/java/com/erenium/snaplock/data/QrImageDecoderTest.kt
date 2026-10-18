package com.erenium.snaplock.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.local.QrImageDecoder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrImageDecoderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val decoder = QrImageDecoder(context)

    @Test
    fun readsTheTextOfAQrCode() {
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        val matrix = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 400, 400)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        assertEquals(uri, decoder.decode(bitmap))
    }

    @Test
    fun blankImageHasNoCode() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

        assertNull(decoder.decode(bitmap))
    }
}
