package com.erenium.snaplock.domain.repository

import android.net.Uri
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Group
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface KdbxRepository {

    suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit>

    suspend fun createDatabase(uri: Uri, password: CharSequence): Result<Unit>

    suspend fun lockDatabase()

    fun isLocked(): Flow<Boolean>

    fun getEntries(): Flow<List<Entry>>

    fun getGroups(): Flow<List<Group>>

    suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail>

    /** Login data of every entry outside the recycle bin; empty while locked. */
    suspend fun getCredentials(): List<Credential>

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

    /** Links an Android app to the entry so autofill offers it there next time. */
    suspend fun linkToApp(uuid: UUID, packageName: String): Result<Unit>

    /** Adds a website to the entry so autofill offers it there next time. */
    suspend fun linkToSite(uuid: UUID, domain: String): Result<Unit>

}