package com.erenium.snaplock.data

import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.getEntry
import app.keemobile.kotpass.database.modifiers.binaries
import app.keemobile.kotpass.database.modifiers.modifyBinaries
import app.keemobile.kotpass.database.modifiers.modifyContent
import app.keemobile.kotpass.models.BinaryData
import app.keemobile.kotpass.models.BinaryReference
import app.keemobile.kotpass.models.DeletedObject
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Group
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.models.TimeData
import com.erenium.snaplock.data.datasource.local.encodeToByteArray
import com.erenium.snaplock.data.sync.UnrelatedVaultException
import com.erenium.snaplock.data.sync.VaultMerger
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMergerTest {

    private val credentials = Credentials.from(EncryptedValue.fromString("master"))
    private val base = KeePassDatabase.Ver4x.create(
        rootName = "Root",
        meta = Meta(recycleBinEnabled = true),
        credentials = credentials
    )
    private val root = base.content.group.uuid
    private val web = UUID.randomUUID()
    private val mail = UUID.randomUUID()
    private val github = UUID.randomUUID()
    private val inbox = UUID.randomUUID()

    @Test
    fun changesToDifferentEntriesOnEachSideAreAllKept() {
        val local = vault(group(web, "Web", entry(github, "v2", T1, history = listOf(entry(github, "v1", T0))), entry(inbox, "c1", T0)))
        val added = UUID.randomUUID()
        val remote = vault(group(web, "Web", entry(github, "v1", T0), entry(inbox, "c2", T2, history = listOf(entry(inbox, "c1", T0))), entry(added, "new", T2)))

        val merged = VaultMerger.merge(local, remote)

        assertEquals("v2", merged.password(github))
        assertEquals(listOf("v1"), merged.history(github))
        assertEquals("c2", merged.password(inbox))
        assertEquals(listOf("c1"), merged.history(inbox))
        assertEquals(web, merged.getEntry { it.uuid == added }!!.first.uuid)
    }

    @Test
    fun sameEntryEditedOnBothSidesKeepsTheNewerAndFilesTheOtherInHistory() {
        val original = entry(github, "v1", T0)
        val local = vault(group(web, "Web", entry(github, "mine", T2, history = listOf(original))))
        val remote = vault(group(web, "Web", entry(github, "theirs", T1, history = listOf(original))))

        val merged = VaultMerger.merge(local, remote)

        assertEquals("mine", merged.password(github))
        assertEquals(listOf("v1", "theirs"), merged.history(github))
        // Either side doing the merge reaches the same result.
        val reversed = VaultMerger.merge(remote, local)
        assertEquals("mine", reversed.password(github))
        assertEquals(listOf("v1", "theirs"), reversed.history(github))
    }

    @Test
    fun deletionsWinUnlessTheEntryChangedAfterwards() {
        val local = vault(group(web, "Web", entry(github, "untouched", T0), entry(inbox, "edited later", T2)))
        val remote = vault(
            group(web, "Web"),
            deleted = listOf(DeletedObject(github, T1), DeletedObject(inbox, T1))
        )

        val merged = VaultMerger.merge(local, remote)

        assertNull(merged.getEntry { it.uuid == github })
        assertEquals("edited later", merged.password(inbox))
        assertEquals(listOf(github), merged.content.deletedObjects.map { it.id })
    }

    @Test
    fun theLaterMoveDecidesWhereAnEntryLives() {
        val local = vault(group(web, "Web", entry(github, "edited here", T2)), group(mail, "Mail"))
        val remote = vault(group(web, "Web"), group(mail, "Mail", entry(github, "v1", T0, moved = T1)))

        val merged = VaultMerger.merge(local, remote)

        val (group, entry) = merged.getEntry { it.uuid == github }!!
        assertEquals(mail, group.uuid)
        assertEquals("edited here", entry.fields["Password"]?.content)
    }

    @Test
    fun entriesOfAGroupDeletedElsewhereMoveToTheRootWhenStillNeeded() {
        val newGroup = UUID.randomUUID()
        val local = vault(
            group(web, "Web", entry(github, "edited later", T2), entry(inbox, "c1", T0)),
            group(newGroup, "Added here", entry(UUID.randomUUID(), "x", T2))
        )
        val remote = vault(deleted = listOf(DeletedObject(web, T1), DeletedObject(github, T1), DeletedObject(inbox, T1)))

        val merged = VaultMerger.merge(local, remote)

        assertEquals(root, merged.getEntry { it.uuid == github }!!.first.uuid)
        assertNull(merged.getEntry { it.uuid == inbox })
        assertEquals(listOf(newGroup), merged.content.group.groups.map { it.uuid })
    }

    @Test
    fun groupsMovedIntoEachOtherOnTwoDevicesStayReachable() {
        val local = vault(group(web, "Web", group(mail, "Mail", moved = T1)))
        val remote = vault(group(mail, "Mail", group(web, "Web", moved = T2)))

        val merged = VaultMerger.merge(local, remote)

        val all = merged.content.group.flatten()
        assertTrue(all.containsAll(listOf(web, mail)))
        assertEquals(3, all.size)
    }

    @Test
    fun attachmentsAndRecycleBinFromTheOtherCopyComeAlong() {
        val bin = UUID.randomUUID()
        val file = BinaryData.Uncompressed(false, "secret".toByteArray())
        val withAttachment = entry(github, "v1", T1).copy(binaries = listOf(BinaryReference(file.hash, "key.txt")))
        val remote = vault(group(web, "Web", withAttachment), group(bin, "Recycle Bin"))
            .modifyContent { copy(meta = meta.copy(recycleBinUuid = bin, recycleBinChanged = T1)) }
            .modifyBinaries { it + (file.hash to file) }

        val merged = VaultMerger.merge(vault(), remote)

        assertNotNull(merged.binaries[file.hash])
        assertEquals(bin, merged.content.meta.recycleBinUuid)
    }

    @Test
    fun aCopyReadBackFromDiskAddsNoHistory() {
        // This session's times have sub-second parts the saved file drops.
        val now = Instant.now()
        val local = vault(group(web, "Web", entry(github, "v2", now, history = listOf(entry(github, "v1", T0)))))
        val remote = KeePassDatabase.decode(ByteArrayInputStream(local.encodeToByteArray()), credentials)

        val merged = VaultMerger.merge(local, remote)

        assertEquals("v2", merged.password(github))
        assertEquals(listOf("v1"), merged.history(github))
    }

    @Test
    fun aDifferentVaultIsRejected() {
        val other = KeePassDatabase.Ver4x.create(rootName = "Root", meta = Meta(), credentials = credentials)

        assertThrows(UnrelatedVaultException::class.java) { VaultMerger.merge(base, other) }
    }

    private fun vault(vararg children: Group, deleted: List<DeletedObject> = emptyList()): KeePassDatabase =
        base.modifyContent { copy(group = group.copy(groups = children.toList()), deletedObjects = deleted) }

    private fun group(uuid: UUID, name: String, vararg children: Any, moved: Instant = T0) = Group(
        uuid = uuid,
        name = name,
        times = times(T0, moved),
        groups = children.filterIsInstance<Group>(),
        entries = children.filterIsInstance<Entry>()
    )

    private fun entry(uuid: UUID, password: String, modified: Instant, moved: Instant = T0, history: List<Entry> = emptyList()) = Entry(
        uuid = uuid,
        times = times(modified, moved),
        fields = EntryFields(mapOf("Title" to EntryValue.Plain("Entry"), "Password" to EntryValue.Plain(password))),
        history = history
    )

    private fun times(modified: Instant, moved: Instant) =
        TimeData.create(T0).copy(lastModificationTime = modified, locationChanged = moved)

    private fun KeePassDatabase.password(uuid: UUID) = getEntry { it.uuid == uuid }?.second?.fields?.get("Password")?.content

    private fun KeePassDatabase.history(uuid: UUID) =
        getEntry { it.uuid == uuid }!!.second.history.map { it.fields["Password"]?.content }

    private fun Group.flatten(): List<UUID> = listOf(uuid) + groups.flatMap { it.flatten() }

    private companion object {
        val T0: Instant = Instant.parse("2026-01-01T10:00:00Z")
        val T1: Instant = T0.plusSeconds(60)
        val T2: Instant = T0.plusSeconds(120)
    }
}
