package com.erenium.snaplock.presentation

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.usecase.CreateGroupUseCase
import com.erenium.snaplock.domain.usecase.DeleteGroupUseCase
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.domain.usecase.RenameGroupUseCase
import com.erenium.snaplock.presentation.entrylist.EntryListUiState
import com.erenium.snaplock.presentation.entrylist.EntryListViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private val repository = FakeKdbxRepository().apply {
        entries.value = listOf(github, bank, vpn)
        groups.value = listOf(Group(root, "Passwords", 2), Group(work, "Work", 1, parentUuid = root))
    }

    private fun viewModel() = EntryListViewModel(
        GetEntriesUseCase(repository),
        GetGroupsUseCase(repository),
        CreateGroupUseCase(repository),
        RenameGroupUseCase(repository),
        DeleteGroupUseCase(repository)
    )

    private suspend fun EntryListViewModel.state(): EntryListUiState = uiState.first { !it.isLoading }

    @Test
    fun rootShowsItsSubgroupsAndOwnEntriesSorted() = runTest {
        val viewModel = viewModel()

        val state = viewModel.state()

        assertTrue(state.isRoot)
        assertEquals(listOf("Work"), state.subgroups.map { it.name })
        assertEquals(listOf("Bank", "GitHub"), state.entries.map { it.title })
    }

    @Test
    fun openingAGroupAndGoingBackUp() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onOpenGroup(work)
        val inside = viewModel.state()
        assertFalse(inside.isRoot)
        assertEquals("Work", inside.currentGroup?.name)
        assertEquals(listOf("VPN"), inside.entries.map { it.title })

        assertTrue(viewModel.onNavigateUp())
        assertTrue(viewModel.state().isRoot)
        assertFalse(viewModel.onNavigateUp())
    }

    @Test
    fun searchCoversEveryGroupAndField() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("company")
        assertEquals(listOf("VPN"), viewModel.state().entries.map { it.title })

        viewModel.onQueryChange("birthday")
        assertEquals(listOf("Bank"), viewModel.state().entries.map { it.title })

        viewModel.onQueryChange("wor")
        assertEquals(listOf("Work"), viewModel.state().subgroups.map { it.name })
    }

    @Test
    fun groupsAreCreatedInsideTheOpenGroupAndDeletingGoesBackUp() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onOpenGroup(work)
        viewModel.state()

        viewModel.onCreateGroup("Servers")
        assertEquals(work, repository.groups.value.single { it.name == "Servers" }.parentUuid)

        viewModel.onDeleteCurrentGroup()
        assertTrue(repository.groups.value.none { it.uuid == work })
        assertTrue(viewModel.state().isRoot)
    }
}
