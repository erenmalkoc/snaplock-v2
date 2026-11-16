package com.erenium.snaplock.autofill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.testing.testRecentRepository
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.repository.KdbxRepository
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutofillSaveViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository().apply { locked.value = false }
    private val githubUuid = UUID.randomUUID()

    private fun viewModel() = AutofillSaveViewModel(
        repository,
        testRecentRepository(context)
    )

    private fun withGithubEntry() {
        repository.credentials.value = listOf(
            Credential(githubUuid, "GitHub", "octocat", "old", listOf("https://github.com"), emptyList())
        )
        repository.entryDetails[githubUuid] = EntryDetail(
            githubUuid, "GitHub", "octocat", "old", "https://github.com", "my notes", FakeKdbxRepository.ROOT_GROUP_UUID
        )
    }

    @Test
    fun changedPasswordUpdatesTheExistingEntry() = runTest {
        withGithubEntry()
        val viewModel = viewModel()
        viewModel.start(CHROME, "github.com", "github.com", "octocat", "new")

        viewModel.onSave()
        withTimeout(5_000) { viewModel.done.first() }

        val updated = repository.entryDetails.getValue(githubUuid)
        assertEquals("new", updated.password)
        assertEquals("my notes", updated.notes)
        assertEquals(1, repository.entryDetails.size)
    }

    @Test
    fun saveAsNewKeepsTheOldEntry() = runTest {
        withGithubEntry()
        val viewModel = viewModel()
        viewModel.start(CHROME, "github.com", "github.com", "octocat", "new")

        viewModel.onSave(asNew = true)
        withTimeout(5_000) { viewModel.done.first() }

        assertEquals(2, repository.entryDetails.size)
        assertEquals("old", repository.entryDetails.getValue(githubUuid).password)
    }

    @Test
    fun appLoginBecomesANewEntryLinkedToTheApp() = runTest {
        val viewModel = viewModel()
        viewModel.start("com.bank.app", null, "Bank", "client", "1234")

        viewModel.onSave()
        withTimeout(5_000) { viewModel.done.first() }

        val saved = repository.entryDetails.values.single()
        assertEquals("Bank", saved.title)
        assertEquals("client", saved.username)
        assertEquals("androidapp://com.bank.app", saved.url)
    }

    @Test
    fun identicalLoginFinishesWithoutWriting() = runTest {
        withGithubEntry()
        val viewModel = viewModel()

        viewModel.start(CHROME, "github.com", "github.com", "octocat", "old")
        withTimeout(5_000) { viewModel.done.first() }

        assertTrue(repository.entryDetails.getValue(githubUuid).password == "old")
        assertEquals(1, repository.entryDetails.size)
    }

    @Test
    fun savingReportsThatSomethingWasSaved() = runTest {
        val viewModel = viewModel()
        viewModel.start("com.bank.app", null, "Bank", "client", "1234")

        viewModel.onSave()

        assertEquals(SaveOutcome.SAVED, withTimeout(5_000) { viewModel.done.first() })
    }

    @Test
    fun identicalLoginReportsNothingToSave() = runTest {
        withGithubEntry()
        val viewModel = viewModel()

        viewModel.start(CHROME, "github.com", "github.com", "octocat", "old")

        assertEquals(SaveOutcome.NOTHING_TO_SAVE, withTimeout(5_000) { viewModel.done.first() })
    }

    @Test
    fun editedFieldsAreNoticedAndRevertingClearsThem() = runTest {
        val viewModel = viewModel()
        val states = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.start("com.bank.app", null, "Bank", "client", "1234")
        assertFalse(awaitState(viewModel) { true }.hasEdits)

        viewModel.onTitleChange("Bank 2")
        awaitState(viewModel) { it.hasEdits }

        viewModel.onTitleChange("Bank")
        awaitState(viewModel) { !it.hasEdits && it.title == "Bank" }
        states.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun aFailedLookupShowsAnErrorThatRetryCanClear() = runTest {
        val failing = object : KdbxRepository by repository {
            var broken = true
            override suspend fun getCredentials(): List<Credential> =
                if (broken) throw IllegalStateException("boom") else repository.getCredentials()
        }
        val viewModel = AutofillSaveViewModel(failing, testRecentRepository(context))
        val states = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.start("com.bank.app", null, "Bank", "client", "1234")
        awaitState(viewModel) { it.loadFailed }

        failing.broken = false
        viewModel.onRetry()

        assertTrue(awaitState(viewModel) { !it.loadFailed && it.decision != null }.decision != null)
        states.cancel()
        viewModel.viewModelScope.cancel()
    }

    private suspend fun awaitState(viewModel: AutofillSaveViewModel, condition: (AutofillSaveUiState) -> Boolean) =
        withTimeout(5_000) { viewModel.uiState.first(condition) }

    private companion object {
        const val CHROME = "com.android.chrome"
    }
}
