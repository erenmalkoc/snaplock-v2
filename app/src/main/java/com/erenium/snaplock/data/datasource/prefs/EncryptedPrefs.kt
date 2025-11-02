package com.erenium.snaplock.data.datasource.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.core.content.edit

class EncryptedPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)

    private val KEY_ENCRYPTED_PASSWORD = "encrypted_password"
    private val KEY_ENCRYPTION_IV = "encrypted_iv"

    fun saveEncryptedCredentials(encryptedPassword: ByteArray, iv: ByteArray) {
        prefs.edit {
            putString(
                KEY_ENCRYPTED_PASSWORD,
                Base64.encodeToString(encryptedPassword, Base64.DEFAULT)
            )
                .putString(KEY_ENCRYPTION_IV, Base64.encodeToString(iv, Base64.DEFAULT))
        }
    }

    fun getEncryptedPassword(): ByteArray? {
        val base64String = prefs.getString(KEY_ENCRYPTED_PASSWORD, null)
        return base64String?.let { Base64.decode(it, Base64.DEFAULT) }
    }

    fun getEncryptionIv(): ByteArray? {
        val base64String = prefs.getString(KEY_ENCRYPTION_IV, null)
        return base64String?.let { Base64.decode(it, Base64.DEFAULT) }
    }


}