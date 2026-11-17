package com.erenium.snaplock.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    isDatabaseLockedUseCase: IsDatabaseLockedUseCase,
    private val lockDatabaseUseCase: LockDatabaseUseCase,
    appMessages: AppMessages = AppMessages()
) : ViewModel() {
    val isLocked: StateFlow<Boolean> = isDatabaseLockedUseCase()

    val messages: Flow<AppMessage> = appMessages.messages
        .filter { System.currentTimeMillis() - it.postedAt < MESSAGE_LIFETIME_MILLIS }

    private var shortcutExpiry: Job? = null

    init {
        viewModelScope.launch {
            var wasUnlocked = !isLocked.value
            isLocked.collect { locked ->
                if (locked && wasUnlocked) clearShortcut()
                wasUnlocked = !locked
            }
        }
    }

    private val _pendingShortcut = MutableStateFlow<AppShortcut?>(null)
    val pendingShortcut = _pendingShortcut.asStateFlow()

    fun onShortcut(shortcut: AppShortcut) {
        if (shortcut == AppShortcut.LOCK) {
            clearShortcut()
            viewModelScope.launch { lockDatabaseUseCase() }
        } else {
            _pendingShortcut.value = shortcut
            shortcutExpiry?.cancel()
            shortcutExpiry = viewModelScope.launch {
                delay(SHORTCUT_LIFETIME_MILLIS)
                _pendingShortcut.value = null
            }
        }
    }

    fun onShortcutHandled() = clearShortcut()

    private fun clearShortcut() {
        shortcutExpiry?.cancel()
        shortcutExpiry = null
        _pendingShortcut.value = null
    }

    private companion object {
        const val SHORTCUT_LIFETIME_MILLIS = 30_000L
        const val MESSAGE_LIFETIME_MILLIS = 15_000L
    }
}
