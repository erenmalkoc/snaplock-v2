package com.erenium.snaplock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val md_light_primary = Color(0xFF006D41)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFF84FAB5)
val md_light_onPrimaryContainer = Color(0xFF002110)

val md_light_secondary = Color(0xFF516356)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFD4E7D7)
val md_light_onSecondaryContainer = Color(0xFF0F1F15)

val md_light_tertiary = Color(0xFF326667)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFB7ECEC)
val md_light_onTertiaryContainer = Color(0xFF002020)

val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)

val md_light_background = Color(0xFFF8FAF5)
val md_light_onBackground = Color(0xFF191C1A)
val md_light_surface = Color(0xFFF8FAF5)
val md_light_onSurface = Color(0xFF191C1A)
val md_light_surfaceVariant = Color(0xFFDCE5DC)
val md_light_onSurfaceVariant = Color(0xFF414942)
val md_light_outline = Color(0xFF717972)
val md_light_outlineVariant = Color(0xFFC0C9C0)

val md_dark_primary = Color(0xFF67DD9A)
val md_dark_onPrimary = Color(0xFF00391F)
val md_dark_primaryContainer = Color(0xFF00522F)
val md_dark_onPrimaryContainer = Color(0xFF84FAB5)

val md_dark_secondary = Color(0xFFB8CBBB)
val md_dark_onSecondary = Color(0xFF243429)
val md_dark_secondaryContainer = Color(0xFF3A4B3F)
val md_dark_onSecondaryContainer = Color(0xFFD4E7D7)

val md_dark_tertiary = Color(0xFF9BD0D0)
val md_dark_onTertiary = Color(0xFF003737)
val md_dark_tertiaryContainer = Color(0xFF164E4F)
val md_dark_onTertiaryContainer = Color(0xFFB7ECEC)

val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_background = Color(0xFF111412)
val md_dark_onBackground = Color(0xFFE1E3DE)
val md_dark_surface = Color(0xFF111412)
val md_dark_onSurface = Color(0xFFE1E3DE)
val md_dark_surfaceVariant = Color(0xFF414942)
val md_dark_onSurfaceVariant = Color(0xFFC0C9C0)
val md_dark_outline = Color(0xFF8A938B)
val md_dark_outlineVariant = Color(0xFF414942)

val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFF2F4EF)
val md_light_surfaceContainer = Color(0xFFEDEEEA)
val md_light_surfaceContainerHigh = Color(0xFFE7E9E4)
val md_light_surfaceContainerHighest = Color(0xFFE1E3DE)
val md_dark_surfaceContainerLowest = Color(0xFF0C0F0C)
val md_dark_surfaceContainerLow = Color(0xFF191C1A)
val md_dark_surfaceContainer = Color(0xFF1D201E)
val md_dark_surfaceContainerHigh = Color(0xFF282B28)
val md_dark_surfaceContainerHighest = Color(0xFF323532)

@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LightExtendedColors = ExtendedColors(
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB6F0BA),
    onSuccessContainer = Color(0xFF00210A),
    warning = Color(0xFF9A6700),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFE08C),
    onWarningContainer = Color(0xFF2E2000),
)

val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF9BD89B),
    onSuccess = Color(0xFF003912),
    successContainer = Color(0xFF12531F),
    onSuccessContainer = Color(0xFFB6F0BA),
    warning = Color(0xFFF3C04A),
    onWarning = Color(0xFF402D00),
    warningContainer = Color(0xFF5C4300),
    onWarningContainer = Color(0xFFFFE08C),
)

val AvatarColors = listOf(
    Color(0xFF1B5E9B),
    Color(0xFF2E7D5B),
    Color(0xFF6B5B95),
    Color(0xFFB5651D),
    Color(0xFFB0306A),
    Color(0xFF0F8B8D),
    Color(0xFF7A5C00),
    Color(0xFF4A4E8C),
)

fun avatarColorFor(key: String): Color {
    if (key.isEmpty()) return AvatarColors.first()
    val index = (key.hashCode() and Int.MAX_VALUE) % AvatarColors.size
    return AvatarColors[index]
}
