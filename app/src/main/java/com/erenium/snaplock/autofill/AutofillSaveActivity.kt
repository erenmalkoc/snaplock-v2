package com.erenium.snaplock.autofill

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.data.datasource.prefs.SettingsPrefs
import com.erenium.snaplock.ui.blockScreenshots
import com.erenium.snaplock.ui.screens.autofill.AutofillSaveScreen
import com.erenium.snaplock.ui.theme.SnaplockTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Opened after the system asks whether to save a login typed into another app or site. */
@AndroidEntryPoint
class AutofillSaveActivity : FragmentActivity() {

    @Inject
    lateinit var settingsPrefs: SettingsPrefs

    private val viewModel: AutofillSaveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockScreenshots()
        val requestingPackage = intent.getStringExtra(EXTRA_PACKAGE)
        val password = intent.getStringExtra(EXTRA_PASSWORD)
        if (requestingPackage == null || password.isNullOrEmpty()) {
            finish()
            return
        }
        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val target = webDomain?.takeIf { Browsers.isTrusted(requestingPackage) } ?: appLabel(requestingPackage)
        viewModel.start(
            packageName = requestingPackage,
            webDomain = webDomain,
            title = target,
            username = intent.getStringExtra(EXTRA_USERNAME),
            password = password
        )

        setContent {
            val settings by settingsPrefs.settings.collectAsState()
            SnaplockTheme(settings) {
                AutofillSaveScreen(
                    viewModel = viewModel,
                    target = target,
                    onDone = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onCancel = ::finish
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "com.erenium.snaplock.autofill.PACKAGE"
        private const val EXTRA_WEB_DOMAIN = "com.erenium.snaplock.autofill.WEB_DOMAIN"
        private const val EXTRA_USERNAME = "com.erenium.snaplock.autofill.USERNAME"
        private const val EXTRA_PASSWORD = "com.erenium.snaplock.autofill.PASSWORD"

        fun newIntent(
            context: Context,
            packageName: String,
            webDomain: String?,
            username: String?,
            password: String
        ): Intent = Intent(context, AutofillSaveActivity::class.java)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_WEB_DOMAIN, webDomain)
            .putExtra(EXTRA_USERNAME, username)
            .putExtra(EXTRA_PASSWORD, password)
    }
}
