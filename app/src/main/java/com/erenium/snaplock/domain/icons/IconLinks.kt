package com.erenium.snaplock.domain.icons

import java.net.URI

/** An icon a page declares, with the size it claims (0 when it states none). */
data class IconCandidate(val url: String, val size: Int)

/**
 * Finds the icons a web page declares in its `<link>` tags, largest first, so Snaplock can
 * store the sharpest one instead of the small favicon.ico.
 */
object IconLinks {

    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([a-zA-Z-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")
    private val sizeValue = Regex("(\\d+)[xX](\\d+)")

    /** Apple touch icons without a size are 180 px; plain icons without one are usually 32 px. */
    private const val APPLE_TOUCH_DEFAULT = 180
    private const val ICON_DEFAULT = 32

    fun parse(html: String, pageUrl: String): List<IconCandidate> {
        val base = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        return linkTag.findAll(html).mapNotNull { tag ->
            val attributes = attribute.findAll(tag.value).associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[2].trim('"', '\'').trim()
            }
            val rel = attributes["rel"]?.lowercase()?.split(Regex("\\s+")).orEmpty()
            val appleTouch = rel.any { it.startsWith("apple-touch-icon") }
            if (!appleTouch && "icon" !in rel) return@mapNotNull null
            val href = attributes["href"]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // Android cannot decode SVG icons into a bitmap.
            if (attributes["type"]?.contains("svg") == true || href.substringBefore('?').endsWith(".svg", ignoreCase = true)) {
                return@mapNotNull null
            }
            val url = resolve(base, href) ?: return@mapNotNull null
            val declared = attributes["sizes"]?.let { sizes ->
                sizeValue.findAll(sizes).maxOfOrNull { maxOf(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            }
            IconCandidate(url, declared ?: if (appleTouch) APPLE_TOUCH_DEFAULT else ICON_DEFAULT)
        }.distinctBy { it.url }.sortedByDescending { it.size }.toList()
    }

    /** An absolute https or http URL, or a base64 data URI; null for anything else. */
    private fun resolve(base: URI, href: String): String? {
        if (href.startsWith("data:image/", ignoreCase = true)) return href.takeIf { ";base64," in it }
        val resolved = runCatching { base.resolve(href.replace(" ", "%20")) }.getOrNull() ?: return null
        return resolved.toString().takeIf { resolved.scheme == "https" || resolved.scheme == "http" }
    }
}
