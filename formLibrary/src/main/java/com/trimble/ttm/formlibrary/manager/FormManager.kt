package com.trimble.ttm.formlibrary.manager

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.FragmentManager
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.composable.androidViews.SignatureCanvasView
import com.trimble.ttm.commons.composable.customViews.CustomBarcodeField
import com.trimble.ttm.commons.composable.customViews.CustomDateField
import com.trimble.ttm.commons.composable.customViews.CustomDateTimeField
import com.trimble.ttm.commons.composable.customViews.CustomDisplayText
import com.trimble.ttm.commons.composable.customViews.CustomNumericField
import com.trimble.ttm.commons.composable.customViews.CustomPasswordField
import com.trimble.ttm.commons.composable.customViews.CustomSignatureField
import com.trimble.ttm.commons.composable.customViews.CustomTextField
import com.trimble.ttm.commons.composable.customViews.CustomTimeField
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.isDriverEditable
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.commons.viewModel.SignatureDialogViewModel
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.customViews.FormBarCodeScanner
import com.trimble.ttm.formlibrary.customViews.FormDate
import com.trimble.ttm.formlibrary.customViews.FormDateTime
import com.trimble.ttm.formlibrary.customViews.FormEditText
import com.trimble.ttm.formlibrary.customViews.FormImageReference
import com.trimble.ttm.formlibrary.customViews.FormNumeric
import com.trimble.ttm.formlibrary.customViews.FormPassword
import com.trimble.ttm.formlibrary.customViews.FormSignature
import com.trimble.ttm.formlibrary.customViews.FormTime
import com.trimble.ttm.formlibrary.customViews.PendingClickListener
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.FormUtils.validateNumericField
import com.trimble.ttm.formlibrary.utils.UiUtil
import com.trimble.ttm.formlibrary.utils.UiUtil.isTablet
import com.trimble.ttm.formlibrary.utils.isNull
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.drakeet.support.toast.ToastCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Stack

@ExperimentalCoroutinesApi
class FormManager : KoinComponent {

    private var isFormFieldEmpty = false
    private val formDataStoreManager: FormDataStoreManager by inject()
    private val tag = "FormManager"
    var branchTo = -1

    fun getDisplayText(
        formField: FormField, textInputLayout: TextInputLayout, context: Context
    ): TextView = TextView(context).apply {
        textInputLayout.hint = formField.qtext
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        formField.displayText?.let { dispayText ->
            text = dispayText
            formField.uiData = dispayText
        }
        setTextColor(Color.WHITE)
        background = null
        (layoutParams as LinearLayout.LayoutParams).setMargins(
            resources.getDimension(R.dimen.defaultLeftRightMargin).toInt(),
            resources.getDimension(R.dimen.defaultTopBottomMargin).toInt(),
            resources.getDimension(R.dimen.defaultLeftRightMargin).toInt(),
            resources.getDimension(R.dimen.defaultTopBottomMargin).toInt()
        )
        setPadding(
            resources.getDimensionPixelSize(R.dimen.defaultFormsEditTextPadding),
            0,
            resources.getDimensionPixelSize(R.dimen.defaultFormsEditTextPadding),
            0
        )
    }

    fun createTextInputLayout(
        qType: Int,
        formId: Int,
        formClass: Int,
        formFieldListSize: Int,
        context: Context,
        isRequired: Boolean
    ): TextInputLayout = if (isFreeForm(
            FormDef(
                formid = formId, formClass = formClass
            )
        ) && formFieldListSize == 1
    ) {
        if (isRequired) {
            LayoutInflater.from(context).inflate(
                R.layout.required_text_input_layout_counter_enabled, null
            ).findViewById(R.id.text_input_layout)
        } else {
            LayoutInflater.from(context).inflate(
                R.layout.text_input_layout_counter_enabled, null
            ).findViewById(R.id.text_input_layout)
        }
    } else {
        getTextInputLayout(qType, isRequired, context)
    }

