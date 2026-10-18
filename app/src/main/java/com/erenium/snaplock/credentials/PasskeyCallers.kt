package com.erenium.snaplock.credentials

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.CallingAppInfo
import com.erenium.snaplock.domain.passkeys.WebAuthn

/**
 * Browsers allowed to pass their own client data for any web origin: Google's list from
 * https://www.gstatic.com/gpm-passkeys-privileged-apps/apps.json, bundled as an asset.
 * Only these may make Snaplock sign for a website; every other app signs as itself.
 */
private const val PRIVILEGED_BROWSERS = "privileged_browsers.json"

@Volatile
private var privilegedBrowsers: String? = null

private fun privilegedBrowsers(context: Context): String =
    privilegedBrowsers ?: context.assets.open(PRIVILEGED_BROWSERS).bufferedReader().use { it.readText() }
        .also { privilegedBrowsers = it }

/**
 * Who a passkey request comes from. A privileged browser speaks for the web origin it
 * names; any other app gets its signing certificate as the origin, which sites check
 * against the apps they trust, and a client data hash it passes is ignored.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun passkeyCaller(context: Context, app: CallingAppInfo, clientDataHash: ByteArray?): PasskeyCaller {
    val webOrigin = runCatching { app.getOrigin(privilegedBrowsers(context)) }.getOrNull()
    return if (webOrigin != null) {
        PasskeyCaller(webOrigin.trimEnd('/'), app.packageName, clientDataHash)
    } else {
        PasskeyCaller(apkKeyHashOrigin(app), app.packageName)
    }
}

/** "android:apk-key-hash:" and the SHA-256 of the app's signing certificate, base64url. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun apkKeyHashOrigin(app: CallingAppInfo): String {
    val certificate = app.signingInfo.apkContentsSigners.firstOrNull()?.toByteArray()
        ?: throw IllegalStateException("The calling app has no signing certificate")
    return "android:apk-key-hash:" + WebAuthn.base64Url(WebAuthn.sha256(certificate))
}
