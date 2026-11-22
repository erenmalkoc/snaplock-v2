package com.erenium.snaplock.domain.usecase

import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.passwords.BreachChecker
import com.erenium.snaplock.domain.passwords.PasswordHealthAnalyzer
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.TwoFactorDomains
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.testing.FakeKdbxRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckPasswordHealthTwoFactorTest {
    private val repository = FakeKdbxRepository()
    private val analyzer = PasswordHealthAnalyzer(PasswordStrengthEstimator(object : WordList {
        override val words = emptyList<String>()
    }))
    private val breachChecker = object : BreachChecker {
        override suspend fun breachCount(password: String): Result<Int> = Result.success(0)
    }
    private val directory = TwoFactorDomains(setOf("github.com", "proton.me", "bbc.co.uk"))
    private val useCase = CheckPasswordHealthUseCase(repository, analyzer, breachChecker, directory)

    private fun entry(title: String, url: String?, hasOtp: Boolean = false, password: String? = "Hq7!vR2#nLp9xZ-$title") =
        AuditEntry(UUID.randomUUID(), title, "me", password, Instant.now(), null, url, hasOtp)

    @Test
    fun listsLoginsOnSitesWithAuthenticatorCodesThatHaveNone() = runTest {
        repository.auditEntries.value = listOf(
            entry("Proton", "https://proton.me"),
            entry("GitHub", "https://github.com", hasOtp = true),
            entry("Forum", "https://forum.example"),
            entry("Notes", null)
        )

        val report = useCase(checkBreaches = false)

        assertEquals(listOf("Proton"), report.noTwoFactor.map { it.title })
    }

    @Test
    fun theListIsSortedByTitleAndSubdomainsAndMultiPartDomainsCount() = runTest {
        repository.auditEntries.value = listOf(
            entry("Zed", "https://gist.github.com"),
            entry("alpha", "www.bbc.co.uk/iplayer"),
            entry("Mid", "https://proton.me")
        )

        val report = useCase(checkBreaches = false)

        assertEquals(listOf("alpha", "Mid", "Zed"), report.noTwoFactor.map { it.title })
    }

    @Test
    fun aLoginWithoutAPasswordIsStillWorthATip() = runTest {
        repository.auditEntries.value = listOf(entry("Passkey only", "https://github.com", password = null))

        assertEquals(listOf("Passkey only"), useCase(checkBreaches = false).noTwoFactor.map { it.title })
    }

    @Test
    fun theTipNeverChangesTheScoreOrWhatCountsAsAffected() = runTest {
        val logins = listOf(entry("GitHub", "https://github.com"), entry("Proton", "https://proton.me"), entry("Forum", "https://forum.example"))
        repository.auditEntries.value = logins
        val withTip = useCase(checkBreaches = false)
        val quiet = CheckPasswordHealthUseCase(repository, analyzer, breachChecker, TwoFactorDomains(emptySet()))(checkBreaches = false)

        assertEquals(2, withTip.noTwoFactor.size)
        assertTrue(quiet.noTwoFactor.isEmpty())
        assertEquals(quiet.score, withTip.score)
        assertEquals(quiet.affected, withTip.affected)
    }

    @Test
    fun theTipIsThereWithTheBreachCheckOnToo() = runTest {
        repository.auditEntries.value = listOf(entry("GitHub", "https://github.com"))

        assertEquals(listOf("GitHub"), useCase(checkBreaches = true).noTwoFactor.map { it.title })
    }
}
