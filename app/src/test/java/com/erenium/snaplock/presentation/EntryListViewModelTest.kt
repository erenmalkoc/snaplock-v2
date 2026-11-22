package com.erenium.snaplock.presentation

import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.usecase.GetRecycleBinEnabledUseCase
import com.erenium.snaplock.domain.usecase.RestoreEntryUseCase
import com.erenium.snaplock.domain.search.SearchMatch
import java.util.Locale
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
        DeleteGroupUseCase(repository),
        RestoreEntryUseCase(repository),
        GetRecycleBinEnabledUseCase(repository),
        recentlyDeleted
    )

    private val recentlyDeleted = RecentlyDeleted()

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

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun turkishTitlesFollowTheTurkishAlphabet() {
        withLocale(Locale.forLanguageTag("tr")) {
            val titles = listOf("Zeytin", "Ördek", "İnci", "Şirket", "Ğ", "Orman", "Irmak", "Çiçek", "Gül", "Cam", "Sera", "Hat")
            repository.entries.value = titles.map { Entry(UUID.randomUUID(), it, null, root, "Passwords") }
            repository.groups.value = listOf(Group(root, "Passwords", titles.size))

            runTest {
                val sorted = viewModel().state().entries.map { it.title }
                assertEquals(
                    listOf("Cam", "Çiçek", "Gül", "Ğ", "Hat", "Irmak", "İnci", "Orman", "Ördek", "Sera", "Şirket", "Zeytin"),
                    sorted
                )
            }
        }
    }

    @Test
    fun turkishGroupNamesAreSortedTheSameWay() {
        withLocale(Locale.forLanguageTag("tr")) {
            val names = listOf("Şirket", "Sera", "Çay", "Cam")
            repository.groups.value = listOf(Group(root, "Passwords", 0)) +
                names.map { Group(UUID.randomUUID(), it, 0, parentUuid = root) }

            runTest {
                assertEquals(listOf("Cam", "Çay", "Sera", "Şirket"), viewModel().state().subgroups.map { it.name })
            }
        }
    }

    @Test
    fun backClearsASearchBeforeAnythingElse() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onOpenGroup(work)
        viewModel.onQueryChange("birthday")
        viewModel.state()

        assertTrue(viewModel.onBack())
        assertEquals("", viewModel.state().query)
        assertEquals("Work", viewModel.state().currentGroup?.name)

        assertTrue(viewModel.onBack())
        assertTrue(viewModel.state().isRoot)
        assertFalse(viewModel.onBack())
    }

    @Test
    fun searchResultsSayWhichFieldMatched() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("company")
        assertEquals(SearchMatch.URL, viewModel.state().matches[vpn.uuid])

        viewModel.onQueryChange("birthday")
        assertEquals(SearchMatch.NOTES, viewModel.state().matches[bank.uuid])

        viewModel.onQueryChange("octo")
        assertEquals(SearchMatch.USERNAME, viewModel.state().matches[github.uuid])

        viewModel.onQueryChange("git")
        assertEquals(SearchMatch.TITLE, viewModel.state().matches[github.uuid])

        viewModel.onQueryChange("")
        assertTrue(viewModel.state().matches.isEmpty())
    }

    @Test
    fun searchPutsTheBestMatchFirstInsteadOfTheAlphabet() = runTest {
        val inUsername = Entry(UUID.randomUUID(), "Aardvark", "bank", root, "Passwords")
        val inTitle = Entry(UUID.randomUUID(), "Zebra Bank", null, root, "Passwords")
        repository.entries.value = listOf(inUsername, inTitle)
        val viewModel = viewModel()

        viewModel.onQueryChange("bank")

        assertEquals(listOf("Zebra Bank", "Aardvark"), viewModel.state().entries.map { it.title })
    }

    @Test
    fun searchFindsTurkishTextTypedWithoutTurkishLetters() = runTest {
        val kasa = Entry(UUID.randomUUID(), "Şifre Kasası", "ahmet", root, "Passwords")
        repository.entries.value = listOf(kasa, github)
        repository.groups.value = listOf(Group(root, "Passwords", 2), Group(work, "İş Hesapları", 0, parentUuid = root))
        val viewModel = viewModel()

        viewModel.onQueryChange("SIFRE kasasi")
        assertEquals(listOf("Şifre Kasası"), viewModel.state().entries.map { it.title })

        viewModel.onQueryChange("is hesaplari")
        assertEquals(listOf("İş Hesapları"), viewModel.state().subgroups.map { it.name })
    }

    @Test
    fun searchLooksInCustomFieldsAndSaysSo() = runTest {
        val account = Entry(
            UUID.randomUUID(), "Insurance", null, root, "Passwords",
            customFields = mapOf("Policy number" to "TR-4711")
        )
        repository.entries.value = listOf(account, github)
        val viewModel = viewModel()

        viewModel.onQueryChange("4711")

        val state = viewModel.state()
        assertEquals(listOf("Insurance"), state.entries.map { it.title })
        assertEquals(SearchMatch.FIELD, state.matches[account.uuid])
    }

    @Test
    fun aMisspelledSearchStillFindsTheEntryAndMarksItSimilar() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("githbu")

        val state = viewModel.state()
        assertEquals(listOf("GitHub"), state.entries.map { it.title })
        assertEquals(SearchMatch.SIMILAR, state.matches[github.uuid])
    }

    @Test
    fun breadcrumbListsTheParentsOfANestedGroup() = runTest {
        val servers = UUID.randomUUID()
        repository.groups.value = repository.groups.value + Group(servers, "Servers", 0, parentUuid = work)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        assertTrue(viewModel.state().breadcrumb.isEmpty())
        viewModel.onOpenGroup(servers)

        assertEquals(listOf("Passwords", "Work"), viewModel.state().breadcrumb.map { it.name })
    }

    @Test
    fun groupDeletionReportsWhetherTheRecycleBinIsOn() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertTrue(viewModel.state().recycleBinEnabled)

        viewModel.onOpenGroup(work)
        viewModel.state()
        viewModel.onDeleteCurrentGroup()
        assertEquals(R.string.group_moved_to_bin to "Work", viewModel.messages.first())

        repository.databaseSettings = repository.databaseSettings?.copy(recycleBinEnabled = false)
        repository.groups.value = repository.groups.value + Group(work, "Work", 0, parentUuid = root)
        assertFalse(viewModel.state().recycleBinEnabled)
        viewModel.onOpenGroup(work)
        viewModel.onDeleteCurrentGroup()
        assertEquals(R.string.group_deleted to "Work", viewModel.messages.first())
    }

    @Test
    fun groupCreateAndRenameConfirm() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.state()

        viewModel.onCreateGroup(" Servers ")
        assertEquals(R.string.group_created to "Servers", viewModel.messages.first())

        viewModel.onOpenGroup(work)
        viewModel.state()
        viewModel.onRenameCurrentGroup("Office")
        assertEquals(R.string.group_renamed to "Office", viewModel.messages.first())
    }

    @Test
    fun undoBringsARecycledEntryBack() = runTest {
        repository.recycled.value = listOf(github)
        repository.entries.value = listOf(bank, vpn)
        val viewModel = viewModel()

        recentlyDeleted.moved(DeletedEntry(github.uuid, github.title))
        val event = viewModel.deletedEntries.first()
        viewModel.onUndoDelete(event.uuid)

        assertTrue(repository.entries.value.any { it.uuid == github.uuid })
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
