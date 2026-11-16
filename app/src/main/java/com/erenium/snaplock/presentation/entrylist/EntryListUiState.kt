package com.erenium.snaplock.presentation.entrylist

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import java.util.UUID

enum class SearchMatch { TITLE, USERNAME, URL, NOTES }

data class EntryListUiState(
    /** Entries of the open group, or search results across the whole vault. */
    val entries: List<Entry> = emptyList(),
    /** Child groups of the open group, or groups whose name matches the search. */
    val subgroups: List<Group> = emptyList(),
    /** The open group; null until the vault has loaded. */
    val currentGroup: Group? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val errorStringId: Int? = null,
    val matches: Map<UUID, SearchMatch> = emptyMap(),
    val breadcrumb: List<Group> = emptyList(),
    val recycleBinEnabled: Boolean = true
) {
    val isEmpty: Boolean get() = entries.isEmpty() && subgroups.isEmpty()
    val isSearching: Boolean get() = query.isNotBlank()
    val isRoot: Boolean get() = currentGroup?.parentUuid == null
}
