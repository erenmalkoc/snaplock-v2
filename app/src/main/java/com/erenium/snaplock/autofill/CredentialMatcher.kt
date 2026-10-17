package com.erenium.snaplock.autofill

import com.erenium.snaplock.domain.model.Credential
import java.net.URI

/** Picks the entries that belong to the app or website asking for autofill. */
object CredentialMatcher {

    /**
     * Websites match on their registrable domain, so an entry saved for login.example.com
     * also fills www.example.com. The web domain is only trusted when a known browser
     * reports it: any app can show a real site in a WebView and read what gets filled.
     * Everything else matches on the package name.
     */
    fun match(
        credentials: List<Credential>,
        packageName: String,
        webDomain: String?
    ): List<Credential> {
        val trustedDomain = webDomain?.takeIf { Browsers.isTrusted(packageName) }?.let(::hostOf)
        return if (trustedDomain != null) {
            val requestBase = Domains.registrableDomain(trustedDomain)
            credentials
                .mapNotNull { credential ->
                    val hosts = credential.urls.mapNotNull(::hostOf)
                    when {
                        hosts.any { it == trustedDomain } -> credential to EXACT_HOST
                        hosts.any { Domains.registrableDomain(it) == requestBase } -> credential to SAME_SITE
                        else -> null
                    }
                }
                .sortedWith(compareBy({ it.second }, { it.first.title.lowercase() }))
                .map { it.first }
        } else {
            credentials
                .filter { packageName in it.androidApps }
                .sortedBy { it.title.lowercase() }
        }
    }

    /** Host of a URL or bare domain, lower case, without a leading "www.". */
    fun hostOf(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val host = runCatching { URI(withScheme).host }.getOrNull()
            ?: withScheme.substringAfter("://").substringBefore('/').substringBefore(':')
        return host.lowercase().removePrefix("www.").trimEnd('.').takeIf { it.isNotEmpty() }
    }

    private const val EXACT_HOST = 0
    private const val SAME_SITE = 1
}

object Domains {

    // Two-label public suffixes common enough to matter; anything else is treated as a
    // single-label TLD. Not the full public suffix list, which would cost ~200 KB.
    private val multiLabelSuffixes = setOf(
        "com.tr", "net.tr", "org.tr", "edu.tr", "gov.tr", "k12.tr", "bel.tr", "gen.tr", "av.tr",
        "biz.tr", "info.tr", "web.tr", "tsk.tr", "pol.tr",
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp",
        "com.br", "net.br", "org.br", "gov.br",
        "co.nz", "co.za", "co.in", "co.kr", "co.id", "com.cn", "com.mx", "com.ar", "com.sg",
        "com.hk", "com.tw", "com.my", "com.ph", "com.pk", "com.sa", "com.eg", "com.ua", "co.il",
        "github.io", "gitlab.io", "herokuapp.com", "vercel.app", "netlify.app", "pages.dev",
        "blogspot.com", "appspot.com", "web.app", "firebaseapp.com", "azurewebsites.net"
    )

    fun registrableDomain(host: String): String {
        val labels = host.lowercase().trimEnd('.').split('.')
        if (labels.size <= 2 || labels.all { it.all(Char::isDigit) }) return labels.joinToString(".")
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in multiLabelSuffixes) labels.takeLast(3).joinToString(".") else lastTwo
    }
}

object Browsers {

    private val trusted = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.google.android.apps.chrome",
        "org.chromium.chrome", "org.chromium.webview_shell",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.fennec_aurora",
        "org.mozilla.focus", "org.mozilla.klar", "org.mozilla.fennec_fdroid",
        "io.github.forkmaintainers.iceraven", "org.ironfoxoss.ironfox", "us.spotco.fennec_dos",
        "org.torproject.torbrowser", "org.gnu.icecat",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.microsoft.emmx", "com.microsoft.emmx.beta",
        "com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native", "com.opera.gx",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.vivaldi.browser", "com.vivaldi.browser.snapshot",
        "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.ecosia.android",
        "org.bromite.bromite", "org.cromite.cromite", "app.vanadium.browser",
        "com.yandex.browser", "com.yandex.browser.beta", "mark.via.gp", "mark.via",
        "com.mi.globalbrowser", "com.huawei.browser", "com.cloudmosa.puffinFree",
        "com.stoutner.privacybrowser.standard", "org.mozilla.reference.browser",
        "net.slions.fulguris.full.fdroid", "acr.browser.lightning", "com.qwant.liberty"
    )

    fun isTrusted(packageName: String): Boolean = packageName in trusted
}
