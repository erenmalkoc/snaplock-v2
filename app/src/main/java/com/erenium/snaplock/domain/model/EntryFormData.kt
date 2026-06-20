package com.erenium.snaplock.domain.model

import java.util.UUID

data class EntryFormData(
    val title: String,
    val username: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val groupUuid: UUID?
)
