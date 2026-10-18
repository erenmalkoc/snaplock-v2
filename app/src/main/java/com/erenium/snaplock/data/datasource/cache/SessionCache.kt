package com.erenium.snaplock.data.datasource.cache

import android.net.Uri
import app.keemobile.kotpass.database.KeePassDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString
import javax.inject.Inject

class SessionCache @Inject constructor() {
    private val _database = MutableStateFlow<KeePassDatabase?>(null)
    private var _uri: Uri? = null
    private var _fingerprint: ByteString? = null

    private val _isLocked = MutableStateFlow(true)

    /** Readable right away, so a screen created while the vault is open never starts locked. */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun getDatabase(): KeePassDatabase? = _database.value

    fun getUri(): Uri? = _uri

    /** Identifies the file contents the open database was last read from or saved as. */
    fun getFingerprint(): ByteString? = _fingerprint

    val databaseFlow: Flow<KeePassDatabase?> = _database.asStateFlow()

    fun setDatabase(database: KeePassDatabase, uri: Uri, fingerprint: ByteString? = null) {
        _uri = uri
        _fingerprint = fingerprint
        _database.value = database
        _isLocked.value = false
    }

    fun lock() {
        _uri = null
        _fingerprint = null
        _database.value = null
        _isLocked.value = true
    }
}
