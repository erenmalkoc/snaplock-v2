package com.erenium.snaplock.presentation

import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.EmptyRecycleBinUseCase
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetRecycledEntriesUseCase
import com.erenium.snaplock.domain.usecase.RestoreEntryUseCase
import com.erenium.snaplock.presentation.recyclebin.RecycleBinViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecycleBinViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val root = FakeKdbxRepository.ROOT_GROUP_UUID
    private val web = UUID.randomUUID()
    private val old = Entry(UUID.randomUUID(), "Old mail", "me", root, "Passwords")
    private val stale = Entry(UUID.randomUUID(), "Stale bank", "client", root, "Passwords")
    private val repository = FakeKdbxRepository().apply {
        recycled.value = listOf(old, stale)
        groups.value = listOf(Group(root, "Passwords", 0), Group(web, "Web", 0, root))
    }

    private fun viewModel() = RecycleBinViewModel(
        GetRecycledEntriesUseCase(repository),
        RestoreEntryUseCase(repository),
        DeleteEntryUseCase(repository),
        EmptyRecycleBinUseCase(repository),
        GetEntriesUseCase(repository)
    )

    @Test
    fun restoreBringsTheEntryBack() = runTest {
        val viewModel = viewModel()

        viewModel.onRestore(old.uuid)

        assertEquals(listOf(old), repository.entries.value)
        assertEquals(listOf("Stale bank"), viewModel.uiState.first { !it.isLoading }.entries.map { it.title })
    }

    @Test
    fun restoreSaysWhereTheEntryWent() = runTest {
        val inWeb = old.copy(previousGroupUuid = web)
        repository.recycled.value = listOf(inWeb)
        val viewModel = viewModel()

        viewModel.onRestore(old.uuid)

        assertEquals(R.string.recycle_bin_restored to "Web", viewModel.messages.first())
    }

    @Test
    fun restoreAdmitsFallingBackToTheRoot() = runTest {
        repository.recycled.value = listOf(old.copy(previousGroupUuid = UUID.randomUUID()))
        val viewModel = viewModel()

        viewModel.onRestore(old.uuid)

        assertEquals(R.string.recycle_bin_restored_fallback to "Passwords", viewModel.messages.first())
    }

    @Test
    fun rowsOpenAReadOnlyViewThatClosesOnRestoreOrDismiss() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.first()

        viewModel.onView(stale)
        assertEquals(stale, viewModel.uiState.first { it.viewing != null }.viewing)

        viewModel.onDismissDialog()
        assertNull(viewModel.uiState.first { !it.isLoading }.viewing)

        viewModel.onView(stale)
        viewModel.onRestore(stale.uuid)
        assertNull(viewModel.uiState.first { !it.isLoading }.viewing)
    }

    @Test
    fun deletingFromTheViewAsksForConfirmationFirst() = runTest {
        val viewModel = viewModel()
        viewModel.onView(stale)

        viewModel.onDeleteRequest(stale)

        val state = viewModel.uiState.first { it.pendingDelete != null }
        assertNull(state.viewing)
        assertEquals(2, repository.recycled.value.size)
    }

    @Test
    fun binTitlesFollowTheTurkishAlphabet() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr"))
        try {
            repository.recycled.value = listOf("Şirket", "Sera", "İnci", "Irmak", "Çay", "Cam")
                .map { Entry(UUID.randomUUID(), it, null, root, "Passwords") }
            runTest {
                val titles = viewModel().uiState.first { !it.isLoading }.entries.map { it.title }
                assertEquals(listOf("Cam", "Çay", "Irmak", "İnci", "Sera", "Şirket"), titles)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun permanentDeleteWaitsForConfirmation() = runTest {
        val viewModel = viewModel()

        viewModel.onDeleteRequest(stale)
        assertEquals(stale, viewModel.uiState.first { !it.isLoading }.pendingDelete)
        assertEquals(2, repository.recycled.value.size)

        viewModel.onDeleteConfirmed()
        assertEquals(listOf(old), repository.recycled.value)
    }

    @Test
    fun dismissingKeepsEverything() = runTest {
        val viewModel = viewModel()

        viewModel.onEmptyRequest()
        viewModel.onDismissDialog()

        assertEquals(2, repository.recycled.value.size)
    }

    @Test
    fun emptyingRemovesAllAfterConfirmation() = runTest {
        val viewModel = viewModel()

        viewModel.onEmptyRequest()
        viewModel.onEmptyConfirmed()

        assertTrue(repository.recycled.value.isEmpty())
    }
}
