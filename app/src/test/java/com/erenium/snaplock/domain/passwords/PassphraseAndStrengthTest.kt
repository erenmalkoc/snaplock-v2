package com.erenium.snaplock.domain.passwords

import com.erenium.snaplock.domain.model.PassphraseOptions
import com.erenium.snaplock.domain.usecase.GeneratePassphraseUseCase
import com.erenium.snaplock.domain.usecase.GeneratePasswordUseCase
import com.erenium.snaplock.domain.model.PasswordOptions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseAndStrengthTest {

    /** The wordlist the app ships, read straight from the module's assets. */
    private val eff = object : WordList {
        override val words: List<String> by lazy {
            File("src/main/assets/eff_large_wordlist.txt").readLines().filter { it.isNotBlank() }
        }
    }
    private val estimator = PasswordStrengthEstimator(eff)

    @Test
    fun shippedWordlistIsTheFullEffList() {
        assertEquals(7776, eff.words.size)
        assertEquals(7776, eff.words.toSet().size)
    }

    @Test
    fun passphrasesFollowTheOptions() {
        val generate = GeneratePassphraseUseCase(eff)

        val phrase = generate(PassphraseOptions(wordCount = 6, separator = ".", capitalize = true, includeNumber = true))

        val words = phrase.split(".")
        assertEquals(6, words.size)
        assertTrue(words.all { it.first().isUpperCase() })
        assertEquals(1, phrase.count(Char::isDigit))
        assertTrue(words.all { it.trimEnd { c -> c.isDigit() }.lowercase() in eff.words })
    }

    @Test
    fun plainPassphraseAndClampedWordCount() {
        val generate = GeneratePassphraseUseCase(eff)

        val phrase = generate(PassphraseOptions(wordCount = 1, separator = " ", capitalize = false, includeNumber = false))

        assertEquals(GeneratePassphraseUseCase.MIN_WORDS, phrase.split(" ").size)
        assertTrue(phrase == phrase.lowercase() && phrase.none(Char::isDigit))
    }

    @Test
    fun commonAndPatternedPasswordsAreWeak() {
        listOf("123456", "password", "Galatasaray", "qwerty123").forEach {
            assertEquals(it, PasswordStrength.VERY_WEAK, estimator.estimate(it).strength)
        }
        assertTrue(estimator.estimate("aaaaaaaaaaaa").strength <= PasswordStrength.WEAK)
        assertTrue(estimator.estimate("abcdefgh1234").strength <= PasswordStrength.WEAK)
        assertTrue(estimator.estimate("qwertyuiopasdf").entropyBits < estimator.estimate("hgkwvyrnbpzemq").entropyBits)
    }

    @Test
    fun passphrasesAreRatedByWordCount() {
        val four = eff.words.take(4).joinToString("-")
        val seven = eff.words.takeLast(7).joinToString("-")

        assertEquals(PasswordStrength.FAIR, estimator.estimate(four).strength)
        assertEquals(PasswordStrength.VERY_STRONG, estimator.estimate(seven).strength)
        assertEquals(4 * kotlin.math.log2(7776.0), estimator.estimate(four).entropyBits, 0.01)
    }

    @Test
    fun generatedPasswordsAreStrong() {
        val password = GeneratePasswordUseCase()(PasswordOptions(length = 20, useSymbols = true))

        assertEquals(PasswordStrength.VERY_STRONG, estimator.estimate(password).strength)
        assertEquals(PasswordStrength.VERY_WEAK, estimator.estimate("").strength)
    }
}