    private fun getTextInputLayout(
        qType: Int,
        isRequired: Boolean,
        context: Context
    ): TextInputLayout {
        return if (qType != FormFieldType.MULTIPLE_CHOICE.ordinal) {
            if (isRequired) {
                LayoutInflater.from(context).inflate(
                    R.layout.required_text_input_layout, null
                ).findViewById(R.id.text_input_layout)
            } else {
                LayoutInflater.from(context).inflate(
                    R.layout.text_input_layout, null
                ).findViewById(R.id.text_input_layout)
            }
        } else {
            if (isRequired) {
                LayoutInflater.from(context).inflate(
                    R.layout.required_text_input_drop_down, null
                ).findViewById(R.id.textInputLayout)
            } else {
                LayoutInflater.from(context).inflate(
                    R.layout.text_input_drop_down, null
                ).findViewById(R.id.textInputLayout)
            }
        }
    }


    fun iterateViewsToFetchDataFromFormFields(
        leftLinearLayout: LinearLayout,
        rightLinearLayout: LinearLayout,
        viewIdToFormFieldMap: HashMap<Int, FormField>,
        tag: String
    ): ArrayList<FormField> {
        val constructedFormFieldList = ArrayList<FormField>()
        var leftLayoutIndex = 0
        var rightLayoutIndex = 0
        var layoutIdentifier = 0
        var formFieldView: View?
        var isLeftIterationFinished = false
        var isRightIterationFinished = false
        while (true) {
            if (layoutIdentifier % 2 == 0) {
                formFieldView = leftLinearLayout.getChildAt(leftLayoutIndex)
                leftLayoutIndex++
                if (formFieldView.isNull()) {
                    isLeftIterationFinished = true
                }
            } else {
                formFieldView = rightLinearLayout.getChildAt(rightLayoutIndex)
                rightLayoutIndex++
                if (formFieldView.isNull()) {
                    isRightIterationFinished = true
                }
            }
            if (checkIfIterationFinished(isLeftIterationFinished, isRightIterationFinished)) {
                break
            }
            layoutIdentifier++
            formFieldView?.let {
                if (viewIdToFormFieldMap.containsKey(it.id)) {
                    val formFieldFromMap = viewIdToFormFieldMap[it.id] as FormField
                    constructedFormFieldList.add(formFieldFromMap)
                }else{
                    Log.w(this.tag, "formFieldFromMap is null for viewId: ${it.id}")
                }
            }
        }
        logConstructedFormFieldList(constructedFormFieldList)
        return constructedFormFieldList
    }

    private fun logConstructedFormFieldList(constructedFormFieldList: ArrayList<FormField>){
        val constructedFormFieldListString = StringBuilder()
        constructedFormFieldList.forEach {
            constructedFormFieldListString.append("viewId : ${it.viewId} ").append("fieldId : ${it.fieldId} | ")
        }
        Log.d(tag, "constructedFormFieldListString : $constructedFormFieldListString")
    }

    fun checkIfIterationFinished(
        isLeftIterationFinished: Boolean, isRightIterationFinished: Boolean
    ): Boolean = isLeftIterationFinished && isRightIterationFinished

    fun removeViews(
        viewId: Int, leftLinearLayout: LinearLayout, rightLinearLayout: LinearLayout, tag: String
    ) {
        val leftChildListToBeRemoved = ArrayList<View>()
        leftLinearLayout.children.forEachIndexed { _, view ->
            if (view.id >= viewId) {
                leftChildListToBeRemoved.add(view)
            }
        }
        val rightChildListToBeRemoved = ArrayList<View>()
        rightLinearLayout.children.forEachIndexed { _, view ->
            if (view.id >= viewId) {
                rightChildListToBeRemoved.add(view)
            }
        }
        Log.d(
            tag,
            "leftChildListToBeRemoved:$leftChildListToBeRemoved,rightChildListToBeRemoved:$rightChildListToBeRemoved"
        )
        leftChildListToBeRemoved.forEach {
            leftLinearLayout.removeView(it)
        }
        rightChildListToBeRemoved.forEach {
            rightLinearLayout.removeView(it)
        }
    }

