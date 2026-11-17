package com.erenium.snaplock.autofill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.RecentDatabase
import com.erenium.snaplock.domain.repository.KdbxRepository
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SaveOutcome { SAVED, NOTHING_TO_SAVE }

data class AutofillSaveUiState(
    val isLoading: Boolean = true,
    val isLocked: Boolean = true,
    val database: RecentDatabase? = null,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val decision: SaveDecision? = null,
    val isSaving: Boolean = false,
    val errorStringId: Int? = null,
    val loadFailed: Boolean = false,
    val hasEdits: Boolean = false
)

@HiltViewModel
class AutofillSaveViewModel @Inject constructor(
    private val repository: KdbxRepository,
    recentDatabasesRepository: RecentDatabasesRepository
) : ViewModel() {
    private data class Form(
        val title: String = "",
        val username: String = "",
        val password: String = "",
        val decision: SaveDecision? = null,
        val isSaving: Boolean = false,
        val errorStringId: Int? = null,
        val loadFailed: Boolean = false
    )

    private var initial = Form()
    private var packageName = ""
    private var webDomain: String? = null
    private var typedUsername: String? = null
    private var started = false

    val isStarted: Boolean get() = started
    private val form = MutableStateFlow(Form())
    private val finished = Channel<SaveOutcome>(Channel.CONFLATED)

    val done: Flow<SaveOutcome> = finished.receiveAsFlow()

    val uiState: StateFlow<AutofillSaveUiState> = combine(
        repository.isLocked(),
        recentDatabasesRepository.recentDatabases.map { it.firstOrNull() },
        form
    ) { isLocked, database, form ->
        AutofillSaveUiState(
            isLoading = false,
            isLocked = isLocked,
            database = database,
            title = form.title,
            username = form.username,
            password = form.password,
            decision = form.decision,
            isSaving = form.isSaving,
            errorStringId = form.errorStringId,
            loadFailed = form.loadFailed,
            hasEdits = form.title != initial.title || form.username != initial.username || form.password != initial.password
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutofillSaveUiState())

    fun start(packageName: String, webDomain: String?, title: String, username: String?, password: String) {
        if (started) return
        started = true
        this.packageName = packageName
        this.webDomain = webDomain
        initial = Form(title = title, username = username.orEmpty(), password = password)
        form.value = initial
        this.typedUsername = username
        viewModelScope.launch {
            repository.isLocked().collect { isLocked ->
                if (!isLocked) decide()
            }
        }
    }

    fun onRetry() {
        form.update { it.copy(loadFailed = false) }
        viewModelScope.launch { decide() }
    }

    private suspend fun decide() {
        runCatching {
            SavePlanner.decide(repository.getCredentials(), packageName, webDomain, typedUsername, initial.password)
        }
            .onSuccess { decision ->
                form.update { it.copy(decision = decision, loadFailed = false) }
                if (decision == SaveDecision.AlreadySaved) finished.send(SaveOutcome.NOTHING_TO_SAVE)
            }
            .onFailure { form.update { it.copy(loadFailed = true) } }
    }

    fun onTitleChange(value: String) = form.update { it.copy(title = value, errorStringId = null) }

    fun onUsernameChange(value: String) = form.update { it.copy(username = value, errorStringId = null) }

    fun onPasswordChange(value: String) = form.update { it.copy(password = value, errorStringId = null) }

    fun onSave(asNew: Boolean = false) {
        val current = form.value
        if (current.isSaving || current.password.isEmpty()) return
        form.update { it.copy(isSaving = true, errorStringId = null) }
        viewModelScope.launch {
            val decision = current.decision
            val result = if (!asNew && decision is SaveDecision.Update) {
                repository.getEntryByUuid(decision.credential.uuid).fold(
                    onSuccess = { existing ->
                        repository.updateEntry(
                            existing.uuid,
                            EntryFormData(
                                title = existing.title,
                                username = current.username.ifEmpty { null },
                                password = current.password,
                                url = existing.url,
                                notes = existing.notes,
                                groupUuid = existing.groupUuid
                            )
                        )
                    },
                    onFailure = { Result.failure(it) }
                )
            } else {
                repository.addEntry(
                    EntryFormData(
                        title = current.title.ifBlank { packageName },
                        username = current.username.ifEmpty { null },
                        password = current.password,
                        url = SavePlanner.urlFor(packageName, webDomain),
                        notes = null,
                        groupUuid = null
                    )
                )
            }
            result
                .onSuccess { finished.send(SaveOutcome.SAVED) }
                .onFailure { error -> form.update { it.copy(isSaving = false, errorStringId = error.saveErrorMessage()) } }
        }
    }
}
