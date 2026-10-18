package com.erenium.snaplock.domain.passwords

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHealthAnalyzerTest {

    private val analyzer = PasswordHealthAnalyzer(
        PasswordStrengthEstimator(object : WordList {
            override val words = listOf("abacus", "abdomen", "abide")
        })
    )
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val recent = Instant.parse("2026-09-01T00:00:00Z")

    private fun entry(title: String, password: String?, modified: Instant? = recent, expires: Instant? = null) =
        AuditEntry(UUID.randomUUID(), title, "user", password, modified, expires)

    @Test
    fun findsEachKindOfProblem() {
        val weak = entry("Forum", "123456")
        val reusedA = entry("Mail", "S0me-Shared-Passw0rd!")
        val reusedB = entry("Bank", "S0me-Shared-Passw0rd!")
        val old = entry("Router", "vX9#kQ2!mL7@pR4z", modified = Instant.parse("2024-01-01T00:00:00Z"))
        val expired = entry("VPN", "N7&uY3*hT6^gR1!w", expires = Instant.parse("2026-10-01T00:00:00Z"))
        val healthy = entry("GitHub", "Zq8!xW3#vB6\$nM1%kL", expires = Instant.parse("2027-10-01T00:00:00Z"))

        val report = analyzer.analyze(listOf(weak, reusedA, reusedB, old, expired, healthy), now)

        assertEquals(listOf(weak), report.weak)
        assertEquals(listOf(listOf(reusedB, reusedA)), report.reused)
        assertEquals(listOf(old), report.old)
        assertEquals(listOf(expired), report.expired)
        assertEquals(6, report.checked)
        assertEquals(16, report.score)
    }

    @Test
    fun entriesWithoutPasswordsAreNotCountedOrFlaggedAsReused() {
        val report = analyzer.analyze(listOf(entry("Note", null), entry("Card", "")), now)

        assertEquals(0, report.checked)
        assertTrue(report.reused.isEmpty() && report.weak.isEmpty())
        assertEquals(100, report.score)
    }

    @Test
    fun emptyVaultIsHealthy() {
        assertEquals(100, analyzer.analyze(emptyList(), now).score)
    }
}
