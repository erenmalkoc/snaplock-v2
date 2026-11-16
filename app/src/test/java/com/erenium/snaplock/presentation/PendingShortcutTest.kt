package com.erenium.snaplock.presentation

import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import com.erenium.snaplock.presentation.main.AppShortcut
import com.erenium.snaplock.presentation.main.MainViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingShortcutTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeKdbxRepository()

    private fun viewModel() = MainViewModel(IsDatabaseLockedUseCase(repository), LockDatabaseUseCase(repository))

    @Test
    fun aShortcutWaitingForTheUnlockExpiresAfterHalfAMinute() = runTest {
        val viewModel = viewModel()

        viewModel.onShortcut(AppShortcut.SEARCH)
        advanceTimeBy(29_000)
        assertEquals(AppShortcut.SEARCH, viewModel.pendingShortcut.value)

        advanceTimeBy(2_000)
        assertNull(viewModel.pendingShortcut.value)
    }

    @Test
    fun aNewerShortcutRestartsTheClock() = runTest {
        val viewModel = viewModel()

        viewModel.onShortcut(AppShortcut.SEARCH)
        advanceTimeBy(20_000)
        viewModel.onShortcut(AppShortcut.NEW_ENTRY)
        advanceTimeBy(20_000)

        assertEquals(AppShortcut.NEW_ENTRY, viewModel.pendingShortcut.value)
    }

    @Test
    fun lockingDropsAShortcutThatWasWaiting() = runTest {
        repository.locked.value = false
        val viewModel = viewModel()
        viewModel.onShortcut(AppShortcut.SEARCH)

        repository.locked.value = true

        assertNull(viewModel.pendingShortcut.value)
    }

    @Test
    fun handlingAShortcutClearsIt() = runTest {
        val viewModel = viewModel()
        viewModel.onShortcut(AppShortcut.GENERATOR)

        viewModel.onShortcutHandled()

        assertNull(viewModel.pendingShortcut.value)
    }
}
