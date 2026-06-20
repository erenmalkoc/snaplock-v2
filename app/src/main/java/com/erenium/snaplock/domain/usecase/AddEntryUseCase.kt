package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class AddEntryUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(data: EntryFormData): Result<Unit> = repository.addEntry(data)
}
