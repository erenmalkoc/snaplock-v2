package com.erenium.snaplock.domain.model

import java.util.UUID

data class EntryDetail(
    val uuid: UUID,
    val title: String,
    val username: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val groupUuid: UUID?,
    val customFields: List<CustomField> = emptyList(),
    /** Previous versions, newest first. */
    val history: List<EntryVersion> = emptyList()
)
