package com.erenium.snaplock.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

// Many entries share one icon and lists rebind rows while scrolling, so decode each once.
private val decodedIcons = LruCache<EntryIcon, ImageBitmap>(128)

private fun EntryIcon.decoded(): ImageBitmap? =
    decodedIcons.get(this) ?: BitmapFactory.decodeByteArray(data, 0, data.size)
        ?.asImageBitmap()
        ?.also { decodedIcons.put(this, it) }

/** The entry's custom icon when it has one that decodes, else a coloured initial. */
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
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
            .background(avatarColorFor(title)),
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

private const val ICON_SCALE = 0.6f
