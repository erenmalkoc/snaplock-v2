package com.erenium.snaplock.domain.usecase

import android.net.Uri
import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class UnlockDatabaseUseCase @Inject constructor(private val repository: KdbxRepository) {
    suspend operator fun invoke(uri: Uri, password: CharSequence): Result<Unit> {
        return repository.unlockDatabase(uri, password)
    }
}