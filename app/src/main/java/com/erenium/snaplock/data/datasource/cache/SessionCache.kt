package com.erenium.snaplock.data.datasource.cache

import android.net.Uri
import app.keemobile.kotpass.database.KeePassDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SessionCache @Inject constructor() {
    private val _database = MutableStateFlow<KeePassDatabase?>(null)
    private var _uri: Uri? = null

    val isLocked = _database.map { it == null }

    fun getDatabase(): KeePassDatabase? = _database.value

    fun getUri(): Uri? = _uri

    val databaseFlow: Flow<KeePassDatabase?> = _database.asStateFlow()

    fun setDatabase(database: KeePassDatabase, uri: Uri) {
        _uri = uri
        _database.value = database
    }

    fun lock() {
        _uri = null
        _database.value = null
    }
}
