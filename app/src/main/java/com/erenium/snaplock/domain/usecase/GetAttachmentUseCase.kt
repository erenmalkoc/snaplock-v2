package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.repository.KdbxRepository
import java.util.UUID
import javax.inject.Inject

class GetAttachmentUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(uuid: UUID, name: String): Result<ByteArray> = repository.getAttachment(uuid, name)
}
