package com.erenium.snaplock.presentation

import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.passwords.PasswordHealthAnalyzer
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.WordList
import com.erenium.snaplock.domain.usecase.CheckPasswordHealthUseCase
import com.erenium.snaplock.presentation.health.PasswordHealthViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PasswordHealthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeKdbxRepository()
    private val viewModel = PasswordHealthViewModel(
        CheckPasswordHealthUseCase(
            repository,
            PasswordHealthAnalyzer(PasswordStrengthEstimator(object : WordList {
                override val words = emptyList<String>()
            }))
        )
    )

    @Test
    fun refreshReportsTheCurrentVault() = runTest {
        repository.auditEntries.value = listOf(
            AuditEntry(UUID.randomUUID(), "Forum", "me", "123456", Instant.now(), null)
        )

        viewModel.refresh()
        // The analysis runs on Dispatchers.Default, so wait in real time.
        val report = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { viewModel.report.filterNotNull().first() }
        }

        assertEquals(listOf("Forum"), report.weak.map { it.title })
        assertEquals(0, report.score)
    }
}
