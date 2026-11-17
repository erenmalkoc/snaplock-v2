package com.erenium.snaplock.domain.model

import java.time.Instant

data class EntryVersion(
    val index: Int,
    val modified: Instant?,
    val title: String,
    val username: String?,
    val password: String?,
    val url: String? = null,
    val notes: String? = null,
    val customFields: List<CustomField> = emptyList()
)
