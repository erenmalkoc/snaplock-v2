package com.erenium.snaplock.ui.screens.licenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenSourceLibrariesTest {
    private val everything = OpenSourceLibraries + OpenSourceData

    private fun catalogGroups(): Map<String, String> {
        val groups = mutableMapOf<String, String>()
        File("../gradle/libs.versions.toml").readLines().forEach { line ->
            val alias = Regex("""^([\w-]+)\s*=\s*\{""").find(line)?.groupValues?.get(1) ?: return@forEach
            val module = Regex("""module\s*=\s*"([^":]+):""").find(line)?.groupValues?.get(1)
            val group = Regex("""group\s*=\s*"([^"]+)"\s*,""").find(line)?.groupValues?.get(1)
            (module ?: group)?.let { groups[alias] = it }
        }
        return groups
    }

    private fun shippedAliases(): List<String> =
        File("build.gradle.kts").readLines()
            .filter { Regex("""^\s*(implementation|coreLibraryDesugaring)\(""").containsMatchIn(it) }
            .flatMap { line ->
                Regex("""libs\.([\w.]+)""").findAll(line).map { it.groupValues[1].replace('.', '-') }.toList()
            }

    @Test
    fun everyEntryNamesItsLicenseAndAnHttpsHome() {
        everything.forEach {
            assertTrue("${it.name} name", it.name.isNotBlank())
            assertTrue("${it.name} license", it.license.isNotBlank())
            assertTrue("${it.name} url", it.url.startsWith("https://"))
        }
    }

    @Test
    fun namesAreUnique() {
        assertEquals(everything.size, everything.map { it.name }.toSet().size)
    }

    @Test
    fun theBuildShipsDependenciesTheCatalogCanName() {
        assertTrue(shippedAliases().isNotEmpty())
        assertTrue(catalogGroups().isNotEmpty())
    }

    @Test
    fun everyShippedDependencyIsCovered() {
        val catalog = catalogGroups()
        val uncovered = shippedAliases().filter { alias ->
            val group = catalog[alias]
            group == null || OpenSourceLibraries.none { library -> library.groups.any { group.startsWith(it) } }
        }
        assertTrue("missing from the licenses list: $uncovered", uncovered.isEmpty())
    }
}
