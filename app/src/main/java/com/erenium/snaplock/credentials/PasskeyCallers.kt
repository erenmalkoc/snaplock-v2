package com.erenium.snaplock.credentials

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.CallingAppInfo
import com.erenium.snaplock.domain.passkeys.WebAuthn

private const val PRIVILEGED_BROWSERS = "privileged_browsers.json"

@Volatile
private var privilegedBrowsers: String? = null

private fun privilegedBrowsers(context: Context): String =
    privilegedBrowsers ?: context.assets.open(PRIVILEGED_BROWSERS).bufferedReader().use { it.readText() }
        .also { privilegedBrowsers = it }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun passkeyCaller(context: Context, app: CallingAppInfo, clientDataHash: ByteArray?): PasskeyCaller {
    val webOrigin = runCatching { app.getOrigin(privilegedBrowsers(context)) }.getOrNull()
    return if (webOrigin != null) {
        PasskeyCaller(webOrigin.trimEnd('/'), app.packageName, clientDataHash)
    } else {
        PasskeyCaller(apkKeyHashOrigin(app), app.packageName)
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun apkKeyHashOrigin(app: CallingAppInfo): String {
    val certificate = app.signingInfo.apkContentsSigners.firstOrNull()?.toByteArray()
        ?: throw IllegalStateException("The calling app has no signing certificate")
    return "android:apk-key-hash:" + WebAuthn.base64Url(WebAuthn.sha256(certificate))
}
