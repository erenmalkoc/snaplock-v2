package com.erenium.snaplock.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.erenium.snaplock.R
import com.erenium.snaplock.ui.theme.Dimens

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = Dimens.logo
) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
