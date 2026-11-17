package com.erenium.snaplock.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns

class FakeDocumentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val id = uri.lastPathSegment
        val size = when (id) {
            "ok" -> 100L
            "empty" -> 0L
            "unknown-size" -> null
            else -> return null
        }
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf<Any?>("$id.kdbx", size))
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        deleted += method
        return Bundle.EMPTY
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "fake.documents"
        val deleted = mutableListOf<String>()

        fun uri(id: String): Uri = Uri.parse("content://$AUTHORITY/document/$id")
    }
}
