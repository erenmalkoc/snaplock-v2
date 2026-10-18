package com.erenium.snaplock.domain.model

import java.util.UUID

/**
 * A KeePass custom icon: an image stored in the vault and shared by the entries that use
 * it. KeePassXC writes these when it downloads site icons.
 */
class EntryIcon(val id: UUID, val data: ByteArray) {
    override fun equals(other: Any?): Boolean = other is EntryIcon && other.id == id && other.data.contentEquals(data)

    override fun hashCode(): Int = id.hashCode()
}
