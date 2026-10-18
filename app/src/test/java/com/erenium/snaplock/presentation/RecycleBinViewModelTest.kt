package com.erenium.snaplock.presentation

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.EmptyRecycleBinUseCase
import com.erenium.snaplock.domain.usecase.GetRecycledEntriesUseCase
import com.erenium.snaplock.domain.usecase.RestoreEntryUseCase
import com.erenium.snaplock.presentation.recyclebin.RecycleBinViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecycleBinViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val root = FakeKdbxRepository.ROOT_GROUP_UUID
    private val old = Entry(UUID.randomUUID(), "Old mail", "me", root, "Passwords")
    private val stale = Entry(UUID.randomUUID(), "Stale bank", "client", root, "Passwords")
    private val repository = FakeKdbxRepository().apply { recycled.value = listOf(old, stale) }

    private fun viewModel() = RecycleBinViewModel(
        GetRecycledEntriesUseCase(repository),
        RestoreEntryUseCase(repository),
        DeleteEntryUseCase(repository),
        EmptyRecycleBinUseCase(repository)
    )

    @Test
    fun restoreBringsTheEntryBack() = runTest {
        val viewModel = viewModel()

        viewModel.onRestore(old.uuid)

        assertEquals(listOf(old), repository.entries.value)
        assertEquals(listOf("Stale bank"), viewModel.uiState.first { !it.isLoading }.entries.map { it.title })
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
