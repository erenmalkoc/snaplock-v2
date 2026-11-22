package com.erenium.snaplock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.erenium.snaplock.domain.share.QrCode
import com.erenium.snaplock.domain.share.QrEncoder
import com.erenium.snaplock.ui.theme.Dimens
import kotlin.math.floor

/**
 * Draws the code black on white with its full quiet zone, whatever the app theme is, because that is
 * what scanners expect. Every module is a whole number of pixels so the edges stay sharp.
 */
@Composable
fun QrCodeImage(code: QrCode, contentDescription: String, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
    ) {
        drawRect(Color.White)
        val cells = code.size + 2 * QrEncoder.QUIET_ZONE
        val cell = floor(size.minDimension / cells)
        if (cell < 1f) return@Canvas
        val left = floor((size.width - cell * cells) / 2f) + QrEncoder.QUIET_ZONE * cell
        val top = floor((size.height - cell * cells) / 2f) + QrEncoder.QUIET_ZONE * cell
        for (y in 0 until code.size) {
            var x = 0
            while (x < code.size) {
                if (!code[x, y]) {
                    x++
                    continue
                }
                var end = x
                while (end < code.size && code[end, y]) end++
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(left + x * cell, top + y * cell),
                    size = Size((end - x) * cell, cell)
                )
                x = end
            }
        }
    }
}
