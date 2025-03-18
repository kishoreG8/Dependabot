package com.trimble.ttm.formlibrary.ui.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MESSAGE_DETAIL
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.model.isValidForm
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.SHOULD_NOT_RETURN_TO_FORM_LIST
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.customViews.FormEditText
import com.trimble.ttm.formlibrary.customViews.FormMultipleChoice
import com.trimble.ttm.formlibrary.customViews.FreeFormEditText
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.CustomActionBarMessageDetailBinding
import com.trimble.ttm.formlibrary.databinding.FragmentMessageDetailBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.Message.Companion.getFormattedUiString
import com.trimble.ttm.formlibrary.ui.activities.FormLibraryActivity
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.CAN_DRIVER_REPLY
import com.trimble.ttm.formlibrary.utils.COMES_FROM_DETAIL
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.FormUtils.isViewInflationRequired
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.IS_REPLY_WITH_SAME
import com.trimble.ttm.formlibrary.utils.MARK_READ
import com.trimble.ttm.formlibrary.utils.MESSAGE
import com.trimble.ttm.formlibrary.utils.NO_REPLY_ACTION
import com.trimble.ttm.formlibrary.utils.READ_ONLY_VIEW_ALPHA
import com.trimble.ttm.formlibrary.utils.SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH
import com.trimble.ttm.formlibrary.utils.TRASH_COLLECTION
import com.trimble.ttm.formlibrary.utils.UiUtil.convertDpToPixel
import com.trimble.ttm.formlibrary.utils.UiUtil.getDisplayHeight
import com.trimble.ttm.formlibrary.utils.UiUtil.isTablet
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.findNavControllerSafely
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.navigateBack
import com.trimble.ttm.formlibrary.utils.ext.navigateTo
import com.trimble.ttm.formlibrary.utils.ext.setDebounceClickListener
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.isGreaterThan
import com.trimble.ttm.formlibrary.utils.isLessThan
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val FREEFORM_FORM_FIELD_INDEX = "1"

class MessageDetailFragment : Fragment(), KoinComponent {
    private val logTag = "MessageDetailFrag"
    private var formTemplateData: FormTemplate = FormTemplate()
    private var formName = ""
    private var formId: Int = -1
    private var formClass: Int = -1
    private var userName = ""
    private var sentDate = ""
    private var asn = ""
    private var replyActionType: Int = -1
    private var isDTF = false
    private var formTemplate: FormTemplate = FormTemplate()
    private var viewId = 0
    private var formTemplateSerializedString: String = ""
    private var viewIdToFormFieldMap = HashMap<Int, FormField>()
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val messagingViewModel: MessagingViewModel by sharedViewModel()
    private var formFieldsValuesMap: HashMap<String, ArrayList<String>> = HashMap()
    private var messageContent: String = ""
    private val displayHeight
        get() = getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = context?.let { context -> convertDpToPixel(60.0f, context) } ?: 0.0f
    private var customActionBarHeight = 100f
    private val formDataStoreManager: FormDataStoreManager by inject()
    private var deleteMessageDialog: AlertDialog? = null
    private var multipleInvocationLock: Boolean = false
    private lateinit var customActionBarView: View
    private var isFragmentOpenedForTrash = false

    private val appModuleCommunicator: AppModuleCommunicator by inject()

    private val formManager: FormManager by lazy {
        FormManager()
    }

