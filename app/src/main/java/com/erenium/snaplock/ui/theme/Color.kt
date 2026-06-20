package com.erenium.snaplock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color


val md_light_primary = Color(0xFF1B5E9B)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFD3E4FF)
val md_light_onPrimaryContainer = Color(0xFF001D36)

val md_light_secondary = Color(0xFF2E7D5B)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFB6F0CE)
val md_light_onSecondaryContainer = Color(0xFF00210F)

val md_light_tertiary = Color(0xFF6B5B95)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFE9DDFF)
val md_light_onTertiaryContainer = Color(0xFF251431)

val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)

val md_light_background = Color(0xFFFDFCFF)
val md_light_onBackground = Color(0xFF1A1C1E)
val md_light_surface = Color(0xFFFDFCFF)
val md_light_onSurface = Color(0xFF1A1C1E)
val md_light_surfaceVariant = Color(0xFFDFE2EB)
val md_light_onSurfaceVariant = Color(0xFF43474E)
val md_light_outline = Color(0xFF73777F)
val md_light_outlineVariant = Color(0xFFC3C7CF)

val md_dark_primary = Color(0xFFA0C9FF)
val md_dark_onPrimary = Color(0xFF003258)
val md_dark_primaryContainer = Color(0xFF00497D)
val md_dark_onPrimaryContainer = Color(0xFFD3E4FF)

val md_dark_secondary = Color(0xFF9AD4B2)
val md_dark_onSecondary = Color(0xFF00391E)
val md_dark_secondaryContainer = Color(0xFF0F532F)
val md_dark_onSecondaryContainer = Color(0xFFB6F0CE)

val md_dark_tertiary = Color(0xFFD2BCFF)
val md_dark_onTertiary = Color(0xFF3B2A47)
val md_dark_tertiaryContainer = Color(0xFF52415F)
val md_dark_onTertiaryContainer = Color(0xFFE9DDFF)

val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_background = Color(0xFF1A1C1E)
val md_dark_onBackground = Color(0xFFE2E2E6)
val md_dark_surface = Color(0xFF1A1C1E)
val md_dark_onSurface = Color(0xFFE2E2E6)
val md_dark_surfaceVariant = Color(0xFF43474E)
val md_dark_onSurfaceVariant = Color(0xFFC3C7CF)
val md_dark_outline = Color(0xFF8D9199)
val md_dark_outlineVariant = Color(0xFF43474E)

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
