package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEntriesUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    operator fun invoke(): Flow<List<Entry>> {
        return repository.getEntries()
    }


}