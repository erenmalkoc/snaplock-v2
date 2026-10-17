package com.erenium.snaplock.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.ui.blockScreenshots
import com.erenium.snaplock.ui.screens.autofill.AutofillScreen
import com.erenium.snaplock.ui.theme.SnaplockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Opened by the system when the user taps a Snaplock autofill item that needs the app.
 * Hands the chosen entry back as a [Dataset]; nothing is filled if the user backs out.
 */
@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class AutofillActivity : FragmentActivity() {

    enum class Mode { UNLOCK, SEARCH }

    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    private val viewModel: AutofillViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockScreenshots()
        val structure = IntentCompat.getParcelableExtra(
            intent,
            AutofillManager.EXTRA_ASSIST_STRUCTURE,
            AssistStructure::class.java
        )
        val form = structure?.parseLoginForm()
        if (structure == null || form == null || form.isEmpty) {
            finish()
            return
        }
        val mode = intent.getStringExtra(EXTRA_MODE)?.let(Mode::valueOf) ?: Mode.SEARCH
        val requestingPackage = structure.packageName
        viewModel.start(requestingPackage, form.webDomain, fillSingleMatch = mode == Mode.UNLOCK)
        val responses = AutofillResponses(this)
        val target = form.webDomain ?: appLabel(requestingPackage)

        setContent {
            val settings by settingsPrefs.settings.collectAsState()
            SnaplockTheme(settings) {
                AutofillScreen(
                    viewModel = viewModel,
                    target = target,
                    onPicked = { credential -> finishWith(responses.credentialDataset(form, credential)) },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onCancel = ::finish
                )
            }
        }
    }

    private fun finishWith(dataset: Dataset?) {
        if (dataset != null) {
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        }
        finish()
    }

    companion object {
        private const val EXTRA_MODE = "com.erenium.snaplock.autofill.MODE"

        fun newIntent(context: Context, mode: Mode): Intent =
            Intent(context, AutofillActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}
