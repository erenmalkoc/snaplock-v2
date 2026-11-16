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
    /** When the entry was moved to the recycle bin; only set for entries in it. */
    val deletedAt: Instant? = null,
    /** The group the entry was in before it was recycled, if the vault recorded it. */
    val previousGroupUuid: UUID? = null
)
