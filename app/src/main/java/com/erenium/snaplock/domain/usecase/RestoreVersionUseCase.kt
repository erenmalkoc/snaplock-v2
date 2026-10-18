package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.repository.KdbxRepository
import java.util.UUID
import javax.inject.Inject

class RestoreVersionUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(uuid: UUID, index: Int): Result<Unit> = repository.restoreVersion(uuid, index)
}
