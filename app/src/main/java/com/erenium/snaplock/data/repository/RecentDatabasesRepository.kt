package com.erenium.snaplock.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.prefs.KeyFilePrefs
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.datasource.security.BiometricVault
import com.erenium.snaplock.domain.model.RecentDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentDatabasesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: RecentDatabasesPrefs,
    private val keyFilePrefs: KeyFilePrefs,
    private val vault: BiometricVault
) {
    private val probeTrigger = MutableStateFlow(0)

    val recentDatabases: Flow<List<RecentDatabase>> = prefs.uris
        .map { uris ->
            val granted = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
                .toSet()
            uris.filter { it in granted }.map { RecentDatabase(it, displayName(it)) }
        }
        .flowOn(Dispatchers.IO)

    val entries: Flow<List<RecentDatabase>> = combine(prefs.uris, probeTrigger) { uris, _ -> uris }
        .map { uris ->
            val permissions = context.contentResolver.persistedUriPermissions.associateBy { it.uri }
            uris.map { uri -> describe(uri, permissions[uri]?.isWritePermission) }
        }
        .flowOn(Dispatchers.IO)

    fun refresh() {
        probeTrigger.value += 1
    }

    fun add(uri: Uri) = prefs.add(uri)

    fun keepAccess(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val both = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching { resolver.takePersistableUriPermission(uri, both) }.isSuccess) return true
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return false
    }

    fun hide(uri: Uri): Int {
        val index = prefs.indexOf(uri)
        prefs.remove(uri)
        return index
    }

    fun restore(uri: Uri, index: Int) = prefs.restore(uri, index)

    fun forget(uri: Uri) {
        prefs.remove(uri)
        prefs.forgetOpened(uri)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        keyFilePrefs.set(uri, null)
        vault.disable(uri)
    }

    fun remove(uri: Uri) = forget(uri)

    private fun describe(uri: Uri, writable: Boolean?): RecentDatabase {
        val probe = probe(uri)
        return RecentDatabase(
            uri = uri,
            name = probe.name?.takeIf { it.isNotBlank() } ?: fallbackName(uri),
            available = probe.available,
            location = location(uri),
            lastOpened = prefs.lastOpened(uri),
            readOnly = writable == false
        )
    }

    private class Probe(val available: Boolean, val name: String?)

    private fun probe(uri: Uri): Probe = runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use Probe(false, null)
            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
            val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let(cursor::getLong)
            Probe(size == null || size > 0, name)
        } ?: Probe(false, null)
    }.getOrDefault(Probe(false, null))

    private fun displayName(uri: Uri): String =
        probe(uri).name?.takeIf { it.isNotBlank() } ?: fallbackName(uri)

    private fun fallbackName(uri: Uri): String {
        val segment = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return segment?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
            ?: context.getString(R.string.recent_unknown_name)
    }

    private fun location(uri: Uri): String? {
        val provider = uri.authority?.let { authority ->
            runCatching {
                val packages = context.packageManager
                packages.resolveContentProvider(authority, 0)?.loadLabel(packages)?.toString()
            }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        val folder = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()?.let(::folderOfDocumentId)
        return listOfNotNull(provider, folder).takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }
}

internal fun folderOfDocumentId(documentId: String): String? {
    val path = documentId.substringAfter(':', "")
    if (!path.contains('/')) return null
    return path.substringBeforeLast('/').substringAfterLast('/').takeIf { it.isNotBlank() }
}
