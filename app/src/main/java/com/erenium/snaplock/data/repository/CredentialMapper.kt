package com.erenium.snaplock.data.repository

import app.keemobile.kotpass.models.CustomIcon
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.model.EntryIcon
import com.erenium.snaplock.domain.otp.OtpParser
import java.util.UUID
import app.keemobile.kotpass.models.Entry as KotpassEntry

// Field names other KeePass clients use to link entries to apps and extra sites:
// KeePassDX stores package names in "AndroidApp", "AndroidApp_1", ...; KeePassXC and
// Keepass2Android store more URLs in "KP2A_URL", "KP2A_URL_1", ..., where Keepass2Android
// writes apps as androidapp://<package>.
internal const val FIELD_URL = "URL"
internal const val FIELD_ANDROID_APP = "AndroidApp"
internal const val FIELD_EXTRA_URL = "KP2A_URL"
internal const val ANDROID_APP_SCHEME = "androidapp://"

internal fun KotpassEntry.toCredential(
    untitledTitle: String,
    icons: Map<UUID, CustomIcon> = emptyMap()
): Credential {
    val urlValues = fields
        .filterKeys { it == FIELD_URL || it.isNumberedField(FIELD_EXTRA_URL) }
        .values.map { it.content.trim() }
        .filter { it.isNotEmpty() }
    val appsFromUrls = urlValues
        .filter { it.startsWith(ANDROID_APP_SCHEME, ignoreCase = true) }
        .map { it.substring(ANDROID_APP_SCHEME.length).trim('/') }
    val appsFromFields = fields
        .filterKeys { it.isNumberedField(FIELD_ANDROID_APP) }
        .values.map { it.content.trim() }
    return Credential(
        uuid = uuid,
        title = fields.title?.content?.takeIf { it.isNotBlank() } ?: untitledTitle,
        username = fields.userName?.content?.takeIf { it.isNotEmpty() },
        password = fields.password?.content?.takeIf { it.isNotEmpty() },
        urls = urlValues.filterNot { it.startsWith(ANDROID_APP_SCHEME, ignoreCase = true) },
        androidApps = (appsFromFields + appsFromUrls).filter { it.isNotEmpty() }.distinct(),
        otp = OtpParser.fromFields(fields.entries.associate { it.key to it.value.content }),
        icon = iconFrom(icons)
    )
}

/** The entry's custom icon, if it has one the vault still holds. */
internal fun KotpassEntry.iconFrom(icons: Map<UUID, CustomIcon>): EntryIcon? =
    customIconUuid?.let { id -> icons[id]?.let { EntryIcon(id, it.data) } }

/** Fields that link an entry to apps and extra sites; edited through autofill, not as custom fields. */
internal fun String.isLinkField(): Boolean =
    isNumberedField(FIELD_ANDROID_APP) || isNumberedField(FIELD_EXTRA_URL)

/** True for [base] itself and for [base]_1, [base]_2, ... */
private fun String.isNumberedField(base: String): Boolean =
    this == base || (startsWith("${base}_") && substring(base.length + 1).let { suffix ->
        suffix.isNotEmpty() && suffix.all(Char::isDigit)
    })
