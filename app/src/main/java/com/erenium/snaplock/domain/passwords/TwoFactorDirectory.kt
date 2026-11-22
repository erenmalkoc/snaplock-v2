package com.erenium.snaplock.domain.passwords

import java.net.URI

interface TwoFactorDirectory {
    /** True when the website behind [url] is known to offer authenticator app codes. */
    fun offersTotp(url: String?): Boolean
}

/**
 * A fixed list of website domains. A login matches when its host is one of them or a subdomain of
 * one, so "accounts.example.com" is covered by "example.com". Only http and https addresses count,
 * app links, IP addresses and single-word hosts never do.
 */
class TwoFactorDomains(private val domains: Set<String>) : TwoFactorDirectory {
    override fun offersTotp(url: String?): Boolean {
        var candidate = hostOf(url) ?: return false
        while ('.' in candidate) {
            if (candidate in domains) return true
            candidate = candidate.substringAfter('.')
        }
        return false
    }

    companion object {
        /** One domain per line, blank lines and lines starting with # are skipped. */
        fun parse(lines: Sequence<String>): TwoFactorDomains = TwoFactorDomains(
            lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        )

        private fun hostOf(url: String?): String? {
            val trimmed = url?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
            if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val host = runCatching { URI(withScheme).host }.getOrNull()
                ?: withScheme.substringAfter("://").substringBefore('/').substringBefore('?')
                    .substringBefore('#').substringAfterLast('@').substringBefore(':')
            val clean = host.lowercase().trimEnd('.')
            if ('.' !in clean || clean.all { it.isDigit() || it == '.' }) return null
            return clean
        }
    }
}
