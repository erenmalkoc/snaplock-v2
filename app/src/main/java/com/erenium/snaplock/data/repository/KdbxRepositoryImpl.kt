package com.erenium.snaplock.data.repository

import android.net.Uri
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import app.keemobile.kotpass.database.getEntries
import app.keemobile.kotpass.database.getEntryBy
import com.erenium.snaplock.domain.error.EntryNotFoundException
import com.erenium.snaplock.domain.model.EntryDetail
import java.util.UUID
import app.keemobile.kotpass.models.Entry as KotpassEntry


class KdbxRepositoryImpl @Inject constructor(
    private val localDataSource: KdbxLocalDataSource,
    private val sessionCache: SessionCache
) : KdbxRepository {

    override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
        val database = sessionCache.getDatabase()

        if (database == null) {
            return Result.failure(IllegalStateException("Veritabanı kilitli veya yüklenmemiş."))
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
            title = this.fields["Title"]?.content ?: "Başlık Yok",
            username = this.fields["UserName"]?.content,
            password = this.fields["Password"]?.content,
            url = this.fields["URL"]?.content,
            notes = this.fields["Notes"]?.content
        )
    }

    private fun KotpassEntry.toDomainEntry(): Entry {
        return Entry(
            uuid = this.uuid,
            title = this.fields["Title"]?.content ?: "Başlık Yok",
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
                Result.failure(Exception("Veritabanı çözüldü ama boş geldi."))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Bilinmeyen bir hata oluştu."))
        }
    }

    override suspend fun lockDatabase() {
        sessionCache.lock()
    }

    override fun isLocked(): Flow<Boolean> {
        return sessionCache.isLocked
    }

}