package com.erenium.snaplock.data.datasource.local

import android.content.Context
import com.erenium.snaplock.domain.passwords.WordList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetWordList @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WordList {
    override val words: List<String> by lazy {
        context.assets.open(FILE).bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.toList() }
    }

    private companion object {
        const val FILE = "eff_large_wordlist.txt"
    }
}
