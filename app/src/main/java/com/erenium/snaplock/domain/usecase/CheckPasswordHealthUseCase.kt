package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.passwords.BreachCheck
import com.erenium.snaplock.domain.passwords.BreachChecker
import com.erenium.snaplock.domain.passwords.HealthReport
import com.erenium.snaplock.domain.passwords.PasswordHealthAnalyzer
import com.erenium.snaplock.domain.repository.KdbxRepository
import com.erenium.snaplock.domain.text.localeComparator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class CheckPasswordHealthUseCase @Inject constructor(
    private val repository: KdbxRepository,
    private val analyzer: PasswordHealthAnalyzer,
    private val breachChecker: BreachChecker
) {
    private val knownBreachCounts = ConcurrentHashMap<String, Int>()

    suspend fun auditEntries(): List<AuditEntry> = repository.getAuditEntries()

    /** @param checkBreaches asks the online breach service; only when the user turned it on. */
    suspend operator fun invoke(checkBreaches: Boolean, entries: List<AuditEntry>? = null): HealthReport {
        val audit = entries ?: repository.getAuditEntries()
        val report = sorted(analyzer.analyze(audit))
        if (!checkBreaches) return report

        val passwords = audit.mapNotNull { it.password?.takeIf(String::isNotEmpty) }.distinct()
        var failed = 0
        val counts = HashMap<String, Int>()
        for (password in passwords) {
            currentCoroutineContext().ensureActive()
            val key = fingerprint(password)
            val known = knownBreachCounts[key]
            if (known != null) {
                counts[password] = known
                continue
            }
            breachChecker.breachCount(password)
                .onSuccess {
                    knownBreachCounts[key] = it
                    counts[password] = it
                }
                .onFailure { failed++ }
        }
        val breached = audit.filter { entry -> (counts[entry.password] ?: 0) > 0 }
        val outcome = when {
            failed == 0 -> BreachCheck.DONE
            failed == passwords.size -> BreachCheck.FAILED
            else -> BreachCheck.INCOMPLETE
        }
        return report.copy(breached = breached.sortedWith(byTitle()), breachCheck = outcome)
    }

    private fun sorted(report: HealthReport): HealthReport {
        val byTitle = byTitle()
        return report.copy(
            weak = report.weak.sortedWith(byTitle),
            reused = report.reused
                .map { group -> group.sortedWith(byTitle) }
                .sortedWith(compareByDescending<List<AuditEntry>> { it.size }.then { a, b -> byTitle.compare(a.first(), b.first()) })
        )
    }

    private fun byTitle(): Comparator<AuditEntry> = compareBy(localeComparator()) { it.title }

    private fun fingerprint(password: String): String =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
}
