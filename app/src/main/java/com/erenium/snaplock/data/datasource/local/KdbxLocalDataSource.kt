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
import com.erenium.snaplock.domain.error.VaultChangedElsewhereException
import com.erenium.snaplock.domain.model.VaultBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/** A vault as read from or written to its file; [fingerprint] identifies those exact bytes. */
class StoredVault(val database: KeePassDatabase, val fingerprint: ByteString)

class KdbxLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backups: VaultBackups
) {
    companion object {
        private const val TAG = "KdbxLocalDataSource"
        private const val GENERATOR_NAME = "Snaplock"
    }

    suspend fun openDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray? = null): Result<StoredVault> {
        return withContext(Dispatchers.IO) {
            try {
                val credentials = credentialsOf(password, keyFile)
                val bytes = readBytes(uri)
                Result.success(StoredVault(decode(bytes, credentials), fingerprintOf(bytes)))
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
    ): Result<StoredVault> {
        return withContext(Dispatchers.IO) {
            try {
                val credentials = credentialsOf(password, keyFile)
                val database = KeePassDatabase.Ver4x.create(
                    rootName = rootName,
                    // kotpass leaves the recycle bin off; KeePass turns it on for new databases.
                    meta = Meta(generator = GENERATOR_NAME, name = rootName, recycleBinEnabled = true),
                    credentials = credentials
                )
                val bytes = database.encodeToByteArray()
                writeBytes(uri, bytes)
                Result.success(StoredVault(database, fingerprintOf(bytes)))
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı oluşturulamadı.", e)
                Result.failure(e)
            }
        }
    }

    /** Writes [database] over the vault file and returns the fingerprint of what was written. */
    suspend fun saveDatabase(uri: Uri, database: KeePassDatabase): Result<ByteString> {
        return withContext(Dispatchers.IO) {
            try {
                // Encode first: opening with "wt" truncates the vault, so a failed
                // encode must happen before the existing file is touched.
                val bytes = database.encodeToByteArray()
                keepBackup(uri)
                writeBytes(uri, bytes)
                Result.success(fingerprintOf(bytes))
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı kaydedilemedi.", e)
                Result.failure(e)
            }
        }
    }

    /**
     * The vault as its file holds it now, when another app or device (Syncthing, a cloud
     * drive) wrote to it after [fingerprint] was taken; null when it is unchanged or empty.
     * A file that no longer opens with [credentials] fails with [VaultChangedElsewhereException].
     */
    suspend fun readIfChanged(uri: Uri, fingerprint: ByteString?, credentials: Credentials): Result<StoredVault?> {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = readBytes(uri)
                val current = fingerprintOf(bytes)
                if (bytes.isEmpty() || current == fingerprint) return@withContext Result.success(null)
                val database = try {
                    decode(bytes, credentials)
                } catch (e: Exception) {
                    throw VaultChangedElsewhereException(e)
                }
                Result.success(StoredVault(database, current))
            } catch (e: Exception) {
                Log.e(TAG, "Veritabanı dosyası okunamadı.", e)
                Result.failure(e)
            }
        }
    }

    /** Copies of this vault file from before earlier saves, newest first. */
    suspend fun listBackups(uri: Uri): List<VaultBackup> = withContext(Dispatchers.IO) { backups.list(uri) }

    suspend fun readBackup(uri: Uri, id: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching { backups.read(uri, id) ?: throw NoSuchElementException(id) }
    }

    // A backup that cannot be made must not stop the save it was meant to protect.
    private fun keepBackup(uri: Uri) {
        if (!backups.isDue(uri)) return
        runCatching { backups.keep(uri, readBytes(uri)) }
            .onFailure { Log.w(TAG, "Yedek alınamadı.", it) }
    }

    private fun readBytes(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException(context.getString(R.string.error_kdbx_file))

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException(context.getString(R.string.error_kdbx_file))
        outputStream.use { it.write(bytes) }
    }

    private fun decode(bytes: ByteArray, credentials: Credentials): KeePassDatabase =
        KeePassDatabase.decode(ByteArrayInputStream(bytes), credentials)

    private fun fingerprintOf(bytes: ByteArray): ByteString = bytes.toByteString().sha256()
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
