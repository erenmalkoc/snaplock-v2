package com.erenium.snaplock.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi

/** Exposes a platform [AssistStructure.ViewNode] to [FormParser]. */
@RequiresApi(Build.VERSION_CODES.O)
internal class ViewNodeFormNode(private val node: AssistStructure.ViewNode) : FormNode<AutofillId> {
    override val autofillId: AutofillId? get() = node.autofillId
    override val autofillHints: List<String> get() = node.autofillHints?.toList().orEmpty()
    override val htmlTag: String? get() = node.htmlInfo?.tag
    override val htmlAttributes: Map<String, String>
        get() = node.htmlInfo?.attributes
            ?.associate { it.first.lowercase() to it.second }
            .orEmpty()
    override val inputType: Int get() = node.inputType
    override val idEntry: String? get() = node.idEntry
    override val hint: String? get() = node.hint
    override val isTextField: Boolean get() = node.autofillType == View.AUTOFILL_TYPE_TEXT
    override val isVisible: Boolean get() = node.visibility == View.VISIBLE
    override val isFocused: Boolean get() = node.isFocused
    override val webDomain: String? get() = node.webDomain
    override val text: String?
        get() = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
    override val children: List<FormNode<AutofillId>>
        get() = (0 until node.childCount).map { ViewNodeFormNode(node.getChildAt(it)) }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun AssistStructure.parseLoginForm(): LoginForm<AutofillId> =
    FormParser.parse((0 until windowNodeCount).map { ViewNodeFormNode(getWindowNodeAt(it).rootViewNode) })

@get:RequiresApi(Build.VERSION_CODES.O)
internal val AssistStructure.packageName: String
    get() = activityComponent.packageName
