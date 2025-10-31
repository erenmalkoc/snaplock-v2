package com.erenium.snaplock.domain.repository

import android.net.Uri
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface KdbxRepository {

    suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit>

    suspend fun lockDatabase()

    fun getEntries(): Flow<List<Entry>>

    suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail>

}