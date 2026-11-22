package com.erenium.snaplock.presentation.entrylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.search.EntrySearch
import com.erenium.snaplock.domain.text.localeComparator
import com.erenium.snaplock.domain.usecase.CreateGroupUseCase
import com.erenium.snaplock.domain.usecase.DeleteGroupUseCase
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.domain.usecase.GetRecycleBinEnabledUseCase
import com.erenium.snaplock.domain.usecase.RenameGroupUseCase
import com.erenium.snaplock.domain.usecase.RestoreEntryUseCase
import com.erenium.snaplock.presentation.RecentlyDeleted
import com.erenium.snaplock.presentation.saveErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EntryListViewModel @Inject constructor(
    getEntriesUseCase: GetEntriesUseCase,
    getGroupsUseCase: GetGroupsUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val restoreEntryUseCase: RestoreEntryUseCase,
    private val getRecycleBinEnabled: GetRecycleBinEnabledUseCase,
    recentlyDeleted: RecentlyDeleted
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _currentGroupUuid = MutableStateFlow<UUID?>(null)
    private val _errorStringId = MutableStateFlow<Int?>(null)

    private val _messages = Channel<Pair<Int, String?>>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val deletedEntries = recentlyDeleted.events

    // The text of every entry is prepared once per vault change, not once per keystroke.
    private val searchIndex = getEntriesUseCase().map { EntrySearch.index(it) }

    val uiState: StateFlow<EntryListUiState> =
        combine(
            searchIndex,
            getGroupsUseCase(),
            _query,
            _currentGroupUuid,
            _errorStringId
        ) { index, groups, query, currentUuid, error ->
            val root = groups.firstOrNull { it.parentUuid == null }
            val current = groups.firstOrNull { it.uuid == currentUuid } ?: root
            val searching = query.isNotBlank()
            val text = localeComparator()
            val hits = if (searching) index.search(query, text) else emptyList()
            val (visibleEntries, visibleGroups) = if (!searching) {
                index.entries.filter { it.groupUuid == current?.uuid }.sortedWith(compareBy(text) { it.title }) to
                    groups.filter { it.parentUuid != null && it.parentUuid == current?.uuid }
            } else {
                hits.map { it.entry } to
                    groups.filter { it.parentUuid != null && EntrySearch.matches(query, it.name) }
            }
            EntryListUiState(
                entries = visibleEntries,
                subgroups = visibleGroups.sortedWith(compareBy(text) { it.name }),
                currentGroup = current,
                query = query,
                isLoading = false,
                errorStringId = error,
                matches = hits.associate { it.entry.uuid to it.match },
                breadcrumb = breadcrumbOf(current, groups),
                recycleBinEnabled = getRecycleBinEnabled()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EntryListUiState()
        )

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onOpenGroup(uuid: UUID) {
        _query.value = ""
        _currentGroupUuid.value = uuid
    }

    fun onNavigateUp(): Boolean {
        val parent = uiState.value.currentGroup?.parentUuid ?: return false
        _currentGroupUuid.value = parent
        return true
    }

    fun onBack(): Boolean {
        if (_query.value.isNotBlank()) {
            _query.value = ""
            return true
        }
        return onNavigateUp()
    }

    fun onCreateGroup(name: String) {
        val parent = uiState.value.currentGroup?.uuid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            createGroupUseCase(parent, name)
                .onSuccess { message(R.string.group_created, name.trim()) }
                .onFailure { _errorStringId.value = it.saveErrorMessage() }
        }
    }

    fun onRenameCurrentGroup(name: String) {
        val current = uiState.value.currentGroup ?: return
        if (name.isBlank() || current.parentUuid == null) return
        viewModelScope.launch {
            renameGroupUseCase(current.uuid, name)
                .onSuccess { message(R.string.group_renamed, name.trim()) }
                .onFailure { _errorStringId.value = it.saveErrorMessage() }
        }
    }

    fun onDeleteCurrentGroup() {
        val current = uiState.value.currentGroup ?: return
        val parent = current.parentUuid ?: return
        viewModelScope.launch {
            val toBin = getRecycleBinEnabled()
            deleteGroupUseCase(current.uuid)
                .onSuccess {
                    _currentGroupUuid.value = parent
                    message(if (toBin) R.string.group_moved_to_bin else R.string.group_deleted, current.name)
                }
                .onFailure { _errorStringId.value = it.saveErrorMessage(R.string.entry_delete_error) }
        }
    }

    fun onUndoDelete(uuid: UUID) {
        viewModelScope.launch {
            restoreEntryUseCase(uuid).onFailure { _errorStringId.value = it.saveErrorMessage() }
        }
    }

    fun onErrorShown() {
        _errorStringId.value = null
    }

    private fun message(resId: Int, argument: String? = null) {
        _messages.trySend(resId to argument)
    }

    private fun breadcrumbOf(current: Group?, groups: List<Group>): List<Group> {
        val byId = groups.associateBy { it.uuid }
        val path = mutableListOf<Group>()
        var parent = current?.parentUuid?.let(byId::get)
        while (parent != null && path.size <= groups.size) {
            path.add(0, parent)
            parent = parent.parentUuid?.let(byId::get)
        }
        return path
    }
}
