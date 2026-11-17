package com.erenium.snaplock.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.domain.model.VaultBackup
import com.erenium.snaplock.domain.repository.KdbxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupsViewModel @Inject constructor(
    private val repository: KdbxRepository,
    private val documentFiles: DocumentFiles
) : ViewModel() {
    private val _backups = MutableStateFlow<List<VaultBackup>?>(null)
    val backups = _backups.asStateFlow()

    private var exporting: VaultBackup? = null

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onShowBackups() {
        viewModelScope.launch {
            runCatching { repository.getBackups() }
                .onSuccess { _backups.value = it }
                .onFailure { _messages.send(R.string.backups_load_error) }
        }
    }

    fun onDismiss() {
        _backups.value = null
    }

    fun onBackupChosen(backup: VaultBackup) {
        exporting = backup
        _backups.value = null
    }

    fun onExportTargetPicked(uri: Uri) {
        val backup = exporting ?: return
        exporting = null
        viewModelScope.launch {
            repository.getBackup(backup.id)
                .fold(onSuccess = { documentFiles.write(uri, it) }, onFailure = { Result.failure(it) })
                .onSuccess { _messages.send(R.string.backup_saved) }
                .onFailure { _messages.send(R.string.backup_error) }
        }
    }
}
