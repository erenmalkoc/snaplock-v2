package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.repository.KdbxRepository
import java.util.UUID
import javax.inject.Inject

class GetEntryDetailsUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(uuid: UUID): Result<EntryDetail> {
        return repository.getEntryByUuid(uuid)
    }
}