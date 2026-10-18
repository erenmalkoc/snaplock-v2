package com.erenium.snaplock.domain.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconLinksTest {

    @Test
    fun theLargestIconComesFirst() {
        val html = """
            <head>
              <link rel="icon" class="js-site-favicon" type="image/png" href="https://github.githubassets.com/favicons/favicon.png">
              <link rel="apple-touch-icon" href="https://github.githubassets.com/apple-touch-icon.png">
              <link rel="icon" sizes="192x192" href="/icons/icon-192.png">
              <link rel="stylesheet" href="/site.css">
            </head>
        """.trimIndent()

        val icons = IconLinks.parse(html, "https://github.com/login")

        assertEquals(
            listOf(
                IconCandidate("https://github.com/icons/icon-192.png", 192),
                IconCandidate("https://github.githubassets.com/apple-touch-icon.png", 180),
                IconCandidate("https://github.githubassets.com/favicons/favicon.png", 32)
            ),
            icons
        )
    }

    @Test
    fun relativeLinksSingleQuotesAndShortcutIconsAreRead() {
        val html = "<LINK REL='shortcut icon' HREF='../img/fav.ico'><link href=\"touch.png\" rel=\"apple-touch-icon-precomposed\" sizes=\"152x152\">"

        val icons = IconLinks.parse(html, "https://example.com/app/page.html")

        assertEquals(
            listOf(
                IconCandidate("https://example.com/app/touch.png", 152),
                IconCandidate("https://example.com/img/fav.ico", 32)
            ),
            icons
        )
    }

    @Test
    fun svgAndUnusableLinksAreSkipped() {
        val html = """
            <link rel="icon" type="image/svg+xml" href="/icon.svg">
            <link rel="mask-icon" href="/mask.svg" color="#000">
            <link rel="icon" href="javascript:alert(1)">
            <link rel="icon" href="">
            <link rel="icon" sizes="any" href="data:image/png;base64,iVBORw0KGgo=">
        """.trimIndent()

        val icons = IconLinks.parse(html, "https://example.com/")

        assertEquals(listOf(IconCandidate("data:image/png;base64,iVBORw0KGgo=", 32)), icons)
    }

    @Test
    fun multipleSizesCountTheLargest() {
        val icons = IconLinks.parse("<link rel=icon sizes=\"16x16 32x32 64x64\" href=/f.ico>", "https://example.com")

        assertEquals(listOf(IconCandidate("https://example.com/f.ico", 64)), icons)
    }

    @Test
    fun pagesWithoutIconLinksGiveNothing() {
        assertTrue(IconLinks.parse("<html><head><title>x</title></head></html>", "https://example.com/").isEmpty())
    }
}
