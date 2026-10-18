package com.erenium.snaplock.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.passwords.PasswordStrength
import com.erenium.snaplock.domain.passwords.StrengthEstimate
import com.erenium.snaplock.ui.theme.Dimens
import com.erenium.snaplock.ui.theme.extendedColors
import kotlin.math.roundToInt

/** A bar and a label such as "Strong · 78 bits". */
@Composable
fun StrengthMeter(estimate: StrengthEstimate, modifier: Modifier = Modifier) {
    val label = strengthLabel(estimate.strength)
    val text = stringResource(R.string.strength_with_bits, label, estimate.entropyBits.roundToInt())
    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = text }) {
        LinearProgressIndicator(
            progress = { (estimate.strength.ordinal + 1) / PasswordStrength.entries.size.toFloat() },
            color = strengthColor(estimate.strength),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = strengthColor(estimate.strength),
            modifier = Modifier.padding(top = Dimens.spaceXs)
        )
    }
}

@Composable
fun strengthLabel(strength: PasswordStrength): String = stringResource(
    when (strength) {
        PasswordStrength.VERY_WEAK -> R.string.strength_very_weak
        PasswordStrength.WEAK -> R.string.strength_weak
        PasswordStrength.FAIR -> R.string.strength_fair
        PasswordStrength.STRONG -> R.string.strength_strong
        PasswordStrength.VERY_STRONG -> R.string.strength_very_strong
    }
)

@Composable
private fun strengthColor(strength: PasswordStrength): Color = when (strength) {
    PasswordStrength.VERY_WEAK, PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
    PasswordStrength.FAIR -> MaterialTheme.extendedColors.warning
    PasswordStrength.STRONG, PasswordStrength.VERY_STRONG -> MaterialTheme.extendedColors.success
}
