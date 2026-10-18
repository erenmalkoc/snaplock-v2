package com.erenium.snaplock.presentation.entryform

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Expiry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.model.PasswordOptions
import com.erenium.snaplock.domain.passwords.PasswordStrengthEstimator
import com.erenium.snaplock.domain.passwords.StrengthEstimate
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import com.erenium.snaplock.domain.otp.OtpParameters
import com.erenium.snaplock.domain.otp.OtpParser
import com.erenium.snaplock.domain.usecase.AddEntryUseCase
import com.erenium.snaplock.domain.usecase.GetEntryDetailsUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.domain.usecase.UpdateEntryUseCase
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** A custom field being edited; [key] keeps rows stable while names change. */
data class CustomFieldDraft(
    val key: Long,
    val name: String = "",
    val value: String = "",
    val isProtected: Boolean = false
)

data class EntryFormUiState(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val passwordStrength: StrengthEstimate? = null,
    val url: String = "",
    val notes: String = "",
    val customFields: List<CustomFieldDraft> = emptyList(),
    /** Current two-factor settings; null when the entry has none. */
    val otp: OtpParameters? = null,
    /** What the user typed or scanned while adding two-factor settings. */
    val otpInput: String = "",
    val otpInputError: Boolean = false,
    /** When the password should be replaced; null if it never expires. */
    val expires: Instant? = null,
    val groups: List<Group> = emptyList(),
    val groupUuid: UUID? = null,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorStringId: Int? = null
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving && !isLoading
}

@HiltViewModel
class EntryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEntryDetailsUseCase: GetEntryDetailsUseCase,
    private val getGroupsUseCase: GetGroupsUseCase,
    private val addEntryUseCase: AddEntryUseCase,
    private val updateEntryUseCase: UpdateEntryUseCase,
    private val generatePasswordUseCase: GeneratePasswordUseCase,
    private val strengthEstimator: PasswordStrengthEstimator
) : ViewModel() {

    private val entryUuid: UUID? = savedStateHandle.get<String>("uuid")?.let(UUID::fromString)
    /** Group a new entry goes into: the one open in the list. */
    private val initialGroupUuid: UUID? = savedStateHandle.get<String>("group")?.let(UUID::fromString)

    private val _uiState = MutableStateFlow(
        EntryFormUiState(isEditMode = entryUuid != null, isLoading = entryUuid != null)
    )
    val uiState = _uiState.asStateFlow()

    private var nextFieldKey = 0L
    /** Whether the user touched the two-factor settings; untouched ones keep their stored format. */
    private var otpChanged = false
    private var expiryChanged = false

    private val _savedEvents = Channel<Unit>(Channel.BUFFERED)
    val savedEvents = _savedEvents.receiveAsFlow()

    init {
        loadGroups()
        if (entryUuid != null) {
            loadEntry(entryUuid)
        }
    }

    private fun loadGroups() {
        viewModelScope.launch {
            val groups = getGroupsUseCase().first()
            _uiState.update { state ->
                state.copy(
                    groups = groups,
                    groupUuid = state.groupUuid
                        ?: initialGroupUuid?.takeIf { uuid -> groups.any { it.uuid == uuid } }
                        ?: groups.firstOrNull()?.uuid
                )
            }
        }
    }

    fun onGroupSelected(groupUuid: UUID) = _uiState.update { it.copy(groupUuid = groupUuid) }

    private fun loadEntry(uuid: UUID) {
        viewModelScope.launch {
            getEntryDetailsUseCase(uuid)
                .onSuccess { entry ->
                    _uiState.update {
                        it.copy(
                            title = entry.title,
                            username = entry.username.orEmpty(),
                            password = entry.password.orEmpty(),
                            passwordStrength = entry.password?.takeIf { it.isNotEmpty() }?.let(strengthEstimator::estimate),
                            url = entry.url.orEmpty(),
                            notes = entry.notes.orEmpty(),
                            customFields = entry.customFields.map { field ->
                                CustomFieldDraft(nextFieldKey++, field.name, field.value, field.isProtected)
                            },
                            otp = entry.otp,
                            expires = entry.expires,
                            groupUuid = entry.groupUuid ?: it.groupUuid,
                            isLoading = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorStringId = R.string.entry_load_error) }
                }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, errorStringId = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update {
        it.copy(password = value, passwordStrength = value.takeIf { v -> v.isNotEmpty() }?.let(strengthEstimator::estimate))
    }

    fun onGeneratePassword() = onPasswordChange(generatePasswordUseCase(GENERATED_PASSWORD))
    fun onUrlChange(value: String) = _uiState.update { it.copy(url = value) }
    fun onNotesChange(value: String) = _uiState.update { it.copy(notes = value) }

    fun onOtpInputChange(value: String) = _uiState.update { it.copy(otpInput = value, otpInputError = false) }

    /** Takes a setup key or otpauth:// link that was typed, pasted or read from a QR code. */
    fun onOtpEntered(value: String = _uiState.value.otpInput) {
        val parsed = OtpParser.parse(value)
        if (parsed == null) {
            _uiState.update { it.copy(otpInput = value, otpInputError = true) }
            return
        }
        otpChanged = true
        _uiState.update { it.copy(otp = parsed, otpInput = "", otpInputError = false) }
    }

    fun onExpiryChange(expires: Instant?) {
        expiryChanged = true
        _uiState.update { it.copy(expires = expires) }
    }

    fun onRemoveOtp() {
        otpChanged = true
        _uiState.update { it.copy(otp = null) }
    }

    fun onAddCustomField() = _uiState.update {
        it.copy(customFields = it.customFields + CustomFieldDraft(key = nextFieldKey++))
    }

    fun onCustomFieldChange(field: CustomFieldDraft) = _uiState.update { state ->
        state.copy(customFields = state.customFields.map { if (it.key == field.key) field else it })
    }

    fun onRemoveCustomField(key: Long) = _uiState.update { state ->
        state.copy(customFields = state.customFields.filterNot { it.key == key })
    }

    fun onSave() {
        // A key typed without pressing its confirm button still counts; a bad one stops the
        // save so the user sees the error instead of losing the 2FA silently.
        if (_uiState.value.otpInput.isNotBlank()) {
            onOtpEntered()
            if (_uiState.value.otpInputError) return
        }
        val state = _uiState.value
        if (!state.canSave) return

        val data = EntryFormData(
            title = state.title.trim(),
            username = state.username.trim().ifBlank { null },
            password = state.password.ifBlank { null },
            url = state.url.trim().ifBlank { null },
            notes = state.notes.ifBlank { null },
            groupUuid = state.groupUuid,
            customFields = state.customFields
                .filter { it.name.isNotBlank() }
                .map { CustomField(it.name.trim(), it.value, it.isProtected) },
            otpUri = if (otpChanged) state.otp?.let(OtpParser::toUri).orEmpty() else null,
            expiry = if (expiryChanged) Expiry(state.expires) else null
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorStringId = null) }

            val result = if (entryUuid != null) {
                updateEntryUseCase(entryUuid, data)
            } else {
                addEntryUseCase(data)
            }

            result
                .onSuccess { _savedEvents.trySend(Unit) }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorStringId = error.saveErrorMessage()) }
                }
        }
    }

    private companion object {
        /** What "generate" in the form produces: long, every character class. */
        val GENERATED_PASSWORD = PasswordOptions(length = 20, useSymbols = true)
    }
}
