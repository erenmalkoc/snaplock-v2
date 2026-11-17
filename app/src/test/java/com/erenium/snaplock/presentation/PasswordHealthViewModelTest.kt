package com.erenium.snaplock.presentation

import com.erenium.snaplock.testing.awaitReal
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
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private var failingPasswords = setOf<String>()
    private var crashing = false
    private val breachChecker = object : BreachChecker {
        override suspend fun breachCount(password: String): Result<Int> {
            if (crashing) throw IllegalStateException("boom")
            lookups += password
            if (password in failingPasswords) return Result.failure(IOException("flaky"))
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
        val report = withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitReal { viewModel.report.filterNotNull().first() }
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

    private suspend fun PasswordHealthViewModel.awaitReport() =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitReal { report.filterNotNull().first() }
        }

    private suspend fun PasswordHealthViewModel.awaitIdle() =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            awaitReal { refreshing.first { !it } }
        }

    @Test
    fun oneFailedLookupKeepsTheOthersAndMarksTheCheckIncomplete() = runTest {
        breachedPasswords = setOf("Sh4red-but-Leaked!")
        failingPasswords = setOf("Flaky-lookup-pw-9")
        repository.auditEntries.value = listOf(
            entry("Mail", "Sh4red-but-Leaked!"),
            entry("Flaky", "Flaky-lookup-pw-9"),
            entry("GitHub", "Zq8!xW3#vB6nM1%kL")
        )

        val report = useCase(checkBreaches = true)

        assertEquals(BreachCheck.INCOMPLETE, report.breachCheck)
        assertEquals(listOf("Mail"), report.breached.map { it.title })
    }

    @Test
    fun answersAreRememberedSoOnlyFailedLookupsRepeat() = runTest {
        failingPasswords = setOf("Flaky-lookup-pw-9")
        repository.auditEntries.value = listOf(entry("Flaky", "Flaky-lookup-pw-9"), entry("GitHub", "Zq8!xW3#vB6nM1%kL"))

        useCase(checkBreaches = true)
        failingPasswords = emptySet()
        lookups.clear()
        val report = useCase(checkBreaches = true)

        assertEquals(BreachCheck.DONE, report.breachCheck)
        assertEquals(listOf("Flaky-lookup-pw-9"), lookups)
    }

    @Test
    fun anEmptyVaultChecksNothing() = runTest {
        val report = useCase(checkBreaches = true)

        assertEquals(0, report.checked)
        assertEquals(BreachCheck.DONE, report.breachCheck)
        assertTrue(lookups.isEmpty())
    }

    @Test
    fun entriesSharingAPasswordComeBackGrouped() = runTest {
        repository.auditEntries.value = listOf(
            entry("Mail", "Zq8!xW3#vB6nM1%kL"),
            entry("Bank", "Zq8!xW3#vB6nM1%kL"),
            entry("Forum", "Another-Strong-1!x"),
            entry("Shop", "Another-Strong-1!x"),
            entry("Blog", "Another-Strong-1!x")
        )

        val report = useCase(checkBreaches = false)

        assertEquals(
            listOf(listOf("Blog", "Forum", "Shop"), listOf("Bank", "Mail")),
            report.reused.map { group -> group.map { it.title } }
        )
    }

    @Test
    fun listsFollowTheTurkishAlphabet() = runTest {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr"))
        try {
            repository.auditEntries.value = listOf("Şirket", "Sera", "İnci", "Irmak", "Çay", "Cam").map { entry(it, "123456") }

            val report = useCase(checkBreaches = false)

            assertEquals(
                listOf("Cam", "Çay", "Irmak", "İnci", "Sera", "Şirket"),
                report.weak.map { it.title }
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun comingBackToTheScreenDoesNotRepeatTheOnlineLookups() = runTest {
        SettingsPrefs(context).setBreachCheckEnabled(true)
        repository.auditEntries.value = listOf(entry("Mail", "Zq8!xW3#vB6nM1%kL"))
        val viewModel = PasswordHealthViewModel(useCase, SettingsPrefs(context))

        viewModel.refresh()
        viewModel.awaitReport()
        viewModel.awaitIdle()
        assertEquals(1, lookups.size)

        viewModel.refresh()
        viewModel.awaitIdle()
        assertEquals(1, lookups.size)

        repository.auditEntries.value = listOf(entry("Mail", "Zq8!xW3#vB6nM1%kL"), entry("New", "Another-Strong-1!x"))
        viewModel.refresh()
        viewModel.awaitIdle()
        assertEquals(2, lookups.size)
        assertEquals(2, viewModel.awaitReport().checked)
    }

    @Test
    fun anExplicitRefreshRunsEvenWhenNothingChanged() = runTest {
        repository.auditEntries.value = listOf(entry("Forum", "123456"))
        val viewModel = PasswordHealthViewModel(useCase, SettingsPrefs(context))
        viewModel.refresh()
        val first = viewModel.awaitReport()
        viewModel.awaitIdle()

        viewModel.refresh(force = true)
        viewModel.awaitIdle()

        assertEquals(first.checked, viewModel.awaitReport().checked)
        assertFalse(viewModel.failed.value)
    }

    @Test
    fun aCrashedCheckShowsFailureNotASpinnerAndRetryRecovers() = runTest {
        SettingsPrefs(context).setBreachCheckEnabled(true)
        repository.auditEntries.value = listOf(entry("Mail", "Zq8!xW3#vB6nM1%kL"))
        crashing = true
        val viewModel = PasswordHealthViewModel(useCase, SettingsPrefs(context))

        viewModel.refresh()
        viewModel.awaitIdle()

        assertTrue(viewModel.failed.value)
        assertNull(viewModel.report.value)

        crashing = false
        viewModel.refresh(force = true)
        viewModel.awaitIdle()

        assertFalse(viewModel.failed.value)
        assertEquals(BreachCheck.DONE, viewModel.report.value?.breachCheck)
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
