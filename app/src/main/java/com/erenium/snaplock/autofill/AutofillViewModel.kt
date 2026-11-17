package com.erenium.snaplock.autofill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.data.repository.RecentDatabasesRepository
import com.erenium.snaplock.domain.model.Credential
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutofillUiState(
    val isLoading: Boolean = true,
    val isLocked: Boolean = true,
    val database: RecentDatabase? = null,
    val matches: List<Credential> = emptyList(),
    val others: List<Credential> = emptyList(),
    val query: String = "",
    val linkPicked: Boolean = false,
    val isVaultEmpty: Boolean = false,
    val loadFailed: Boolean = false
)

@HiltViewModel
class AutofillViewModel @Inject constructor(
    private val repository: KdbxRepository,
    recentDatabasesRepository: RecentDatabasesRepository
) : ViewModel() {
    private var packageName = ""
    private var webDomain: String? = null
    private var started = false
    private var fillSingleMatch = false

    private data class Loaded(
        val credentials: List<Credential> = emptyList(),
        val done: Boolean = false,
        val failed: Boolean = false
    )

    private val query = MutableStateFlow("")
    private val linkPicked = MutableStateFlow(false)
    private val loaded = MutableStateFlow(Loaded())
    private val picks = Channel<Credential>(Channel.BUFFERED)

    val picked: Flow<Credential> = picks.receiveAsFlow()

    val uiState: StateFlow<AutofillUiState> = combine(
        repository.isLocked(),
        recentDatabasesRepository.recentDatabases.map { it.firstOrNull() },
        loaded,
        query,
        linkPicked
    ) { isLocked, database, loaded, query, linkPicked ->
        val all = loaded.credentials
        val matches = CredentialMatcher.match(all, packageName, webDomain)
        val others = all.filterNot { it in matches }.sortedBy { it.title.lowercase() }
        AutofillUiState(
            isLoading = !isLocked && !loaded.done,
            isLocked = isLocked,
            database = database,
            matches = matches.filter { it.matchesQuery(query) },
            others = others.filter { it.matchesQuery(query) },
            query = query,
            linkPicked = linkPicked,
            isVaultEmpty = loaded.done && !loaded.failed && all.isEmpty(),
            loadFailed = loaded.failed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutofillUiState())

    fun start(packageName: String, webDomain: String?, fillSingleMatch: Boolean) {
        if (started) return
        started = true
        this.packageName = packageName
        this.webDomain = webDomain
        this.fillSingleMatch = fillSingleMatch
        viewModelScope.launch {
            repository.isLocked().collect { isLocked ->
                if (isLocked) {
                    loaded.value = Loaded()
                } else {
                    load()
                }
            }
        }
    }

    fun onRetry() {
        loaded.value = Loaded()
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        runCatching { repository.getCredentials() }
            .onSuccess { all ->
                loaded.value = Loaded(all, done = true)
                val matches = CredentialMatcher.match(all, packageName, webDomain)
                if (fillSingleMatch && matches.size == 1) picks.send(matches.single())
            }
            .onFailure { loaded.value = Loaded(done = true, failed = true) }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onLinkPickedChange(value: Boolean) {
        linkPicked.value = value
    }

    fun onPick(credential: Credential) {
        viewModelScope.launch {
            val isMatch = credential in CredentialMatcher.match(loaded.value.credentials, packageName, webDomain)
            if (linkPicked.value && !isMatch) {
                val domain = webDomain?.let(CredentialMatcher::hostOf)
                if (domain != null && Browsers.isTrusted(packageName)) {
                    repository.linkToSite(credential.uuid, domain)
                } else {
                    repository.linkToApp(credential.uuid, packageName)
                }
            }
            picks.send(credential)
        }
    }

    private fun Credential.matchesQuery(query: String): Boolean =
        query.isBlank() ||
            title.contains(query, ignoreCase = true) ||
            username?.contains(query, ignoreCase = true) == true ||
            urls.any { it.contains(query, ignoreCase = true) }
}
