package com.erenium.snaplock.domain.repository

import android.net.Uri
import com.erenium.snaplock.domain.model.Entry
import kotlinx.coroutines.flow.Flow

interface KdbxRepository {

    suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit>

    suspend fun lockDatabase()

    fun getEntries(): Flow<List<Entry>>

}