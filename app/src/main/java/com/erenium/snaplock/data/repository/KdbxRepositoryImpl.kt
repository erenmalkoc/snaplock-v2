package com.erenium.snaplock.data.repository

import android.net.Uri
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class KdbxRepositoryImpl @Inject constructor(
    private val localDataSource: KdbxLocalDataSource,
    private val sessionCache: SessionCache
) : KdbxRepository {

    override fun getEntries(): Flow<List<Entry>> {
        return sessionCache.databaseFlow.map { database ->
            database?.entries?.map { kdbxEntry ->
                kdbxEntry.toDomainEntry()

            } ?: emptyList()

        }
    }

    private fun KdbxEntry.toDomainEntry(): Entry {
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

}