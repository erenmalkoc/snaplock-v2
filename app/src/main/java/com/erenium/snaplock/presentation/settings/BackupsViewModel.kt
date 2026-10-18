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

/** Lists the daily copies of the open vault and saves one where the user picks. */
@HiltViewModel
class BackupsViewModel @Inject constructor(
    private val repository: KdbxRepository,
    private val documentFiles: DocumentFiles
) : ViewModel() {

    /** The copies to choose from while the list is open; null when it is closed. */
    private val _backups = MutableStateFlow<List<VaultBackup>?>(null)
    val backups = _backups.asStateFlow()

    /** The copy being saved while the system file picker is open. */
    private var exporting: VaultBackup? = null

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onShowBackups() {
        viewModelScope.launch { _backups.value = repository.getBackups() }
    }

    fun onDismiss() {
        _backups.value = null
    }

    /** Closes the list; the screen then asks where to save [backup]. */
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
