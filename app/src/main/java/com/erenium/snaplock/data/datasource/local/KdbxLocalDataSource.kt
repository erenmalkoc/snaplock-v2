package com.erenium.snaplock.data.datasource.local

import android.content.Context
import android.net.Uri
import android.util.Log
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import com.erenium.snaplock.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class KdbxLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "KdbxLocalDataSource"
    }

    suspend fun openDatabase(uri: Uri, password: CharSequence): Result<KeePassDatabase> {
        return withContext(Dispatchers.IO) {
            try {
                val credentials = Credentials.from(EncryptedValue.fromString(password.toString()))
                val inputStream = context.contentResolver.openInputStream(uri)
                val database = inputStream.use {
                    if (it == null) throw Exception(context.getString(R.string.error_kdbx_file))
                    KeePassDatabase.decode(it, credentials)
                }
                Result.success(database)
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı açılamadı.", e)
                Result.failure(e)
            }
        }
    }
}