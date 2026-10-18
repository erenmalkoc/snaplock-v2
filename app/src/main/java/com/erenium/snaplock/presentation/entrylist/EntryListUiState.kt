package com.erenium.snaplock.presentation.entrylist

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group

data class EntryListUiState(
    /** Entries of the open group, or search results across the whole vault. */
    val entries: List<Entry> = emptyList(),
    /** Child groups of the open group, or groups whose name matches the search. */
    val subgroups: List<Group> = emptyList(),
    /** The open group; null until the vault has loaded. */
    val currentGroup: Group? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val errorStringId: Int? = null
) {
    val isEmpty: Boolean get() = entries.isEmpty() && subgroups.isEmpty()
    val isSearching: Boolean get() = query.isNotBlank()
    val isRoot: Boolean get() = currentGroup?.parentUuid == null
}
