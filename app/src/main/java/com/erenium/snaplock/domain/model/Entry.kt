package com.erenium.snaplock.domain.model

import java.util.UUID

data class Entry(
    val uuid: UUID,
    val title: String,
    val username: String?
)