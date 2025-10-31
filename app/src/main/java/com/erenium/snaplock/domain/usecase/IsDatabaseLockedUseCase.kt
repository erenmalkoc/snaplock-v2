package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class IsDatabaseLockedUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    operator fun invoke(): Flow<Boolean> {
        return repository.isLocked()
    }
}