package com.erenium.snaplock.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.scale
import com.erenium.snaplock.domain.icons.IconLinks
import com.erenium.snaplock.domain.icons.SiteIconSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gets a site's sharpest icon and returns it as a PNG of at most [ICON_SIZE] pixels, ready
 * to store as a KeePass custom icon. It reads the icons the home page declares (apple touch
 * icons are usually 180 px) and falls back to the usual fixed paths. Sends no cookies or
 * referrer; only the site, and any server its page links its icons from, sees the requests.
 */
@Singleton
class SiteIconClient @Inject constructor() : SiteIconSource {

    override suspend fun iconFor(host: String): ByteArray? = withContext(Dispatchers.IO) {
        val declared = fetch("https://$host/", MAX_PAGE_BYTES)
            ?.let { (page, finalUrl) -> IconLinks.parse(page.toString(Charsets.UTF_8), finalUrl) }
            .orEmpty()
            .map { it.url }
        val fallbacks = FALLBACK_PATHS.map { "https://$host$it" }
        (declared + fallbacks).distinct().take(MAX_ATTEMPTS).firstNotNullOfOrNull { url ->
            iconBytes(url)?.let(::toPng)
        }
    }

    private fun iconBytes(url: String): ByteArray? =
        if (url.startsWith("data:", ignoreCase = true)) {
            runCatching { Base64.decode(url.substringAfter(";base64,"), Base64.DEFAULT) }.getOrNull()
        } else {
            fetch(url, MAX_ICON_BYTES)?.first
        }

    /** The body and the URL it finally came from, after redirects; null on any failure. */
    private fun fetch(address: String, maxBytes: Int): Pair<ByteArray, String>? = runCatching {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            // Some sites refuse requests without a browser-like agent.
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            if (connection.contentLengthLong > maxBytes) return@runCatching null
            val body = connection.inputStream.use { it.readAtMost(maxBytes + 1) }
            if (body.size > maxBytes) null else body to connection.url.toString()
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

    private fun InputStream.readAtMost(limit: Int): ByteArray {
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
        val FALLBACK_PATHS = listOf("/apple-touch-icon.png", "/favicon.ico")
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Snaplock"
        const val ICON_SIZE = 128
        const val MIN_SIZE = 16
        const val MAX_ATTEMPTS = 5
        const val MAX_PAGE_BYTES = 512 * 1024
        const val MAX_ICON_BYTES = 512 * 1024
        const val BUFFER_BYTES = 8 * 1024
        const val TIMEOUT_MILLIS = 5_000
    }
}
