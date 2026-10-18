package com.erenium.snaplock.presentation

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import com.erenium.snaplock.presentation.main.AppShortcut
import com.erenium.snaplock.presentation.main.MainViewModel
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import com.erenium.snaplock.ui.publishAppShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppShortcutsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository().apply { locked.value = false }
    private val viewModel = MainViewModel(IsDatabaseLockedUseCase(repository), LockDatabaseUseCase(repository))

    @Test
    fun everyShortcutIsPublishedAndOpensMainActivity() {
        publishAppShortcuts(context)

        val published = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals(AppShortcut.entries.map { it.action }, published.map { it.intent.action })
        assertTrue(published.all { it.intent.component?.className == MainActivity::class.java.name })
        assertEquals(AppShortcut.entries.map { it }, published.map { AppShortcut.fromAction(it.intent.action) })
    }

    @Test
    fun unknownActionsAreNotShortcuts() {
        assertNull(AppShortcut.fromAction("android.intent.action.MAIN"))
        assertNull(AppShortcut.fromAction(null))
    }

    @Test
    fun theLockShortcutLocksRightAway() {
        viewModel.onShortcut(AppShortcut.LOCK)

        assertTrue(repository.locked.value)
        assertNull(viewModel.pendingShortcut.value)
    }

    @Test
    fun otherShortcutsWaitForTheirScreen() {
        viewModel.onShortcut(AppShortcut.NEW_ENTRY)
        assertEquals(AppShortcut.NEW_ENTRY, viewModel.pendingShortcut.value)

        viewModel.onShortcutHandled()
        assertNull(viewModel.pendingShortcut.value)
    }
}
