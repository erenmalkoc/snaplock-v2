package com.erenium.snaplock.domain.repository

import android.net.Uri
import com.erenium.snaplock.domain.importer.ExportedEntry
import com.erenium.snaplock.domain.importer.ImportedEntry
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.DatabaseSettings
import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.model.VaultBackup
import com.erenium.snaplock.domain.passkeys.Passkey
import com.erenium.snaplock.domain.passkeys.StoredPasskey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface KdbxRepository {
    suspend fun unlockDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray? = null): Result<Unit>

    suspend fun createDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray? = null): Result<Unit>

    suspend fun changeCredentials(password: CharSequence, keyFile: ByteArray?): Result<Unit>

    suspend fun getDatabaseSettings(): DatabaseSettings?

    suspend fun updateDatabaseSettings(settings: DatabaseSettings): Result<Unit>

    suspend fun lockDatabase()

    suspend fun reloadIfChanged(): Result<Boolean>

    suspend fun getBackups(): List<VaultBackup>

    suspend fun getBackup(id: String): Result<ByteArray>

    fun isLocked(): StateFlow<Boolean>

    fun getEntries(): Flow<List<Entry>>

    fun getGroups(): Flow<List<Group>>

    suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail>

    suspend fun getCredentials(): List<Credential>

    suspend fun getAuditEntries(): List<AuditEntry>

    suspend fun addEntry(data: EntryFormData): Result<Unit>

    suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit>

    suspend fun deleteEntry(uuid: UUID): Result<Unit>

    fun getRecycledEntries(): Flow<List<Entry>>

    suspend fun restoreEntry(uuid: UUID): Result<Unit>

    suspend fun emptyRecycleBin(): Result<Unit>

    suspend fun restoreVersion(uuid: UUID, index: Int): Result<Unit>

    suspend fun addAttachment(uuid: UUID, name: String, content: ByteArray): Result<Unit>

    suspend fun getAttachment(uuid: UUID, name: String): Result<ByteArray>

    suspend fun removeAttachment(uuid: UUID, name: String): Result<Unit>

    suspend fun importEntries(groupName: String, entries: List<ImportedEntry>): Result<Int>

    suspend fun getExportEntries(): List<ExportedEntry>

    suspend fun createGroup(parentUuid: UUID, name: String): Result<UUID>

    suspend fun renameGroup(uuid: UUID, name: String): Result<Unit>

    suspend fun deleteGroup(uuid: UUID): Result<Unit>

    suspend fun setEntryIcons(icons: Map<UUID, ByteArray>): Result<Unit>

    suspend fun getPasskeys(): List<StoredPasskey>

    suspend fun addPasskey(title: String, url: String, passkey: Passkey): Result<UUID>

    suspend fun linkToApp(uuid: UUID, packageName: String): Result<Unit>

    suspend fun linkToSite(uuid: UUID, domain: String): Result<Unit>
}