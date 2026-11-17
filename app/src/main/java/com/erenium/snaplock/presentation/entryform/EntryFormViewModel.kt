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

data class CustomFieldDraft(
    val key: Long,
    val name: String = "",
    val value: String = "",
    val isProtected: Boolean = false
)

data class EntryFormUiState(
    val title: String = "",
    val titleError: Boolean = false,
    val username: String = "",
    val password: String = "",
    val passwordStrength: StrengthEstimate? = null,
    val passwordGenerated: Boolean = false,
    val url: String = "",
    val notes: String = "",
    val customFields: List<CustomFieldDraft> = emptyList(),
    val otp: OtpParameters? = null,
    val otpInput: String = "",
    val otpInputError: Boolean = false,
    val expires: Instant? = null,
    val groups: List<Group> = emptyList(),
    val groupPaths: Map<UUID, String> = emptyMap(),
    val groupUuid: UUID? = null,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false
) {
    val canSave: Boolean get() = title.isNotBlank() && !isSaving && !isLoading && !loadFailed
}

private data class FormSnapshot(
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val fields: List<Triple<String, String, Boolean>>,
    val otp: OtpParameters?,
    val otpInput: String,
    val expires: Instant?,
    val groupUuid: UUID?
)

private fun EntryFormUiState.snapshot() = FormSnapshot(
    title, username, password, url, notes,
    customFields.map { Triple(it.name, it.value, it.isProtected) },
    otp, otpInput, expires, groupUuid
)

fun groupPaths(groups: List<Group>): Map<UUID, String> {
    val byId = groups.associateBy { it.uuid }
    return groups.associate { group ->
        val names = mutableListOf(group.name)
        var parent = group.parentUuid?.let(byId::get)
        while (parent != null && parent.parentUuid != null && names.size <= groups.size) {
            names.add(0, parent.name)
            parent = parent.parentUuid?.let(byId::get)
        }
        group.uuid to names.joinToString(" / ")
    }
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
    private val initialGroupUuid: UUID? = savedStateHandle.get<String>("group")?.let(UUID::fromString)

    private val _uiState = MutableStateFlow(
        EntryFormUiState(isEditMode = entryUuid != null, isLoading = entryUuid != null)
    )
    val uiState = _uiState.asStateFlow()

    private var baseline = _uiState.value.snapshot()
    private var nextFieldKey = 0L
    private var otpChanged = false
    private var expiryChanged = false

    private val _savedEvents = Channel<Unit>(Channel.BUFFERED)
    val savedEvents = _savedEvents.receiveAsFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        loadGroups()
        if (entryUuid != null) {
            loadEntry(entryUuid)
        }
    }

    private fun edit(change: (EntryFormUiState) -> EntryFormUiState) = _uiState.update { state ->
        val next = change(state)
        next.copy(isDirty = next.snapshot() != baseline)
    }

    private fun loadGroups() {
        viewModelScope.launch {
            val groups = getGroupsUseCase().first()
            _uiState.update { state ->
                val chosen = state.groupUuid
                    ?: initialGroupUuid?.takeIf { uuid -> groups.any { it.uuid == uuid } }
                    ?: groups.firstOrNull()?.uuid
                if (baseline.groupUuid == null) baseline = baseline.copy(groupUuid = chosen)
                val next = state.copy(groups = groups, groupPaths = groupPaths(groups), groupUuid = chosen)
                next.copy(isDirty = next.snapshot() != baseline)
            }
        }
    }

    fun onGroupSelected(groupUuid: UUID) = edit { it.copy(groupUuid = groupUuid) }

    private fun loadEntry(uuid: UUID) {
        viewModelScope.launch {
            getEntryDetailsUseCase(uuid)
                .onSuccess { entry ->
                    _uiState.update {
                        val loaded = it.copy(
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
                            isLoading = false,
                            loadFailed = false
                        )
                        baseline = loaded.snapshot()
                        loaded.copy(isDirty = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    _messages.trySend(R.string.entry_load_error)
                }
        }
    }

    fun onRetryLoad() {
        val uuid = entryUuid ?: return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        loadEntry(uuid)
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value, titleError = value.isBlank()) }
    fun onUsernameChange(value: String) = edit { it.copy(username = value) }
    fun onPasswordChange(value: String) = edit {
        it.copy(
            password = value,
            passwordStrength = value.takeIf { v -> v.isNotEmpty() }?.let(strengthEstimator::estimate),
            passwordGenerated = false
        )
    }

    fun onGeneratePassword() {
        onPasswordChange(generatePasswordUseCase(GENERATED_PASSWORD))
        _uiState.update { it.copy(passwordGenerated = true) }
    }

    fun onUrlChange(value: String) = edit { it.copy(url = value) }
    fun onNotesChange(value: String) = edit { it.copy(notes = value) }

    fun onOtpInputChange(value: String) = edit { it.copy(otpInput = value, otpInputError = false) }

    fun onOtpEntered(value: String = _uiState.value.otpInput) {
        val parsed = OtpParser.parse(value)
        if (parsed == null) {
            edit { it.copy(otpInput = value, otpInputError = true) }
            return
        }
        otpChanged = true
        edit { it.copy(otp = parsed, otpInput = "", otpInputError = false) }
    }

    fun onExpiryChange(expires: Instant?) {
        expiryChanged = true
        edit { it.copy(expires = expires) }
    }

    fun onRemoveOtp() {
        otpChanged = true
        edit { it.copy(otp = null) }
    }

    fun onAddCustomField() = edit {
        it.copy(customFields = it.customFields + CustomFieldDraft(key = nextFieldKey++))
    }

    fun onCustomFieldChange(field: CustomFieldDraft) = edit { state ->
        state.copy(customFields = state.customFields.map { if (it.key == field.key) field else it })
    }

    fun onRemoveCustomField(key: Long) = edit { state ->
        state.copy(customFields = state.customFields.filterNot { it.key == key })
    }

    fun onSave() {
        if (_uiState.value.otpInput.isNotBlank()) {
            onOtpEntered()
            if (_uiState.value.otpInputError) return
        }
        val state = _uiState.value
        if (!state.canSave) return

        val names = state.customFields.map { it.name.trim() }.filter { it.isNotEmpty() }
        if (names.size != names.toSet().size || names.any { it in RESERVED_FIELD_NAMES }) {
            _messages.trySend(R.string.entry_form_duplicate_field)
            return
        }

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
            _uiState.update { it.copy(isSaving = true) }

            val result = if (entryUuid != null) {
                updateEntryUseCase(entryUuid, data)
            } else {
                addEntryUseCase(data)
            }

            result
                .onSuccess { _savedEvents.trySend(Unit) }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    _messages.trySend(error.saveErrorMessage())
                }
        }
    }

    private companion object {
        val GENERATED_PASSWORD = PasswordOptions(length = 20, useSymbols = true)

        val RESERVED_FIELD_NAMES = setOf("Title", "UserName", "Password", "URL", "Notes")
    }
}
