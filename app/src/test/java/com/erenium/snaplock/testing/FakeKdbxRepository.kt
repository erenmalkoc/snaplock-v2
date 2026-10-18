package com.erenium.snaplock.testing

import android.net.Uri
import com.erenium.snaplock.domain.importer.ExportedEntry
import com.erenium.snaplock.domain.importer.ImportedEntry
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.passwords.AuditEntry
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
    val recycled = MutableStateFlow<List<Entry>>(emptyList())
    /** Attachment contents by entry and file name. */
    val attachments = mutableMapOf<UUID, MutableMap<String, ByteArray>>()
    /** Key file passed to the last unlockDatabase call. */
    var lastKeyFile: ByteArray? = null
    val groups = MutableStateFlow(listOf(Group(ROOT_GROUP_UUID, "Passwords", 0)))
    /** Every form passed to addEntry or updateEntry, in order. */
    val savedForms = mutableListOf<EntryFormData>()
    /** How many times reloadIfChanged was called. */
    var reloads = 0

    override suspend fun unlockDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray?): Result<Unit> {
        lastKeyFile = keyFile
        locked.value = false
        return Result.success(Unit)
    }

    override suspend fun createDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray?): Result<Unit> {
        locked.value = false
        return Result.success(Unit)
    }

    override suspend fun changeCredentials(password: CharSequence, keyFile: ByteArray?): Result<Unit> =
        Result.success(Unit)

    override suspend fun lockDatabase() {
        locked.value = true
    }

    override fun isLocked(): Flow<Boolean> = locked

    override fun getEntries(): Flow<List<Entry>> = entries

    override fun getGroups(): Flow<List<Group>> = groups

    override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
        return entryDetails[uuid]?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException(uuid.toString()))
    }

    val auditEntries = MutableStateFlow<List<AuditEntry>>(emptyList())

    override suspend fun getAuditEntries(): List<AuditEntry> = auditEntries.value

    override suspend fun getCredentials(): List<Credential> =
        if (locked.value) emptyList() else credentials.value

    override suspend fun addEntry(data: EntryFormData): Result<Unit> {
        savedForms += data
        val uuid = UUID.randomUUID()
        entryDetails[uuid] = EntryDetail(uuid, data.title, data.username, data.password, data.url, data.notes, ROOT_GROUP_UUID)
        entries.value = entries.value + Entry(uuid, data.title, data.username, ROOT_GROUP_UUID, "Passwords")
        return Result.success(Unit)
    }

    override suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit> {
        savedForms += data
        entryDetails[uuid] = EntryDetail(uuid, data.title, data.username, data.password, data.url, data.notes, ROOT_GROUP_UUID)
        entries.value = entries.value.map {
            if (it.uuid == uuid) Entry(uuid, data.title, data.username, ROOT_GROUP_UUID, "Passwords") else it
        }
        return Result.success(Unit)
    }

    override suspend fun deleteEntry(uuid: UUID): Result<Unit> {
        val live = entries.value.firstOrNull { it.uuid == uuid }
        if (live != null) {
            entries.value = entries.value - live
            recycled.value = recycled.value + live
        } else {
            entryDetails.remove(uuid)
            recycled.value = recycled.value.filterNot { it.uuid == uuid }
        }
        return Result.success(Unit)
    }

    override fun getRecycledEntries(): Flow<List<Entry>> = recycled

    override suspend fun restoreEntry(uuid: UUID): Result<Unit> {
        val entry = recycled.value.firstOrNull { it.uuid == uuid }
            ?: return Result.failure(NoSuchElementException(uuid.toString()))
        recycled.value = recycled.value - entry
        entries.value = entries.value + entry
        return Result.success(Unit)
    }

    override suspend fun emptyRecycleBin(): Result<Unit> {
        recycled.value.forEach { entryDetails.remove(it.uuid) }
        recycled.value = emptyList()
        return Result.success(Unit)
    }

    override suspend fun restoreVersion(uuid: UUID, index: Int): Result<Unit> = Result.success(Unit)

    override suspend fun addAttachment(uuid: UUID, name: String, content: ByteArray): Result<Unit> {
        attachments.getOrPut(uuid) { mutableMapOf() }[name] = content
        return Result.success(Unit)
    }

    override suspend fun getAttachment(uuid: UUID, name: String): Result<ByteArray> =
        attachments[uuid]?.get(name)?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException(name))

    override suspend fun removeAttachment(uuid: UUID, name: String): Result<Unit> {
        attachments[uuid]?.remove(name)
        return Result.success(Unit)
    }

    val imported = mutableListOf<Pair<String, List<ImportedEntry>>>()

    override suspend fun importEntries(groupName: String, entries: List<ImportedEntry>): Result<Int> {
        imported += groupName to entries
        return Result.success(entries.size)
    }

    var exportEntries = emptyList<ExportedEntry>()

    override suspend fun getExportEntries(): List<ExportedEntry> = exportEntries

    override suspend fun createGroup(parentUuid: UUID, name: String): Result<UUID> {
        val uuid = UUID.randomUUID()
        groups.value = groups.value + Group(uuid, name, 0, parentUuid)
        return Result.success(uuid)
    }

    override suspend fun renameGroup(uuid: UUID, name: String): Result<Unit> {
        groups.value = groups.value.map { if (it.uuid == uuid) it.copy(name = name) else it }
        return Result.success(Unit)
    }

    override suspend fun deleteGroup(uuid: UUID): Result<Unit> {
        if (uuid == ROOT_GROUP_UUID) return Result.failure(IllegalArgumentException("root"))
        groups.value = groups.value.filterNot { it.uuid == uuid }
        return Result.success(Unit)
    }

    override suspend fun reloadIfChanged(): Result<Boolean> {
        reloads++
        return Result.success(false)
    }

    override suspend fun linkToApp(uuid: UUID, packageName: String): Result<Unit> =
        updateCredential(uuid) { copy(androidApps = (androidApps + packageName).distinct()) }

    override suspend fun linkToSite(uuid: UUID, domain: String): Result<Unit> =
        updateCredential(uuid) { copy(urls = (urls + "https://$domain").distinct()) }

    private fun updateCredential(uuid: UUID, change: Credential.() -> Credential): Result<Unit> {
        if (credentials.value.none { it.uuid == uuid }) return Result.failure(NoSuchElementException(uuid.toString()))
        credentials.value = credentials.value.map { if (it.uuid == uuid) it.change() else it }
        return Result.success(Unit)
    }

    companion object {
        val ROOT_GROUP_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000ff")
    }
}
