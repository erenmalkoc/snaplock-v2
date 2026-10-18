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
import com.erenium.snaplock.domain.usecase.RemoveAttachmentUseCase
import com.erenium.snaplock.domain.usecase.RestoreVersionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    /** Names of protected custom fields the user chose to show. */
    val revealedFields: Set<String> = emptySet()
)

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
    private val clipboardManager: ClipboardManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryDetailUiState())
    val uiState = _uiState.asStateFlow()

    /** Label of the field just copied, for the confirmation message. */
    private val _copyEvents = Channel<String>(Channel.BUFFERED)
    val copyEvents = _copyEvents.receiveAsFlow()

    private val _restoredEvents = Channel<Unit>(Channel.BUFFERED)
    val restoredEvents = _restoredEvents.receiveAsFlow()

    /** Short confirmations and errors: a string resource and its optional argument. */
    private val _messages = Channel<Pair<Int, String?>>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _deletedEvents = Channel<Unit>(Channel.BUFFERED)
    val deletedEvents = _deletedEvents.receiveAsFlow()

    private val entryUuid: String = checkNotNull(savedStateHandle["uuid"])

    init {
        loadEntryDetails()
    }

    fun reload() = loadEntryDetails()

    private fun loadEntryDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = getEntryDetailsUseCase(UUID.fromString(entryUuid))

            result.onSuccess { entry ->
                _uiState.update { it.copy(isLoading = false, entry = entry) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, errorStringId = R.string.entry_load_error) }
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
        _copyEvents.trySend(label)
    }

    fun onCopyUsername(label: String) {
        val username = _uiState.value.entry?.username
        if (username.isNullOrEmpty()) return
        clipboardManager.copyToClipboard(label = label, text = username, sensitive = false)
        _copyEvents.trySend(label)
    }

    fun onCopyOtp(label: String) {
        val otp = _uiState.value.entry?.otp ?: return
        clipboardManager.copyToClipboard(label = label, text = Totp.code(otp, System.currentTimeMillis()))
        _copyEvents.trySend(label)
    }

    fun onCopyCustomField(field: CustomField) {
        if (field.value.isEmpty()) return
        clipboardManager.copyToClipboard(label = field.name, text = field.value, sensitive = field.isProtected)
        _copyEvents.trySend(field.name)
    }

    fun toggleFieldVisibility(name: String) {
        _uiState.update {
            val revealed = if (name in it.revealedFields) it.revealedFields - name else it.revealedFields + name
            it.copy(revealedFields = revealed)
        }
    }

    fun onRestoreVersion(index: Int) {
        viewModelScope.launch {
            restoreVersionUseCase(UUID.fromString(entryUuid), index)
                .onSuccess {
                    loadEntryDetails()
                    _restoredEvents.trySend(Unit)
                }
                .onFailure { _uiState.update { it.copy(errorStringId = R.string.entry_save_error) } }
        }
    }

    fun onAddAttachment(uri: Uri) {
        val uuid = UUID.fromString(entryUuid)
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
                        .onFailure { message(R.string.attachment_error) }
                }
        }
    }

    fun onSaveAttachment(name: String, target: Uri) {
        viewModelScope.launch {
            getAttachmentUseCase(UUID.fromString(entryUuid), name)
                .fold(onSuccess = { documentFiles.write(target, it) }, onFailure = { Result.failure(it) })
                .onSuccess { message(R.string.attachment_saved, name) }
                .onFailure { message(R.string.attachment_error) }
        }
    }

    fun onRemoveAttachment(name: String) {
        viewModelScope.launch {
            removeAttachmentUseCase(UUID.fromString(entryUuid), name)
                .onSuccess { loadEntryDetails() }
                .onFailure { message(R.string.attachment_error) }
        }
    }

    private fun message(resId: Int, argument: String? = null) {
        _messages.trySend(resId to argument)
    }

    fun onDelete() {
        viewModelScope.launch {
            deleteEntryUseCase(UUID.fromString(entryUuid))
                .onSuccess { _deletedEvents.trySend(Unit) }
                .onFailure {
                    _uiState.update { it.copy(errorStringId = R.string.entry_delete_error) }
                }
        }
    }

    private companion object {
        // Attachments live inside the vault file, so a size cap keeps it quick to open and sync.
        const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
    }
}
