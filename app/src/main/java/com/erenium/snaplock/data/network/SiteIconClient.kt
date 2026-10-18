package com.erenium.snaplock.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.erenium.snaplock.domain.icons.SiteIconSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks a site for its icon at the usual fixed paths and returns it as a PNG of at most
 * [ICON_SIZE] pixels, ready to store as a KeePass custom icon. Sends no cookies or
 * referrer; only the site itself learns that its icon was requested.
 */
@Singleton
class SiteIconClient @Inject constructor() : SiteIconSource {

    override suspend fun iconFor(host: String): ByteArray? = withContext(Dispatchers.IO) {
        ICON_PATHS.firstNotNullOfOrNull { path -> download("https://$host$path")?.let(::toPng) }
    }

    private fun download(address: String): ByteArray? = runCatching {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Snaplock")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            if (connection.contentLengthLong > MAX_BYTES) return@runCatching null
            connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                bytes.takeIf { it.size <= MAX_BYTES }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun toPng(bytes: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest < MIN_SIZE) return null
        val scaled = if (largest > ICON_SIZE) {
            bitmap.scale(bitmap.width * ICON_SIZE / largest, bitmap.height * ICON_SIZE / largest)
        } else {
            bitmap
        }
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        val ICON_PATHS = listOf("/favicon.ico", "/apple-touch-icon.png")
        const val ICON_SIZE = 64
        const val MIN_SIZE = 8
        const val MAX_BYTES = 256 * 1024
        const val BUFFER_BYTES = 8 * 1024
        const val TIMEOUT_MILLIS = 5_000
    }
}