    fun removePreviouslyRenderedField(viewId: Int,parentMap:HashMap<Int,FormField>): HashMap<Int,FormField>{
        val fieldsToBeAdd = parentMap.filterKeys {
            it < viewId
        }
        return if(fieldsToBeAdd.isNotEmpty())
            HashMap(fieldsToBeAdd)
        else
            parentMap
    }


    fun highlightErrorField(
        formId: Int,
        formClass: Int,
        errorFormFieldList: ArrayList<FormField>,
        formFieldListSize: Int,
        leftLinearLayout: LinearLayout,
        rightLinearLayout: LinearLayout,
        context: Context
    ) {
        if (isFreeForm(FormDef(formid = formId, formClass = formClass)) && formFieldListSize == 1) {
            if (errorFormFieldList.isNotEmpty()) processLayoutForErrorDisplay(
                errorFormFieldList, leftLinearLayout, context
            )
        } else {
            if (isTablet(context) && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (errorFormFieldList.isNotEmpty()) {
                    processLayoutForErrorDisplay(errorFormFieldList, leftLinearLayout, context)
                    processLayoutForErrorDisplay(errorFormFieldList, rightLinearLayout, context)
                }
            } else {
                processLayoutForErrorDisplay(errorFormFieldList, leftLinearLayout, context)
            }
        }
        if (isFormFieldEmpty) {
            context.apply {
                ToastCompat.makeText(
                    this, getString(R.string.fill_required_field), Toast.LENGTH_SHORT
                ).show()
            }
            isFormFieldEmpty = false
        }
    }

    private fun processLayoutForErrorDisplay(
        errorFormFields: ArrayList<FormField>, layout: LinearLayout, context: Context
    ) {
        layout.children.forEach { view ->
            val fmField = errorFormFields.firstOrNull { p -> view.tag == p.viewId }
            fmField?.let {
                if (view is TextInputLayout) {
                    if (it.uiData.isEmpty() && it.required == 1) {
                        SpannableString(context.getString(R.string.cannot_be_empty)).let {
                            it.setSpan(
                                ForegroundColorSpan(
                                    ContextCompat.getColor(
                                        context, R.color.outlineBoxErrorColor
                                    )
                                ), 0, it.length, SPAN_INCLUSIVE_EXCLUSIVE
                            )
                            view.error = it
                        }
                        isFormFieldEmpty = true
                    } else {
                        view.error = ""
                        isFormFieldEmpty = false
                    }
                    if (view.error.toString() != context.getString(R.string.cannot_be_empty) && it.validateNumericField()) view.error =
                        context.getString(R.string.form_not_a_valid_number)
                } else {
                    view.background = ContextCompat.getDrawable(
                        context, R.drawable.error_rounded_border_drawable
                    )
                    view.findViewById<TextView>(R.id.errorText).visibility = View.VISIBLE
                    isFormFieldEmpty = true
                }
            }
        }
    }

    fun isFreeForm(formDef: FormDef): Boolean = formDef.isFreeForm()

    fun focusNextEditableFormField(activity: Activity?, mapFieldIdsToViews: Map<Int, View>) {
        for (mapFieldIdsToView in mapFieldIdsToViews) {
            if (mapFieldIdsToView.value.hasFocus() && mapFieldIdsToViews[mapFieldIdsToView.key.plus(
                    1
                )] != null
            ) {
                if (checkIfFormEditTextIsEditable(mapFieldIdsToViews[mapFieldIdsToView.key.plus(1)]!!)) {
                    mapFieldIdsToViews[mapFieldIdsToView.key.plus(1)]?.requestFocus()
                    if (mapFieldIdsToView.value.hasFocus()) {
                        UiUtil.hideKeyboard(activity)
                        mapFieldIdsToViews[mapFieldIdsToView.key]?.clearFocus()
                    }
                    break
                } else {
                    break
                }
            }
        }
    }

    private fun checkIfFormEditTextIsEditable(view: View): Boolean {
        if (view is TextInputLayout) {
            if (view.editText is FormEditText) {
                val formEditText = view.editText as FormEditText
                return formEditText.showSoftInputOnFocus
            }
            return false
        } else {
            return false
        }
    }

