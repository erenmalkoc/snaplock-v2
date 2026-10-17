package com.erenium.snaplock.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.slice.Slice
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.erenium.snaplock.MainActivity
import com.erenium.snaplock.R
import com.erenium.snaplock.domain.model.Credential

/** Builds the datasets the system shows in the autofill dropdown and the keyboard strip. */
@RequiresApi(Build.VERSION_CODES.O)
internal class AutofillResponses(private val context: Context) {

    fun fillResponse(
        form: LoginForm<AutofillId>,
        items: List<FillItem>,
        inlineRequest: InlineSuggestionsRequest?
    ): FillResponse? {
        val datasets = items.mapIndexedNotNull { index, item ->
            val inline = inlineFor(inlineRequest, index)
            when (item) {
                FillItem.Unlock -> actionDataset(
                    form,
                    title = context.getString(R.string.autofill_unlock_title),
                    subtitle = context.getString(R.string.autofill_unlock_subtitle),
                    mode = AutofillActivity.Mode.UNLOCK,
                    inline = inline
                )
                FillItem.Search -> actionDataset(
                    form,
                    title = context.getString(R.string.autofill_search_title),
                    subtitle = null,
                    mode = AutofillActivity.Mode.SEARCH,
                    inline = inline
                )
                is FillItem.Fill -> credentialDataset(form, item.credential, inline)
            }
        }
        if (datasets.isEmpty()) return null
        return FillResponse.Builder().apply { datasets.forEach(::addDataset) }.build()
    }

    /** Fills [credential] straight away; also what the unlock and search screens hand back. */
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    fun credentialDataset(
        form: LoginForm<AutofillId>,
        credential: Credential,
        inline: InlineSpec? = null
    ): Dataset? {
        val values = form.usernameIds.mapNotNull { id -> credential.username?.let { id to it } } +
            form.passwordIds.mapNotNull { id -> credential.password?.let { id to it } }
        if (values.isEmpty()) return null
        val builder = Dataset.Builder(remoteViews(credential.title, credential.username))
        values.forEach { (id, text) -> builder.setValue(id, AutofillValue.forText(text)) }
        inline?.presentation(credential.title, credential.username)?.let(builder::setInlinePresentation)
        return builder.build()
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun actionDataset(
        form: LoginForm<AutofillId>,
        title: String,
        subtitle: String?,
        mode: AutofillActivity.Mode,
        inline: InlineSpec?
    ): Dataset {
        val builder = Dataset.Builder(remoteViews(title, subtitle))
        form.allIds.forEach { builder.setValue(it, null) }
        inline?.presentation(title, subtitle)?.let(builder::setInlinePresentation)
        builder.setAuthentication(authentication(mode))
        return builder.build()
    }

    private fun authentication(mode: AutofillActivity.Mode): IntentSender {
        // The system adds the screen's structure to the intent, so it must stay mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(
            context,
            mode.ordinal + 1,
            AutofillActivity.newIntent(context, mode),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable
        ).intentSender
    }

    private fun remoteViews(title: String, subtitle: String?): RemoteViews =
        RemoteViews(context.packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_title, title)
            if (subtitle.isNullOrEmpty()) {
                setViewVisibility(R.id.autofill_subtitle, View.GONE)
            } else {
                setTextViewText(R.id.autofill_subtitle, subtitle)
            }
        }

    private fun inlineFor(request: InlineSuggestionsRequest?, index: Int): InlineSpec? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || request == null) return null
        val specs = request.inlinePresentationSpecs
        if (specs.isEmpty() || index >= request.maxSuggestionCount) return null
        return InlineSpec(context, specs[minOf(index, specs.lastIndex)])
    }

    /** One inline (keyboard) suggestion slot. */
    @RequiresApi(Build.VERSION_CODES.R)
    internal class InlineSpec(private val context: Context, private val spec: InlinePresentationSpec) {

        // getSlice() is marked library-internal, yet InlinePresentation needs the slice and the
        // library offers no other way to get it; Google's own autofill sample does the same.
        @SuppressLint("RestrictedApi")
        fun presentation(title: String, subtitle: String?): InlinePresentation? {
            if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
            val attribution = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val content = InlineSuggestionUi.newContentBuilder(attribution)
                .setTitle(title)
                .setStartIcon(Icon.createWithResource(context, R.drawable.app_logo))
            if (!subtitle.isNullOrEmpty()) content.setSubtitle(subtitle)
            val slice: Slice = content.build().slice
            return InlinePresentation(slice, spec, false)
        }
    }
}
