package com.erenium.snaplock.data.sync

import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyContent
import app.keemobile.kotpass.database.modifiers.removeUnusedBinaries
import app.keemobile.kotpass.models.DatabaseContent
import app.keemobile.kotpass.models.DeletedObject
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.models.TimeData
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The two copies are different vaults, not two versions of one. */
class UnrelatedVaultException : IllegalArgumentException("The vaults have different root groups")

/**
 * Combines two copies of one vault the way KeePass synchronises them: groups and entries
 * are matched by UUID, the copy changed last wins (the other version of an entry goes into
 * its history), the copy moved last decides where an item lives, and an item deleted after
 * its last change stays deleted.
 *
 * Used when another device or app (Syncthing, a cloud drive, a desktop client) wrote the
 * vault file between opening it here and saving it.
 */
object VaultMerger {

    /** [local] with [remote]'s changes folded in; keeps [local]'s credentials and header. */
    fun merge(local: KeePassDatabase, remote: KeePassDatabase): KeePassDatabase {
        val localContent = local.content
        val remoteContent = remote.content
        val rootUuid = localContent.group.uuid
        if (remoteContent.group.uuid != rootUuid) throw UnrelatedVaultException()

        val localItems = Items().apply { collect(localContent.group, parent = null) }
        val remoteItems = Items().apply { collect(remoteContent.group, parent = null) }
        val deletions = (localContent.deletedObjects + remoteContent.deletedObjects)
            .groupBy { it.id }
            .mapValues { (_, records) -> records.maxOf { it.deletionTime } }

        fun deleted(uuid: UUID, times: TimeData?) = deletions[uuid]?.let { it.seconds() >= times.modified } == true

        val groups = LinkedHashMap<UUID, Placed<Group>>()
        (localItems.groups.keys + remoteItems.groups.keys).forEach { uuid ->
            val mine = localItems.groups[uuid]
            val theirs = remoteItems.groups[uuid]
            val merged = when {
                mine != null && theirs != null -> {
                    val newer = if (theirs.item.times.modified > mine.item.times.modified) theirs else mine
                    val placement = if (theirs.item.times.moved > mine.item.times.moved) theirs else mine
                    Placed(newer.item, placement.parent)
                }
                else -> (mine ?: theirs)!!.takeUnless { uuid != rootUuid && deleted(uuid, it.item.times) }
            }
            if (merged != null) groups[uuid] = merged
        }

        val historyLimit = localContent.meta.historyMaxItems
        val entries = LinkedHashMap<UUID, Placed<Entry>>()
        (localItems.entries.keys + remoteItems.entries.keys).forEach { uuid ->
            val mine = localItems.entries[uuid]
            val theirs = remoteItems.entries[uuid]
            val merged = when {
                mine != null && theirs != null -> {
                    val placement = if (theirs.item.times.moved > mine.item.times.moved) theirs else mine
                    Placed(mergeVersions(mine.item, theirs.item, historyLimit), placement.parent)
                }
                else -> (mine ?: theirs)!!.takeUnless { deleted(uuid, it.item.times) }
            }
            if (merged != null) entries[uuid] = merged
        }

        // A parent that was deleted, or a move that made two groups each other's parent,
        // leaves an item without a place in the tree: it goes to the root instead.
        val parents = groups.mapValues { (uuid, placed) -> if (uuid == rootUuid) null else placed.parent }.toMutableMap()
        parents.keys.forEach { uuid ->
            if (uuid == rootUuid) return@forEach
            if (parents[uuid] !in groups) parents[uuid] = rootUuid
            val seen = mutableSetOf(uuid)
            var current = parents[uuid]
            while (current != null && current != rootUuid) {
                if (!seen.add(current)) {
                    parents[uuid] = rootUuid
                    break
                }
                current = parents[current]
            }
        }
        val childGroups = groups.keys.filter { it != rootUuid }.groupBy { parents[it]!! }
        val childEntries = entries.values.groupBy { placed -> placed.parent.takeIf { it in groups } ?: rootUuid }

        fun build(uuid: UUID): Group = groups.getValue(uuid).item.copy(
            groups = childGroups[uuid].orEmpty().map(::build),
            entries = childEntries[uuid].orEmpty().map { it.item }
        )

        val root = build(rootUuid)
        val kept = groups.keys + entries.keys
        val content = DatabaseContent(
            meta = mergeMeta(localContent.meta, remoteContent.meta, groups.keys),
            group = root,
            deletedObjects = deletions.filterKeys { it !in kept }.map { (id, time) -> DeletedObject(id, time) }
        )
        val remoteBinaries = remote.binaries
        return local
            .modifyContent { content }
            .modifyBinaries { remoteBinaries + it }
            .removeUnusedBinaries()
    }

    /** The newer version of an entry, with the older one and both histories as its history. */
    private fun mergeVersions(mine: Entry, theirs: Entry, historyLimit: Int): Entry {
        val (newer, older) = if (theirs.times.modified > mine.times.modified) theirs to mine else mine to theirs
        // Versions are told apart by their modification time; the newer copy's own
        // history is kept whole, the older copy only adds versions it lacks.
        val known = newer.history.map { it.times.modified }.toSet() + newer.times.modified
        val missing = (older.history + older.copy(history = emptyList()))
            .filter { it.times.modified !in known }
            .distinctBy { it.times.modified }
        val history = (newer.history + missing).sortedBy { it.times.modified }
        return newer.copy(history = if (historyLimit >= 0) history.takeLast(historyLimit) else history)
    }

    private fun mergeMeta(mine: Meta, theirs: Meta, groups: Set<UUID>): Meta {
        val name = if (theirs.nameChanged.seconds() > mine.nameChanged.seconds()) theirs else mine
        val description = if (theirs.descriptionChanged.seconds() > mine.descriptionChanged.seconds()) theirs else mine
        // Prefer the newer recycle bin setting, but only one that still points at a group.
        val bin = listOf(mine, theirs)
            .filter { it.recycleBinUuid == null || it.recycleBinUuid in groups }
            .maxByOrNull { it.recycleBinChanged.seconds() } ?: mine
        return mine.copy(
            name = name.name,
            nameChanged = name.nameChanged,
            description = description.description,
            descriptionChanged = description.descriptionChanged,
            recycleBinEnabled = bin.recycleBinEnabled,
            recycleBinUuid = bin.recycleBinUuid?.takeIf { it in groups },
            recycleBinChanged = bin.recycleBinChanged,
            customIcons = theirs.customIcons + mine.customIcons,
            customData = theirs.customData + mine.customData
        )
    }

    private class Placed<T>(val item: T, val parent: UUID?)

    /** Every group and entry of one copy by UUID, in tree order, with its parent group. */
    private class Items {
        val groups = LinkedHashMap<UUID, Placed<Group>>()
        val entries = LinkedHashMap<UUID, Placed<Entry>>()

        fun collect(group: Group, parent: UUID?) {
            groups[group.uuid] = Placed(group, parent)
            group.entries.forEach { entries[it.uuid] = Placed(it, group.uuid) }
            group.groups.forEach { collect(it, group.uuid) }
        }
    }

    // KDBX files keep whole seconds, so a copy read back from disk loses the
    // sub-second part of times this session set; compare at that precision.
    private fun Instant?.seconds(): Instant = (this ?: Instant.EPOCH).truncatedTo(ChronoUnit.SECONDS)

    private val TimeData?.modified: Instant get() = this?.lastModificationTime.seconds()

    private val TimeData?.moved: Instant get() = this?.locationChanged.seconds()
}
