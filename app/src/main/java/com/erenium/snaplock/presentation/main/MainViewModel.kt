package com.erenium.snaplock.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.domain.usecase.IsDatabaseLockedUseCase
import com.erenium.snaplock.domain.usecase.LockDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    isDatabaseLockedUseCase: IsDatabaseLockedUseCase,
    private val lockDatabaseUseCase: LockDatabaseUseCase
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = isDatabaseLockedUseCase()

    /** A launcher shortcut waiting for its screen, which may need the vault unlocked first. */
    private val _pendingShortcut = MutableStateFlow<AppShortcut?>(null)
    val pendingShortcut = _pendingShortcut.asStateFlow()

    fun onShortcut(shortcut: AppShortcut) {
        if (shortcut == AppShortcut.LOCK) {
            _pendingShortcut.value = null
            viewModelScope.launch { lockDatabaseUseCase() }
        } else {
            _pendingShortcut.value = shortcut
        }
    }

    fun onShortcutHandled() {
        _pendingShortcut.value = null
    }
}
