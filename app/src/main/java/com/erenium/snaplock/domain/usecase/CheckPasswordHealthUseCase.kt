package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.passwords.HealthReport
import com.erenium.snaplock.domain.passwords.PasswordHealthAnalyzer
import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class CheckPasswordHealthUseCase @Inject constructor(
    private val repository: KdbxRepository,
    private val analyzer: PasswordHealthAnalyzer
) {
    suspend operator fun invoke(): HealthReport = analyzer.analyze(repository.getAuditEntries())
}
