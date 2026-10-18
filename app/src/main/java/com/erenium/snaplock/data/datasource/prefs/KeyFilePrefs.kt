package com.erenium.snaplock.data.datasource.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers which key file belongs to which database. Stores locations only, never key contents. */
@Singleton
class KeyFilePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(database: Uri): Uri? = prefs.getString(database.toString(), null)?.toUri()

    fun set(database: Uri, keyFile: Uri?) = prefs.edit {
        if (keyFile == null) remove(database.toString()) else putString(database.toString(), keyFile.toString())
    }

    private companion object {
        const val PREFS_NAME = "snaplock_key_files"
    }
}
