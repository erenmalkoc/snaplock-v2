package com.erenium.snaplock.ui.screens.entrydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.share.QrEncoder
import com.erenium.snaplock.domain.share.QrShare
import com.erenium.snaplock.domain.share.QrShareKind
import com.erenium.snaplock.domain.share.QrShareOption
import com.erenium.snaplock.domain.share.WifiSecurity
import com.erenium.snaplock.domain.share.WifiSettings
import com.erenium.snaplock.ui.components.AppDialog
import com.erenium.snaplock.ui.components.ChoiceRow
import com.erenium.snaplock.ui.components.QrCodeImage
import com.erenium.snaplock.ui.components.SettingToggleRow
import com.erenium.snaplock.ui.theme.Dimens

/**
 * Shows one piece of an entry as a QR code so another device can pick it up with its camera.
 * Only the choice and the network settings are remembered over a rotation, never the secret itself.
 */
@Composable
fun QrShareDialog(entry: EntryDetail, onDismiss: () -> Unit) {
    val options = remember(entry) { QrShare.options(entry) }
    if (options.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    var selectedKey by rememberSaveable { mutableStateOf(QrShare.defaultOption(entry, options)?.key) }
    var ssid by rememberSaveable { mutableStateOf(QrShare.defaultSsid(entry)) }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.Wpa) }
    var hidden by rememberSaveable { mutableStateOf(false) }

    val selected = options.firstOrNull { it.key == selectedKey } ?: options.first()
    val wifi = WifiSettings(ssid, security, hidden)
    val code = remember(entry, selected, wifi) {
        QrShare.payload(entry, selected, wifi)?.let { QrEncoder.encode(it) }
    }
    val label = optionLabel(selected)

    AppDialog(
        title = stringResource(R.string.qr_share_title),
        confirmText = stringResource(R.string.close_button),
        confirmIcon = Icons.Filled.Close,
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissText = null
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceMd)
        ) {
            if (code != null) {
                QrCodeImage(
                    code = code,
                    contentDescription = stringResource(R.string.qr_content_description, label),
                    modifier = Modifier
                        .widthIn(max = QR_MAX_WIDTH)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                )
            } else {
                Text(
                    text = stringResource(
                        if (selected.kind == QrShareKind.Wifi && ssid.isBlank()) R.string.qr_wifi_name_needed
                        else R.string.qr_too_long
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = stringResource(R.string.qr_share_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChoiceRow(
                options = options,
                selected = selected,
                label = { optionLabel(it) },
                onSelect = { selectedKey = it.key }
            )
            if (selected.kind == QrShareKind.Wifi) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text(stringResource(R.string.qr_wifi_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.qr_wifi_security),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ChoiceRow(
                    options = WifiSecurity.entries,
                    selected = security,
                    label = { securityLabel(it) },
                    onSelect = { security = it }
                )
                SettingToggleRow(
                    label = stringResource(R.string.qr_wifi_hidden),
                    checked = hidden,
                    onCheckedChange = { hidden = it }
                )
            }
        }
    }
}

private val QR_MAX_WIDTH = Dimens.contentMaxWidth / 2

@Composable
private fun optionLabel(option: QrShareOption): String = when (option.kind) {
    QrShareKind.Password -> stringResource(R.string.entry_password_label)
    QrShareKind.Wifi -> stringResource(R.string.qr_source_wifi)
    QrShareKind.Username -> stringResource(R.string.entry_username_label)
    QrShareKind.Url -> stringResource(R.string.entry_url_label)
    QrShareKind.OneTimeCode -> stringResource(R.string.entry_otp_label)
    QrShareKind.Field -> option.fieldName.orEmpty()
}

@Composable
private fun securityLabel(security: WifiSecurity): String = when (security) {
    WifiSecurity.Wpa -> stringResource(R.string.qr_wifi_security_wpa)
    WifiSecurity.Wep -> stringResource(R.string.qr_wifi_security_wep)
    WifiSecurity.Open -> stringResource(R.string.qr_wifi_security_open)
}
