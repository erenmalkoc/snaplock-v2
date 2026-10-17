package com.erenium.snaplock.presentation

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EntryListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val root = FakeKdbxRepository.ROOT_GROUP_UUID
    private val work = UUID.randomUUID()
    private val github = Entry(UUID.randomUUID(), "GitHub", "octocat", root, "Passwords", url = "https://github.com")
    private val bank = Entry(UUID.randomUUID(), "Bank", "client", root, "Passwords", notes = "PIN hint: birthday")
    private val vpn = Entry(UUID.randomUUID(), "VPN", "eren", work, "Work", url = "vpn.company.example")
    private val repository = FakeKdbxRepository().apply { entries.value = listOf(github, bank, vpn) }

    private fun viewModel() = EntryListViewModel(GetEntriesUseCase(repository), GetGroupsUseCase(repository))

    private suspend fun EntryListViewModel.visibleTitles() =
        uiState.first { !it.isLoading }.entries.map { it.title }

    @Test
    fun searchMatchesTitleUsernameUrlAndNotes() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("git")
        assertEquals(listOf("GitHub"), viewModel.visibleTitles())

        viewModel.onQueryChange("CLIENT")
        assertEquals(listOf("Bank"), viewModel.visibleTitles())

        viewModel.onQueryChange("company")
        assertEquals(listOf("VPN"), viewModel.visibleTitles())

        viewModel.onQueryChange("birthday")
        assertEquals(listOf("Bank"), viewModel.visibleTitles())
    }

    @Test
    fun groupFilterAndSearchCombine() = runTest {
        val viewModel = viewModel()

        viewModel.onGroupSelected(work)
        assertEquals(listOf("VPN"), viewModel.visibleTitles())

        viewModel.onQueryChange("git")
        assertEquals(emptyList<String>(), viewModel.visibleTitles())
    }
}
