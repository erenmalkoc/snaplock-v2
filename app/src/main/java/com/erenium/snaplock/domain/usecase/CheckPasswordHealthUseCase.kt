package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.passwords.BreachCheck
import com.erenium.snaplock.domain.passwords.BreachChecker
import com.erenium.snaplock.domain.passwords.HealthReport
import com.erenium.snaplock.domain.passwords.PasswordHealthAnalyzer
import com.erenium.snaplock.domain.repository.KdbxRepository
import javax.inject.Inject

class CheckPasswordHealthUseCase @Inject constructor(
    private val repository: KdbxRepository,
    private val analyzer: PasswordHealthAnalyzer,
    private val breachChecker: BreachChecker
) {
    /** @param checkBreaches asks the online breach service; only when the user turned it on. */
    suspend operator fun invoke(checkBreaches: Boolean): HealthReport {
        val entries = repository.getAuditEntries()
        val report = analyzer.analyze(entries)
        if (!checkBreaches) return report
        // One lookup per distinct password, however many entries share it.
        val counts = entries.mapNotNull { it.password?.takeIf(String::isNotEmpty) }
            .distinct()
            .associateWith { breachChecker.breachCount(it) }
        if (counts.values.any { it.isFailure }) return report.copy(breachCheck = BreachCheck.FAILED)
        val breached = entries.filter { entry -> (counts[entry.password]?.getOrNull() ?: 0) > 0 }
        return report.copy(breached = breached.sortedBy { it.title.lowercase() }, breachCheck = BreachCheck.DONE)
    }
}
