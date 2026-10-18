package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.repository.KdbxRepository
import java.util.UUID
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(parentUuid: UUID, name: String): Result<UUID> =
        repository.createGroup(parentUuid, name)
}
