package com.erenium.snaplock.presentation

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import com.erenium.snaplock.presentation.main.AutoLockManager
import com.erenium.snaplock.testing.FakeKdbxRepository
import com.erenium.snaplock.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AutoLockManagerTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = FakeKdbxRepository().apply { locked.value = false }
    private val settings = SettingsPrefs(context)
    private val owner = ProcessLifecycleOwner.get()

    private fun manager() = AutoLockManager(LockDatabaseUseCase(repository), settings)

    @Test
    fun locksRightAwayWhenTheDelayIsZero() = runTest(dispatcher) {
        settings.setAutoLockSeconds(0)

        manager().onStop(owner)
        runCurrent()

        assertTrue(repository.locked.value)
    }

    @Test
    fun locksOnlyAfterTheConfiguredDelay() = runTest(dispatcher) {
        settings.setAutoLockSeconds(30)
        val manager = manager()

        manager.onStop(owner)
        advanceTimeBy(29_000)
        assertFalse(repository.locked.value)

        advanceTimeBy(2_000)
        assertTrue(repository.locked.value)
    }

    @Test
    fun comingBackCancelsThePendingLock() = runTest(dispatcher) {
        settings.setAutoLockSeconds(30)
        val manager = manager()

        manager.onStop(owner)
        advanceTimeBy(10_000)
        manager.onStart(owner)
        advanceTimeBy(60_000)

        assertFalse(repository.locked.value)
    }
}
