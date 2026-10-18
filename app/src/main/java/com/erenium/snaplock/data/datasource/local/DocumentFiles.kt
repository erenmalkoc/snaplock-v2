package com.erenium.snaplock.data.datasource.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** A file the user picked, read fully into memory. */
class PickedDocument(val name: String, val content: ByteArray)

class FileTooLargeException(val maxBytes: Long) : IOException("File is larger than $maxBytes bytes")

/** Reads and writes files the user picks through the system file picker. */
class DocumentFiles @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    suspend fun read(uri: Uri, maxBytes: Long): Result<PickedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val (name, size) = describe(uri)
            if (size != null && size > maxBytes) throw FileTooLargeException(maxBytes)
            val content = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes) }
                ?: throw IOException("Cannot open $uri")
            PickedDocument(name ?: fallbackName(uri), content)
        }
    }

    suspend fun write(uri: Uri, content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
            stream.use { it.write(content) }
        }
    }

    /** The name a provider shows for the file, or a name taken from the URI. */
    fun displayName(uri: Uri): String = describe(uri).first ?: fallbackName(uri)

    private fun fallbackName(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
            ?: "attachment"

    private fun describe(uri: Uri): Pair<String?, Long?> = runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
            val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let(cursor::getLong)
            name to size
        }
    }.getOrNull() ?: (null to null)

    // Providers do not always report a size, so the limit is enforced while reading too.
    private fun InputStream.readAtMost(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw FileTooLargeException(maxBytes)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
