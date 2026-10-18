package com.erenium.snaplock.domain.model

import java.time.Instant

/** A previous version of an entry, kept in its KeePass history. */
data class EntryVersion(
    /** Position in the entry's history, oldest first; used to restore it. */
    val index: Int,
    val modified: Instant?,
    val title: String,
    val username: String?,
    val password: String?
)
