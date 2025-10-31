package com.erenium.snaplock.data.datasource.cache

import app.keemobile.kotpass.database.KeePassDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SessionCache @Inject constructor() {
    private val _database = MutableStateFlow<KeePassDatabase?>(null)

    val isLocked = _database.map { it == null }

    fun getDatabase() : KeePassDatabase? = _database.value

    val databaseFlow: Flow<KeePassDatabase?> = _database.asStateFlow()

    fun setDatabase(database : KeePassDatabase) {
        _database.value = database
    }

    fun lock() {
            _database.value = null
    }
}