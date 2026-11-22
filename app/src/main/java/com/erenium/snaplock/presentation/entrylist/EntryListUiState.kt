package com.erenium.snaplock.presentation.entrylist

import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.search.SearchMatch
import java.util.UUID

data class EntryListUiState(
    val entries: List<Entry> = emptyList(),
    val subgroups: List<Group> = emptyList(),
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
