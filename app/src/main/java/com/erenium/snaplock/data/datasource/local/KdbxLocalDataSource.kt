package com.erenium.snaplock.data.datasource.local

import android.content.Context
import android.net.Uri
import android.util.Log
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.models.Meta
import com.erenium.snaplock.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class KdbxLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "KdbxLocalDataSource"
        private const val GENERATOR_NAME = "Snaplock"
    }

    suspend fun openDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray? = null): Result<KeePassDatabase> {
        return withContext(Dispatchers.IO) {
            try {
                val credentials = credentialsOf(password, keyFile)
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

    suspend fun createDatabase(
        uri: Uri,
        password: CharSequence,
        rootName: String,
        keyFile: ByteArray? = null
    ): Result<KeePassDatabase> {
        return withContext(Dispatchers.IO) {
            try {
                val credentials = credentialsOf(password, keyFile)
                val database = KeePassDatabase.Ver4x.create(
                    rootName = rootName,
                    // kotpass leaves the recycle bin off; KeePass turns it on for new databases.
                    meta = Meta(generator = GENERATOR_NAME, name = rootName, recycleBinEnabled = true),
                    credentials = credentials
                )
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                outputStream.use {
                    if (it == null) throw Exception(context.getString(R.string.error_kdbx_file))
                    database.encode(it)
                }
                Result.success(database)
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı oluşturulamadı.", e)
                Result.failure(e)
            }
        }
    }

    suspend fun saveDatabase(uri: Uri, database: KeePassDatabase): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Encode first: opening with "wt" truncates the vault, so a failed
                // encode must happen before the existing file is touched.
                val bytes = database.encodeToByteArray()
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                outputStream.use {
                    if (it == null) throw Exception(context.getString(R.string.error_kdbx_file))
                    it.write(bytes)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı kaydedilemedi.", e)
                Result.failure(e)
            }
        }
    }
}

/** KeePass composite key: password, key file or both; an empty password with a key file means key file only. */
internal fun credentialsOf(password: CharSequence, keyFile: ByteArray?): Credentials = when {
    keyFile == null -> Credentials.from(EncryptedValue.fromString(password.toString()))
    password.isEmpty() -> Credentials.from(keyFile)
    else -> Credentials.from(EncryptedValue.fromString(password.toString()), keyFile)
}

/** Encodes the database into a complete, encrypted KDBX file held in memory. */
internal fun KeePassDatabase.encodeToByteArray(): ByteArray =
    ByteArrayOutputStream().also { encode(it) }.toByteArray()
