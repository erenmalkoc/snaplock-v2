package com.erenium.snaplock.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.domain.model.RecentDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentDatabasesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: RecentDatabasesPrefs
) {

    /** Recent databases that are still accessible, newest first. */
    val recentDatabases: Flow<List<RecentDatabase>> = prefs.uris
        .map { uris ->
            val granted = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
                .toSet()
            uris.filter { it in granted }.map { RecentDatabase(it, displayName(it)) }
        }
        .flowOn(Dispatchers.IO)

    fun add(uri: Uri) = prefs.add(uri)

    fun remove(uri: Uri) = prefs.remove(uri)

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name ?: uri.lastPathSegment ?: uri.toString()
    }
}
