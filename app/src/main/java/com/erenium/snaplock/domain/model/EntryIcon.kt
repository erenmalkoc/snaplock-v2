package com.erenium.snaplock.domain.model

import java.util.UUID

class EntryIcon(val id: UUID, val data: ByteArray) {
    override fun equals(other: Any?): Boolean = other is EntryIcon && other.id == id && other.data.contentEquals(data)

    override fun hashCode(): Int = id.hashCode()
}
