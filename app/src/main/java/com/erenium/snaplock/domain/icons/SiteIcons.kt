package com.erenium.snaplock.domain.icons

import com.erenium.snaplock.domain.repository.KdbxRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.UUID
import javax.inject.Inject

/** How many entries got an icon, and which sites were asked but had none to give. */
data class IconDownload(val added: Int, val failedHosts: Set<String>)

/** Fetches a site's icon as a small PNG, or null when the site has none that decodes. */
fun interface SiteIconSource {
    suspend fun iconFor(host: String): ByteArray?
}

object SiteHosts {

    private val ipv4 = Regex("^[0-9]{1,3}([.][0-9]{1,3}){3}$")

    /**
     * The web host an entry URL points at, or null when there is nothing public to ask:
     * other schemes, IP addresses, localhost and single-label LAN names.
     */
    fun hostOf(url: String): String? {
        val text = url.trim()
        if (text.isEmpty()) return null
        val withScheme = if ("://" in text) text else "https://$text"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        return host.takeUnless { it.startsWith("[") || ipv4.matches(it) || it == "localhost" || '.' !in it }
    }
}

/**
 * Downloads icons for entries that have a website but no icon yet and stores them in the
 * vault as KeePass custom icons, the way KeePassXC does. Each site is asked once, directly,
 * and only for its own icon.
 */
class DownloadSiteIconsUseCase @Inject constructor(
    private val repository: KdbxRepository,
    private val source: SiteIconSource
) {
    /** Entries the next run would look up icons for. */
    suspend fun pendingCount(): Int = targets().values.sumOf { it.size }

    /** Returns how many entries got an icon. */
    suspend operator fun invoke(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result<Int> =
        download(onProgress = onProgress).map { it.added }

    /**
     * Looks up every site still missing an icon, except [skipHosts], and reports the sites
     * that had none, so a caller running this repeatedly can leave them alone.
     */
    suspend fun download(
        skipHosts: Set<String> = emptySet(),
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<IconDownload> {
        val byHost = targets().filterKeys { it !in skipHosts }
        var done = 0
        val permits = Semaphore(PARALLEL_DOWNLOADS)
        val results = coroutineScope {
            byHost.map { (host, entries) ->
                async {
                    val icon = permits.withPermit { runCatching { source.iconFor(host) }.getOrNull() }
                    synchronized(this@DownloadSiteIconsUseCase) { onProgress(++done, byHost.size) }
                    host to icon?.let { png -> entries.map { it to png } }
                }
            }.awaitAll()
        }
        val failed = results.filter { it.second == null }.map { it.first }.toSet()
        val found = results.flatMap { it.second.orEmpty() }.toMap()
        if (found.isEmpty()) return Result.success(IconDownload(0, failed))
        return repository.setEntryIcons(found).map { IconDownload(found.size, failed) }
    }

    private suspend fun targets(): Map<String, List<UUID>> =
        repository.getCredentials()
            .filter { it.icon == null }
            .mapNotNull { credential -> credential.urls.firstNotNullOfOrNull(SiteHosts::hostOf)?.let { it to credential.uuid } }
            .groupBy({ it.first }, { it.second })

    private companion object {
        const val PARALLEL_DOWNLOADS = 6
    }
}
