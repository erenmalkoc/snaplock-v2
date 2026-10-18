package com.erenium.snaplock.data.network

import com.erenium.snaplock.domain.passwords.BreachChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Have I Been Pwned's Pwned Passwords range API with k-anonymity: only the first five hex
 * characters of the password's SHA-1 leave the device, and padded responses hide how many
 * hashes matched. https://haveibeenpwned.com/API/v3#PwnedPasswords
 */
@Singleton
class PwnedPasswordsClient @Inject constructor() : BreachChecker {

    override suspend fun breachCount(password: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val hash = sha1Hex(password)
            val connection = URL(RANGE_URL + hash.take(PREFIX_LENGTH)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("Add-Padding", "true")
                connection.setRequestProperty("User-Agent", "Snaplock")
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
                connection.inputStream.bufferedReader().use { countIn(it.readText(), hash.drop(PREFIX_LENGTH)) }
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val RANGE_URL = "https://api.pwnedpasswords.com/range/"
        private const val PREFIX_LENGTH = 5
        private const val TIMEOUT_MILLIS = 10_000

        fun sha1Hex(password: String): String =
            MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02X".format(it) }

        /** Reads "SUFFIX:COUNT" lines; padding lines have a count of 0. */
        fun countIn(body: String, suffix: String): Int =
            body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.substringBefore(':').equals(suffix, ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.toIntOrNull()
                ?: 0
    }
}