    private var customActionBarMessageDetailBinding: CustomActionBarMessageDetailBinding? = null
    private var fragmentMessageDetailBinding: FragmentMessageDetailBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        Log.d(logTag, "is orientation Landscape : ${resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE}")
        fragmentMessageDetailBinding =
            FragmentMessageDetailBinding.inflate(inflater, container, false)
        customActionBarMessageDetailBinding =
            CustomActionBarMessageDetailBinding.inflate(inflater, container, false)
        return fragmentMessageDetailBinding!!.root
            .apply {
                activity?.applicationContext?.let { appContext ->
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (displayHeight - convertDpToPixel(
                            customActionBarHeight,
                            appContext
                        ).toInt())
                    )
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        // To close this fragment when it's opened and a HPN arrives
        messagingViewModel.shouldFinishFragment.observe(viewLifecycleOwner) {
            if (it) {
                navigateBack()
            }
        }

        fragmentMessageDetailBinding?.run {
            progressErrorViewForm.setProgressState(getString(R.string.loading_text))
            //To adjust view height for sendAddress and sentDate
            customActionBarHeight =
                (tvFrom.height + tvDate.height + dividerFrom.height + dividerDate.height).toFloat()
        }
        arguments?.getBoolean(SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH, false)?.let {
            isFragmentOpenedForTrash = it
        }
        arguments?.getSerializable(MESSAGE)?.let {
            with(it as Message) {
                this@MessageDetailFragment.formId =
                    if (formId.isNotEmpty()) formId.toInt() else -1
                this@MessageDetailFragment.formName = formName
                this@MessageDetailFragment.formClass =
                    if (formClass.isNotEmpty()) formClass.toFloat().toInt() else -1
                this@MessageDetailFragment.asn = asn
                this@MessageDetailFragment.userName = userName
                this@MessageDetailFragment.sentDate = dateTime
                this@MessageDetailFragment.replyActionType= if (replyActionType.isNotEmpty()) replyActionType.toFloat().toInt() else -1
                formFieldsValuesMap = messageFormViewModel.processDispatcherFormValues(
                    fieldList = formFieldList
                )
                Log.d("FormDefValues","Dispatcher Form Values $formFieldsValuesMap")
                messageContent = summaryText
            }
        }
        multipleInvocationLock = false
        loadMessages()
        observeAndRenderForm()
    }

