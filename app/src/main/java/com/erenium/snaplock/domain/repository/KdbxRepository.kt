package com.erenium.snaplock.domain.repository

import android.net.Uri
import com.erenium.snaplock.domain.importer.ExportedEntry
import com.erenium.snaplock.domain.importer.ImportedEntry
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Group
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface KdbxRepository {

    /** Opens the database with a password, a key file, or both. */
    suspend fun unlockDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray? = null): Result<Unit>

    suspend fun createDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray? = null): Result<Unit>

    /** Replaces the master password and key file of the open database. */
    suspend fun changeCredentials(password: CharSequence, keyFile: ByteArray?): Result<Unit>

    suspend fun lockDatabase()

    fun isLocked(): Flow<Boolean>

    fun getEntries(): Flow<List<Entry>>

    fun getGroups(): Flow<List<Group>>

    suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail>

    /** Login data of every entry outside the recycle bin; empty while locked. */
    suspend fun getCredentials(): List<Credential>

    /** Passwords and dates of every entry outside the recycle bin, for the health check. */
    suspend fun getAuditEntries(): List<AuditEntry>

    suspend fun addEntry(data: EntryFormData): Result<Unit>

    suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit>

    /**
     * Moves the entry to the recycle bin. Deletes it for good when it is already there or
     * the database has the recycle bin turned off, like other KeePass clients.
     */
    suspend fun deleteEntry(uuid: UUID): Result<Unit>

    /** Entries in the recycle bin. [getEntries] and [getCredentials] leave them out. */
    fun getRecycledEntries(): Flow<List<Entry>>

    /** Moves an entry out of the recycle bin, back to its previous group if that still exists. */
    suspend fun restoreEntry(uuid: UUID): Result<Unit>

    suspend fun emptyRecycleBin(): Result<Unit>

    /** Makes a version from the entry's history current again; the current one goes to history. */
    suspend fun restoreVersion(uuid: UUID, index: Int): Result<Unit>

    /** Stores a file with the entry; a name already in use gets a " (1)" style suffix. */
    suspend fun addAttachment(uuid: UUID, name: String, content: ByteArray): Result<Unit>

    suspend fun getAttachment(uuid: UUID, name: String): Result<ByteArray>

    suspend fun removeAttachment(uuid: UUID, name: String): Result<Unit>

    /**
     * Adds [entries] in one save under a new group called [groupName] below the root;
     * each entry's folder becomes a subgroup. Returns how many entries were added.
     */
    suspend fun importEntries(groupName: String, entries: List<ImportedEntry>): Result<Int>

    /** Every entry outside the recycle bin, for a CSV export. */
    suspend fun getExportEntries(): List<ExportedEntry>

    /** Adds an empty group under [parentUuid] and returns its id. */
    suspend fun createGroup(parentUuid: UUID, name: String): Result<UUID>

    suspend fun renameGroup(uuid: UUID, name: String): Result<Unit>

    /**
     * Moves the group and everything in it to the recycle bin, or deletes it for good when
     * it is already there or the recycle bin is off. The root group cannot be deleted.
     */
    suspend fun deleteGroup(uuid: UUID): Result<Unit>

    /** Links an Android app to the entry so autofill offers it there next time. */
    suspend fun linkToApp(uuid: UUID, packageName: String): Result<Unit>

    /** Adds a website to the entry so autofill offers it there next time. */
    suspend fun linkToSite(uuid: UUID, domain: String): Result<Unit>

}