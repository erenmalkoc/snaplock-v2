package com.erenium.snaplock.data

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.erenium.snaplock.data.datasource.prefs.RecentDatabasesPrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentDatabasesPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun addPutsNewestFirstWithoutDuplicates() {
        val prefs = RecentDatabasesPrefs(context)

        prefs.add(uri(1))
        prefs.add(uri(2))
        prefs.add(uri(1))

        assertEquals(listOf(uri(1), uri(2)), prefs.uris.value)
    }

    @Test
    fun keepsOnlyTheFiveMostRecent() {
        val prefs = RecentDatabasesPrefs(context)

        (1..7).forEach { prefs.add(uri(it)) }

        assertEquals((7 downTo 3).map { uri(it) }, prefs.uris.value)
    }

    @Test
    fun removeDropsOnlyThatDatabase() {
        val prefs = RecentDatabasesPrefs(context)
        prefs.add(uri(1))
        prefs.add(uri(2))

        prefs.remove(uri(1))

        assertEquals(listOf(uri(2)), prefs.uris.value)
    }

    @Test
    fun listSurvivesANewInstance() {
        RecentDatabasesPrefs(context).apply {
            add(uri(1))
            add(uri(2))
        }

        assertEquals(listOf(uri(2), uri(1)), RecentDatabasesPrefs(context).uris.value)
    }

    private fun uri(n: Int) =
        "content://com.android.externalstorage.documents/document/primary%3Avault$n.kdbx".toUri()
}
