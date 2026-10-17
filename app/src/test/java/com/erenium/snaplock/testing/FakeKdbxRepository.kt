package com.erenium.snaplock.testing

import android.net.Uri
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** In-memory [KdbxRepository] for tests; every entry lives in one root group. */
class FakeKdbxRepository : KdbxRepository {
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val locked = MutableStateFlow(true)
    val entryDetails = mutableMapOf<UUID, EntryDetail>()
    val credentials = MutableStateFlow<List<Credential>>(emptyList())

    override suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit> {
        locked.value = false
        return Result.success(Unit)
    }

    override suspend fun createDatabase(uri: Uri, password: CharSequence): Result<Unit> {
        locked.value = false
        return Result.success(Unit)
    }

    override suspend fun lockDatabase() {
        locked.value = true
    }

    override fun isLocked(): Flow<Boolean> = locked

    override fun getEntries(): Flow<List<Entry>> = entries

    override fun getGroups(): Flow<List<Group>> =
        MutableStateFlow(listOf(Group(ROOT_GROUP_UUID, "Passwords", entries.value.size)))

    override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
        return entryDetails[uuid]?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException(uuid.toString()))
    }

    override suspend fun getCredentials(): List<Credential> =
        if (locked.value) emptyList() else credentials.value

    override suspend fun addEntry(data: EntryFormData): Result<Unit> {
        val uuid = UUID.randomUUID()
        entryDetails[uuid] = EntryDetail(uuid, data.title, data.username, data.password, data.url, data.notes, ROOT_GROUP_UUID)
        entries.value = entries.value + Entry(uuid, data.title, data.username, ROOT_GROUP_UUID, "Passwords")
        return Result.success(Unit)
    }

    override suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit> {
        entryDetails[uuid] = EntryDetail(uuid, data.title, data.username, data.password, data.url, data.notes, ROOT_GROUP_UUID)
        entries.value = entries.value.map {
            if (it.uuid == uuid) Entry(uuid, data.title, data.username, ROOT_GROUP_UUID, "Passwords") else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteEntry(uuid: UUID): Result<Unit> {
        entryDetails.remove(uuid)
        entries.value = entries.value.filterNot { it.uuid == uuid }
        return Result.success(Unit)
    }

    companion object {
        val ROOT_GROUP_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000ff")
    }
}
