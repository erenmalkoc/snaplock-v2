package com.erenium.snaplock.domain.model

import java.util.UUID

data class Group(
    val uuid: UUID,
    val name: String,
    val entryCount: Int
)
