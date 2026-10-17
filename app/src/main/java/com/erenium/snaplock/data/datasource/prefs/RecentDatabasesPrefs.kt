package com.erenium.snaplock.data.datasource.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Most recently opened databases first. Only the document URIs are stored, never content. */
@Singleton
class RecentDatabasesPrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uris = MutableStateFlow(readUris())
    val uris: StateFlow<List<Uri>> = _uris.asStateFlow()

    fun add(uri: Uri) = update { current -> listOf(uri) + (current - uri) }

    fun remove(uri: Uri) = update { current -> current - uri }

    private fun update(transform: (List<Uri>) -> List<Uri>) {
        val next = transform(_uris.value).take(MAX_RECENT)
        prefs.edit { putString(KEY_URIS, next.joinToString(SEPARATOR)) }
        _uris.value = next
    }

    private fun readUris(): List<Uri> =
        prefs.getString(KEY_URIS, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.map { it.toUri() }
            .orEmpty()

    private companion object {
        const val PREFS_NAME = "snaplock_recent_databases"
        const val KEY_URIS = "uris"
        const val SEPARATOR = "\n"
        const val MAX_RECENT = 5
    }
}