    private fun observeAndRenderForm(){
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(
                logTag
            )
        ) {
            messageFormViewModel.renderValues.collect {
                if (!(it.branchTargetId == -1 && it.selectedViewId == -1 && it.loopEndId == -1)) {
                    renderForms(
                        it.branchTargetId,
                        it.selectedViewId,
                        it.loopEndId,
                        it.actualLoopCount
                    )
                }
            }
        }
    }

    private fun loadMessages() {
        if (multipleInvocationLock.not()) {
            multipleInvocationLock = true
            if (fragmentMessageDetailBinding?.llLeftLayout?.childCount == 0 && fragmentMessageDetailBinding?.llRightLayout?.childCount == 0) loadFormTemplate()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        activity?.onBackPressedDispatcher?.addCallback(this, onBackButtonPressCallback)
        //Hides the toolbar of the MessageActivity to show custom toolbar
        activity?.findViewById<ComposeView>(R.id.toolbar)?.visibility = View.GONE
        (activity as? MessagingActivity)?.lockDrawer()
        enableOrDisableReplyAndDeleteButton(true)
        setupToolbar()
    }

    override fun onStop() {
        Log.logLifecycle(logTag, "$logTag onStop")
        (activity as? MessagingActivity)?.stopTTS()
        super.onStop()
    }

    private fun loadFormTemplate() {
        viewId = 0
        viewIdToFormFieldMap.clear()
        messageFormViewModel.getViewIdToFormFieldStackMap().clear()
        fragmentMessageDetailBinding?.tvFromValue?.text = userName
        fragmentMessageDetailBinding?.tvDateValue?.text = sentDate
        if (formId.isGreaterThan(ZERO)) {
            lifecycleScope.launch(
                messageFormViewModel.coroutineDispatcherProvider.default() + CoroutineName(
                    logTag
                )
            ) {
                messageFormViewModel.markMessageAsRead(
                    logTag,
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber(),
                    appModuleCommunicator.doGetObcId(),
                    asn,
                    MARK_READ,
                    if (isFragmentOpenedForTrash) TRASH_COLLECTION else INBOX_COLLECTION,
                )
                //If a received form is a freeform, then this writes the messageContent to the read-only cff.
                // Because the cff is considered as freeform messaging.
                if (FormDef(formid = formId, formClass = formClass).isFreeForm()) {
                    formFieldsValuesMap[FREEFORM_FORM_FIELD_INDEX] = arrayListOf(
                        messageContent
                    )
                }
                val form: Form = messageFormViewModel.getForm(
                    formId = formId.toString(),
                    isFreeForm = FormDef(formid = formId, formClass = formClass).isFreeForm(),
                    uiFormResponse = messageFormViewModel.getSavedUIDataDuringOrientationChange(),
                    dispatcherFormValuesFormFieldMap = formFieldsValuesMap,
                    shouldFillUiResponse = false,
                    isFormSaved = false,
                    isReplayWithSame = (formClass == -1)
                )
                processForm(
                    form.formTemplate
                )
                withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                    setupToolbar()
                }
            }
        }
    }

    private suspend fun processForm(
        formTemplate: FormTemplate
    ) {
        if (messageFormViewModel.checkIfFormIsValid(formTemplate)) {
            formName = formTemplate.formDef.name
            isDTF = messageFormViewModel.checkIfFormIsDtf(formTemplate)
            this.formTemplate = formTemplate
            formTemplateSerializedString =
                messageFormViewModel.serializeFormTemplate(formTemplate)
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) { fragmentMessageDetailBinding?.progressErrorViewForm?.setNoState() }
            val formTemplateToRender= messageFormViewModel.fetchFormTemplate(
                formTemplateSerializedString
            )
            messageFormViewModel.ttsInputList = Utils.setTtsHeader(userName,sentDate,formName)
            getFormFields(formTemplateToRender)
            if(isDTF){
                (this.activity as? Activity)?.let { formManager.restrictOrientationChange(it) }
            }
            else{
                messageFormViewModel.ttsInputList + (context?.let {
                    Utils.getTtsList(
                        formTemplateToRender.formFieldsList
                    )
                } ?: ArrayList() )
            }
            Log.d(logTag, "TTS list: ${messageFormViewModel.ttsInputList}")
        } else {
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                fragmentMessageDetailBinding?.progressErrorViewForm?.setErrorState(getString(R.string.form_not_displayed))
            }
        }
    }

    private suspend fun getFormFields(
        formTemplateToRender: FormTemplate
    ) {
        withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
            if (isFragmentOpenedForTrash.not()) customActionBarMessageDetailBinding?.imgBtnPlay?.show()
        }
        formTemplateToRender.formFieldsList.forEach { formField ->
            formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
            messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                formFieldValuesMap = formFieldsValuesMap,
                formField = formField,
                caller = logTag,
                actualLoopCount = -1
            )
            var isResponseNeededToProceed: Boolean
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                isResponseNeededToProceed =
                    createAndAddFormControl(
                        formField,
                        dispatcherFormValuesFormFieldMap = formFieldsValuesMap
                    )
            }
            if (isResponseNeededToProceed) return
        }
        formManager.checkAndResetBranchTarget()
    }

    /**
     * This method will filter the form fields based on branchTargetId and then renders the form fields.
     * It also removes the form fields if it's viewId is greater than selectedViewId
     *
     * @param branchTargetId The form field id from which form fields should be rendered.
     * @param selectedViewId viewId of the selected form field
     */
    private suspend fun renderForms(
        branchTargetId: Int,
        selectedViewId: Int,
        loopEndId: Int,
        actualLoopCount : Int = -1
    ) = withContext(
        messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(
            logTag
        )
    ) {
        viewId = selectedViewId + 1
        fragmentMessageDetailBinding?.llLeftLayout?.let { leftLayout ->
            fragmentMessageDetailBinding?.llRightLayout?.let { rightLayout ->
                formManager.removeViews(
                    viewId,
                    leftLayout,
                    rightLayout,
                    logTag
                )
            } ?: Log.w(logTag, "rightLayout is null. removeView not happened")
        } ?: Log.w(logTag, "leftLayout is null. removeView not happened")
        val formTemplateToRender = messageFormViewModel.getFormTemplateBasedOnBranchTargetId(
            branchTargetId,
            loopEndId,
            isDTF,
            formTemplate,
            formTemplateSerializedString
        )
        branchTargetId.let {
            val iterationStartIndex =
                messageFormViewModel.getLastIndexOfBranchTargetId(formTemplate, branchTargetId)
            if (iterationStartIndex >= 0) {
                formTemplateToRender.formFieldsList.forEachIndexed { index, formField ->
                    if (index >= iterationStartIndex) {
                        formField.uiData = EMPTY_STRING
                        messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                            formFieldsValuesMap,
                            formField,
                            caller = logTag,
                            actualLoopCount = actualLoopCount
                        )
                        val isResponseNeededToProceed =
                            createAndAddFormControl(
                                formField,
                                dispatcherFormValuesFormFieldMap = formFieldsValuesMap
                            )
                        if (isResponseNeededToProceed) {
                            return@let
                        }
                    }
                }
                formManager.checkAndResetBranchTarget()
            }
        }
    }

    override fun onPause() {
        onBackButtonPressCallback.remove()
        (activity as? MessagingActivity)?.unlockDrawer()
        draftFormLocally()
        super.onPause()
    }

    private fun draftFormLocally() {
        if (isDTF || formTemplate.formDef.cid.isLessThan(ZERO)) return
        constructFormTemplateData()
        messageFormViewModel.draftFormLocally(formTemplateData)
    }

    private fun constructFormTemplateData() {
        fragmentMessageDetailBinding?.let {
            fragmentMessageDetailBinding?.llLeftLayout?.let { leftLayout ->
                it.llRightLayout.let { rightLayout ->
                    formManager.iterateViewsToFetchDataFromFormFields(
                        leftLayout,
                        rightLayout,
                        viewIdToFormFieldMap,
                        logTag
                    ).let { constructedFormFieldList ->
                        messageFormViewModel.constructFormFieldsWithAutoFields(this.formTemplate, constructedFormFieldList)
                        formTemplateData =
                            FormTemplate(
                                formTemplate.formDef,
                                constructedFormFieldList
                            )
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            setBackgroundDrawable(
                this@MessageDetailFragment.context?.let { context ->
                    AppCompatResources.getDrawable(
                        context.applicationContext,
                        R.color.toolBarColor
                    )
                }
            )
            customActionBarMessageDetailBinding?.run {
                root.let {
                    customActionBarView = it
                    toolbarTitle.text = formName
                    setCustomView(
                        it,
                        ActionBar.LayoutParams(
                            ActionBar.LayoutParams.MATCH_PARENT,
                            ActionBar.LayoutParams.MATCH_PARENT
                        )
                    )
                    if (isFragmentOpenedForTrash) {
                        imgBtnPlay.hide()
                        imgBtnReply.hide()
                    } else {
                        imgBtnPlay.show()
                        imgBtnPlay.setOnClickListener {
                            Log.logUiInteractionInInfoLevel(logTag, "$logTag TTS Play clicked",null,"TTSlist" to {messageFormViewModel.ttsInputList})
                            lifecycleScope.launch(CoroutineName(logTag)) {
                                (activity as? MessagingActivity)?.readTheList(messageFormViewModel.ttsInputList)
                            }
                        }
                        imgBtnReply.setDebounceClickListener {
                            if (replyActionType == NO_REPLY_ACTION ) {
                                //When Form is sent with no reply action and is driver editable
                                if (formTemplate.formDef.driverEditable == 1) {
                                    Log.logUiInteractionInInfoLevel(logTag,"$logTag Msg Reply with same clicked")
                                    findNavControllerSafely()?.navigateTo(
                                        R.id.messageDetailFragment,
                                        R.id.action_messageDetailFragment_to_messageReplyFragment,
                                        bundleOf(
                                            MESSAGE to arguments?.getSerializable(MESSAGE),
                                            FORM_RESPONSE_TYPE to INBOX_FORM_RESPONSE_TYPE,
                                            SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH to false,
                                            IS_REPLY_WITH_SAME to true,
                                            CAN_DRIVER_REPLY to true
                                        )
                                    )
                                } else {
                                    //When Form is sent with no reply action and is not driver editable
                                    Log.logUiInteractionInInfoLevel(logTag,"$logTag Moving to screen form Library")
                                    val intent = Intent(context, FormLibraryActivity::class.java).apply {
                                        putExtra(SHOULD_NOT_RETURN_TO_FORM_LIST, true)
                                    }
                                    intent.flags =
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    startActivity(intent)
                                }
                            }
                            else {
                                //When form sent with reply action - reply with same and reply with new
                                Log.logUiInteractionInInfoLevel(logTag, "$logTag Msg Reply clicked")
                                enableOrDisableReplyAndDeleteButton(false)
                                findNavControllerSafely()?.navigateTo(
                                    R.id.messageDetailFragment,
                                    R.id.action_messageDetailFragment_to_messageReplyFragment,
                                    bundleOf(
                                        MESSAGE to arguments?.getSerializable(MESSAGE),
                                        FORM_RESPONSE_TYPE to INBOX_FORM_RESPONSE_TYPE,
                                        COMES_FROM_DETAIL to true,
                                        CAN_DRIVER_REPLY to false
                                    )
                                )
                            }
                        }
                    }
                    imgBtnDelete.setOnClickListener {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag Msg Delete clicked")
                        enableOrDisableReplyAndDeleteButton(false)
                        (activity as? MessagingActivity)?.stopTTS()
                        showDeleteAlertDialogBox()
                    }
                }
                setDisplayHomeAsUpEnabled(true)
                show()
            }
        }
    }

    private fun showDeleteAlertDialogBox() {
        context?.let { context ->
            deleteMessageDialog = UiUtils.showAlertDialog(
                AlertDialogData(
                    context = context,
                    message = getString(if (isFragmentOpenedForTrash) R.string.delete_message_permanent_content_message_detail else R.string.delete_message_content_message_detail),
                    title = getString(R.string.delete_message_title),
                    positiveActionText = getString(if (isFragmentOpenedForTrash) R.string.delete else R.string.move_to_trash),
                    negativeActionText = getString(R.string.cancel).uppercase(),
                    isCancelable = false,
                    positiveAction = {
                        Log.logUiInteractionInInfoLevel(
                            logTag,
                            "$logTag Move to Trash positive clicked"
                        )
                        onPositiveActionClick(context)
                    },
                    negativeAction = {
                        Log.logUiInteractionInInfoLevel(
                            logTag,
                            "$logTag Move to Trash negative clicked"
                        )
                        enableOrDisableReplyAndDeleteButton(true)
                        deleteMessageDialog?.dismiss()
                        deleteMessageDialog = null
                    })
            ).also { alertDialog ->
                setAlertDialogKeyListener(alertDialog)
            }
        }
    }

    private fun onPositiveActionClick(context: Context) {
        enableOrDisableReplyAndDeleteButton(true)
        if (isFragmentOpenedForTrash) {
            messageFormViewModel.deleteMessageForTrash(
                asn,
                MESSAGE_DETAIL
            ).also {
                showToastAndNavigateBack(context)
            }
        } else {
            messageFormViewModel.markMessageAsDeleted(
                asn,
                MESSAGE_DETAIL
            )
            messageFormViewModel.viewModelScope.launch {
                messageFormViewModel.observeDeletedInboxMessagesASN()
                    .collect {
                        if (it.isNotNull() && it.isNotEmpty()) {
                            showToastAndNavigateBack(context)
                        }
                    }
            }
        }
        deleteMessageDialog?.dismiss()
    }

    private fun showToastAndNavigateBack(context: Context) {
        Toast.makeText(
            context,
            context.getString(R.string.delete_success_message),
            Toast.LENGTH_SHORT
        ).show()
        findNavControllerSafely()?.navigateUp()
    }

    private fun setAlertDialogKeyListener(alertDialog: AlertDialog) {
        alertDialog.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && alertDialog.isShowing) {
                enableOrDisableReplyAndDeleteButton(true)
                alertDialog.dismiss()
            }
            true
        }
    }

    //assigned true to disable views in imessage form
    private suspend fun createAndAddFormControl(
        formField: FormField,
        isFormSaved: Boolean = true,
        dispatcherFormValuesFormFieldMap: HashMap<String, ArrayList<String>>
    ): Boolean {
        val textInputLayout = formManager.createTextInputLayout(
            formField.qtype,
            formId,
            formClass,
            formTemplate.formFieldsList.size,
            activity as AppCompatActivity,
            isRequired = messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = formField,
                makeFieldsNonEditable = true
            )
        )
        var view: View? = null
        var isResponseNeeded = false
        formField.isInDriverForm = false

        val nextQNumAndInflationRequirement =
            isViewInflationRequired(formManager.branchTo, formField, formTemplate)
        val nextQNumToShow = nextQNumAndInflationRequirement.first
        formField.viewId = viewId
        Log.d(logTag, "Field to be processed, qnum : ${formField.qnum} fieldId : ${formField.fieldId} " +
                "nextQNumToShow : $nextQNumToShow inflationRequirement : ${nextQNumAndInflationRequirement.second} qType : ${formField.qtype}")
        if (nextQNumAndInflationRequirement.second) {
            when (formField.qtype) {
                FormFieldType.TEXT.ordinal ->
                    //Checks only for the fields size insteadof isFreeform.
                    // If a single field form received, it should be rendered as cff
                    view = if (formTemplate.formFieldsList.size == 1
                    ) {
                        // Set counter max length
                        textInputLayout.counterMaxLength =
                            formTemplate.formFieldsList[0].ffmLength
                        context?.let { context ->
                            FreeFormEditText(
                                context,
                                textInputLayout,
                                formField,
                                isFormSaved,
                                (displayHeight - toolbarHeightInPixels.toInt()) / 2
                            ).apply {
                                filters =
                                    arrayOf(InputFilter.LengthFilter(formTemplate.formFieldsList[0].ffmLength))
                            }.also {
                                if (FormDef(formid = formId, formClass = formClass).isValidForm()
                                        .not()
                                ) {
                                    // Only single corporate free form
                                    messageFormViewModel.getFreeFormEditTextHintAndMessage(
                                        formTemplate.formFieldsList[0],
                                        if (dispatcherFormValuesFormFieldMap.values.size > ZERO) dispatcherFormValuesFormFieldMap.values.first().first() else EMPTY_STRING
                                    ).also { hintAndText ->
                                        textInputLayout.hint = hintAndText.first
                                        it.setText(hintAndText.second.getFormattedUiString())
                                    }
                                } else {
                                    textInputLayout.hint =
                                        getString(R.string.freeform_non_editable_input_field_hint_text)
                                    if (dispatcherFormValuesFormFieldMap.values.size > ZERO)
                                        it.setText(
                                            dispatcherFormValuesFormFieldMap.values.first().first()
                                                .getFormattedUiString()
                                        )
                                }
                            }
                        }
                    } else context?.let { context ->
                        FormEditText(
                            context,
                            textInputLayout,
                            formField,
                            isFormSaved
                        )
                    }
                FormFieldType.MULTIPLE_CHOICE.ordinal -> {
                    formField.formChoiceList?.find { formChoice -> formChoice.value == formField.uiData }
                        ?.let { matchedFormChoice ->
                            formField.uiData = matchedFormChoice.value
                        }
                    view = context?.let { context ->
                        FormMultipleChoice(
                            context,
                            textInputLayout,
                            formField,
                            isFormSaved,
                            processMultipleChoice
                        )
                    }
                    isResponseNeeded=formField.multipleChoiceDriverInputNeeded()
                }
                else -> {
                    this.context?.let {context ->
                        view = formManager.createViewWithFormField(
                            formField = formField,
                            stack = messageFormViewModel.getFormFieldStack(),
                            context = context,
                            textInputLayout = textInputLayout,
                            isFormSaved = isFormSaved,
                            lifecycleScope = lifecycleScope,
                            supportFragmentManager = childFragmentManager
                        ) {
                            messageFormViewModel.getFormFieldCopy(formField)
                        }
                        } ?: Log.w(logTag, "context is null when attempting the method createViewWithFormField")
                }
            }
            messageFormViewModel.createTtsString(formField)
        }

        if(messageFormViewModel.isOfTextInputLayoutViewType(formField = formField))
            addViewToLayout(view, nextQNumToShow, formField, textInputLayout)
        else view?.let {
            addFormComponentToLayout(formField,it)
        }

        if(messageFormViewModel.isProcessingMultipleChoiceFieldRequired(formField = formField, isSyncToQueue = false, processMultipleChoice = processMultipleChoice)) return true

        formManager.setPendingClickListener(view, false)

        if (FormUtils.isProcessLoopFieldsRequired(
                formTemplate,
                formField,
                messageFormViewModel.getFormFieldStack()
            )
        ) {
            formManager.renderLoopFieldsWithoutBranch(
                formField = formField,
                formTemplate = formTemplate,
                formFieldStack = messageFormViewModel.getFormFieldStack(),
                renderForm = { branchTargetId: Int, selectedViewId: Int, loopEndId: Int, _: Boolean, actualLoopCount : Int, _ : Int ->
                    renderForms(
                        branchTargetId = branchTargetId,
                        selectedViewId = selectedViewId,
                        loopEndId = loopEndId,
                        actualLoopCount = actualLoopCount
                    )
                }
            )
            return true
        }

        //return if response needed
        if (isResponseNeeded) {
            return true
        }
        return false
    }


    private fun addViewToLayout(
        view: View?,
        nextQNumToShow: Int,
        formField: FormField,
        textInputLayout: TextInputLayout
    ) {
        if (formManager.branchTo > -1) {
            if (nextQNumToShow != -1 && (formField.qnum == nextQNumToShow)) {
                view?.let {
                    textInputLayout.addView(view)
                    addFormComponentToLayout(formField, textInputLayout)
                }
                formManager.branchTo = -1
            }
        } else {
            view?.let {
                textInputLayout.addView(view)
                addFormComponentToLayout(formField, textInputLayout)
            }
        }
    }


    private fun addFormComponentToLayout(
        formField: FormField,
        view: View
    ) {
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(
                logTag
            )
        ) {
            formField.viewId = viewId++
            //Map between viewId and FormField
            viewIdToFormFieldMap[formField.viewId] = formField
            formField.formChoiceList?.forEach {
                it.viewId = formField.viewId
            }
            view.id = formField.viewId
            view.tag = formField.viewId
            checkAndStoreMultipleChoiceFormFieldStack(formField, view)
            //Checks only for the fields size insteadof isFreeform.
            // If a single field form received, it should be rendered as cff

            if (formTemplate.formFieldsList.size == 1
            ) {
                addFreeFormView(view)
            } else {
                checkForOrientationAndAddFields(view)
            }

            //To make driver clear that this form is non-editable, The alpha is reduced
            view.alpha = READ_ONLY_VIEW_ALPHA
        }
    }

    private fun checkAndStoreMultipleChoiceFormFieldStack(formField: FormField, view: View) {
        //Saving in map only if view is of Multiple choice type, it is unnecessary for other form fields
        if (formField.qtype == FormFieldType.MULTIPLE_CHOICE.ordinal) {
            //viewIdToFormFieldStackMap[view.id ] = formFiledStack.clone() as Stack<FormField>
            messageFormViewModel.getViewIdToFormFieldStackMap()[view.id] =
                messageFormViewModel.getFormFieldStackCopy()
        }
    }

    private fun addFreeFormView(view: View) {
        fragmentMessageDetailBinding?.run {
            llRightLayout.visibility = View.GONE
            val llLeftLayoutParams: LinearLayout.LayoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            llLeftLayoutParams.weight = 2.0f
            llLeftLayout.layoutParams = llLeftLayoutParams
            llLeftLayout.addView(view)
        }
    }

    private fun checkForOrientationAndAddFields(view: View) {
        Log.d(logTag, "Field rendered, fieldId : ${viewIdToFormFieldMap[view.id]?.fieldId} viewID : ${view.id}")
        fragmentMessageDetailBinding?.run {
            if (context?.let { context ->
                    isTablet(context)
                } == true
                && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (view.id % 2 == 0) {
                    llLeftLayout.addView(view)
                } else {
                    llRightLayout.addView(view)
                }
            } else {
                llLeftLayout.addView(view)
            }
            mapFieldIdsToViews(view, true)
        }
    }

    private fun mapFieldIdsToViews(view: View? = null, isAddingFields: Boolean) {
        lifecycleScope.launch(CoroutineName(logTag)) {
            (activity as? MessagingActivity)?.run {
                if (isAddingFields && view != null) {
                    messagingViewModel.mapFieldIdsToViews[view.id] = view
                    formManager.setNextActionInKeypad(messagingViewModel.mapFieldIdsToViews, this)
                } else if (!isAddingFields) messagingViewModel.mapFieldIdsToViews.clear()
            }
        }
    }

    private fun enableOrDisableReplyAndDeleteButton(isEnabled: Boolean) {
        if (::customActionBarView.isInitialized) {
            customActionBarMessageDetailBinding?.run {
                imgBtnReply.isEnabled = isEnabled
                imgBtnDelete.isEnabled = isEnabled
            }
        }
    }

    private var processMultipleChoice: (FormChoice?) -> Unit = {
        messageFormViewModel.processMultipleChoice(it, formTemplate)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        draftFormLocally()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        //Hides the custom toolbar of this fragment
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        deleteMessageDialog?.dismiss()
        mapFieldIdsToViews(isAddingFields = false)
        customActionBarMessageDetailBinding = null
        fragmentMessageDetailBinding = null
    }

    private val onBackButtonPressCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            (activity as? AppCompatActivity)?.supportActionBar?.hide()
            navigateBack()
        }
    }

    private fun navigateBack() {
        findNavControllerSafely()?.navigateBack(R.id.messageDetailFragment)
    }
}