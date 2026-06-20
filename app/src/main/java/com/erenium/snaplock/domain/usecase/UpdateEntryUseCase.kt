package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.repository.KdbxRepository
import java.util.UUID
import javax.inject.Inject

class UpdateEntryUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(uuid: UUID, data: EntryFormData): Result<Unit> =
        repository.updateEntry(uuid, data)
}
