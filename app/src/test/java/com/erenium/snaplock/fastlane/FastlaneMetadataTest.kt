package com.erenium.snaplock.fastlane

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class FastlaneMetadataTest {
    private val root = File("../fastlane/metadata/android")

    private val tagsFdroidRenders = setOf(
        "b", "big", "blockquote", "br", "cite", "em", "i", "li", "ol", "small", "strike", "strong", "sub", "sup", "tt", "u", "ul"
    )

    private val textNames = listOf("title.txt", "short_description.txt", "full_description.txt")

    private val locales: List<File>
        get() = root.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }

    private fun File.body(): String = readText(Charsets.UTF_8)

    private fun File.changelogs(): List<File> =
        resolve("changelogs").listFiles { file -> file.isFile }.orEmpty().sortedBy { it.name }

    private fun currentVersionCode(): Int =
        Regex("""versionCode\s*=\s*(\d+)""").find(File("build.gradle.kts").readText())!!.groupValues[1].toInt()

    private fun assertImageSize(file: File, width: Int, height: Int) {
        val image = ImageIO.read(file)
        assertEquals("${file.path} size", width to height, image.width to image.height)
    }

    @Test
    fun theFallbackLocaleExists() {
        assertTrue(root.resolve("en-US").isDirectory)
    }

    @Test
    fun everyLocaleHasTheMandatoryTexts() {
        locales.forEach { locale ->
            textNames.forEach { name ->
                assertTrue("${locale.name}/$name is missing", locale.resolve(name).isFile)
            }
        }
    }

    @Test
    fun textsStayWithinTheFdroidLimits() {
        locales.forEach { locale ->
            assertTrue("${locale.name} title", locale.resolve("title.txt").body().trim().length <= 50)
            assertTrue("${locale.name} short description", locale.resolve("short_description.txt").body().trim().length <= 80)
            assertTrue("${locale.name} full description", locale.resolve("full_description.txt").body().trim().length <= 4000)
            locale.changelogs().forEach {
                assertTrue("${locale.name}/changelogs/${it.name}", it.body().trim().length <= 500)
            }
        }
    }

    @Test
    fun descriptionsUseOnlyTagsFdroidRenders() {
        locales.forEach { locale ->
            val used = Regex("""</?\s*([a-zA-Z][a-zA-Z0-9]*)""")
                .findAll(locale.resolve("full_description.txt").body())
                .map { it.groupValues[1].lowercase() }
                .toSet()
            assertTrue("${locale.name} uses ${used - tagsFdroidRenders}", tagsFdroidRenders.containsAll(used))
        }
    }

    @Test
    fun descriptionTagsAreBalanced() {
        locales.forEach { locale ->
            val text = locale.resolve("full_description.txt").body()
            (tagsFdroidRenders - "br").forEach { tag ->
                val opened = Regex("""<$tag(\s[^>]*)?>""").findAll(text).count()
                val closed = Regex("""</$tag\s*>""").findAll(text).count()
                assertEquals("${locale.name} <$tag>", opened, closed)
            }
        }
    }

    @Test
    fun storeTextsHaveNoSemicolons() {
        locales.forEach { locale ->
            textNames.forEach { name ->
                assertTrue("${locale.name}/$name has a semicolon", ';' !in locale.resolve(name).body())
            }
            locale.changelogs().forEach {
                assertTrue("${locale.name}/changelogs/${it.name} has a semicolon", ';' !in it.body())
            }
        }
    }

    @Test
    fun changelogsAreNamedAfterVersionCodes() {
        locales.forEach { locale ->
            locale.changelogs().forEach {
                assertTrue("${locale.name}/changelogs/${it.name}", Regex("""\d+\.txt""").matches(it.name))
            }
        }
    }

    @Test
    fun everyLocaleHasAChangelogForTheCurrentVersion() {
        val current = currentVersionCode()
        locales.forEach {
            assertTrue("${it.name} lacks changelogs/$current.txt", it.resolve("changelogs/$current.txt").isFile)
        }
    }

    @Test
    fun localesShareTheSameChangelogHistory() {
        val reference = root.resolve("en-US").changelogs().map { it.name }
        locales.forEach { assertEquals(it.name, reference, it.changelogs().map { file -> file.name }) }
    }

    @Test
    fun theFallbackLocaleCarriesTheGraphics() {
        val images = root.resolve("en-US/images")
        assertImageSize(images.resolve("icon.png"), 512, 512)
        assertImageSize(images.resolve("featureGraphic.png"), 1024, 500)
        assertTrue(images.resolve("phoneScreenshots").listFiles().orEmpty().size >= 2)
    }

    @Test
    fun localizedGraphicsKeepTheirSizes() {
        locales.forEach { locale ->
            val images = locale.resolve("images")
            images.resolve("icon.png").takeIf { it.isFile }?.let { assertImageSize(it, 512, 512) }
            images.resolve("featureGraphic.png").takeIf { it.isFile }?.let { assertImageSize(it, 1024, 500) }
        }
    }

    @Test
    fun everyImageIsAReadablePngOrJpeg() {
        locales.flatMap { it.resolve("images").walkTopDown().filter { file -> file.isFile }.toList() }.forEach {
            assertTrue("${it.path} has an unexpected extension", it.extension in setOf("png", "jpg", "jpeg"))
            assertTrue("${it.path} cannot be read as an image", ImageIO.read(it) != null)
        }
    }
}
