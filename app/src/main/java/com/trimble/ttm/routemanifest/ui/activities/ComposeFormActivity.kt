package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.composable.commonComposables.ProgressErrorComposable
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.composable.customViews.CustomAndroidView
import com.trimble.ttm.commons.composable.customViews.CustomMultipleChoice
import com.trimble.ttm.commons.composable.customViews.CustomTextField
import com.trimble.ttm.commons.composable.uiutils.styles.WorkFlowTheme
import com.trimble.ttm.commons.logger.BACK_BUTTON_CLICK
import com.trimble.ttm.commons.logger.DRIVER_AND_REPLY_MESSAGE_NAVIGATION
import com.trimble.ttm.commons.logger.FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.ON_STOP_CALLBACK
import com.trimble.ttm.commons.logger.SAVE_TO_DRAFTS_BUTTON_CLICK
import com.trimble.ttm.commons.logger.TRIP_FORM
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.model.isValidForm
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.ui.BaseToolbarInteractionActivity
import com.trimble.ttm.commons.utils.CAN_SHOW_CANCEL
import com.trimble.ttm.commons.utils.DISPATCH_FORM_PATH_SAVED
import com.trimble.ttm.commons.utils.DRIVER_FORM_ID
import com.trimble.ttm.commons.utils.FORM_RESPONSE_PATH
import com.trimble.ttm.commons.utils.IMESSAGE_REPLY_FORM_DEF
import com.trimble.ttm.commons.utils.IS_ACTION_RESPONSE_SENT_TO_SERVER
import com.trimble.ttm.commons.utils.IS_FROM_TRIP_PANEL
import com.trimble.ttm.commons.utils.IS_SECOND_FORM_KEY
import com.trimble.ttm.commons.utils.checkGmsBarcodeScanningModuleAvlAndDownload
import com.trimble.ttm.commons.utils.ext.getCompleteFormID
import com.trimble.ttm.commons.utils.ext.getDispatchFormPathSaved
import com.trimble.ttm.commons.utils.ext.getDriverFormId
import com.trimble.ttm.commons.utils.ext.getFormData
import com.trimble.ttm.commons.utils.ext.getReplyFormDef
import com.trimble.ttm.commons.utils.ext.isFromDraft
import com.trimble.ttm.commons.viewModel.SignatureDialogViewModel
import com.trimble.ttm.formlibrary.customViews.FreeFormEditText
import com.trimble.ttm.formlibrary.databinding.ActivityFormBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FREE_FORM_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.NO_REPLY_ACTION
import com.trimble.ttm.formlibrary.utils.READ_ONLY_VIEW_ALPHA
import com.trimble.ttm.formlibrary.utils.UiUtil
import com.trimble.ttm.formlibrary.utils.Utils.scheduleOneTimeImageUploadIfImagesAvailable
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FormData
import com.trimble.ttm.routemanifest.model.isInValid
import com.trimble.ttm.routemanifest.utils.CURRENT_STOP_INDEX
import com.trimble.ttm.routemanifest.utils.IS_DRIVER_IN_IMESSAGE_REPLY_FORM
import com.trimble.ttm.routemanifest.utils.LAUNCH_SCREEN_INTENT
import com.trimble.ttm.routemanifest.utils.SELECTED_STOP_ID
import com.trimble.ttm.routemanifest.utils.STOP_NAME_FOR_FORM
import com.trimble.ttm.routemanifest.utils.ext.hide
import com.trimble.ttm.routemanifest.utils.ext.setVisibility
import com.trimble.ttm.routemanifest.utils.ext.startDispatchFormActivity
import com.trimble.ttm.routemanifest.viewmodel.FormViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ComposeFormActivity : BaseToolbarInteractionActivity() {

    private val tag = "ComposeFormActivity"
    private var formTemplateData: FormTemplate = FormTemplate()
    private var path: String = ""
    private var stopActionFormKey = DispatchFormPath()
    private var stopName = ""
    private var formId: Int? = null
    private var formClass: Int = -1
    private var actionId = -1
    private var dialog: AlertDialog? = null
    private var stopId = -1
    private var isSyncToQueue = false
    private var isDTF = false
    private var formTemplate: FormTemplate = FormTemplate()
    private var formTemplateSerializedString: String = ""
    //This variable is used to store both the default and form response values
    private var formFieldValuesMap: HashMap<String, ArrayList<String>> = HashMap()
    private var actionPayload: Action = Action()
    private var dispatchFormPathSaved = DispatchFormPath()
    private var isDriverInImessageReplyForm: Boolean = false
    private var isDriverInSingleForm: Boolean = false // Single form scenario
    private val dataStoreManager: DataStoreManager by inject()
    private val formDataStoreManager: FormDataStoreManager by inject()
    private val formViewModel: FormViewModel by viewModel()
    private val messageFormViewModel: MessageFormViewModel by inject()
    private val draftingViewModel: DraftingViewModel by inject()
    private val signatureViewModel: SignatureDialogViewModel by viewModel()
    private val appModuleCommunicator: AppModuleCommunicator by inject()

    private val displayHeight
        get() = UiUtil.getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = UiUtil.convertDpToPixel(60.0f, this)
    private var driverFormDef = FormDef(formid = -1, formClass = -1)
    private var replyFormDef = FormDef(formid = -1, formClass = -1)
    private var latestFormRecipients: ArrayList<Recipients> = ArrayList()
    private var isFromTripPanel: Boolean = false
    private var isActionResponseSentToServer: Boolean = false
    private var processMultipleChoice: (FormChoice?) -> Triple<Int, Int, Int> = {
        messageFormViewModel.processComposeMultipleChoice(
            formChoice = it,
            formTemplate = formTemplate
        )
    }
    private val formManager: FormManager by lazy {
        FormManager()
    }
    private val mapFieldIdsToViews = mutableMapOf<Int, View>()
    private lateinit var binding: ActivityFormBinding
    private lateinit var focusManager: FocusManager
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        Log.d(tag, "is orientation Landscape : ${resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE}")
        draftingViewModel.lookupSaveToDraftsFeatureFlag(appModuleCommunicator.getAppModuleApplicationScope())
        binding = DataBindingUtil.setContentView(this, R.layout.activity_form)
        binding.root.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (
                    displayHeight - UiUtil.convertDpToPixel(
                        resources.getDimension(R.dimen.formsLayoutMargin),
                        applicationContext
                    ).toInt()
                    )
            )
        }.also {
            binding.progressErrorViewFormCompose.setContent {
                ProgressErrorComposable(screenContentState = formViewModel.formUiState.collectAsState().value)
            }
            binding.formFieldsLayout.visibility = View.VISIBLE
            binding.llLeftLayout.visibility = View.INVISIBLE
            path = intent.getStringExtra(FORM_RESPONSE_PATH) ?: ""
            isDriverInImessageReplyForm = intent.getBooleanExtra(IS_SECOND_FORM_KEY, false)
            setUpToolbar()
            isFromTripPanel = intent.getBooleanExtra(IS_FROM_TRIP_PANEL, false)
            intent.getCompleteFormID().let {
                dispatchFormPathSaved = it
            }
            isActionResponseSentToServer =
                intent.getBooleanExtra(IS_ACTION_RESPONSE_SENT_TO_SERVER, false)
            formViewModel.getDispatchFormPath(isDriverInImessageReplyForm, intent, dispatchFormPathSaved)
                .let {
                    stopActionFormKey = it
                    stopName = it.stopName
                    stopId = it.stopId
                    actionId = it.actionId
                    formId = it.formId
                    formClass = it.formClass
                }
            binding.ivCancel.setVisibility(
                formViewModel.canShowCancel(
                    isDriverInImessageReplyForm,
                    intent.getBooleanExtra(CAN_SHOW_CANCEL, false)
                )
            )
            formId?.let { formId ->
                lifecycleScope.launch(
                    messageFormViewModel.coroutineDispatcherProvider.default() + CoroutineName(
                        tag
                    )
                ) {
                    if (actionId >= 0) {
                        actionPayload =
                            formViewModel.getStopAction(stopId.toString(), actionId.toString())
                        if (actionPayload.isInValid()) {
                            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                                onInvalidForm(tag + "_ActionPayloadInvalid")
                            }
                        } else {
                            driverFormDef = FormDef(
                                formid = actionPayload.driverFormid,
                                formClass = actionPayload.driverFormClass
                            )
                            if (actionPayload.forcedFormId.isNotEmpty() && actionPayload.forcedFormId.toInt() > 0) {
                                replyFormDef = FormDef(
                                    formid = actionPayload.forcedFormId.toInt(),
                                    formClass = actionPayload.forcedFormClass
                                )
                            }
                            val isFreeForm: Boolean
                            if (isDriverInImessageReplyForm) {
                                withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                                    binding.btnSave.text = getString(R.string.send)
                                }
                                isFreeForm = replyFormDef.isFreeForm()
                            } else {
                                isFreeForm = driverFormDef.isFreeForm()
                            }
                            val form: Form = formViewModel.getForm(
                                FormData(formTemplatePath = path, stopId = stopId.toString(),
                                    actionId = actionId.toString(), formId = formId.toString(), isFreeForm = isFreeForm),
                                isActionResponseSentToServer,
                                isDriverInImessageReplyForm,
                                messageFormViewModel.isReplayWithSameFormType(
                                    driverFormId = driverFormDef.formid,
                                    replyFormId = replyFormDef.formid
                                )
                            )
                            formFieldValuesMap = form.formFieldValuesMap
                            val draftedFormFieldMap = formViewModel.getDraftedFormFieldMap(intent.getFormData())
                            val isDraftResponseOfDispatchForm = intent.isFromDraft()
                            val formTemplate = form.formTemplate
                            val uiFormResponse: UIFormResponse
                            if (isDraftResponseOfDispatchForm) {
                                uiFormResponse = UIFormResponse().also {
                                    it.formData = FormResponse(
                                        fieldData = ArrayList<HashMap<String, String>>().let { fieldData ->
                                            draftedFormFieldMap.values.forEach { formField ->
                                                fieldData.add(hashMapOf(formField.fieldType to formField.formFieldData))
                                            }
                                            fieldData as? ArrayList<Any> ?: ArrayList()
                                        }
                                    )
                                    formViewModel.isUIResponseAvailableInFireStore = draftedFormFieldMap.values.isNotEmpty()
                                }
                            } else {
                                uiFormResponse = form.uiFormResponse
                                formViewModel.isUIResponseAvailableInFireStore = uiFormResponse.formData.fieldData.isNotEmpty()
                            }
                            if (driverFormDef.isValidForm() && replyFormDef.formid >= 0) {
                                // check if we need to show the display screen
                                if (showFirstDisplayScreen()) {
                                    // imessage form(1st form) - no user interaction
                                    withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                                        binding.btnSave.text =
                                            getString(R.string.next_button_formactivity)
                                    }
                                    // if form is already saved
                                    if (uiFormResponse.isSyncDataToQueue) {
                                        // If freeform is already saved. show the saved driver freeform, not imessage form
                                        if (replyFormDef.isFreeForm()) {
                                            val corporateFreeForm = formViewModel.getFreeForm(
                                                path,
                                                actionId.toString(),
                                                replyFormDef.formid
                                            )
                                            // If the first form is not a freeform, it is mocked as freeform to align the freeform to the full screen
                                            // showing second screen(driver screen).To align freeform properly in 1st screen with the data of 2nd screen,
                                            // while fetching saved form
                                            // If order changed, Freeform message won't display
                                            isDriverInImessageReplyForm = true
                                            processForm(
                                                corporateFreeForm.formTemplate,
                                                corporateFreeForm.uiFormResponse,
                                                formFieldValuesMap
                                            )
                                        } else {
                                            // If form already saved. show the saved driver form, not imessage form
                                            val savedForm: Form = formViewModel.getForm(
                                                FormData(formTemplatePath = path, stopId = stopId.toString(),
                                                    actionId = actionId.toString(), formId = formId.toString(), isFreeForm = isFreeForm),
                                                isActionResponseSentToServer = true,
                                                isDriverInImessageReplyForm,
                                                messageFormViewModel.isReplayWithSameFormType(
                                                    driverFormId = driverFormDef.formid,
                                                    replyFormId = replyFormDef.formid
                                                )
                                            )
                                            processForm(
                                                savedForm.formTemplate,
                                                savedForm.uiFormResponse,
                                                savedForm.formFieldValuesMap
                                            )
                                        }
                                    } else {
                                        processForm(
                                            formTemplate,
                                            UIFormResponse(),
                                            formFieldValuesMap
                                        )
                                    }
                                } else {
                                    // driver form(2nd form)
                                    when {
                                        // reply_with_freeform
                                        replyFormDef.isFreeForm() -> {
                                            // we put as true if we are in a free form
                                            isDriverInImessageReplyForm = true
                                            val corporateFreeForm = formViewModel.getFreeForm(
                                                path,
                                                actionId.toString(),
                                                formId
                                            )
                                            processForm(
                                                corporateFreeForm.formTemplate,
                                                corporateFreeForm.uiFormResponse,
                                                formFieldValuesMap
                                            )
                                        }
                                        // reply_with_new
                                        messageFormViewModel.isReplayWithNewFormType(
                                            driverFormId = driverFormDef.formid,
                                            replyFormId = replyFormDef.formid
                                        ) -> {
                                            processForm(
                                                formTemplate,
                                                uiFormResponse,
                                                formFieldValuesMap
                                            ) }
                                        // reply_with_same
                                        messageFormViewModel.isReplayWithSameFormType(
                                            driverFormId = driverFormDef.formid,
                                            replyFormId = replyFormDef.formid
                                        ) -> {
                                            // we put as true if we are in a reply with same
                                            isDriverInImessageReplyForm = true
                                            processForm(
                                                formTemplate,
                                                uiFormResponse,
                                                formFieldValuesMap
                                            )
                                        }
                                    }
                                }
                            } else {
                                isDriverInSingleForm = true
                                // Either DriverFormId or ForcedFormId is missing.(Single screen scenario)
                                withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                                    binding.btnSave.text = getString(R.string.send)
                                }
                                processForm(
                                    formTemplate,
                                    uiFormResponse,
                                    formFieldValuesMap
                                )
                            }
                        }
                    }
                    withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                        displayFormButtons()
                    }
                }
            }
        }
        checkGmsBarcodeScanningModuleAvlAndDownload(this)
        observeOnBackPress()
    }

    private fun observeOnBackPress() {
        onBackPressedCallback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                // Handle the back button press
                softOrHardBackButtonPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
    }

    override fun onRestart() {
        Log.logLifecycle(tag, "$tag onRestart")
        lifecycleScope.launch(CoroutineName(tag)) {
            val formActivityLaunchAttributes =
                formViewModel.getFormActivityLaunchAttributes(dataStoreManager, stopId)
            if (formActivityLaunchAttributes.first) {
                this@ComposeFormActivity.startDispatchFormActivity(
                    isComposeEnabled = true,
                    path = formActivityLaunchAttributes.third,
                    dispatchFormPath = formActivityLaunchAttributes.second,
                    isManualArrival = false,
                    isFormResponseSentToServer = true
                )
            }
        }
        super.onRestart()
    }

    private suspend fun processForm(
        formTemplate: FormTemplate,
        uiFormResponse: UIFormResponse,
        formFieldValuesMap: HashMap<String, ArrayList<String>>
    ) {
        Log.d(FORM_DATA_RESPONSE,"processForm formFieldValuesMap ${formFieldValuesMap}")

        if (messageFormViewModel.checkIfFormIsValid(formTemplate)) {
            isDTF = messageFormViewModel.checkIfFormIsDtf(formTemplate)
            this.formTemplate = formTemplate
            formTemplateSerializedString = messageFormViewModel.serializeFormTemplate(formTemplate)
            setIsSyncToQueue(messageFormViewModel.checkIfFormIsSaved(uiFormResponse))
            checkForCompleteFormMessages()
            if (isDTF) {
                formManager.restrictOrientationChange(this)
            }
            fetchLatestRecipientsAsynchronously()
            setStopNameAsTitle()
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                formViewModel.setFormUiState(
                    screenContentState = ScreenContentState.Success()
                )
            }
            val formTemplateToRender: FormTemplate = messageFormViewModel.fetchFormTemplate(
                formTemplateSerializedString
            )
            updateScreenStateWhenAllFieldsAreAuto(formTemplate = formTemplateToRender)
            restoreImageFormFieldUiData(formTemplate = formTemplateToRender)
            setFormFieldsLayout(formTemplate = formTemplateToRender, formFieldValuesMap = formFieldValuesMap)
        } else {
            onInvalidForm(tag + "_FormIsInvalid")
        }
    }

    private fun setIsInFormDataStoreKey() {
        lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.io()) {
            formDataStoreManager.setValue(FormDataStoreManager.IS_IN_FORM_KEY, true)
        }
    }

    private fun fetchLatestRecipientsAsynchronously() {
        lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.io()) {
            fetchLatestRecipients()
        }
    }

    private suspend fun fetchLatestRecipients() {
        latestFormRecipients = formViewModel.getLatestFormRecipients(
            formTemplate.formDef.cid,
            formTemplate.formDef.formid
        )
    }

    private fun displayFormButtons() {
        if (isSyncToQueue) {
            binding.btnSave.visibility = View.INVISIBLE
            binding.ivCancel.visibility = View.VISIBLE
            binding.btnSaveToDrafts.visibility = View.INVISIBLE
        } else {
            binding.btnSave.visibility = View.VISIBLE
            if (isDriverInImessageReplyForm.not() && isDriverInSingleForm.not()) {
                binding.ivCancel.visibility = View.INVISIBLE
                binding.btnSaveToDrafts.visibility = View.INVISIBLE
            } else {
                if (draftingViewModel.getSaveToDraftsFeatureFlag() && isDTF.not()) binding.btnSaveToDrafts.visibility =
                    View.VISIBLE
                if (isDriverInSingleForm) {
                    binding.ivCancel.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun hideSaveAndDraftButtons() {
        binding.btnSave.visibility = View.INVISIBLE
        binding.btnSaveToDrafts.visibility = View.INVISIBLE
    }

    private suspend fun onInvalidForm(caller: String) {
        formViewModel.setFormUiState(
            screenContentState = ScreenContentState.Error(
                errorMessage = getString(
                    R.string.form_not_displayed
                )
            )
        )
        formViewModel.onInvalidForm(caller, stopId, actionId)
    }

    /**
     * This method will filter the form fields based on branchTargetId and then renders the form fields.
     * It also removes the form fields if it's viewId is greater than selectedViewId
     *
     * @param branchTargetId The form field id from which form fields should be rendered.
     * @param selectedViewId viewId of the selected form field
     */
    @Composable
    private fun RenderForms(
        branchTargetId: Int,
        selectedViewId: Int,
        loopEndId: Int,
        isFormSaved: Boolean
    ) {
        formViewModel.viewId = selectedViewId + 1

        //Removing the previously rendered fields based on the response
        formViewModel.viewIdToFormFieldMap = formManager.removePreviouslyRenderedField(
            viewId = formViewModel.viewId,
            parentMap = formViewModel.viewIdToFormFieldMap
        )

        val formTemplateToRender = messageFormViewModel.getFormTemplateBasedOnBranchTargetId(
            branchTargetId,
            loopEndId,
            isDTF,
            formTemplate,
            formTemplateSerializedString
        )
        RenderFieldBasedOnBranchTargetId(
            branchTargetId = branchTargetId,
            formTemplateToRender = formTemplateToRender,
            isFormSaved = isFormSaved
        )
    }

    @Composable
    private fun RenderFieldBasedOnBranchTargetId(
        branchTargetId: Int,
        formTemplateToRender: FormTemplate,
        isFormSaved: Boolean
    ) {
        branchTargetId.let {
            val iterationStartIndex =
                messageFormViewModel.getLastIndexOfBranchTargetId(formTemplate, branchTargetId)
            if (iterationStartIndex >= 0) {
                formTemplateToRender.formFieldsList.forEachIndexed { index, formField ->
                    if (index >= iterationStartIndex) {
                        formField.uiData = EMPTY_STRING
                        val isResponseNeededToProceed = createAndAddFormControl(
                            formField,
                            isSyncToQueue || isFormSaved,
                            formFieldValuesMap
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FORM_RESPONSE_PATH, path)
        if (isDriverInImessageReplyForm.not() || replyFormDef.isValidForm()
                .not()
        ) outState.putParcelable(DISPATCH_FORM_PATH_SAVED, stopActionFormKey)
        else outState.putParcelable(DRIVER_FORM_ID, stopActionFormKey)
        outState.putBoolean(IS_DRIVER_IN_IMESSAGE_REPLY_FORM, isDriverInImessageReplyForm)
        outState.putParcelable(IMESSAGE_REPLY_FORM_DEF, replyFormDef)
        outState.putBoolean(IS_FROM_TRIP_PANEL, isFromTripPanel)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        path = savedInstanceState.getString(FORM_RESPONSE_PATH) as String
        isDriverInImessageReplyForm =
            savedInstanceState.getBoolean(IS_DRIVER_IN_IMESSAGE_REPLY_FORM, false)
        replyFormDef = savedInstanceState.getReplyFormDef()
        stopActionFormKey = if (isDriverInImessageReplyForm.not() || replyFormDef.isValidForm()
                .not()
        ) {
            (savedInstanceState.getDispatchFormPathSaved())
        } else {
            (savedInstanceState.getDriverFormId())
        }
        isFromTripPanel = savedInstanceState.getBoolean(IS_FROM_TRIP_PANEL)
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setTitleTextColor(
            ContextCompat.getColor(
                applicationContext,
                R.color.textColor
            )
        )
        if (isDriverInImessageReplyForm.not()) {
            binding.ivCancel.setOnClickListener {
                Log.logUiInteractionInNoticeLevel(TRIP_FORM, "$tag: Cancel button clicked. isDriverInImessageReplyForm: $isDriverInImessageReplyForm")
                if (draftingViewModel.getSaveToDraftsFeatureFlag()) {
                    softOrHardBackButtonPress()
                } else {
                    returnToStopDetailView(stopId)
                }
            }
        } else {
            binding.ivCancel.apply {
                setImageDrawable(
                    ContextCompat.getDrawable(
                        this@ComposeFormActivity,
                        R.drawable.ic_back_white
                    )
                )
                setOnClickListener {
                    Log.logUiInteractionInNoticeLevel(TRIP_FORM, "$tag: Cancel button clicked. isDriverInImessageReplyForm: $isDriverInImessageReplyForm")
                    // Only available on reply screen
                    traverseBetweenDriverAndReplyMessageIntent(1)
                }
            }
        }

        binding.btnSaveToDrafts.setOnClickListener {
            Log.logUiInteractionInNoticeLevel(TRIP_FORM, "$tag: Save To Draft button clicked.")
            hideSaveAndDraftButtons()
            saveAsDraft(SAVE_TO_DRAFTS_BUTTON_CLICK)
            returnToStopDetailView(stopId)
        }

        binding.btnSave.setOnClickListener {
            if (binding.btnSave.text.toString().equals(getString(R.string.send), true)) {
                Log.logUiInteractionInNoticeLevel(TRIP_FORM, "$tag: Send button clicked.")
                formViewModel.setSendButtonClickEvent(true)
                // If DTF, then construct new FormField list
                constructFormTemplateData()
                val errorFormFields = FormUtils.getErrorFields(formTemplateData)
                displayFillRequiredFieldsToast(errorFormFields = errorFormFields)
                if (errorFormFields.isEmpty()) {
                    hideSaveAndDraftButtons()
                    binding.svContainer.hide()
                    lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.main()) {
                        formViewModel.setFormUiState(
                            screenContentState = ScreenContentState.Loading(
                                loadingMessage = getString(
                                    R.string.sending_text
                                )
                            )
                        )
                    }
                    lifecycleScope.launch(CoroutineName(tag)) {
                        formViewModel.removeFormFromStack(
                            stopId,
                            dataStoreManager
                        )
                        if (formTemplate.formDef.cid >= 0) {
                            with(formViewModel) {
                                if (latestFormRecipients.isEmpty()) fetchLatestRecipients()
                                setIsSyncToQueue(true)
                                saveFormData(
                                    formTemplateData, path, true,
                                    latestFormRecipients, stopActionFormKey
                                )
                            }
                            ToastCompat.makeText(
                                this@ComposeFormActivity,
                                R.string.form_sent_successfully,
                                Toast.LENGTH_LONG
                            ).show()
                            applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, false)
                        }
                        stopActionFormKey = DispatchFormPath()
                        formViewModel.dismissTripPanelMessage()
                        if (isFromTripPanel) {
                            Intent(
                                this@ComposeFormActivity,
                                DispatchDetailActivity::class.java
                            ).apply {
                                this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
                                    startActivity(it)
                                }
                            }
                        } else {
                            startActivity(
                                Intent(
                                    this@ComposeFormActivity,
                                    StopDetailActivity::class.java
                                )
                            )
                        }
                        finish()
                    }
                } else {
                    dialog = null
                }
            } else if (binding.btnSave.text.toString()
                    .equals(getString(R.string.next_button_formactivity), true)
            ) {
                Log.logUiInteractionInNoticeLevel(TRIP_FORM, "$tag: Next button clicked.")
                traverseBetweenDriverAndReplyMessageIntent(2)
            }
        }
    }

    override fun onStop() {
        Log.logLifecycle(tag, "$tag onStop")
        if (intent?.getBooleanExtra(CAN_SHOW_CANCEL, false)!!) {
            binding.ivCancel.visibility =
                View.INVISIBLE
        }
        if (isChangingConfigurations.not()) formManager.removeKeyForImage()
        if (formViewModel.shouldFormBeDrafted(
                isSyncToQueue = isSyncToQueue,
                isDraftFeatureFlagEnabled = draftingViewModel.getSaveToDraftsFeatureFlag(),
                isDriverInSingleForm = isDriverInSingleForm,
                isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                cid = formTemplate.formDef.cid
            )
        ) {
            Log.d(tag, "Drafting form data onStop, path : $path actionId : $actionId isSyncToQueue : $isSyncToQueue")
            saveAsDraft(ON_STOP_CALLBACK)
        }
        super.onStop()
    }

    override fun onPause() {
        //drafts form for single screen scenario too. In single screen scenario, isSecondForm will become true.
        if (formViewModel.shouldSaveFormDataDuringConfigurationChange(
                isSyncToQueue = isSyncToQueue,
                isDriverInSingleForm = isDriverInSingleForm,
                isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                cid = formTemplateData.formDef.cid,
                isChangingConfigurations = isChangingConfigurations
            )
        ) {
            (application as WorkflowApplication).applicationScope.launch(CoroutineName(tag)) {
                if (latestFormRecipients.isEmpty()) fetchLatestRecipients()

                constructFormTemplateData()
                Log.d(tag, "Saving form data onPause, path : $path actionId : $actionId isSyncToQueue : $isSyncToQueue")
                formViewModel.saveFormData(
                    formTemplateData,
                    path,
                    isSyncToQueue,
                    latestFormRecipients,
                    stopActionFormKey
                )
            }
        }
        if (isSyncToQueue.not()) {
            formViewModel.checkForCompleteFormMessages()
        }
        super.onPause()
    }

    private fun saveFormState() {
        (application as WorkflowApplication).applicationScope.launch(CoroutineName(tag)) {
            if (formViewModel.shouldSaveFormData(
                    isSyncToQueue = isSyncToQueue,
                    isDriverInSingleForm = isDriverInSingleForm,
                    isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                    cid = formTemplate.formDef.cid
                )
            ) {
                if (latestFormRecipients.isEmpty()) fetchLatestRecipients()
                formViewModel.saveFormData(
                    formTemplateData,
                    path,
                    isSyncToQueue,
                    latestFormRecipients,
                    stopActionFormKey
                )
            }
        }
    }

    override fun onDestroy() {
        Log.logLifecycle(tag, "$tag onDestroy")
        Log.d(tag, "Saving form data onDestroy, path : $path actionId : $actionId isSyncToQueue : $isSyncToQueue")
        saveFormState()
        super.onDestroy()
        if(::onBackPressedCallback.isInitialized.not()){
            onBackPressedCallback.remove() // Remove the callback when the activity is destroyed
        }
    }

    private fun softOrHardBackButtonPress() {
        if (isSyncToQueue) {
            closeApplicationIfStandAloneForm()
        } else {
                if (isDriverInImessageReplyForm.not() && isDriverInSingleForm.not()) {
                    returnToStopDetailView(stopId)
                } else {
                    if (draftingViewModel.getSaveToDraftsFeatureFlag()) {
                        saveAsDraft(BACK_BUTTON_CLICK)
                        returnToStopDetailView(stopId)
                    }
                }
            }
        }


    private fun closeApplicationIfStandAloneForm() {
        if (intent?.getBooleanExtra(CAN_SHOW_CANCEL, false)!!) {
            startActivity(
                Intent(
                    LAUNCH_SCREEN_INTENT
                )
            )
            finish()
        } else {
            // Don't reload form is it is not saved and back pressed
            if (isSyncToQueue ) returnToStopDetailView(stopId)
        }
    }

    @Composable
    private fun createAndAddFormControl(
        formField: FormField,
        isFormSaved: Boolean,
        formFieldValuesMap: HashMap<String, ArrayList<String>>
    ): Boolean {
        val isFormReadOnly = isFormInEditMode(formField, formFieldValuesMap)
        val textInputLayout = formManager.createTextInputLayout(
            formField.qtype,
            formId!!,
            formClass,
            formTemplate.formFieldsList.size,
            this@ComposeFormActivity,
            isFormReadOnly
        )
        setIsInDriverForm(formField = formField)
        val nextQNumAndInflationRequirement =
            FormUtils.isViewInflationRequired(formManager.branchTo, formField, formTemplate)
        val nextQNumToShow = nextQNumAndInflationRequirement.first
        Log.d(tag, "Field to be processed, qnum : ${formField.qnum} fieldId : ${formField.fieldId} " +
                "nextQNumToShow : $nextQNumToShow inflationRequirement : ${nextQNumAndInflationRequirement.second} qType : ${formField.qtype}")
        val viewAndBooleanPair = createFieldBasedOnFormFieldType(
            formField = formField,
            textInputLayout = textInputLayout,
            isFormSaved = isFormSaved,
            nextQNumAndInflationRequirement = nextQNumAndInflationRequirement.second
        )
        val view: View? = viewAndBooleanPair.first
        val isResponseNeeded: Boolean = viewAndBooleanPair.second

        if (formViewModel.isOfTextInputLayoutViewType(formField)) {
            AddViewToLayout(view, nextQNumToShow, formField, textInputLayout)
        } else {
            view?.let {
                AddFormComponentToLayout(formField, view)
            }
        }

        formManager.assignPendingClickListener(view)

        val isResponseNeededForLoop: Boolean =
            isProcessLoopFieldsRequired(formField = formField, isFormSaved = isFormSaved)

        // return if response needed
        return isResponseNeeded || isResponseNeededForLoop
    }

    private fun shouldDisplayOriginalMessage(): Boolean {
        return actionPayload.forcedFormClass != FREE_FORM_FORM_CLASS && actionPayload.forcedFormClass != actionPayload.driverFormClass
    }

    private fun addViewToFormFieldMap(formField: FormField) {
        formViewModel.viewId++
        formViewModel.viewIdToFormFieldMap[formField.viewId] = formField
    }

    private suspend fun restoreImageData(formField: FormField) =
        formViewModel.fetchEncodedImgAndUpdateUiDataField(formField).await()

    @Composable
    private fun AddViewToLayout(
        view: View?,
        nextQNumToShow: Int,
        formField: FormField,
        textInputLayout: TextInputLayout
    ) {
        if (formManager.branchTo > -1) {
            if (nextQNumToShow != -1 && (formField.qnum == nextQNumToShow)) {
                view?.let {
                    textInputLayout.addView(view)
                    AddFormComponentToLayout(formField, textInputLayout)
                }
                formManager.branchTo = -1
            }
        } else {
            view?.let {
                textInputLayout.addView(view)
                AddFormComponentToLayout(formField, textInputLayout)
            }
        }
    }

    private fun isDriverInReadOnlyForm(): Boolean {
        return (driverFormDef.isValidForm() && replyFormDef.isValidForm() && isDriverInImessageReplyForm.not() && isSyncToQueue.not())
    }

    @Composable
    private fun AddFormComponentToLayout(
        formField: FormField,
        view: View
    ) {
        addViewToFormFieldMap(formField)

        view.id = formField.viewId
        view.tag = formField.viewId

        checkAndStoreMultipleChoiceFormFieldStack(formField, view)

        if ((
                formManager.isFreeForm(
                    FormDef(
                        formid = formId!!,
                        formClass = formClass
                    )
                ) || (isSyncToQueue && replyFormDef.isFreeForm())
                ) && formTemplate.formFieldsList.size == 1
        ) {
            AddFreeFormView(view, formField)
        } else {
            AddFieldToForm(view, formField)
            mapFieldIdsToViews[view.id] = view
        }
        // To make driver clear that this form is non-editable, The alpha is reduced in first form
        if (isDriverInReadOnlyForm()) {
            view.alpha =
                READ_ONLY_VIEW_ALPHA
        }
    }

    private fun checkAndStoreMultipleChoiceFormFieldStack(formField: FormField, view: View) {
        // Saving in map only if view is of Multiple choice type, it is unnecessary for other form fields
        if (formField.qtype == FormFieldType.MULTIPLE_CHOICE.ordinal) {
            messageFormViewModel.getViewIdToFormFieldStackMap()[view.id] =
                messageFormViewModel.getFormFieldStackCopy()
        }
    }

    @Composable
    private fun AddFreeFormView(view: View, formField: FormField) {
        binding.llRightLayout.visibility = View.GONE
        val llLeftLayoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llLeftLayoutParams.weight = 2.0f
        view.apply {
            layoutParams = llLeftLayoutParams
        }
        CustomAndroidView(
            view = view,
            sendButtonState = formViewModel.sendButtonClickEventListener.collectAsState(
                initial = false
            ),
            formField = formField
        )
    }

    @Composable
    private fun AddFieldToForm(view: View, formField: FormField) {
        view.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        CustomAndroidView(
            view = view,
            sendButtonState = formViewModel.sendButtonClickEventListener.collectAsState(
                initial = false
            ),
            formField = formField
        )
    }

    private suspend fun setStopNameAsTitle() =
        withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
            binding.tvToolbarTitle.text = stopName
        }

    private suspend fun updateScreenStateWhenAllFieldsAreAuto(formTemplate: FormTemplate) {
        val autoFieldCount =
            messageFormViewModel.countOfAutoFields(formTemplate.formFieldsList)
        //if all the fields are auto fields, show the following message
        if (messageFormViewModel.areAllAutoFields(
                formTemplate.formFieldsList.size,
                autoFieldCount
            )
        ) {
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                if (isSyncToQueue.not()) {
                    formViewModel.setFormUiState(
                        screenContentState = ScreenContentState.Success(
                            successMessage = getString(R.string.auto_field_form)
                        )
                    )
                } else {
                    formViewModel.setFormUiState(
                        screenContentState = ScreenContentState.Success(
                            successMessage = getString(R.string.saved_auto_field_form)
                        )
                    )
                }
            }
        }
    }

    private fun checkForCompleteFormMessages() {
        if (isSyncToQueue.not()) {
            try {
                formViewModel.checkForCompleteFormMessages()
            } catch (e: Exception) {
                Log.e(tag, "exception: calling checkForCompleteFormMessages with ", e)
            }
        }
    }

    private suspend fun setFormFieldsLayout(
        formTemplate: FormTemplate,
        formFieldValuesMap: HashMap<String, ArrayList<String>>
    ) {
        binding.formFieldsLayout.apply {
            setContent {
                WorkFlowTheme {
                    focusManager = LocalFocusManager.current
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus(true)
                                })
                            }
                    ) {
                        setIsInFormDataStoreKey()
                        run breakPoint@{
                            formTemplate.formFieldsList.forEach { formField ->
                                val isResponseNeeded = createFormFields(
                                    formField = formField,
                                    formFieldValuesMap = formFieldValuesMap
                                )
                                if (isResponseNeeded) return@breakPoint
                            }
                            formManager.checkAndResetBranchTarget()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun createFormFields(
        formField: FormField,
        formFieldValuesMap: HashMap<String, ArrayList<String>>
    ): Boolean {
        setDataFromDefaultValues(formFieldValuesMap = formFieldValuesMap, formField = formField)
        return if (formViewModel.checkIfImessageFormsAreValid(
                driverFormDef = driverFormDef,
                replyFormDef = replyFormDef,
                isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                isSyncToQueue = isSyncToQueue,
            isCannotSendAction = actionPayload.replyActionType == NO_REPLY_ACTION
            )
        ) {
            //assigned true to disable views in imessage form
            createAndAddFormControl(formField, true, formFieldValuesMap)
        } else {
            createAndAddFormControl(
                formField,
                isSyncToQueue,
                formFieldValuesMap
            )
        }
    }

    private fun setIsInDriverForm(formField: FormField) {
        formField.isInDriverForm =
            (isDriverInImessageReplyForm || isDriverInSingleForm || isSyncToQueue)
    }

    @Composable
    private fun createFieldBasedOnFormFieldType(
        formField: FormField,
        textInputLayout: TextInputLayout,
        isFormSaved: Boolean,
        nextQNumAndInflationRequirement: Boolean
    ): Pair<View?, Boolean> {
        var view: View? = null
        var isResponseNeeded = false

        if (nextQNumAndInflationRequirement) {
            formField.viewId = formViewModel.viewId
            when (formField.qtype) {
                FormFieldType.TEXT.ordinal -> {
                    view = createTextViewForDispatchForm(
                        textInputLayout = textInputLayout,
                        formField = formField,
                        isFormSaved = isFormSaved
                    )
                }
                FormFieldType.MULTIPLE_CHOICE.ordinal -> {
                    formField.formChoiceList?.forEach {
                        it.viewId = formField.viewId
                    }
                    CustomMultipleChoice(
                        formField = formField,
                        isFormSaved = isFormSaved,
                        responseLambda = {
                            processMultipleChoice(it)
                        },
                        sendButtonState = formViewModel.sendButtonClickEventListener.collectAsState(
                            initial = false
                        ),
                        renderFormContent = {
                            with(it) {
                                RenderForms(
                                    branchTargetId = first,
                                    selectedViewId = second,
                                    loopEndId = third,
                                    isFormSaved = isFormSaved
                                )
                            }
                        },
                        isFormInReadOnlyMode = isDriverInReadOnlyForm()
                    )
                    addViewToFormFieldMap(formField)

                    isResponseNeeded = formField.multipleChoiceDriverInputNeeded()

                }
                else -> {
                    if (formViewModel.isComposableView(formField)) {
                        formManager.CreateComposeViews(
                            formField = formField,
                            isFormSaved = isFormSaved,
                            sendButtonState = formViewModel.sendButtonClickEventListener.collectAsState(
                                initial = false
                            ),
                            imeAction = {
                                formManager.setNextActionForFields(
                                    mapFieldIdsToViews = formTemplate.formFieldsList,
                                    viewFormField = formField,
                                    activity = this@ComposeFormActivity
                                )
                            },
                            focusManager = focusManager,
                            isFormInReadOnlyMode = isDriverInReadOnlyForm(),
                            signatureDialogViewModel = signatureViewModel
                        )
                        addViewToFormFieldMap(formField)
                    } else {
                        view = formManager.createViewWithFormField(
                            formField = formField,
                            stack = messageFormViewModel.getFormFieldStack(),
                            context = this,
                            textInputLayout = textInputLayout,
                            isFormSaved = isFormSaved,
                            stopId = stopId,
                            lifecycleScope = lifecycleScope,
                            supportFragmentManager = supportFragmentManager
                        ) {
                            messageFormViewModel.getFormFieldCopy(formField)
                        }
                    }
                }
            }
        }
        return Pair(view, isResponseNeeded)
    }

    @Composable
    private fun isProcessLoopFieldsRequired(formField: FormField, isFormSaved: Boolean): Boolean {
        if (FormUtils.isProcessLoopFieldsRequired(
                formTemplate,
                formField,
                messageFormViewModel.getFormFieldStack()
            )
        ) {
            formManager.RenderComposeLoopFieldsWithoutBranch(
                formField = formField,
                formTemplate = formTemplate,
                formFieldStack = messageFormViewModel.getFormFieldStack(),
                isFormSaved = isFormSaved,
                renderForm = { branchTargetId: Int, selectedViewId: Int, loopEndId: Int, isFormSaved: Boolean ->
                    RenderForms(
                        branchTargetId = branchTargetId,
                        selectedViewId = selectedViewId,
                        loopEndId = loopEndId,
                        isFormSaved = isFormSaved
                    )
                }
            )
            return true
        }
        return false
    }

    private fun setDataFromDefaultValues(
        formFieldValuesMap: HashMap<String, ArrayList<String>>,
        formField: FormField
    ) {
        messageFormViewModel.setDataFromDefaultValueOrFormResponses(
            formFieldValuesMap,
            formField,
            caller = tag,
            actualLoopCount = -1, //pass correct value after clarification
        )
    }

    @Composable
    private fun createTextViewForDispatchForm(
        textInputLayout: TextInputLayout,
        formField: FormField,
        isFormSaved: Boolean
    ): View? {
        var view: View? = null
        if ((
                formManager.isFreeForm(
                    FormDef(
                        formid = formId!!,
                        formClass = formClass
                    )
                ) || (isSyncToQueue && replyFormDef.isFreeForm())
                ) && formTemplate.formFieldsList.size == 1
        ) {
            // Set counter max length
            textInputLayout.counterMaxLength = formTemplate.formFieldsList[0].ffmLength
            formViewModel.changeDriverEditableValueForReplyFreeForm(
                isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                formField = formField
            )
            formViewModel.setDefaultValueForFreeFormMessage(
                isReplyWithSame = messageFormViewModel.isReplayWithSameFormType(
                    driverFormId = driverFormDef.formid,
                    replyFormId = replyFormDef.formid
                ),
                formField = formField,
                freeFormMessage = actionPayload.freeFormMessage,
                isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                isFormSaved = isFormSaved
            )
            view = FreeFormEditText(
                this,
                textInputLayout,
                formField,
                isFormSaved,
                (displayHeight - toolbarHeightInPixels.toInt()) / 2
            ).apply {
                filters =
                    arrayOf(InputFilter.LengthFilter(formTemplate.formFieldsList[0].ffmLength))
            }.also {
                setDispatchFreeFormParams(
                    freeFormEditText = it,
                    formField = formField,
                    textInputLayout = textInputLayout
                )
            }
        } else {
            CustomTextField(
                formField = formField,
                isFormSaved = isFormSaved,
                sendButtonState = formViewModel.sendButtonClickEventListener.collectAsState(
                    initial = false
                ),
                imeAction = formManager.setNextActionForFields(
                    mapFieldIdsToViews = formTemplate.formFieldsList,
                    viewFormField = formField,
                    activity = this@ComposeFormActivity
                ),
                focusManager = focusManager,
                isFormInReadOnlyMode = isDriverInReadOnlyForm()
            )
            addViewToFormFieldMap(formField)
        }
        return view
    }

    private fun setDispatchFreeFormParams(
        freeFormEditText: FreeFormEditText,
        formField: FormField,
        textInputLayout: TextInputLayout
    ) {
        if (replyFormDef.isValidForm().not()) {
            // No reply form. Only single form which is corporate free form
            formViewModel.getFreeFormEditTextHintAndMessage(
                formTemplateData.formFieldsList[0],
                actionPayload.freeFormMessage
            ).also { hintAndText ->
                textInputLayout.hint = hintAndText.first
                formField.uiData = hintAndText.second
                freeFormEditText.setText(hintAndText.second)
            }
        } else {
            if (isSyncToQueue) {
                textInputLayout.hint =
                    getString(R.string.freeform_non_editable_input_field_hint_text)
            } else {
                if (isDriverInImessageReplyForm) {
                    formViewModel.getFreeFormEditTextHintAndMessage(
                        formTemplateData.formFieldsList[0],
                        actionPayload.freeFormMessage,
                        shouldDisplayOriginalMessage()
                    ).also { hintAndText ->
                        textInputLayout.hint = hintAndText.first
                        formField.uiData = hintAndText.second
                        freeFormEditText.setText(hintAndText.second)
                        freeFormEditText.isFocusable = true
                        freeFormEditText.isEnabled = true
                        freeFormEditText.isFocusableInTouchMode = true
                        freeFormEditText.makeEditTextEditable()
                    }
                } else {
                    textInputLayout.hint =
                        getString(R.string.freeform_non_editable_input_field_hint_text)
                    formField.uiData = actionPayload.freeFormMessage
                    freeFormEditText.setText(actionPayload.freeFormMessage)
                }
            }
        }
    }
    private fun isFormInEditMode(formField: FormField, formFieldValuesMap: HashMap<String, ArrayList<String>>): Boolean {
        return (
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = formField,
                makeFieldsNonEditable = isDriverInReadOnlyForm(),
                isFormSaved = isSyncToQueue
            ) || (formTemplate.formFieldsList.size == 1 && formField.uiData.isEmpty() && formFieldValuesMap[formField.qnum.toString()]?.isEmpty() == true)
            )
    }

    private suspend fun restoreImageFormFieldUiData(formTemplate: FormTemplate) {
        messageFormViewModel.filterImageFormField(formFieldList = formTemplate.formFieldsList)
            .forEach { formField ->
                setDataFromDefaultValues(formFieldValuesMap = formFieldValuesMap, formField = formField)
                restoreImageData(formField = formField)
            }
    }

    private fun displayFillRequiredFieldsToast(errorFormFields: ArrayList<FormField>) {
        if (errorFormFields.any { it.errorMessage == "*${resources.getString(R.string.cannot_be_empty)}" }) {
            this.apply {
                ToastCompat.makeText(
                    this,
                    getString(R.string.fill_required_field),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setIsSyncToQueue(syncToQueue: Boolean): Boolean {
        isSyncToQueue = syncToQueue
        return isSyncToQueue
    }

    private fun showFirstDisplayScreen(): Boolean {
        val notInReplyForm = isDriverInImessageReplyForm.not()
        val replyNewFormType = messageFormViewModel.isReplayWithNewFormType(
            driverFormId = driverFormDef.formid,
            replyFormId = replyFormDef.formid
        )
        return (notInReplyForm && replyNewFormType)
    }

    private fun constructFormTemplateData() {
        if (isDTF) {
            formManager.iterateViewsToFetchDataFromFormFields(
                binding.llLeftLayout,
                binding.llRightLayout,
                formViewModel.viewIdToFormFieldMap,
                tag
            ).let { constructedFormFieldList ->
                messageFormViewModel.constructFormFieldsWithAutoFields(formTemplate, constructedFormFieldList)
                formTemplateData = FormTemplate(
                    formTemplate.formDef,
                    constructedFormFieldList
                )
            }
        }
    }

    private fun traverseBetweenDriverAndReplyMessageIntent(page: Int) {

        if (formViewModel.isFormOpenedFromDraft && draftingViewModel.getSaveToDraftsFeatureFlag()) {
            saveAsDraft(DRIVER_AND_REPLY_MESSAGE_NAVIGATION)
            returnToStopDetailView(stopId)
            return
        }
        val driverFormPath = dispatchFormPathSaved.let {
            DispatchFormPath(
                stopName = it.stopName,
                stopId = it.stopId,
                actionId = it.actionId,
                formId = replyFormDef.formid,
                formClass = replyFormDef.formClass
            )
        }
        Log.n(TRIP_FORM,"DispatchFromPath values stopName:${dispatchFormPathSaved.stopName}, stopId:${dispatchFormPathSaved.stopId}, actionId:${dispatchFormPathSaved.actionId}, formId:${dispatchFormPathSaved.formId}, formClass:${dispatchFormPathSaved.formClass}")
        Intent(this@ComposeFormActivity, FormActivity::class.java).apply {
            putExtra(FORM_RESPONSE_PATH, path)
            putExtra(STOP_NAME_FOR_FORM, stopName)
            putExtra(DISPATCH_FORM_PATH_SAVED, dispatchFormPathSaved)
            putExtra(DRIVER_FORM_ID, driverFormPath)
            if (page == 1) {
                putExtra(IS_SECOND_FORM_KEY, false)
                putExtra(IS_FROM_TRIP_PANEL, true)
            }
            if (page > 1) {
                putExtra(IS_SECOND_FORM_KEY, true)
                putExtra(IS_FROM_TRIP_PANEL, false)
            }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }.also {
            startActivity(it)
        }
        finish()
    }

    private fun saveAsDraft(caller: String) {
        Log.d(tag , "Caller $caller Form Saved to draft fieldList:${formTemplateData.formFieldsList}")
        constructFormTemplateData()
        formViewModel.saveDispatchFormDataToDraft(formTemplateData, stopActionFormKey, path, stopId, messageFormViewModel.isReplayWithSameFormType(
            driverFormId = driverFormDef.formid,
            replyFormId = replyFormDef.formid
        ))
        applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, true)
        ToastCompat.makeText(
            this@ComposeFormActivity,
            R.string.form_saved_to_drafts,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun returnToStopDetailView(stopIndex: Int) {
        Log.d(tag, "Send to stopDetailActivity Intent stopIndex=$stopIndex")
        formViewModel.restoreSelectedDispatch {
            Intent(this, StopDetailActivity::class.java).apply {
                putExtra(CURRENT_STOP_INDEX, stopIndex)
                putExtra(SELECTED_STOP_ID, stopIndex + 1)
                this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(this)
            }
            finish()
        }
    }
}
