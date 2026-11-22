package com.erenium.snaplock.domain.text

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TextFoldingTest {
    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun turkishLettersLoseTheirMarks() {
        assertEquals("sifre", "Şifre".foldForSearch())
        assertEquals("cagri", "ÇAĞRI".foldForSearch())
        assertEquals("ogretmen", "Öğretmen".foldForSearch())
        assertEquals("guzel", "güzel".foldForSearch())
    }

    @Test
    fun bothFormsOfTheTurkishIBecomeAPlainI() {
        assertEquals("istanbul", "İstanbul".foldForSearch())
        assertEquals("isparta", "ISPARTA".foldForSearch())
        assertEquals("isi", "ısı".foldForSearch())
        assertEquals("isik", "IŞIK".foldForSearch())
    }

    @Test
    fun otherAccentsAreDroppedToo() {
        assertEquals("cafe muller angstrom", "Café Müller Ångström".foldForSearch())
    }

    @Test
    fun theDeviceLanguageDoesNotChangeTheResult() {
        val results = listOf("tr", "en", "de").map { tag ->
            withLocale(Locale.forLanguageTag(tag)) { "ISPARTA İŞ Ilık".foldForSearch() }
        }

        assertEquals(listOf("isparta is ilik", "isparta is ilik", "isparta is ilik"), results)
    }

    @Test
    fun plainTextDigitsAndPunctuationStayAsTheyAre() {
        assertEquals("", "".foldForSearch())
        assertEquals("github.com/login?x=1", "github.com/login?x=1".foldForSearch())
        assertEquals("4711 - 08:15", "4711 - 08:15".foldForSearch())
    }

    @Test
    fun foldingTwiceChangesNothing() {
        listOf("Şifre Kasası", "İĞNE", "naïve café", "plain").forEach {
            assertEquals(it, it.foldForSearch(), it.foldForSearch().foldForSearch())
        }
    }
}
