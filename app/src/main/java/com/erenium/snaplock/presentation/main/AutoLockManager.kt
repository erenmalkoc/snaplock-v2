package com.erenium.snaplock.presentation.main

import android.os.SystemClock
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

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var lockJob: Job? = null
    private var stoppedAt: Long? = null
    private var pendingDelayMillis: Long = 0
    private var pickerLaunchedAt: Long? = null

    internal var elapsedRealtime: () -> Long = SystemClock::elapsedRealtime

    fun pickerLaunched() {
        pickerLaunchedAt = elapsedRealtime()
    }

    fun pickerFinished() {
        pickerLaunchedAt = null
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val stopped = stoppedAt
        lockJob?.cancel()
        lockJob = null
        stoppedAt = null
        if (stopped != null && elapsedRealtime() - stopped >= pendingDelayMillis) {
            scope.launch { lockDatabaseUseCase() }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        val configuredMillis = settingsPrefs.settings.value.autoLockSeconds * MILLIS_PER_SECOND
        val now = elapsedRealtime()
        val picking = pickerLaunchedAt?.let { now - it < PICKER_MAX_MILLIS } == true
        val delayMillis = if (picking) maxOf(configuredMillis, PICKER_GRACE_MILLIS) else configuredMillis
        stoppedAt = now
        pendingDelayMillis = delayMillis
        lockJob?.cancel()
        lockJob = scope.launch {
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            lockDatabaseUseCase()
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val PICKER_GRACE_MILLIS = 120_000L
        const val PICKER_MAX_MILLIS = 600_000L
    }
}
