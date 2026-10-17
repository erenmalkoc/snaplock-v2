package com.erenium.snaplock.presentation.main

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locks the database once no Snaplock screen is visible, after the configured delay.
 * Watches the whole process rather than one activity, so screens opened for autofill
 * or other system integrations follow the same rule.
 */
@Singleton
class AutoLockManager @Inject constructor(
    private val lockDatabaseUseCase: LockDatabaseUseCase,
    private val settingsPrefs: SettingsPrefs
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lockJob: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        lockJob?.cancel()
        lockJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        val delaySeconds = settingsPrefs.settings.value.autoLockSeconds
        lockJob?.cancel()
        lockJob = scope.launch {
            if (delaySeconds > 0) {
                delay(delaySeconds * MILLIS_PER_SECOND)
            }
            lockDatabaseUseCase()
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
