package com.erenium.snaplock.presentation.main

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.erenium.snaplock.domain.usecase.ReloadIfChangedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks up changes another device or app made to the open vault while Snaplock was in the
 * background, so the lists show them without locking and reopening. A file that can no
 * longer be read is left for the next save to report.
 */
@Singleton
class ExternalChangesWatcher @Inject constructor(
    private val reloadIfChanged: ReloadIfChangedUseCase
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            reloadIfChanged().onFailure { Log.w(TAG, "Could not check the vault file for changes", it) }
        }
    }

    private companion object {
        const val TAG = "ExternalChangesWatcher"
    }
}
