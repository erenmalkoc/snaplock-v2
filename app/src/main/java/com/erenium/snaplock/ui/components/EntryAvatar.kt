package com.erenium.snaplock.ui.components

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.erenium.snaplock.domain.model.EntryIcon
import com.erenium.snaplock.ui.theme.Dimens
import com.erenium.snaplock.ui.theme.avatarColorFor

private val decodedIcons = LruCache<EntryIcon, ImageBitmap>(128)

private fun EntryIcon.decoded(): ImageBitmap? =
    decodedIcons.get(this) ?: BitmapFactory.decodeByteArray(data, 0, data.size)
        ?.asImageBitmap()
        ?.also { decodedIcons.put(this, it) }

@Composable
fun EntryAvatar(
    title: String,
    modifier: Modifier = Modifier,
    icon: EntryIcon? = null,
    size: Dp = Dimens.iconLg
) {
    val bitmap = remember(icon) { icon?.decoded() }
    if (bitmap != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
                .border(Dimens.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size * ICON_SCALE))
        }
        return
    }
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarBrushFor(avatarColorFor(title))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

private fun avatarBrushFor(base: Color): Brush =
    Brush.linearGradient(listOf(lerp(base, Color.White, 0.16f), lerp(base, Color.Black, 0.14f)))

private const val ICON_SCALE = 0.62f
