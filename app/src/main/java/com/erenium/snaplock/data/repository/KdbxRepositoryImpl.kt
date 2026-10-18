package com.erenium.snaplock.data.repository

import android.content.Context
import android.net.Uri
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.cryptography.format.BaseCiphers
import app.keemobile.kotpass.cryptography.format.TwofishCipher
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.database.header.DatabaseHeader
import app.keemobile.kotpass.database.header.KdfParameters
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyContent
import app.keemobile.kotpass.database.modifiers.modifyCredentials
import app.keemobile.kotpass.database.modifiers.modifyEntry
import app.keemobile.kotpass.database.modifiers.modifyGroup
import app.keemobile.kotpass.database.modifiers.modifyMeta
import app.keemobile.kotpass.database.modifiers.moveEntry
import app.keemobile.kotpass.database.modifiers.moveGroup
import app.keemobile.kotpass.database.modifiers.removeEntry
import app.keemobile.kotpass.database.modifiers.removeGroup
import app.keemobile.kotpass.database.modifiers.removeUnusedBinaries
import app.keemobile.kotpass.database.modifiers.withHistory
import app.keemobile.kotpass.database.modifiers.withRecycleBin
import app.keemobile.kotpass.models.BinaryData
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.DeletedObject
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.TimeData
import com.erenium.snaplock.data.datasource.cache.SessionCache
import com.erenium.snaplock.data.datasource.local.KdbxLocalDataSource
import com.erenium.snaplock.data.datasource.local.credentialsOf
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import com.erenium.snaplock.R
import com.erenium.snaplock.data.sync.VaultMerger
import com.erenium.snaplock.domain.error.EntryNotFoundException
import com.erenium.snaplock.domain.error.VaultChangedElsewhereException
import com.erenium.snaplock.domain.importer.ExportedEntry
import com.erenium.snaplock.domain.importer.ImportedEntry
import com.erenium.snaplock.domain.model.Attachment
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.DatabaseSettings
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.Entry
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.model.EntryFormData
import com.erenium.snaplock.domain.model.EntryVersion
import com.erenium.snaplock.domain.model.Expiry
import com.erenium.snaplock.domain.model.Group
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import com.erenium.snaplock.domain.model.problem
import com.erenium.snaplock.domain.model.VaultBackup
import com.erenium.snaplock.domain.otp.OtpParser
import com.erenium.snaplock.domain.passwords.AuditEntry
import com.erenium.snaplock.domain.repository.KdbxRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import app.keemobile.kotpass.models.Entry as KotpassEntry
import app.keemobile.kotpass.models.Group as KotpassGroup


class KdbxRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localDataSource: KdbxLocalDataSource,
    private val sessionCache: SessionCache,
    private val recentDatabasesPrefs: RecentDatabasesPrefs
) : KdbxRepository {

    // Serializes edits so two quick saves can neither interleave their writes to the
    // vault file nor build on the same stale database and drop each other's changes.
    private val writeMutex = Mutex()

    override suspend fun getEntryByUuid(uuid: UUID): Result<EntryDetail> {
        val database = sessionCache.getDatabase()

        if (database == null) {
            return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        }

        val found = database.getEntry { it.uuid == uuid }

        return if (found != null) {
            val (group, entry) = found
            Result.success(entry.toDomainEntryDetail(group.uuid, database.binaries))
        } else {
            Result.failure(EntryNotFoundException(uuid))
        }
    }

    override suspend fun getCredentials(): List<Credential> {
        val database = sessionCache.getDatabase() ?: return emptyList()
        val untitled = context.getString(R.string.untitled_entry)
        return database.content.group.entriesOutside(database.recycleBin())
            .map { (_, entry) -> entry.toCredential(untitled) }
    }

    override suspend fun getAuditEntries(): List<AuditEntry> {
        val database = sessionCache.getDatabase() ?: return emptyList()
        val untitled = context.getString(R.string.untitled_entry)
        return database.content.group.entriesOutside(database.recycleBin()).map { (_, entry) ->
            AuditEntry(
                uuid = entry.uuid,
                title = entry.fields[FIELD_TITLE]?.content?.takeIf { it.isNotBlank() } ?: untitled,
                username = entry.fields[FIELD_USERNAME]?.content,
                password = entry.fields[FIELD_PASSWORD]?.content,
                modified = entry.times?.lastModificationTime,
                expires = entry.times?.takeIf { it.expires }?.expiryTime
            )
        }
    }

    /** Entries with their group, skipping the [excludedGroup] subtree. */
    private fun KotpassGroup.entriesOutside(excludedGroup: UUID?): List<Pair<KotpassGroup, KotpassEntry>> =
        if (uuid == excludedGroup) {
            emptyList()
        } else {
            entries.map { this to it } + groups.flatMap { it.entriesOutside(excludedGroup) }
        }

    /** The recycle bin group, if the database has one that still exists. */
    private fun KeePassDatabase.recycleBin(): UUID? =
        content.meta.recycleBinUuid?.takeIf { content.group.findGroup(it) != null }

    private fun KotpassGroup.findGroup(target: UUID): KotpassGroup? =
        if (uuid == target) this else groups.firstNotNullOfOrNull { it.findGroup(target) }

    /** This group's id and the ids of every group and entry below it. */
    private fun KotpassGroup.descendantIds(): List<UUID> =
        listOf(uuid) + entries.map { it.uuid } + groups.flatMap { it.descendantIds() }

    override fun getEntries(): Flow<List<Entry>> {
        return sessionCache.databaseFlow.map { database ->
            if (database == null) {
                emptyList()
            } else {
                database.content.group.entriesOutside(database.recycleBin())
                    .map { (group, entry) -> entry.toDomainEntry(group) }
            }
        }
    }

    override fun getGroups(): Flow<List<Group>> {
        return sessionCache.databaseFlow.map { database ->
            if (database == null) emptyList() else flattenGroups(database.content.group, database.recycleBin())
        }
    }

    override fun getRecycledEntries(): Flow<List<Entry>> {
        return sessionCache.databaseFlow.map { database ->
            val bin = database?.recycleBin()?.let { database.content.group.findGroup(it) }
            bin?.entriesOutside(excludedGroup = null)
                ?.map { (group, entry) -> entry.toDomainEntry(group) }
                .orEmpty()
        }
    }

    private fun flattenGroups(group: KotpassGroup, excludedGroup: UUID?, parent: UUID? = null): List<Group> {
        if (group.uuid == excludedGroup) return emptyList()
        val current = Group(
            uuid = group.uuid,
            name = group.name,
            entryCount = group.entries.size,
            parentUuid = parent,
            groupCount = group.groups.count { it.uuid != excludedGroup }
        )
        return listOf(current) + group.groups.flatMap { flattenGroups(it, excludedGroup, group.uuid) }
    }

    private fun KotpassEntry.toDomainEntryDetail(
        groupUuid: UUID,
        storedFiles: Map<ByteString, BinaryData>
    ): EntryDetail {
        return EntryDetail(
            uuid = this.uuid,
            title = this.fields[FIELD_TITLE]?.content ?: context.getString(R.string.untitled_entry),
            username = this.fields[FIELD_USERNAME]?.content,
            password = this.fields[FIELD_PASSWORD]?.content,
            url = this.fields[FIELD_URL]?.content,
            notes = this.fields[FIELD_NOTES]?.content,
            groupUuid = groupUuid,
            customFields = fields
                .filterKeys { it !in STANDARD_FIELDS && !it.isLinkField() && !OtpParser.isOtpField(it) }
                .map { (name, value) -> CustomField(name, value.content, value is EntryValue.Encrypted) },
            history = history.mapIndexed { index, version ->
                EntryVersion(
                    index = index,
                    modified = version.times?.lastModificationTime,
                    title = version.fields[FIELD_TITLE]?.content ?: context.getString(R.string.untitled_entry),
                    username = version.fields[FIELD_USERNAME]?.content,
                    password = version.fields[FIELD_PASSWORD]?.content
                )
            }.asReversed(),
            otp = OtpParser.fromFields(fields.entries.associate { it.key to it.value.content }),
            expires = times?.takeIf { it.expires }?.expiryTime,
            attachments = binaries.map { reference ->
                Attachment(reference.name, storedFiles[reference.hash]?.getContent()?.size?.toLong() ?: 0L)
            }
        )
    }

    private fun KotpassEntry.toDomainEntry(group: KotpassGroup): Entry {
        return Entry(
            uuid = this.uuid,
            title = this.fields[FIELD_TITLE]?.content ?: context.getString(R.string.untitled_entry),
            username = this.fields[FIELD_USERNAME]?.content,
            groupUuid = group.uuid,
            groupName = group.name,
            url = this.fields[FIELD_URL]?.content?.takeIf { it.isNotBlank() },
            notes = this.fields[FIELD_NOTES]?.content?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun unlockDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray?): Result<Unit> {
        val result = localDataSource.openDatabase(uri, password, keyFile)
        return if (result.isSuccess) {
            val stored = result.getOrNull()
            if (stored != null) {
                sessionCache.setDatabase(stored.database, uri, stored.fingerprint)
                recentDatabasesPrefs.add(uri)
                Result.success(Unit)
            } else {
                Result.failure(Exception(context.getString(R.string.empty_database_error)))
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception(context.getString(R.string.unknown_error)))
        }
    }

    override suspend fun createDatabase(uri: Uri, password: CharSequence, keyFile: ByteArray?): Result<Unit> {
        return localDataSource.createDatabase(uri, password, context.getString(R.string.default_root_group), keyFile)
            .map { stored ->
                sessionCache.setDatabase(stored.database, uri, stored.fingerprint)
                recentDatabasesPrefs.add(uri)
            }
    }

    override suspend fun addEntry(data: EntryFormData): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val newEntry = KotpassEntry(uuid = UUID.randomUUID(), fields = data.toFields())
            .let { entry -> data.expiry?.let { entry.copy(times = entry.times.withExpiry(it)) } ?: entry }
        val targetGroup = data.groupUuid ?: database.content.group.uuid
        val updated = database.modifyGroup(targetGroup) {
            copy(entries = entries + newEntry)
        }
        persist(updated)
    }

    override suspend fun updateEntry(uuid: UUID, data: EntryFormData): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val historyLimit = database.content.meta.historyMaxItems
        val withUpdatedFields = database.modifyEntry(uuid) {
            // Keep the previous version, as KeePass does, so an edit can be undone.
            withHistory { copy(fields = data.applyTo(fields)) }
                .let { if (historyLimit >= 0) it.copy(history = it.history.takeLast(historyLimit)) else it }
        }.let { updated -> data.expiry?.let { expiry -> updated.withEntryTimes(uuid) { it.withExpiry(expiry) } } ?: updated }
        val targetGroup = data.groupUuid
        val currentGroup = withUpdatedFields.getEntry { it.uuid == uuid }?.first?.uuid
        val moved = if (targetGroup != null && currentGroup != null && targetGroup != currentGroup) {
            withUpdatedFields.moveEntry(uuid, targetGroup)
        } else {
            withUpdatedFields
        }
        persist(moved)
    }

    override suspend fun deleteEntry(uuid: UUID): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val group = database.getEntry { it.uuid == uuid }?.first
            ?: return Result.failure(EntryNotFoundException(uuid))
        val bin = database.recycleBin()
        val alreadyRecycled = bin != null &&
            database.content.group.findGroup(bin)?.findGroup(group.uuid) != null
        val updated = if (!database.content.meta.recycleBinEnabled || alreadyRecycled) {
            database.removeEntry(uuid)
        } else {
            // A recycle bin id that points at a deleted group would swallow the entry.
            val withValidBin = if (bin == null && database.content.meta.recycleBinUuid != null) {
                database.modifyMeta { copy(recycleBinUuid = null) }
            } else {
                database
            }
            withValidBin.withRecycleBin { binUuid ->
                // moveEntry records the previous group itself.
                moveEntry(uuid, binUuid).withEntryTimes(uuid) { it.moved() }
            }
        }
        persist(updated)
    }

    override suspend fun restoreEntry(uuid: UUID): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val entry = database.getEntry { it.uuid == uuid }?.second
            ?: return Result.failure(EntryNotFoundException(uuid))
        val binGroup = database.recycleBin()?.let { database.content.group.findGroup(it) }
        val previous = entry.previousParentGroup?.takeIf { target ->
            database.content.group.findGroup(target) != null && binGroup?.findGroup(target) == null
        }
        val updated = database
            .moveEntry(uuid, previous ?: database.content.group.uuid)
            .withEntryTimes(uuid) { it.moved() }
        persist(updated)
    }

    override suspend fun emptyRecycleBin(): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val bin = database.recycleBin() ?: return Result.success(Unit)
        val binGroup = database.content.group.findGroup(bin) ?: return Result.success(Unit)
        // Record the removals, as removeEntry does, so syncing with another copy
        // of the vault does not bring the emptied items back.
        val now = Instant.now()
        val removed = (binGroup.entries.map { it.uuid } + binGroup.groups.flatMap { it.descendantIds() })
            .map { DeletedObject(it, now) }
        val updated = database
            .modifyGroup(bin) { copy(entries = emptyList(), groups = emptyList()) }
            .modifyContent { copy(deletedObjects = deletedObjects + removed) }
            .removeUnusedBinaries()
        persist(updated)
    }

    override suspend fun changeCredentials(password: CharSequence, keyFile: ByteArray?): Result<Unit> =
        writeMutex.withLock {
            val database = sessionCache.getDatabase()
                ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
            if (password.isEmpty() && keyFile == null) {
                return Result.failure(IllegalArgumentException("A password or a key file is required"))
            }
            val updated = database
                .modifyCredentials { credentialsOf(password, keyFile) }
                .modifyMeta { copy(masterKeyChanged = Instant.now()) }
            persist(updated)
        }

    override suspend fun getDatabaseSettings(): DatabaseSettings? {
        val database = sessionCache.getDatabase() ?: return null
        val meta = database.content.meta
        return DatabaseSettings(
            name = meta.name,
            recycleBinEnabled = meta.recycleBinEnabled,
            historyMaxItems = meta.historyMaxItems,
            encryption = (database as? KeePassDatabase.Ver4x)?.header?.encryptionSettings()
        )
    }

    override suspend fun updateDatabaseSettings(settings: DatabaseSettings): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        settings.encryption?.let { encryption ->
            encryption.problem()?.let { return Result.failure(IllegalArgumentException(it)) }
        }
        val now = Instant.now()
        val name = settings.name.trim()
        // The change times let a sync pick the newer setting when two copies differ.
        val withMeta = database.modifyMeta {
            copy(
                name = name,
                nameChanged = if (name != this.name) now else nameChanged,
                recycleBinEnabled = settings.recycleBinEnabled,
                recycleBinChanged = if (settings.recycleBinEnabled != recycleBinEnabled) now else recycleBinChanged,
                historyMaxItems = settings.historyMaxItems,
                settingsChanged = now
            )
        }
        persist(settings.encryption?.let { withMeta.withEncryption(it) } ?: withMeta)
    }

    private fun DatabaseHeader.Ver4x.encryptionSettings(): EncryptionSettings? {
        val cipher = VaultCipher.entries.firstOrNull { it.uuid == cipherId } ?: return null
        return when (val kdf = kdfParameters) {
            is KdfParameters.Argon2 -> EncryptionSettings(
                cipher = cipher,
                kdf = if (kdf.variant == KdfParameters.Argon2.Variant.Argon2id) KdfAlgorithm.ARGON2ID else KdfAlgorithm.ARGON2D,
                iterations = kdf.iterations.toLong(),
                memoryBytes = kdf.memory.toLong(),
                parallelism = kdf.parallelism.toInt()
            )
            is KdfParameters.Aes -> EncryptionSettings(cipher, KdfAlgorithm.AES_KDF, kdf.rounds.toLong(), 0, 0)
        }
    }

    /** KDBX 3 files keep their encryption; KDBX 4 headers take the new cipher and key derivation. */
    private fun KeePassDatabase.withEncryption(settings: EncryptionSettings): KeePassDatabase {
        val ver4 = this as? KeePassDatabase.Ver4x ?: return this
        if (ver4.header.encryptionSettings() == settings) return this
        // The salt and seed are replaced again on every save; these only fill the fields.
        val salt = ByteArray(KDF_SALT_BYTES).also { SecureRandom().nextBytes(it) }.toByteString()
        val kdf = when (settings.kdf) {
            KdfAlgorithm.AES_KDF -> KdfParameters.Aes(rounds = settings.iterations.toULong(), seed = salt)
            KdfAlgorithm.ARGON2D, KdfAlgorithm.ARGON2ID -> KdfParameters.Argon2(
                variant = if (settings.kdf == KdfAlgorithm.ARGON2ID) {
                    KdfParameters.Argon2.Variant.Argon2id
                } else {
                    KdfParameters.Argon2.Variant.Argon2d
                },
                salt = salt,
                parallelism = settings.parallelism.toUInt(),
                memory = settings.memoryBytes.toULong(),
                iterations = settings.iterations.toULong(),
                version = ARGON2_VERSION,
                secretKey = null,
                associatedData = null
            )
        }
        return ver4.copy(header = ver4.header.copy(cipherId = settings.cipher.uuid, kdfParameters = kdf))
    }

    private val VaultCipher.uuid: UUID
        get() = when (this) {
            VaultCipher.AES_256 -> BaseCiphers.Aes.uuid
            VaultCipher.CHACHA20 -> BaseCiphers.ChaCha20.uuid
            VaultCipher.TWOFISH -> TwofishCipher.uuid
        }

    override suspend fun addAttachment(uuid: UUID, name: String, content: ByteArray): Result<Unit> =
        writeMutex.withLock {
            val database = sessionCache.getDatabase()
                ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
            val entry = database.getEntry { it.uuid == uuid }?.second
                ?: return Result.failure(EntryNotFoundException(uuid))
            val taken = entry.binaries.map { it.name }.toSet()
            val uniqueName = uniqueName(name.ifBlank { "attachment" }, taken)
            val data = BinaryData.Uncompressed(false, content)
            val updated = database
                .modifyBinaries { it + (data.hash to data) }
                .modifyEntry(uuid) {
                    withHistory {
                        copy(binaries = binaries + BinaryReference(data.hash, uniqueName))
                    }.trimmedHistory(database.content.meta.historyMaxItems)
                }
            persist(updated)
        }

    override suspend fun getAttachment(uuid: UUID, name: String): Result<ByteArray> {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val reference = database.getEntry { it.uuid == uuid }?.second?.binaries?.firstOrNull { it.name == name }
            ?: return Result.failure(NoSuchElementException(name))
        val data = database.binaries[reference.hash]
            ?: return Result.failure(NoSuchElementException(name))
        return Result.success(data.getContent())
    }

    override suspend fun removeAttachment(uuid: UUID, name: String): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val entry = database.getEntry { it.uuid == uuid }?.second
            ?: return Result.failure(EntryNotFoundException(uuid))
        if (entry.binaries.none { it.name == name }) return Result.failure(NoSuchElementException(name))
        // The removed file stays reachable through the entry's history, as in KeePass.
        val updated = database.modifyEntry(uuid) {
            withHistory { copy(binaries = binaries.filterNot { it.name == name }) }
                .trimmedHistory(database.content.meta.historyMaxItems)
        }.removeUnusedBinaries()
        persist(updated)
    }

    private fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val extension = name.substring(dot)
        return generateSequence(1) { it + 1 }.map { "$base ($it)$extension" }.first { it !in taken }
    }

    private fun KotpassEntry.trimmedHistory(limit: Int): KotpassEntry =
        if (limit >= 0) copy(history = history.takeLast(limit)) else this

    override suspend fun importEntries(groupName: String, entries: List<ImportedEntry>): Result<Int> =
        writeMutex.withLock {
            val database = sessionCache.getDatabase()
                ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
            val untitled = context.getString(R.string.untitled_entry)
            val importGroup = entries.fold(KotpassGroup(uuid = UUID.randomUUID(), name = groupName)) { group, entry ->
                val path = entry.folder?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                group.withEntryAt(path, entry.toKotpassEntry(untitled))
            }
            val updated = database.modifyGroup(database.content.group.uuid) { copy(groups = groups + importGroup) }
            persist(updated).map { entries.size }
        }

    override suspend fun getExportEntries(): List<ExportedEntry> {
        val database = sessionCache.getDatabase() ?: return emptyList()
        val untitled = context.getString(R.string.untitled_entry)
        fun walk(group: KotpassGroup, path: String, excluded: UUID?): List<ExportedEntry> {
            if (group.uuid == excluded) return emptyList()
            val own = group.entries.map { entry ->
                val values = entry.fields.entries.associate { it.key to it.value.content }
                ExportedEntry(
                    group = path,
                    title = values[FIELD_TITLE]?.takeIf { it.isNotBlank() } ?: untitled,
                    username = values[FIELD_USERNAME],
                    password = values[FIELD_PASSWORD],
                    url = values[FIELD_URL],
                    notes = values[FIELD_NOTES],
                    otpUri = OtpParser.fromFields(values)?.let(OtpParser::toUri)
                )
            }
            return own + group.groups.flatMap { child ->
                walk(child, if (path.isEmpty()) child.name else "$path/${child.name}", excluded)
            }
        }
        return walk(database.content.group, "", database.recycleBin())
    }

    /** Adds [entry] under the subgroup [path] (created as needed) of this group. */
    private fun KotpassGroup.withEntryAt(path: List<String>, entry: KotpassEntry): KotpassGroup {
        if (path.isEmpty()) return copy(entries = entries + entry)
        val name = path.first()
        val existing = groups.firstOrNull { it.name == name }
        val child = (existing ?: KotpassGroup(uuid = UUID.randomUUID(), name = name)).withEntryAt(path.drop(1), entry)
        return copy(groups = if (existing == null) groups + child else groups.map { if (it === existing) child else it })
    }

    private fun ImportedEntry.toKotpassEntry(untitled: String): KotpassEntry {
        val fields = buildList {
            add(FIELD_TITLE to EntryValue.Plain(title.ifBlank { untitled }))
            username?.let { add(FIELD_USERNAME to EntryValue.Plain(it)) }
            password?.let { add(FIELD_PASSWORD to EntryValue.Encrypted(EncryptedValue.fromString(it))) }
            url?.let { add(FIELD_URL to EntryValue.Plain(it)) }
            notes?.let { add(FIELD_NOTES to EntryValue.Plain(it)) }
            otpUri?.let { add(OtpParser.FIELD_OTP to EntryValue.Encrypted(EncryptedValue.fromString(it))) }
        }
        return KotpassEntry(uuid = UUID.randomUUID(), fields = EntryFields(fields.toMap()))
    }

    override suspend fun createGroup(parentUuid: UUID, name: String): Result<UUID> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        if (database.content.group.findGroup(parentUuid) == null) {
            return Result.failure(NoSuchElementException("No group $parentUuid"))
        }
        val uuid = UUID.randomUUID()
        val updated = database.modifyGroup(parentUuid) {
            copy(groups = groups + KotpassGroup(uuid = uuid, name = name.trim()))
        }
        persist(updated).map { uuid }
    }

    override suspend fun renameGroup(uuid: UUID, name: String): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        if (database.content.group.findGroup(uuid) == null) {
            return Result.failure(NoSuchElementException("No group $uuid"))
        }
        persist(database.modifyGroup(uuid) { copy(name = name.trim()) })
    }

    override suspend fun deleteGroup(uuid: UUID): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val root = database.content.group
        if (uuid == root.uuid) return Result.failure(IllegalArgumentException("The root group cannot be deleted"))
        if (root.findGroup(uuid) == null) return Result.failure(NoSuchElementException("No group $uuid"))
        val bin = database.recycleBin()
        val alreadyRecycled = bin != null && root.findGroup(bin)?.findGroup(uuid) != null
        val updated = if (!database.content.meta.recycleBinEnabled || alreadyRecycled) {
            database.removeGroup(uuid).removeUnusedBinaries()
        } else {
            val withValidBin = if (bin == null && database.content.meta.recycleBinUuid != null) {
                database.modifyMeta { copy(recycleBinUuid = null) }
            } else {
                database
            }
            // The move time lets a sync tell this move apart from older ones made elsewhere.
            withValidBin.withRecycleBin { binUuid -> moveGroup(uuid, binUuid).withGroupTimes(uuid) { it.moved() } }
        }
        persist(updated)
    }

    override suspend fun restoreVersion(uuid: UUID, index: Int): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val entry = database.getEntry { it.uuid == uuid }?.second
            ?: return Result.failure(EntryNotFoundException(uuid))
        val version = entry.history.getOrNull(index)
            ?: return Result.failure(IndexOutOfBoundsException("No history version $index"))
        val historyLimit = database.content.meta.historyMaxItems
        val updated = database.modifyEntry(uuid) {
            withHistory { copy(fields = version.fields) }
                .let { if (historyLimit >= 0) it.copy(history = it.history.takeLast(historyLimit)) else it }
        }
        persist(updated)
    }

    /**
     * Changes an entry's times. kotpass' modifyEntry keeps the old times (it only bumps the
     * modification time), so this replaces the entry inside its group instead.
     */
    private fun KeePassDatabase.withEntryTimes(uuid: UUID, change: (TimeData?) -> TimeData): KeePassDatabase {
        val group = getEntry { it.uuid == uuid }?.first ?: return this
        return modifyGroup(group.uuid) {
            copy(entries = entries.map { if (it.uuid == uuid) it.copy(times = change(it.times)) else it })
        }
    }

    /** Changes a group's times without kotpass' modifyGroup, which would keep the old ones. */
    private fun KeePassDatabase.withGroupTimes(uuid: UUID, change: (TimeData?) -> TimeData): KeePassDatabase {
        val parent = content.group.parentOf(uuid) ?: return this
        return modifyGroup(parent.uuid) {
            copy(groups = groups.map { if (it.uuid == uuid) it.copy(times = change(it.times)) else it })
        }
    }

    private fun KotpassGroup.parentOf(target: UUID): KotpassGroup? =
        if (groups.any { it.uuid == target }) this else groups.firstNotNullOfOrNull { it.parentOf(target) }

    private fun TimeData?.withExpiry(expiry: Expiry): TimeData {
        val base = this ?: TimeData.create()
        return base.copy(expires = expiry.at != null, expiryTime = expiry.at ?: base.expiryTime)
    }

    private fun TimeData?.moved(): TimeData = (this ?: TimeData.create()).copy(locationChanged = Instant.now())

    override suspend fun linkToApp(uuid: UUID, packageName: String): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val entry = database.getEntry { it.uuid == uuid }?.second
            ?: return Result.failure(EntryNotFoundException(uuid))
        if (packageName in entry.toCredential("").androidApps) return Result.success(Unit)
        val updated = database.modifyEntry(uuid) {
            copy(fields = fields.plus(fields.nextFreeName(FIELD_ANDROID_APP) to EntryValue.Plain(packageName)))
        }
        persist(updated)
    }

    override suspend fun linkToSite(uuid: UUID, domain: String): Result<Unit> = writeMutex.withLock {
        val database = sessionCache.getDatabase()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        val entry = database.getEntry { it.uuid == uuid }?.second
            ?: return Result.failure(EntryNotFoundException(uuid))
        val url = "https://$domain"
        if (entry.toCredential("").urls.any { it.removeSuffix("/").endsWith("://$domain") }) {
            return Result.success(Unit)
        }
        // The main URL field is what other KeePass clients show; extra sites go in KP2A_URL fields.
        val name = if (entry.fields[FIELD_URL]?.content.isNullOrBlank()) {
            FIELD_URL
        } else {
            entry.fields.nextFreeName(FIELD_EXTRA_URL)
        }
        val updated = database.modifyEntry(uuid) {
            copy(fields = fields.plus(name to EntryValue.Plain(url)))
        }
        persist(updated)
    }

    /** [base] if unused, else the first free [base]_1, [base]_2, ... */
    private fun EntryFields.nextFreeName(base: String): String =
        generateSequence(0) { it + 1 }
            .map { if (it == 0) base else "${base}_$it" }
            .first { it !in keys }

    // Call only while holding writeMutex.
    private suspend fun persist(database: KeePassDatabase): Result<Unit> {
        val uri = sessionCache.getUri()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        // Another device or app may have written the file since this session last read or
        // saved it (Syncthing, a cloud drive): merge those changes instead of overwriting
        // them. The file is still encrypted with the credentials the session opened it with.
        val opened = sessionCache.getDatabase() ?: database
        val external = localDataSource.readIfChanged(uri, sessionCache.getFingerprint(), opened.credentials)
            .getOrElse { return Result.failure(it) }
        val merged = if (external == null) {
            database
        } else {
            runCatching { VaultMerger.merge(database, external.database) }
                .getOrElse { return Result.failure(VaultChangedElsewhereException(it)) }
        }
        return localDataSource.saveDatabase(uri, merged).map { fingerprint ->
            sessionCache.setDatabase(merged, uri, fingerprint)
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

        if (customFields != null) {
            fields = fields.filterKeys { it in STANDARD_FIELDS || it.isLinkField() || OtpParser.isOtpField(it) }
            customFields.filter { it.name.isNotBlank() && it.name !in STANDARD_FIELDS }.forEach { field ->
                val value = if (field.isProtected) {
                    EntryValue.Encrypted(EncryptedValue.fromString(field.value))
                } else {
                    EntryValue.Plain(field.value)
                }
                fields = fields.plus(field.name.trim() to value)
            }
        }
        if (otpUri != null) {
            fields = fields.filterKeys { !OtpParser.isOtpField(it) }
            if (otpUri.isNotBlank()) {
                fields = fields.plus(OtpParser.FIELD_OTP to EntryValue.Encrypted(EncryptedValue.fromString(otpUri)))
            }
        }
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

    override suspend fun getBackups(): List<VaultBackup> =
        sessionCache.getUri()?.let { localDataSource.listBackups(it) }.orEmpty()

    override suspend fun getBackup(id: String): Result<ByteArray> {
        val uri = sessionCache.getUri()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.locked_database_error)))
        return localDataSource.readBackup(uri, id)
    }

    override suspend fun reloadIfChanged(): Result<Boolean> = writeMutex.withLock {
        val database = sessionCache.getDatabase() ?: return Result.success(false)
        val uri = sessionCache.getUri() ?: return Result.success(false)
        val external = localDataSource.readIfChanged(uri, sessionCache.getFingerprint(), database.credentials)
            .getOrElse { return Result.failure(it) }
            ?: return Result.success(false)
        val merged = runCatching { VaultMerger.merge(database, external.database) }
            .getOrElse { return Result.failure(VaultChangedElsewhereException(it)) }
        // Every change made here was saved before the file changed, so the merge only adds
        // what came from elsewhere; the next edit writes the result.
        sessionCache.setDatabase(merged, uri, external.fingerprint)
        Result.success(true)
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
        val STANDARD_FIELDS = setOf(FIELD_TITLE, FIELD_USERNAME, FIELD_PASSWORD, FIELD_URL, FIELD_NOTES)
        const val KDF_SALT_BYTES = 32
        /** Argon2 1.3, the only version KeePass writes. */
        const val ARGON2_VERSION = 0x13u
    }

}
