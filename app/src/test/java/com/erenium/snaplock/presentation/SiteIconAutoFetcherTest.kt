package com.erenium.snaplock.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.icons.DownloadSiteIconsUseCase
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.presentation.main.SiteIconAutoFetcher
import com.erenium.snaplock.testing.FakeKdbxRepository
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SiteIconAutoFetcherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository().apply { locked.value = false }
    private val asked = mutableListOf<String>()
    private val fetcher = SiteIconAutoFetcher(
        SettingsPrefs(context),
        repository,
        DownloadSiteIconsUseCase(repository) { host -> asked += host; if (host == "github.com") byteArrayOf(1) else null }
    )
    private val openVault = listOf(Entry(UUID.randomUUID(), "GitHub", null, UUID.randomUUID(), "Root"))

    @Test
    fun staysOfflineUntilTurnedOn() = runTest {
        repository.credentials.value = listOf(credential("https://github.com"))

        fetcher.fetchMissing(enabled = false, entries = openVault)
        fetcher.fetchMissing(enabled = true, entries = emptyList())

        assertTrue(asked.isEmpty())
    }

    @Test
    fun sitesWithoutAnIconAreNotAskedTwice() = runTest {
        val github = credential("https://github.com")
        repository.credentials.value = listOf(github, credential("https://no-icon.example"))

        fetcher.fetchMissing(enabled = true, entries = openVault)
        fetcher.fetchMissing(enabled = true, entries = openVault)

        assertEquals(1, asked.count { it == "no-icon.example" })
        assertTrue(github.uuid in repository.iconsSet)
    }

    @Test
    fun aSecondAccountOnASiteThatWorkedStillGetsItsIcon() = runTest {
        repository.credentials.value = listOf(credential("https://github.com"))
        fetcher.fetchMissing(enabled = true, entries = openVault)
        val second = credential("https://github.com/login")
        repository.credentials.value = listOf(second)

        fetcher.fetchMissing(enabled = true, entries = openVault)

        assertTrue(second.uuid in repository.iconsSet)
    }

    private fun credential(url: String) = Credential(UUID.randomUUID(), "Entry", null, null, listOf(url), emptyList())
}
