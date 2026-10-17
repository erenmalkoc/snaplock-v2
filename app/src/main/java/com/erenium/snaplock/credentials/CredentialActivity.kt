package com.erenium.snaplock.credentials

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.autofill.AutofillSaveViewModel
import com.erenium.snaplock.autofill.AutofillViewModel
import com.erenium.snaplock.autofill.appLabel
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.domain.model.Credential
import com.erenium.snaplock.domain.repository.KdbxRepository
import com.erenium.snaplock.ui.blockScreenshots
import com.erenium.snaplock.ui.screens.autofill.AutofillSaveScreen
import com.erenium.snaplock.ui.screens.autofill.AutofillScreen
import com.erenium.snaplock.ui.screens.autofill.VaultGate
import com.erenium.snaplock.ui.theme.SnaplockTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/**
 * Credential Manager's way into the vault: unlocks it for the system sheet, hands over the
 * entry the user picked there, lets the user search for another one, or stores a password
 * an app asked to save.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@AndroidEntryPoint
class CredentialActivity : FragmentActivity() {

    private enum class Mode { UNLOCK, SELECT, SEARCH, CREATE }

    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    @Inject
    lateinit var repository: KdbxRepository

    private val pickerViewModel: AutofillViewModel by viewModels()
    private val saveViewModel: AutofillSaveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        blockScreenshots()
        when (intent.getStringExtra(EXTRA_MODE)?.let(Mode::valueOf)) {
            Mode.UNLOCK -> showUnlock()
            Mode.SELECT -> showSelect()
            Mode.SEARCH -> showSearch()
            Mode.CREATE -> showCreate()
            null -> finish()
        }
    }

    /** Unlocks, then gives the system sheet the matching entries. */
    private fun showUnlock() {
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent) ?: return finish()
        pickerViewModel.start(request.callingAppInfo?.packageName.orEmpty(), null, fillSingleMatch = false)
        show {
            VaultGate(pickerViewModel, ::openApp, ::finish) {
                LaunchedEffect(Unit) {
                    val response = PasswordProvider(this@CredentialActivity, repository).beginGet(request)
                    finishWith(Intent().also { PendingIntentHandler.setBeginGetCredentialResponse(it, response) })
                }
            }
        }
    }

    /** Returns the entry the user picked in the system sheet, unlocking first if needed. */
    private fun showSelect() {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) ?: return finish()
        val uuid = intent.getStringExtra(EXTRA_UUID)?.let(UUID::fromString) ?: return finish()
        pickerViewModel.start(request.callingAppInfo.packageName, null, fillSingleMatch = false)
        show {
            VaultGate(pickerViewModel, ::openApp, ::finish) {
                LaunchedEffect(Unit) {
                    val credential = repository.getCredentials().firstOrNull { it.uuid == uuid }
                    if (credential == null) finish() else returnCredential(credential)
                }
            }
        }
    }

    /** Lets the user pick any entry; the app gets linked to it for next time. */
    private fun showSearch() {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) ?: return finish()
        val callingPackage = request.callingAppInfo.packageName
        pickerViewModel.start(callingPackage, null, fillSingleMatch = false)
        show {
            AutofillScreen(
                viewModel = pickerViewModel,
                target = appLabel(callingPackage),
                onPicked = ::returnCredential,
                onOpenApp = ::openApp,
                onCancel = ::finish
            )
        }
    }

    /** Stores a password an app asked Credential Manager to save. */
    private fun showCreate() {
        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent) ?: return finish()
        val passwordRequest = request.callingRequest as? CreatePasswordRequest ?: return finish()
        val callingPackage = request.callingAppInfo.packageName
        val target = appLabel(callingPackage)
        saveViewModel.start(callingPackage, null, target, passwordRequest.id, passwordRequest.password)
        show {
            AutofillSaveScreen(
                viewModel = saveViewModel,
                target = target,
                onDone = {
                    finishWith(Intent().also {
                        PendingIntentHandler.setCreateCredentialResponse(it, CreatePasswordResponse())
                    })
                },
                onOpenApp = ::openApp,
                onCancel = ::finish
            )
        }
    }

    private fun returnCredential(credential: Credential) {
        val password = credential.password
        if (password.isNullOrEmpty()) return finish()
        val id = credential.username?.takeIf { it.isNotEmpty() } ?: credential.title
        finishWith(Intent().also {
            PendingIntentHandler.setGetCredentialResponse(it, GetCredentialResponse(PasswordCredential(id, password)))
        })
    }

    private fun show(content: @Composable () -> Unit) {
        setContent {
            val settings by settingsPrefs.settings.collectAsState()
            SnaplockTheme(settings) {
                content()
            }
        }
    }

    private fun finishWith(result: Intent) {
        setResult(RESULT_OK, result)
        finish()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val EXTRA_MODE = "com.erenium.snaplock.credentials.MODE"
        private const val EXTRA_UUID = "com.erenium.snaplock.credentials.UUID"

        fun unlockIntent(context: Context): PendingIntent = pendingIntent(context, Mode.UNLOCK, 1)

        fun searchIntent(context: Context): PendingIntent = pendingIntent(context, Mode.SEARCH, 2)

        fun createIntent(context: Context): PendingIntent = pendingIntent(context, Mode.CREATE, 3)

        fun selectIntent(context: Context, uuid: UUID): PendingIntent =
            pendingIntent(context, Mode.SELECT, uuid.hashCode()) { putExtra(EXTRA_UUID, uuid.toString()) }

        // Mutable: the system adds the request to the intent before starting the activity.
        private fun pendingIntent(
            context: Context,
            mode: Mode,
            requestCode: Int,
            extras: Intent.() -> Unit = {}
        ): PendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, CredentialActivity::class.java).putExtra(EXTRA_MODE, mode.name).apply(extras),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
