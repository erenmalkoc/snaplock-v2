package com.erenium.snaplock.autofill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutofillViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val github = Credential(UUID.randomUUID(), "GitHub", "octocat", "pw", listOf("https://github.com"), emptyList())
    private val mail = Credential(UUID.randomUUID(), "Mail", "me", "pw2", emptyList(), emptyList())
    private val repository = FakeKdbxRepository().apply {
        locked.value = false
        credentials.value = listOf(github, mail)
    }

    private fun viewModel() =
        AutofillViewModel(repository, RecentDatabasesRepository(context, RecentDatabasesPrefs(context)))

    private fun stored(uuid: UUID) = repository.credentials.value.single { it.uuid == uuid }

    @Test
    fun pickingAnotherEntryInABrowserRemembersTheSite() = runTest {
        val viewModel = viewModel()
        viewModel.start(CHROME, "www.mail.example", fillSingleMatch = false)

        viewModel.onPick(mail)
        withTimeout(5_000) { viewModel.picked.first() }

        assertEquals(listOf("https://mail.example"), stored(mail.uuid).urls)
    }

    @Test
    fun pickingInAnAppRemembersThePackage() = runTest {
        val viewModel = viewModel()
        viewModel.start("com.mail.app", null, fillSingleMatch = false)

        viewModel.onPick(mail)
        withTimeout(5_000) { viewModel.picked.first() }

        assertEquals(listOf("com.mail.app"), stored(mail.uuid).androidApps)
    }

    @Test
    fun webViewInAnAppIsLinkedToTheAppNotTheSite() = runTest {
        val viewModel = viewModel()
        viewModel.start("com.some.app", "github.com", fillSingleMatch = false)

        viewModel.onPick(github)
        withTimeout(5_000) { viewModel.picked.first() }

        assertEquals(listOf("https://github.com"), stored(github.uuid).urls)
        assertEquals(listOf("com.some.app"), stored(github.uuid).androidApps)
    }

    @Test
    fun turnedOffOrAlreadyMatchingLeavesEntriesAlone() = runTest {
        val viewModel = viewModel()
        viewModel.start(CHROME, "github.com", fillSingleMatch = false)

        viewModel.onPick(github)
        withTimeout(5_000) { viewModel.picked.first() }
        viewModel.onLinkPickedChange(false)
        viewModel.onPick(mail)
        withTimeout(5_000) { viewModel.picked.first() }

        assertEquals(listOf(github, mail), repository.credentials.value)
    }

    @Test
    fun singleMatchFillsRightAfterUnlocking() = runTest {
        repository.locked.value = true
        val viewModel = viewModel()
        viewModel.start(CHROME, "github.com", fillSingleMatch = true)

        repository.locked.value = false

        assertEquals(github, withTimeout(5_000) { viewModel.picked.first() })
    }

    private companion object {
        const val CHROME = "com.android.chrome"
    }
}
