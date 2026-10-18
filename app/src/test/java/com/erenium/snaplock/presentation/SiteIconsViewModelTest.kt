package com.erenium.snaplock.presentation

import com.erenium.snaplock.R
import com.erenium.snaplock.domain.icons.DownloadSiteIconsUseCase
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.presentation.settings.SiteIconsViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SiteIconsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeKdbxRepository().apply { locked.value = false }
    private val asked = mutableListOf<String>()
    private val viewModel = SiteIconsViewModel(DownloadSiteIconsUseCase(repository) { host -> asked += host; byteArrayOf(1) })

    @Test
    fun asksBeforeGoingOnlineThenReportsTheResult() = runTest {
        repository.credentials.value = listOf(credential("https://github.com"), credential("https://gitlab.com"))

        viewModel.onDownloadRequested()
        assertEquals(2, viewModel.uiState.value.confirmCount)
        assertTrue(asked.isEmpty())

        viewModel.onConfirmed()

        val message = viewModel.messages.first()
        assertEquals(R.plurals.site_icons_done, message.resId)
        assertEquals(2, message.count)
        assertNull(viewModel.uiState.value.progress)
        assertEquals(2, repository.iconsSet.size)
    }

    @Test
    fun saysSoWhenThereIsNothingToFetch() = runTest {
        viewModel.onDownloadRequested()

        assertEquals(R.string.site_icons_none_pending, viewModel.messages.first().resId)
        assertNull(viewModel.uiState.value.confirmCount)
    }

    @Test
    fun dismissingSendsNothing() = runTest {
        repository.credentials.value = listOf(credential("https://github.com"))
        viewModel.onDownloadRequested()

        viewModel.onDismiss()

        assertNull(viewModel.uiState.value.confirmCount)
        assertTrue(asked.isEmpty())
    }

    private fun credential(url: String) = Credential(UUID.randomUUID(), "Entry", null, null, listOf(url), emptyList())
}
