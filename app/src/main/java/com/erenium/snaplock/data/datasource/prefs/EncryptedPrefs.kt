package com.erenium.snaplock.data.datasource.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun has(database: Uri): Boolean =
        prefs.contains(passwordKey(database)) && prefs.contains(ivKey(database))

    fun save(database: Uri, encryptedPassword: ByteArray, iv: ByteArray) {
        prefs.edit {
            putString(passwordKey(database), Base64.encodeToString(encryptedPassword, Base64.NO_WRAP))
            putString(ivKey(database), Base64.encodeToString(iv, Base64.NO_WRAP))
        }
    }

    fun getEncryptedPassword(database: Uri): ByteArray? = read(passwordKey(database))

    fun getIv(database: Uri): ByteArray? = read(ivKey(database))

    fun clear(database: Uri) {
        prefs.edit {
            remove(passwordKey(database))
            remove(ivKey(database))
        }
    }

    fun clearAll() {
        prefs.edit {
            prefs.all.keys
                .filter { it.startsWith(PASSWORD_PREFIX) || it.startsWith(IV_PREFIX) }
                .forEach { remove(it) }
        }
    }

    fun dropLegacy() {
        if (!prefs.contains(LEGACY_PASSWORD) && !prefs.contains(LEGACY_IV)) return
        prefs.edit {
            remove(LEGACY_PASSWORD)
            remove(LEGACY_IV)
        }
    }

    private fun read(key: String): ByteArray? =
        prefs.getString(key, null)?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }

    private fun passwordKey(database: Uri) = PASSWORD_PREFIX + hash(database)

    private fun ivKey(database: Uri) = IV_PREFIX + hash(database)

    private fun hash(database: Uri): String =
        MessageDigest.getInstance("SHA-256").digest(database.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFS_NAME = "snaplock_secure_preferences"
        const val PASSWORD_PREFIX = "bio_pw_"
        const val IV_PREFIX = "bio_iv_"
        const val LEGACY_PASSWORD = "biometric_encrypted_database_password"
        const val LEGACY_IV = "biometric_database_password_iv"
    }
}
