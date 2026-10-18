package com.erenium.snaplock.data.datasource.local

import android.content.Context
import android.net.Uri
import com.erenium.snaplock.domain.model.VaultBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies of each vault file as it was before Snaplock first overwrote it on a given day,
 * for the newest [KEEP] days. They stay on this device, outside Android's cloud backup,
 * and are the encrypted KDBX files themselves: each opens with the credentials it was
 * saved with.
 */
@Singleton
class VaultBackups(
    private val root: File,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, "vault-backups"))

    /** Whether a save at [now] should first [keep] the file as it is. */
    fun isDue(uri: Uri, now: Instant = Instant.now()): Boolean {
        val today = now.atZone(zone()).toLocalDate()
        return list(uri).none { it.created.atZone(zone()).toLocalDate() == today }
    }

    /** Stores [current], the vault file's contents before a save, unless today's copy exists. */
    fun keep(uri: Uri, current: ByteArray, now: Instant = Instant.now()) {
        if (current.isEmpty() || !isDue(uri, now)) return
        val folder = folderOf(uri).apply { mkdirs() }
        // Written under another name first, so a crash never leaves a half copy behind.
        val partial = File(folder, "${now.toEpochMilli()}$PARTIAL")
        partial.writeBytes(current)
        if (!partial.renameTo(File(folder, "${now.toEpochMilli()}$EXTENSION"))) {
            partial.delete()
            return
        }
        list(uri).drop(KEEP).forEach { File(folder, it.id + EXTENSION).delete() }
    }

    /** Newest first. */
    fun list(uri: Uri): List<VaultBackup> =
        folderOf(uri).listFiles { file -> file.name.endsWith(EXTENSION) }.orEmpty()
            .mapNotNull { file ->
                val id = file.name.removeSuffix(EXTENSION)
                id.toLongOrNull()?.let { VaultBackup(id, Instant.ofEpochMilli(it), file.length()) }
            }
            .sortedByDescending { it.created }

    fun read(uri: Uri, id: String): ByteArray? =
        list(uri).firstOrNull { it.id == id }?.let { File(folderOf(uri), it.id + EXTENSION).readBytes() }

    // The folder name only has to tell vaults apart; the URI itself is not stored.
    private fun folderOf(uri: Uri): File = File(root, uri.toString().encodeUtf8().sha256().hex().take(16))

    private companion object {
        const val KEEP = 7
        const val EXTENSION = ".kdbx"
        const val PARTIAL = ".partial"
    }
}
