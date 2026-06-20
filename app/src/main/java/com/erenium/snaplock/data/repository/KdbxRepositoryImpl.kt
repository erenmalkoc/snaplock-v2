package com.erenium.snaplock.data.repository

import android.content.Context
import android.net.Uri
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.findEntry
import app.keemobile.kotpass.database.getEntries
import app.keemobile.kotpass.database.modifiers.modifyEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.database.modifiers.moveEntry
import app.keemobile.kotpass.database.modifiers.removeEntry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.error.EntryNotFoundException
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.repository.KdbxRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import app.keemobile.kotpass.models.Entry as KotpassEntry
import app.keemobile.kotpass.models.Group as KotpassGroup


class KdbxRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDataSource: KdbxLocalDataSource,
    private val sessionCache: SessionCache
) : KdbxRepository {

    override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
        val database = sessionCache.getDatabase()

        if (database == null) {
            return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        }

        val found = database.findEntry { it.uuid == uuid }

        return if (found != null) {
            val (group, entry) = found
            Result.success(entry.toDomainEntryDetail(group.uuid))
        } else {
            Result.failure(EntryNotFoundException(uuid))
        }
    }

    override fun getEntries(): Flow<List<Entry>> {
        return sessionCache.databaseFlow.map { database ->
            if (database == null) {
                emptyList()
            } else {
                database.getEntries { true }.flatMap { (group, entries) ->
                    entries.map { it.toDomainEntry(group) }
                }
            }
        }
    }

    override fun getGroups(): Flow<List<Group>> {
        return sessionCache.databaseFlow.map { database ->
            if (database == null) emptyList() else flattenGroups(database.content.group)
        }
    }

    private fun flattenGroups(group: KotpassGroup): List<Group> {
        val current = Group(
            uuid = group.uuid,
            name = group.name,
            entryCount = group.entries.size
        )
        return listOf(current) + group.groups.flatMap { flattenGroups(it) }
    }

    private fun KotpassEntry.toDomainEntryDetail(groupUuid: UUID): EntryDetail {
        return EntryDetail(
            uuid = this.uuid,
            title = this.fields[FIELD_TITLE]?.content ?: context.getString(R.string.untitled_entry),
            username = this.fields[FIELD_USERNAME]?.content,
            password = this.fields[FIELD_PASSWORD]?.content,
            url = this.fields[FIELD_URL]?.content,
            notes = this.fields[FIELD_NOTES]?.content,
            groupUuid = groupUuid
        )
    }

    private fun KotpassEntry.toDomainEntry(group: KotpassGroup): Entry {
        return Entry(
            uuid = this.uuid,
            title = this.fields[FIELD_TITLE]?.content ?: context.getString(R.string.untitled_entry),
            username = this.fields[FIELD_USERNAME]?.content,
            groupUuid = group.uuid,
            groupName = group.name
        )
    }

    override suspend fun unlockDatabase(uri: Uri, password: CharSequence): Result<Unit> {
        val result = localDataSource.openDatabase(uri, password)
        return if (result.isSuccess) {
            val database = result.getOrNull()
            if (database != null) {
                sessionCache.setDatabase(database, uri)
                Result.success(Unit)
            } else {
                Result.failure(Exception(context.getString(R.string.empty_database_error)))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception(context.getString(R.string.unknown_error)))
        }
    }

    override suspend fun createDatabase(uri: Uri, password: CharSequence): Result<Unit> {
        return localDataSource.createDatabase(uri, password, context.getString(R.string.default_root_group))
            .map { database -> sessionCache.setDatabase(database, uri) }
    }

    override suspend fun addEntry(data: EntryFormData): Result<Unit> {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val newEntry = KotpassEntry(uuid = UUID.randomUUID(), fields = data.toFields())
        val targetGroup = data.groupUuid ?: database.content.group.uuid
        val updated = database.modifyGroup(targetGroup) {
            copy(entries = entries + newEntry)
        }
        return persist(updated)
    }

    override suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit> {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val withUpdatedFields = database.modifyEntry(uuid) {
            copy(fields = data.applyTo(fields))
        }
        val targetGroup = data.groupUuid
        val currentGroup = withUpdatedFields.findEntry { it.uuid == uuid }?.first?.uuid
        val moved = if (targetGroup != null && currentGroup != null && targetGroup != currentGroup) {
            withUpdatedFields.moveEntry(uuid, targetGroup)
        } else {
            withUpdatedFields
        }
        return persist(moved)
    }

    override suspend fun deleteEntry(uuid: UUID): Result<Unit> {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val updated = database.removeEntry(uuid)
        return persist(updated)
    }

    private suspend fun persist(database: KeePassDatabase): Result<Unit> {
        val uri = sessionCache.getUri()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val saveResult = localDataSource.saveDatabase(uri, database)
        return saveResult.onSuccess {
            sessionCache.setDatabase(database, uri)
        }
    }

    private fun EntryFormData.toFields(): EntryFields {
        return applyTo(EntryFields(emptyMap()))
    }

    private fun EntryFormData.applyTo(existing: EntryFields): EntryFields {
        var fields = existing
            .minus(FIELD_TITLE)
            .minus(FIELD_USERNAME)
            .minus(FIELD_PASSWORD)
            .minus(FIELD_URL)
            .minus(FIELD_NOTES)

        fields = fields.plus(FIELD_TITLE to EntryValue.Plain(title))
        if (!username.isNullOrEmpty()) {
            fields = fields.plus(FIELD_USERNAME to EntryValue.Plain(username))
        }
        if (!password.isNullOrEmpty()) {
            fields = fields.plus(FIELD_PASSWORD to EntryValue.Encrypted(EncryptedValue.fromString(password)))
        }
        if (!url.isNullOrEmpty()) {
            fields = fields.plus(FIELD_URL to EntryValue.Plain(url))
        }
        if (!notes.isNullOrEmpty()) {
            fields = fields.plus(FIELD_NOTES to EntryValue.Plain(notes))
        }
        return fields
    }

    override suspend fun lockDatabase() {
        sessionCache.lock()
    }

    override fun isLocked(): Flow<Boolean> {
        return sessionCache.isLocked
    }

    private companion object {
        const val FIELD_TITLE = "Title"
        const val FIELD_USERNAME = "UserName"
        const val FIELD_PASSWORD = "Password"
        const val FIELD_URL = "URL"
        const val FIELD_NOTES = "Notes"
    }

}
