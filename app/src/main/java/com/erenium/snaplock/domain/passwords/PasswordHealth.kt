package com.erenium.snaplock.domain.passwords

import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** What the health check needs to know about one entry. */
data class AuditEntry(
    val uuid: UUID,
    val title: String,
    val username: String?,
    val password: String?,
    val modified: Instant?,
    val expires: Instant?
)

enum class BreachCheck { OFF, DONE, FAILED }

data class HealthReport(
    val checked: Int,
    val weak: List<AuditEntry>,
    /** Entries sharing a password, one list per shared password. */
    val reused: List<List<AuditEntry>>,
    val old: List<AuditEntry>,
    val expired: List<AuditEntry>,
    /** Entries whose password appears in known breaches; only filled when the user opted in. */
    val breached: List<AuditEntry> = emptyList(),
    val breachCheck: BreachCheck = BreachCheck.OFF
) {
    val affected: Set<UUID> =
        (weak + reused.flatten() + old + expired + breached).map { it.uuid }.toSet()

    /** Share of checked entries with no issue, 0..100. */
    val score: Int = if (checked == 0) 100 else (100 * (checked - affected.size) / checked)
}

/** Finds weak, reused, old and expired passwords without anything leaving the device. */
class PasswordHealthAnalyzer @Inject constructor(
    private val strengthEstimator: PasswordStrengthEstimator
) {

    fun analyze(entries: List<AuditEntry>, now: Instant = Instant.now()): HealthReport {
        val withPassword = entries.filter { !it.password.isNullOrEmpty() }
        val byTitle = compareBy<AuditEntry> { it.title.lowercase() }
        return HealthReport(
            checked = withPassword.size,
            weak = withPassword
                .filter { strengthEstimator.estimate(it.password!!).strength <= PasswordStrength.WEAK }
                .sortedWith(byTitle),
            reused = withPassword
                .groupBy { it.password }
                .values
                .filter { it.size > 1 }
                .map { it.sortedWith(byTitle) }
                .sortedByDescending { it.size },
            old = withPassword
                .filter { it.modified != null && Duration.between(it.modified, now) > MAX_AGE }
                .sortedBy { it.modified },
            expired = entries
                .filter { it.expires != null && it.expires.isBefore(now) }
                .sortedBy { it.expires }
        )
    }

    private companion object {
        val MAX_AGE: Duration = Duration.ofDays(365)
    }
}
