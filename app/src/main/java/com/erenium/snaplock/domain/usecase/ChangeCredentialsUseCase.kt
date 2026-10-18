package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class ChangeCredentialsUseCase @Inject constructor(
    private val repository: KdbxRepository
) {
    suspend operator fun invoke(password: CharSequence, keyFile: ByteArray?): Result<Unit> =
        repository.changeCredentials(password, keyFile)
}
