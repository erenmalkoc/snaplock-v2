package com.erenium.snaplock.autofill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.RecentDatabase
import com.erenium.snaplock.domain.repository.KdbxRepository
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

data class AutofillSaveUiState(
    val isLoading: Boolean = true,
    val isLocked: Boolean = true,
    val database: RecentDatabase? = null,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    /** Null until the vault is open and compared with the typed login. */
    val decision: SaveDecision? = null,
    val isSaving: Boolean = false,
    val errorStringId: Int? = null
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
        val errorStringId: Int? = null
    )

    private var packageName = ""
    private var webDomain: String? = null
    private var started = false
    private val form = MutableStateFlow(Form())
    private val finished = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the login is stored, or when there was nothing to store. */
    val done: Flow<Unit> = finished.receiveAsFlow()

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
            errorStringId = form.errorStringId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutofillSaveUiState())

    fun start(packageName: String, webDomain: String?, title: String, username: String?, password: String) {
        if (started) return
        started = true
        this.packageName = packageName
        this.webDomain = webDomain
        form.value = Form(title = title, username = username.orEmpty(), password = password)
        viewModelScope.launch {
            repository.isLocked().collect { isLocked ->
                if (isLocked) return@collect
                val decision = SavePlanner.decide(
                    repository.getCredentials(), packageName, webDomain, username, password
                )
                form.update { it.copy(decision = decision) }
                if (decision == SaveDecision.AlreadySaved) finished.send(Unit)
            }
        }
    }

    fun onTitleChange(value: String) = form.update { it.copy(title = value, errorStringId = null) }

    fun onUsernameChange(value: String) = form.update { it.copy(username = value, errorStringId = null) }

    fun onPasswordChange(value: String) = form.update { it.copy(password = value, errorStringId = null) }

    /** @param asNew store a new entry even when an existing one could be updated. */
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
                .onSuccess { finished.send(Unit) }
                .onFailure { form.update { it.copy(isSaving = false, errorStringId = R.string.entry_save_error) } }
        }
    }
}
