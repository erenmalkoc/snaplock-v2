package com.erenium.snaplock.domain.text

import java.text.Normalizer
import java.util.Locale

/**
 * Lowercases and strips accents so a search treats "Şifre", "SIFRE" and "sifre" alike.
 *
 * The result never depends on the device language. The dotted and dotless Turkish i both become a
 * plain i, which is also how people type when their keyboard has no Turkish letters.
 */
fun String.foldForSearch(): String {
    val decomposed = Normalizer.normalize(
        replace('İ', 'i').replace('ı', 'i').lowercase(Locale.ROOT),
        Normalizer.Form.NFD
    )
    return buildString(decomposed.length) {
        for (char in decomposed) {
            when (Character.getType(char)) {
                Character.NON_SPACING_MARK.toInt(),
                Character.COMBINING_SPACING_MARK.toInt(),
                Character.ENCLOSING_MARK.toInt() -> Unit
                else -> append(char)
            }
        }
    }
}
