package com.erenium.snaplock.data.repository

import android.content.Context
import android.net.Uri
import app.keemobile.kotpass.database.getEntries
import app.keemobile.kotpass.database.getEntryBy
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.error.EntryNotFoundException
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.repository.KdbxRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import app.keemobile.kotpass.models.Entry as KotpassEntry


class KdbxRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDataSource: KdbxLocalDataSource,
    private val sessionCache: SessionCache
) : KdbxRepository {

    override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
        val database = sessionCache.getDatabase()

        if (database == null) {
            return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        }

        val kotpassEntry = database.getEntryBy {
            this.uuid == uuid
        }

        return if (kotpassEntry != null) {
            Result.success(kotpassEntry.toDomainEntryDetail())
        } else {
            Result.failure(EntryNotFoundException(uuid))
        }
    }

    override fun getEntries(): Flow<List<Entry>> {
        return sessionCache.databaseFlow.map { database ->
            if (database == null) {
                emptyList()
            } else {
                val groupedEntriesList = database.getEntries { true }
                val allEntries: List<KotpassEntry> = groupedEntriesList.flatMap { pair ->
                    pair.second
                }
                allEntries.map { kotpassEntry ->
                    kotpassEntry.toDomainEntry()
                }
            }
        }
    }

    private fun KotpassEntry.toDomainEntryDetail(): EntryDetail {
        return EntryDetail(
            uuid = this.uuid,
            title = this.fields["Title"]?.content ?: context.getString(R.string.untitled_entry),
            username = this.fields["UserName"]?.content,
            password = this.fields["Password"]?.content,
            url = this.fields["URL"]?.content,
            notes = this.fields["Notes"]?.content
        )
    }

    private fun KotpassEntry.toDomainEntry(): Entry {
        return Entry(
            uuid = this.uuid,
            title = this.fields["Title"]?.content ?: context.getString(R.string.untitled_entry),
            username = this.fields["UserName"]?.content
        )
    }

    override suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit> {
        val result = localDataSource.openDatabase(uri, password)
        return if (result.isSuccess) {
            val database = result.getOrNull()
            if (database != null) {
                sessionCache.setDatabase(database)
                Result.success(Unit)
            } else {
                Result.failure(Exception(context.getString(R.string.empty_database_error)))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception(context.getString(R.string.unknown_error)))
        }
    }

    override suspend fun lockDatabase() {
        sessionCache.lock()
    }

    override fun isLocked(): Flow<Boolean> {
        return sessionCache.isLocked
    }

}
