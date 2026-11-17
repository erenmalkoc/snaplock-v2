package com.erenium.snaplock.presentation.entrydetail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.datasource.local.DocumentFiles
import com.erenium.snaplock.data.datasource.local.FileTooLargeException
import com.erenium.snaplock.data.utils.ClipboardManagerHelper
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.otp.Totp
import com.erenium.snaplock.domain.usecase.AddAttachmentUseCase
import com.erenium.snaplock.domain.usecase.DeleteEntryUseCase
import com.erenium.snaplock.domain.usecase.GetAttachmentUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.GetRecycleBinEnabledUseCase
import com.erenium.snaplock.domain.usecase.RemoveAttachmentUseCase
import com.erenium.snaplock.domain.usecase.RestoreVersionUseCase
import com.erenium.snaplock.presentation.DeletedEntry
import com.erenium.snaplock.presentation.RecentlyDeleted
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EntryDetailUiState(
    val entry: EntryDetail? = null,
    val isLoading: Boolean = true,
    val errorStringId: Int? = null,
    val isPasswordVisible: Boolean = false,
    val revealedFields: Set<String> = emptySet(),
    val recycleBinEnabled: Boolean = true
)

data class CopyEvent(val label: String, val clearSeconds: Int?)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEntryDetailsUseCase: GetEntryDetailsUseCase,
    private val deleteEntryUseCase: DeleteEntryUseCase,
    private val restoreVersionUseCase: RestoreVersionUseCase,
    private val addAttachmentUseCase: AddAttachmentUseCase,
    private val getAttachmentUseCase: GetAttachmentUseCase,
    private val removeAttachmentUseCase: RemoveAttachmentUseCase,
    private val documentFiles: DocumentFiles,
    private val clipboardManager: ClipboardManagerHelper,
    private val getRecycleBinEnabled: GetRecycleBinEnabledUseCase,
    private val recentlyDeleted: RecentlyDeleted
) : ViewModel() {
    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState = _uiState.asStateFlow()

    private val _copyEvents = Channel<CopyEvent>(Channel.BUFFERED)
    val copyEvents = _copyEvents.receiveAsFlow()

    private val _restoredEvents = Channel<Unit>(Channel.BUFFERED)
    val restoredEvents = _restoredEvents.receiveAsFlow()

    private val _messages = Channel<Pair<Int, String?>>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _deletedEvents = Channel<Unit>(Channel.BUFFERED)
    val deletedEvents = _deletedEvents.receiveAsFlow()

    private var entryUuid: String? = savedStateHandle["uuid"]
    private var loadJob: Job? = null

    private val currentUuid: UUID get() = UUID.fromString(checkNotNull(entryUuid))

    init {
        loadEntryDetails()
    }

    fun reload() = loadEntryDetails()

    fun show(uuid: UUID) {
        if (entryUuid == uuid.toString()) return
        entryUuid = uuid.toString()
        _uiState.value = EntryDetailUiState()
        loadEntryDetails()
    }

    fun clear() {
        loadJob?.cancel()
        entryUuid = null
        _uiState.value = EntryDetailUiState()
    }

    private fun loadEntryDetails() {
        val uuid = entryUuid?.let(UUID::fromString) ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { if (it.entry == null) it.copy(isLoading = true, errorStringId = null) else it }

            val result = getEntryDetailsUseCase(uuid)

            result.onSuccess { entry ->
                val toBin = getRecycleBinEnabled()
                _uiState.update {
                    it.copy(isLoading = false, entry = entry, errorStringId = null, recycleBinEnabled = toBin)
                }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, entry = null, errorStringId = R.string.entry_load_error) }
            }
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun onCopyPassword(label: String) {
        val password = _uiState.value.entry?.password
        if (password.isNullOrEmpty()) return
        clipboardManager.copyToClipboard(label = label, text = password)
        copied(label)
    }

    fun onCopyUsername(label: String) {
        val username = _uiState.value.entry?.username
        if (username.isNullOrEmpty()) return
        clipboardManager.copyToClipboard(label = label, text = username, sensitive = false)
        copied(label)
    }

    fun onCopyOtp(label: String) {
        val otp = _uiState.value.entry?.otp ?: return
        clipboardManager.copyToClipboard(label = label, text = Totp.code(otp, System.currentTimeMillis()))
        copied(label)
    }

    fun onCopyCustomField(field: CustomField) {
        if (field.value.isEmpty()) return
        clipboardManager.copyToClipboard(label = field.name, text = field.value, sensitive = field.isProtected)
        copied(field.name)
    }

    private fun copied(label: String) {
        _copyEvents.trySend(CopyEvent(label, clipboardManager.autoClearSeconds()))
    }

    fun toggleFieldVisibility(name: String) {
        _uiState.update {
            val revealed = if (name in it.revealedFields) it.revealedFields - name else it.revealedFields + name
            it.copy(revealedFields = revealed)
        }
    }

    fun onRestoreVersion(index: Int) {
        viewModelScope.launch {
            restoreVersionUseCase(currentUuid, index)
                .onSuccess {
                    loadEntryDetails()
                    _restoredEvents.trySend(Unit)
                }
                .onFailure { error -> message(error.saveErrorMessage()) }
        }
    }

    fun onAddAttachment(uri: Uri) {
        val uuid = currentUuid
        viewModelScope.launch {
            documentFiles.read(uri, MAX_ATTACHMENT_BYTES)
                .onFailure { error ->
                    message(if (error is FileTooLargeException) R.string.attachment_too_large else R.string.attachment_error)
                }
                .onSuccess { document ->
                    addAttachmentUseCase(uuid, document.name, document.content)
                        .onSuccess {
                            loadEntryDetails()
                            message(R.string.attachment_added, document.name)
                        }
                        .onFailure { message(it.saveErrorMessage(R.string.attachment_error)) }
                }
        }
    }

    fun onSaveAttachment(name: String, target: Uri) {
        viewModelScope.launch {
            getAttachmentUseCase(currentUuid, name)
                .fold(onSuccess = { documentFiles.write(target, it) }, onFailure = { Result.failure(it) })
                .onSuccess { message(R.string.attachment_saved, name) }
                .onFailure { message(R.string.attachment_error) }
        }
    }

    fun onRemoveAttachment(name: String) {
        viewModelScope.launch {
            removeAttachmentUseCase(currentUuid, name)
                .onSuccess { loadEntryDetails() }
                .onFailure { message(it.saveErrorMessage(R.string.attachment_error)) }
        }
    }

    private fun message(resId: Int, argument: String? = null) {
        _messages.trySend(resId to argument)
    }

    fun onDelete() {
        val uuid = currentUuid
        val title = _uiState.value.entry?.title
        viewModelScope.launch {
            val toBin = getRecycleBinEnabled()
            deleteEntryUseCase(uuid)
                .onSuccess {
                    if (toBin && title != null) recentlyDeleted.moved(DeletedEntry(uuid, title))
                    _deletedEvents.trySend(Unit)
                }
                .onFailure { error -> message(error.saveErrorMessage(R.string.entry_delete_error)) }
        }
    }

    private companion object {
        const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
    }
}
