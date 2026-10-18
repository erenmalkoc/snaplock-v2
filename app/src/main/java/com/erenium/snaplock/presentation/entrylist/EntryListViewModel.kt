package com.erenium.snaplock.presentation.entrylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.usecase.CreateGroupUseCase
import com.erenium.snaplock.domain.usecase.DeleteGroupUseCase
import com.erenium.snaplock.domain.usecase.GetEntriesUseCase
import com.erenium.snaplock.domain.usecase.GetGroupsUseCase
import com.erenium.snaplock.domain.usecase.RenameGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val deleteGroupUseCase: DeleteGroupUseCase
) : ViewModel() {

    private val _query = MutableStateFlow("")
    /** Null means the root group. */
    private val _currentGroupUuid = MutableStateFlow<UUID?>(null)
    private val _errorStringId = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<EntryListUiState> =
        combine(
            getEntriesUseCase(),
            getGroupsUseCase(),
            _query,
            _currentGroupUuid,
            _errorStringId
        ) { entries, groups, query, currentUuid, error ->
            val root = groups.firstOrNull { it.parentUuid == null }
            // A group that disappeared (deleted elsewhere) falls back to the root.
            val current = groups.firstOrNull { it.uuid == currentUuid } ?: root
            val (visibleEntries, visibleGroups) = if (query.isBlank()) {
                entries.filter { it.groupUuid == current?.uuid } to
                    groups.filter { it.parentUuid != null && it.parentUuid == current?.uuid }
            } else {
                entries.filter { it.matches(query) } to
                    groups.filter { it.parentUuid != null && it.name.contains(query, ignoreCase = true) }
            }
            EntryListUiState(
                entries = visibleEntries.sortedBy { it.title.lowercase() },
                subgroups = visibleGroups.sortedBy { it.name.lowercase() },
                currentGroup = current,
                query = query,
                isLoading = false,
                errorStringId = error
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

    /** Goes to the parent group; returns false when already at the root. */
    fun onNavigateUp(): Boolean {
        val parent = uiState.value.currentGroup?.parentUuid ?: return false
        _currentGroupUuid.value = parent
        return true
    }

    fun onCreateGroup(name: String) {
        val parent = uiState.value.currentGroup?.uuid ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            createGroupUseCase(parent, name).onFailure { _errorStringId.value = R.string.entry_save_error }
        }
    }

    fun onRenameCurrentGroup(name: String) {
        val current = uiState.value.currentGroup ?: return
        if (name.isBlank() || current.parentUuid == null) return
        viewModelScope.launch {
            renameGroupUseCase(current.uuid, name).onFailure { _errorStringId.value = R.string.entry_save_error }
        }
    }

    fun onDeleteCurrentGroup() {
        val current = uiState.value.currentGroup ?: return
        val parent = current.parentUuid ?: return
        viewModelScope.launch {
            deleteGroupUseCase(current.uuid)
                .onSuccess { _currentGroupUuid.value = parent }
                .onFailure { _errorStringId.value = R.string.entry_delete_error }
        }
    }

    fun onErrorShown() {
        _errorStringId.value = null
    }

    private fun Entry.matches(query: String): Boolean =
        title.contains(query, ignoreCase = true) ||
            username?.contains(query, ignoreCase = true) == true ||
            url?.contains(query, ignoreCase = true) == true ||
            notes?.contains(query, ignoreCase = true) == true
}
