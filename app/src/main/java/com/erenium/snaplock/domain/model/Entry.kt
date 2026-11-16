package com.erenium.snaplock.domain.model

import java.time.Instant
import java.util.UUID

data class Entry(
    val uuid: UUID,
    val title: String,
    val username: String?,
    val groupUuid: UUID,
    val groupName: String,
    val url: String? = null,
    val notes: String? = null,
    val icon: EntryIcon? = null,
    val deletedAt: Instant? = null,
    val previousGroupUuid: UUID? = null
)
