package com.erenium.snaplock.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.passwords.BreachCheck
import com.erenium.snaplock.domain.passwords.BreachChecker
import com.erenium.snaplock.domain.passwords.PasswordHealthAnalyzer
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.domain.usecase.CheckPasswordHealthUseCase
import com.erenium.snaplock.presentation.health.PasswordHealthViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordHealthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository()
    private val lookups = mutableListOf<String>()
    private var breachedPasswords = setOf<String>()
    private var offline = false
    private val breachChecker = object : BreachChecker {
        override suspend fun breachCount(password: String): Result<Int> {
            lookups += password
            if (offline) return Result.failure(IOException("offline"))
            return Result.success(if (password in breachedPasswords) 42 else 0)
        }
    }
    private val analyzer = PasswordHealthAnalyzer(PasswordStrengthEstimator(object : WordList {
        override val words = emptyList<String>()
    }))
    private val useCase = CheckPasswordHealthUseCase(repository, analyzer, breachChecker)

    private fun entry(title: String, password: String) =
        AuditEntry(UUID.randomUUID(), title, "me", password, Instant.now(), null)

    @Test
    fun refreshReportsTheCurrentVaultWithoutGoingOnlineByDefault() = runTest {
        repository.auditEntries.value = listOf(entry("Forum", "123456"))
        val viewModel = PasswordHealthViewModel(useCase, SettingsPrefs(context))

        viewModel.refresh()
        // The analysis runs on Dispatchers.Default, so wait in real time.
        val report = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { viewModel.report.filterNotNull().first() }
        }

        assertEquals(listOf("Forum"), report.weak.map { it.title })
        assertEquals(BreachCheck.OFF, report.breachCheck)
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun breachCheckLooksUpEachPasswordOnce() = runTest {
        breachedPasswords = setOf("Sh4red-but-Leaked!")
        val mail = entry("Mail", "Sh4red-but-Leaked!")
        val bank = entry("Bank", "Sh4red-but-Leaked!")
        val git = entry("GitHub", "Zq8!xW3#vB6nM1%kL")
        repository.auditEntries.value = listOf(mail, bank, git)

        val report = useCase(checkBreaches = true)

        assertEquals(BreachCheck.DONE, report.breachCheck)
        assertEquals(listOf("Bank", "Mail"), report.breached.map { it.title })
        assertEquals(2, lookups.size)
    }

    @Test
    fun unreachableServiceIsReportedNotHidden() = runTest {
        offline = true
        repository.auditEntries.value = listOf(entry("Mail", "whatever-password-1"))

        val report = useCase(checkBreaches = true)

        assertEquals(BreachCheck.FAILED, report.breachCheck)
        assertTrue(report.breached.isEmpty())
    }
}
