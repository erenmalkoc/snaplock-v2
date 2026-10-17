package com.erenium.snaplock.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import androidx.annotation.RequiresApi
import com.erenium.snaplock.domain.repository.KdbxRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Offers vault entries in other apps' login forms. While the vault is locked it offers a
 * single "unlock" item; values only leave Snaplock after the user picks an item.
 */
@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class SnaplockAutofillService : AutofillService() {

    @Inject
    lateinit var repository: KdbxRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val responses by lazy { AutofillResponses(this) }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.last().structure
        val requestingPackage = structure.packageName
        val form = structure.parseLoginForm()
        if (requestingPackage == packageName || form.isEmpty) {
            callback.onSuccess(null)
            return
        }
        val inlineRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            request.inlineSuggestionsRequest
        } else {
            null
        }
        val job = scope.launch {
            val response = runCatching {
                val isLocked = repository.isLocked().first()
                val matches = if (isLocked) {
                    emptyList()
                } else {
                    CredentialMatcher.match(repository.getCredentials(), requestingPackage, form.webDomain)
                }
                val maxInline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    inlineRequest?.maxSuggestionCount
                } else {
                    null
                }
                responses.fillResponse(form, FillPlanner.plan(isLocked, matches, maxInline), inlineRequest)
            }
            response
                .onSuccess(callback::onSuccess)
                .onFailure { callback.onFailure(it.message) }
        }
        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Multi-page logins put the username and password on different screens.
        val structures = request.fillContexts.map { it.structure }
        val forms = structures.map { it.parseLoginForm() }.asReversed()
        val password = forms.firstNotNullOfOrNull { it.password }
        if (password == null) {
            callback.onSuccess()
            return
        }
        val intent = AutofillSaveActivity.newIntent(
            context = this,
            packageName = structures.last().packageName,
            webDomain = forms.firstNotNullOfOrNull { it.webDomain },
            username = forms.firstNotNullOfOrNull { it.username },
            password = password
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sender = PendingIntent.getActivity(
                this,
                SAVE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).intentSender
            callback.onSuccess(sender)
        } else {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            callback.onSuccess()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val SAVE_REQUEST_CODE = 100
    }
}
