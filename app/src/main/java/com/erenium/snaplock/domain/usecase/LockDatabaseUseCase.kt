package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class LockDatabaseUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke() {
        repository.lockDatabase()
    }
}