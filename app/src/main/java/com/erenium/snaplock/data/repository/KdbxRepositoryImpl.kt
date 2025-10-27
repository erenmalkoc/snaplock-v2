package com.erenium.snaplock.data.repository

import android.net.Uri
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class KdbxRepositoryImpl @Inject constructor(
    private val localDataSource: KdbxLocalDataSource,
    private val sessionCache: SessionCache
) : KdbxRepository {

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