    fun removeKeyForImage() {
        CoroutineScope(Dispatchers.IO).safeLaunch(CoroutineName("$tag Remove encoded image view ID")) {
            if (formDataStoreManager.containsKey(FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID)) formDataStoreManager.removeItem(
                FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID
            )
            if (formDataStoreManager.containsKey(FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM)) formDataStoreManager.removeItem(
                FormDataStoreManager.ENCODED_IMAGE_SHARE_IN_FORM
            )
        }
    }

    fun setEditorListener(
        editText: EditText?, mapFieldIdsToViews: MutableMap<Int, View>, activity: Activity?
    ) {
        editText?.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_NEXT -> {
                    focusNextEditableFormField(activity, mapFieldIdsToViews)
                    true
                }
                EditorInfo.IME_ACTION_DONE -> {
                    editText.clearFocus()
                    UiUtil.hideKeyboard(activity)
                    true
                }
                else -> false
            }
        }
    }

    fun setNextActionInKeypad(mapFieldIdsToViews: MutableMap<Int, View>, activity: Activity?) {
        for (mapFieldIdsToView in mapFieldIdsToViews) {
            if (viewDetails(mapFieldIdsToView.value)) {
                val view = mapFieldIdsToViews[mapFieldIdsToView.key.plus(1)]
                if (view != null && activity != null) {
                    if (viewDetails(view) && checkIfFormEditTextIsEditable(view)) {
                        setKeyValue(
                            mapFieldIdsToView.value,
                            mapFieldIdsToViews,
                            activity,
                            EditorInfo.IME_ACTION_NEXT,
                            activity.getString(R.string.keypad_next)
                        )
                    } else {
                        setKeyValue(
                            mapFieldIdsToView.value,
                            mapFieldIdsToViews,
                            activity,
                            EditorInfo.IME_ACTION_DONE,
                            activity.getString(R.string.keypad_done)
                        )
                    }
                } else {
                    setKeyValue(
                        mapFieldIdsToView.value,
                        mapFieldIdsToViews,
                        activity,
                        EditorInfo.IME_ACTION_DONE,
                        activity?.getString(R.string.keypad_done) ?: ""
                    )
                }
            }
        }
    }

    fun setNextActionForFields(
        mapFieldIdsToViews: ArrayList<FormField>,
        viewFormField: FormField,
        activity: Activity?
    ): ImeAction {
        var imeAction: ImeAction = ImeAction.Default
        if (mapFieldIdsToViews.contains(viewFormField) && checkIfFieldIsTextField(
                formField = viewFormField
            )
        ) {
            val nextFieldIndex = viewFormField.viewId + 1
            val nextField = if(nextFieldIndex < mapFieldIdsToViews.size) mapFieldIdsToViews[nextFieldIndex] else null
            imeAction = if (nextField != null && activity != null) {
                if (checkIfTextFieldIsEditable(formField = nextField) && checkIfFieldIsTextField(
                        nextField
                    )
                ) {
                    ImeAction.Next
                } else {
                    ImeAction.Done
                }
            } else {
                ImeAction.Done
            }
        }
        return imeAction
    }

    private fun setKeyValue(
        value: View,
        mapFieldIdsToViews: MutableMap<Int, View>,
        activity: Activity?,
        imeAction: Int,
        imeActionLabel: String = ""
    ) {
        val v = value as TextInputLayout
        if (imeActionLabel.isNotEmpty()) {
            setKeypadInputType(v)
            v.editText?.setImeActionLabel(imeActionLabel, imeAction)
        }
        v.editText?.imeOptions = imeAction
        setEditorListener(v.editText, mapFieldIdsToViews, activity)
    }

    private fun setKeypadInputType(v: TextInputLayout) {
        when (v.editText) {
            is FormNumeric -> {
                if ((v.editText as? FormNumeric)?.formField?.numspDec == 0) {
                    v.editText?.inputType =
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                } else v.editText?.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            is FormPassword -> {
                v.editText?.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            else -> {
                v.editText?.inputType = InputType.TYPE_CLASS_TEXT
            }
        }
    }

    private fun viewDetails(value: View): Boolean {
        return (value is TextInputLayout) && value.editText?.isFocusableInTouchMode == true && value.isShown
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkIfFieldIsTextField(formField: FormField): Boolean{
        return when(formField.qtype){
            FormFieldType.NUMERIC.ordinal,FormFieldType.NUMERIC_ENHANCED.ordinal,FormFieldType.TEXT.ordinal,FormFieldType.PASSWORD.ordinal -> true
            else -> false
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkIfTextFieldIsEditable(formField: FormField): Boolean{
        return formField.isDriverEditable()
    }

    fun setPendingClickListener(view: View?, isResponseSent: Boolean) {
        if (checkIfViewIsPendingClickListener(view) && !isResponseSent) (view as PendingClickListener).executePendingClick()
    }

    fun assignPendingClickListener(view: View?) {
        if (checkIfViewIsPendingClickListener(view)) (view as? PendingClickListener)?.executePendingClick()
    }

    private fun checkIfViewIsPendingClickListener(view: View?): Boolean =
        view is PendingClickListener

    fun restrictOrientationChange(activity: Activity) {
        val currentOrientation = activity.resources.configuration.orientation
        activity.requestedOrientation =
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
    }

    fun createViewWithFormField(
        formField: FormField,
        stack: Stack<FormField> = Stack(),
        context: Context,
        textInputLayout: TextInputLayout,
        isFormSaved: Boolean,
        stopId: Int = -1,
        lifecycleScope: CoroutineScope,
        supportFragmentManager: FragmentManager,
        getFormFieldCopy: (formField: FormField) -> FormField = { FormField() }
    ): View? {
        var view: View? = null
        when (formField.qtype) {
            FormFieldType.NUMERIC.ordinal, FormFieldType.NUMERIC_ENHANCED.ordinal -> {
                view =
                    FormNumeric(
                        context, textInputLayout, formField, isFormSaved
                    )
            }
            FormFieldType.TEXT.ordinal -> view = FormEditText(
                context, textInputLayout, formField, isFormSaved
            )
            FormFieldType.PASSWORD.ordinal -> view = FormPassword(
                context, textInputLayout, formField, isFormSaved
            )
            FormFieldType.DATE.ordinal -> view =
                FormDate(context, textInputLayout, formField, isFormSaved)
            FormFieldType.TIME.ordinal -> view =
                FormTime(context, textInputLayout, formField, isFormSaved)
            FormFieldType.DATE_TIME.ordinal -> view =
                FormDateTime(context, textInputLayout, formField, isFormSaved)
            FormFieldType.DISPLAY_TEXT.ordinal -> view =
                getDisplayText(formField, textInputLayout, context)
            FormFieldType.BARCODE_SCAN.ordinal -> view = FormBarCodeScanner(
                context,
                textInputLayout,
                formField,
                isFormSaved,
                formDataStoreManager,
                supportFragmentManager
            )
            FormFieldType.IMAGE_REFERENCE.ordinal -> {
                view = FormImageReference(
                    context,
                    formField,
                    isFormSaved,
                    stopId,
                    formField.viewId,
                    formDataStoreManager,
                    lifecycleScope,
                    supportFragmentManager
                )
            }
            FormFieldType.SIGNATURE_CAPTURE.ordinal -> {
                view = FormSignature(context, formField, isFormSaved, stopId, supportFragmentManager)
            }
            FormFieldType.BRANCH_TARGET.ordinal -> {
                //Just a indicator, don't need to do anything. Just coded to understand the code flow
            }
            FormFieldType.BRANCH_TO.ordinal -> {
                formField.branchTargetId?.let {
                    branchTo = it
                }
            }
            FormFieldType.LOOP_START.ordinal -> {
                lifecycleScope.safeLaunch(
                    Dispatchers.Default + CoroutineName(tag)
                ) {
                    val tempFormField = getFormFieldCopy(formField)
                    stack.push(tempFormField)
                }
            }
            FormFieldType.LOOP_END.ordinal -> {
                //Just a indicator, don't need to do anything. Just coded to understand the code flow
                if (stack.isNotEmpty()) {
                    stack.pop()
                }
            }
        }
        return view
    }

    @Composable
    fun CreateComposeViews(
        formField: FormField,
        isFormSaved: Boolean,
        sendButtonState: State<Boolean?>,
        imeAction: () -> ImeAction,
        focusManager: FocusManager,
        isFormInReadOnlyMode: Boolean,
        signatureDialogViewModel: SignatureDialogViewModel
    ) {
        val context = LocalContext.current
        when (formField.qtype) {

            FormFieldType.NUMERIC.ordinal, FormFieldType.NUMERIC_ENHANCED.ordinal -> {
                CustomNumericField(
                    formField = formField,
                    isFormSaved = isFormSaved,
                    sendButtonState = sendButtonState,
                    imeAction = imeAction(),
                    focusManager = focusManager,
                    isFormInReadOnlyMode = isFormInReadOnlyMode
                )
            }

            FormFieldType.TEXT.ordinal -> {
                CustomTextField(
                    formField = formField,
                    isFormSaved = isFormSaved,
                    sendButtonState = sendButtonState,
                    imeAction = imeAction() ,
                    focusManager = focusManager,
                    isFormInReadOnlyMode = isFormInReadOnlyMode
                )
            }

            FormFieldType.PASSWORD.ordinal -> {
                CustomPasswordField(
                    formField = formField,
                    isFormSaved = isFormSaved,
                    sendButtonState = sendButtonState,
                    imeAction = imeAction(),
                    focusManager = focusManager,
                    isFormInReadOnlyMode = isFormInReadOnlyMode
                )
            }

            FormFieldType.DISPLAY_TEXT.ordinal -> {
                CustomDisplayText(formField = formField)
            }

            FormFieldType.SIGNATURE_CAPTURE.ordinal -> {
                val signatureCanvasView = SignatureCanvasView(context,null,signatureDialogViewModel.getCachedSignatureByteArray(formField.viewId))
                val view = LayoutInflater.from(context).inflate(R.layout.signature_canvas_view_layout,null,false).apply {
                    findViewById<LinearLayout>(R.id.ll_signature_view).addView(signatureCanvasView)
                }
                CustomSignatureField(
                    formField = formField,
                    isFormSaved = isFormSaved,
                    sendButtonState = sendButtonState,
                    isFormInReadOnlyMode = isFormInReadOnlyMode,
                    signatureView = view,
                    signatureCanvasView = signatureCanvasView,
                    signatureDialogViewModel = signatureDialogViewModel
                )
            }

            FormFieldType.BARCODE_SCAN.ordinal -> {
                CustomBarcodeField(
                    formField = formField,
                    isFormSaved  = isFormSaved,
                    sendButtonState = sendButtonState,
                    isFormInReadOnlyMode = isFormInReadOnlyMode)
            }

            FormFieldType.DATE.ordinal -> {
                CustomDateField(
                    formField = formField,
                    isFormSaved  = isFormSaved,
                    sendButtonState = sendButtonState,
                    isFormInReadOnlyMode = isFormInReadOnlyMode)
            }

            FormFieldType.TIME.ordinal -> {
                CustomTimeField(
                    formField = formField,
                    isFormSaved  = isFormSaved,
                    sendButtonState = sendButtonState,
                    isFormInReadOnlyMode = isFormInReadOnlyMode)
            }
            FormFieldType.DATE_TIME.ordinal -> {
                CustomDateTimeField(
                    formField = formField,
                    isFormSaved  = isFormSaved,
                    sendButtonState = sendButtonState,
                    isFormInReadOnlyMode = isFormInReadOnlyMode)
            }

        }
    }

    @Deprecated(message = "Remove this after compose migration", level = DeprecationLevel.WARNING)
    suspend fun renderLoopFieldsWithoutBranch(
        formField: FormField,
        formTemplate: FormTemplate,
        formFieldStack: Stack<FormField>,
        isFormSaved: Boolean = false,
        renderForm: suspend (
            branchTargetId: Int,
            selectedViewId: Int,
            loopEndId: Int,
            isFormSaved: Boolean,
            actualLoopCount: Int,
            currentLoopCount: Int
        ) -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).safeLaunch(CoroutineName("$tag Render loop fields without branch")) {
            if (formFieldStack.isNotEmpty()) {
                formFieldStack.peek().let { stackFormField ->
                    stackFormField.loopcount?.let { loopCount ->
                        if (loopCount > 1) {
                            stackFormField.loopcount =
                                loopCount - 1

                            renderForm(
                                FormUtils.getNextQNum(
                                    stackFormField.qnum + 1,
                                    formTemplate
                                ),
                                formField.viewId,
                                formField.qnum,
                                isFormSaved,
                                stackFormField.actualLoopCount!!,
                                loopCount
                            )
                        } else {
                            getNextFieldToBeRenderedForFieldsOutsideTheLoops(
                                formField,
                                formTemplate,
                                formFieldStack,
                                isFormSaved,
                                renderForm
                            )
                        }
                    }
                }
            } else {
                renderForm(
                    FormUtils.getNextQNum(
                        formField.qnum + 1,
                        formTemplate
                    ),
                    formField.viewId,
                    -1,
                    isFormSaved,
                    -1,
                    -1
                )
            }
        }
    }

    @Composable
    fun RenderComposeLoopFieldsWithoutBranch(
        formField: FormField,
        formTemplate: FormTemplate,
        formFieldStack: Stack<FormField>,
        isFormSaved: Boolean = false,
        renderForm: @Composable (Int, Int, Int, Boolean) -> Unit
    ){
        if (formFieldStack.isNotEmpty()) {
            formFieldStack.peek().let { stackFormField ->
                stackFormField.loopcount?.let { loopCount ->
                    if (loopCount > 1) {
                        stackFormField.loopcount =
                            loopCount - 1

                        renderForm(
                            FormUtils.getNextQNum(
                                stackFormField.qnum + 1,
                                formTemplate
                            ),
                            formField.viewId,
                            formField.qnum,
                            isFormSaved
                        )
                    } else {
                        renderForm(
                            FormUtils.getNextQNum(
                                formField.qnum + 1,
                                formTemplate
                            ),
                            formField.viewId,
                            -1,
                            isFormSaved
                        )
                    }
                }
            }
        } else {
            renderForm(
                FormUtils.getNextQNum(
                    formField.qnum + 1,
                    formTemplate
                ),
                formField.viewId,
                -1,
                isFormSaved
            )
        }
    }

    /* If the FDL script has branch_target as empty and placed at end of script,
       this will prevent further fields from not rendering. To avoid that we set branchTo field to -1 */
    fun checkAndResetBranchTarget(){
        if(this.branchTo > -1) {
            this.branchTo = -1
        }
    }

    /*
       This method will return the DTF condition for the field which present outside the inner loop.
       This method will check If the stack have more than one formField then it will fetch the second formField from last and return the actual loop count
       Else it will return actual loop count as -1
     */
    private suspend fun getNextFieldToBeRenderedForFieldsOutsideTheLoops(
        formField: FormField,
        formTemplate: FormTemplate,
        formFieldStack: Stack<FormField>,
        isFormSaved: Boolean = false,
        renderForm: suspend (
            branchTargetId: Int,
            selectedViewId: Int,
            loopEndId: Int,
            isFormSaved: Boolean,
            actualLoopCount: Int,
            currentLoopCount: Int
        ) -> Unit
    ) {
        if (formFieldStack.size > 1) {
            val nextFormFieldToBeRendered = formFieldStack[formFieldStack.size - 2]
            renderForm(
                FormUtils.getNextQNum(
                    formField.qnum + 1,
                    formTemplate
                ),
                formField.viewId,
                formField.qnum,
                isFormSaved,
                nextFormFieldToBeRendered.actualLoopCount ?: -1,
                nextFormFieldToBeRendered.loopcount ?: -1
            )
        } else {
            renderForm(
                FormUtils.getNextQNum(
                    formField.qnum + 1,
                    formTemplate
                ),
                formField.viewId,
                -1,
                isFormSaved,
                -1,
                -1
            )
        }
    }
}