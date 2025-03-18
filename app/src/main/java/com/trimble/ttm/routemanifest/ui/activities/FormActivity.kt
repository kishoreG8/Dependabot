package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.logger.DISPATCH_FORM_DRAFT
import com.trimble.ttm.commons.logger.FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.ON_STOP_CALLBACK
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
import com.trimble.ttm.commons.utils.SHOULD_NOT_RETURN_TO_FORM_LIST
import com.trimble.ttm.commons.utils.ext.getCompleteFormID
import com.trimble.ttm.commons.utils.ext.getDispatchFormPathSaved
import com.trimble.ttm.commons.utils.ext.getDriverFormId
import com.trimble.ttm.commons.utils.ext.getFormData
import com.trimble.ttm.commons.utils.ext.getReplyFormDef
import com.trimble.ttm.commons.utils.ext.isFromDraft
import com.trimble.ttm.formlibrary.customViews.FormEditText
import com.trimble.ttm.formlibrary.customViews.FormMultipleChoice
import com.trimble.ttm.formlibrary.customViews.FreeFormEditText
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.ActivityFormBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.ui.activities.FormLibraryActivity
import com.trimble.ttm.formlibrary.utils.FormUtils.isViewInflationRequired
import com.trimble.ttm.formlibrary.utils.UiUtil.convertDpToPixel
import com.trimble.ttm.formlibrary.utils.UiUtil.getDisplayHeight
import com.trimble.ttm.formlibrary.utils.Utils.scheduleOneTimeImageUploadIfImagesAvailable
import com.trimble.ttm.formlibrary.utils.ext.setDebounceClickListener
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.formlibrary.utils.*
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FormData
import com.trimble.ttm.routemanifest.model.isInValid
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_TO_RENDER
import com.trimble.ttm.routemanifest.utils.IS_DRIVER_IN_IMESSAGE_REPLY_FORM
import com.trimble.ttm.routemanifest.utils.LAUNCH_SCREEN_INTENT
import com.trimble.ttm.routemanifest.utils.SELECTED_STOP_ID
import com.trimble.ttm.routemanifest.utils.STOP_NAME_FOR_FORM
import com.trimble.ttm.routemanifest.utils.TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.hide
import com.trimble.ttm.routemanifest.utils.ext.setVisibility
import com.trimble.ttm.routemanifest.utils.ext.show
import com.trimble.ttm.routemanifest.viewmodel.FormViewModel
import kotlinx.coroutines.*
import me.drakeet.support.toast.ToastCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FormActivity : BaseToolbarInteractionActivity() {

    private val tag = "FormActivity"
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
    private var viewId = 0
    private var formTemplateSerializedString: String = ""
    private var viewIdToFormFieldMap = HashMap<Int, FormField>()
    //This variable is used to store both the default and form response values
    private var formFieldsValuesMap: HashMap<String, ArrayList<String>> = HashMap()
    private var actionPayload: Action = Action()
    private var dispatchFormPathSaved = DispatchFormPath()
    private var isDriverInImessageReplyForm: Boolean = false
    private var isDriverInSingleForm: Boolean = false // Single form scenario
    private val dataStoreManager: DataStoreManager by inject()
    private val formDataStoreManager: FormDataStoreManager by inject()
    private val formViewModel: FormViewModel by viewModel()
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val draftingViewModel: DraftingViewModel by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private var isFormSaved = false
    private val displayHeight
        get() = getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = convertDpToPixel(60.0f, this)
    private var driverFormDef = FormDef(formid = -1, formClass = -1)
    private var replyFormDef = FormDef(formid = -1, formClass = -1)
    private var latestFormRecipients: ArrayList<Recipients> = ArrayList()
    private var isFromTripPanel: Boolean = false
    private var isActionResponseSentToServer: Boolean = false
    private val formManager: FormManager by lazy {
        FormManager()
    }
    private val mapFieldIdsToViews = mutableMapOf<Int, View>()
    private lateinit var binding: ActivityFormBinding
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
                    displayHeight - convertDpToPixel(
                        resources.getDimension(R.dimen.formsLayoutMargin),
                        applicationContext
                    ).toInt()
                    )
            )
        }.also {
            binding.progressErrorViewForm.setProgressState(getString(R.string.loading_text))
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
                                onInvalidForm(tag + "_InvalidActionPayload")
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
                                FormData(
                                    formTemplatePath = path, stopId = stopId.toString(),
                                    actionId = actionId.toString(), formId = formId.toString(), isFreeForm = isFreeForm
                                ),
                                isActionResponseSentToServer = isActionResponseSentToServer,
                                shouldFillUiResponse = isDriverInImessageReplyForm || isDriverInSingleForm || (actionPayload.replyActionType == REPLY_WITH_SAME), //When driver is in Reply form screen
                                isReplyWithSameForm = (actionPayload.replyActionType in setOf(NO_REPLY_ACTION, REPLY_WITH_SAME)),
                                uiResponse = messageFormViewModel.getSavedUIDataDuringOrientationChange()
                            ) // In no reply action and driver editable forms are considered as Reply with same along with reply with same forms
                            formFieldsValuesMap = form.formFieldValuesMap
                            val draftedFormFieldMap = formViewModel.getDraftedFormFieldMap(intent.getFormData())
                            formViewModel.isFormOpenedFromDraft = intent.isFromDraft()
                            val formTemplate = form.formTemplate
                            if(actionPayload.replyActionType == NO_REPLY_ACTION)
                            {
                                //Assign the reply form same as driver form, when no reply action and driver editable form is sent
                                if (formTemplate.formDef.driverEditable == 1){
                                    replyFormDef = FormDef(
                                        formid = actionPayload.driverFormid,
                                        formClass = actionPayload.driverFormClass
                                    )
                                }
                            }
                            val uiFormResponse: UIFormResponse
                            if (formViewModel.isFormOpenedFromDraft) {
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
                            // When forms sent with reply action, reply formId is >=0, to accomodated no reply action too since in no reply action case replyform Id will be empty
                            if (driverFormDef.isValidForm() && (replyFormDef.formid >= 0 || actionPayload.replyActionType == NO_REPLY_ACTION )) {
                                if (showFirstDisplayScreen()) {
                                    // imessage form(1st form) - no user interaction
                                    withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                                        binding.btnSave.text =
                                            getString(R.string.reply_button_formactivity)
                                    }

                                    // if form is already saved
                                    if (uiFormResponse.isSyncDataToQueue) {
                                        // If freeform is already saved. show the saved driver freeform, not imessage form
                                        val isReplyFormDefAFreeForm = replyFormDef.isFreeForm()
                                        if (isReplyFormDefAFreeForm) {
                                            // If the first form is not a freeform, it is mocked as freeform to align the freeform to the full screen
                                            // showing second screen(driver screen).To align freeform properly in 1st screen with the data of 2nd screen,
                                            // while fetching saved form
                                            // If order changed, Freeform message won't display
                                            isDriverInImessageReplyForm = true
                                        }else{
                                            formClass = replyFormDef.formClass
                                        }
                                        isActionResponseSentToServer=true
                                        val savedForm = formViewModel.getForm(
                                            FormData(formTemplatePath = path, stopId = stopId.toString(),
                                                actionId = actionId.toString(), formId = replyFormDef.formid.toString(), isFreeForm = isReplyFormDefAFreeForm),
                                            isActionResponseSentToServer = isActionResponseSentToServer,
                                            isDriverInImessageReplyForm || isDriverInSingleForm || isActionResponseSentToServer,
                                            messageFormViewModel.isReplayWithSameFormType(
                                                driverFormId = driverFormDef.formid,
                                                replyFormId = replyFormDef.formid
                                            )
                                        )
                                        formFieldsValuesMap = savedForm.formFieldValuesMap
                                        processForm(
                                            formTemplate = savedForm.formTemplate,
                                            uiFormResponse = savedForm.uiFormResponse,
                                            formFieldValuesMap = formFieldsValuesMap
                                        )
                                    } else {
                                        processForm(
                                            formTemplate = formTemplate,
                                            uiFormResponse = UIFormResponse(),
                                            formFieldValuesMap = formFieldsValuesMap
                                        )
                                    }
                                } else {
                                    // driver form(2nd form)
                                    when {
                                        // reply_with_freeform
                                        replyFormDef.isFreeForm() -> {
                                            // we put as true if we are in a free form
                                            isDriverInImessageReplyForm = true
                                            val corporateFreeForm = formViewModel.getForm(
                                                FormData(formTemplatePath = path, stopId = stopId.toString(),
                                                    actionId = actionId.toString(), formId = formId.toString(), isFreeForm = isFreeForm),
                                                isActionResponseSentToServer = true,
                                                isDriverInImessageReplyForm || isDriverInSingleForm,
                                                messageFormViewModel.isReplayWithSameFormType(
                                                    driverFormId = driverFormDef.formid,
                                                    replyFormId = replyFormDef.formid
                                                )
                                            )
                                            formFieldsValuesMap = corporateFreeForm.formFieldValuesMap
                                            processForm(
                                                formTemplate = corporateFreeForm.formTemplate,
                                                uiFormResponse = corporateFreeForm.uiFormResponse,
                                                formFieldValuesMap = formFieldsValuesMap
                                            )
                                        }
                                        // reply_with_new
                                        actionPayload.replyActionType == REPLY_WITH_NEW -> {
                                            Log.d(TRIP_FORM,"Reply with new ${driverFormDef.formid} != ${replyFormDef.formid}")
                                            processForm(
                                                formTemplate = formTemplate,
                                                uiFormResponse = uiFormResponse,
                                                formFieldValuesMap = formFieldsValuesMap
                                            )
                                        }
                                        // reply_with_same
                                        messageFormViewModel.isReplayWithSameFormType(
                                            driverFormId = driverFormDef.formid,
                                            replyFormId = replyFormDef.formid
                                        ) -> {
                                            // we put as true if we are in a reply with same
                                            Log.d(TRIP_FORM,"Reply with same ${driverFormDef.formid} == ${replyFormDef.formid}")
                                            isDriverInImessageReplyForm = true
                                            processForm(
                                                formTemplate = formTemplate,
                                                uiFormResponse = uiFormResponse,
                                                formFieldValuesMap = formFieldsValuesMap
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
                                    formTemplate = formTemplate,
                                    uiFormResponse = uiFormResponse,
                                    formFieldValuesMap = formFieldsValuesMap
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
        observeAndRenderForm()
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

    private fun observeAndRenderForm() {
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(
                tag
            )
        ) {
            messageFormViewModel.renderValues.collect {
                if (!(it.branchTargetId == -1 && it.selectedViewId == -1 && it.loopEndId == -1)) {
                    renderForms(
                        it.branchTargetId,
                        it.selectedViewId,
                        it.loopEndId,
                        isFormSaved,
                        it.actualLoopCount
                    )
                }
            }
        }
    }

    private suspend fun processForm(
        formTemplate: FormTemplate,
        uiFormResponse: UIFormResponse,
        formFieldValuesMap: HashMap<String, ArrayList<String>>
    ) {
        if (messageFormViewModel.checkIfFormIsValid(formTemplate)) {
            isDTF = messageFormViewModel.checkIfFormIsDtf(formTemplate)
            this.formTemplate = formTemplate
            formTemplateSerializedString = messageFormViewModel.serializeFormTemplate(formTemplate)
            setIsSyncToQueue(messageFormViewModel.checkIfFormIsSaved(uiFormResponse))
            if (isSyncToQueue.not()) {
                try {
                    formViewModel.checkForCompleteFormMessages()
                } catch (e: Exception) {
                    Log.e(FORM_DATA_RESPONSE, "exception: calling checkForCompleteFormMessages with ", e)
                }
            }
            if (isDTF) {
                formManager.restrictOrientationChange(this)
            }
            fetchLatestRecipientsAsynchronously()
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                binding.tvToolbarTitle.text = stopName
            }
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) { binding.progressErrorViewForm.setNoState() }
            val formTemplateToRender: FormTemplate = messageFormViewModel.fetchFormTemplate(
                formTemplateSerializedString
            )
            val autoFieldCount =
                messageFormViewModel.countOfAutoFields(formTemplateToRender.formFieldsList)

            formTemplateToRender.formFieldsList.forEach { formField ->
                formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
                messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                    formFieldValuesMap = formFieldValuesMap,
                    formField = formField,
                    caller = tag,
                    actualLoopCount = -1,
                )
                var isResponseNeededToProceed: Boolean
                withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                    isResponseNeededToProceed =
                        if (formViewModel.checkIfImessageFormsAreValid(driverFormDef = driverFormDef, replyFormDef = replyFormDef,
                                isDriverInImessageReplyForm = isDriverInImessageReplyForm, isSyncToQueue = isSyncToQueue, isCannotSendAction = actionPayload.replyActionType == NO_REPLY_ACTION)) {
                            // assigned true to disable views in imessage form
                            createAndAddFormControl(formField, true, formFieldValuesMap)
                        } else {
                            createAndAddFormControl(formField, isSyncToQueue, formFieldValuesMap)
                        }
                }
                if (isResponseNeededToProceed) return
            }
            formManager.checkAndResetBranchTarget()

            // if all the fields are auto fields, show the following message
            if (messageFormViewModel.areAllAutoFields(
                    formTemplateToRender.formFieldsList.size,
                    autoFieldCount
                )
            ) {
                withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                    binding.progressErrorViewForm.show()
                    binding.progressErrorViewForm.setState(STATE.ERROR)
                    if (isSyncToQueue.not()) {
                        binding.progressErrorViewForm.setStateText(
                            getString(R.string.auto_field_form)
                        )
                    } else {
                        binding.progressErrorViewForm.setStateText(getString(R.string.saved_auto_field_form))
                    }
                }
            }
        } else {
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                onInvalidForm(tag + "_FormIsInvalid")
            }
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
                if (draftingViewModel.getSaveToDraftsFeatureFlag()) {
                    binding.btnSaveToDrafts.visibility = View.VISIBLE
                }
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
        binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
        formViewModel.onInvalidForm(caller, stopId, actionId)
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
        isFormSaved: Boolean,
        actualLoopCount : Int = -1
    ) = withContext(
        messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(
            tag
        )
    ) {
        viewId = selectedViewId + 1
        formManager.removeViews(
            viewId,
            binding.llLeftLayout,
            binding.llRightLayout,
            tag
        )
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
                            formFieldValuesMap = formFieldsValuesMap,
                            formField = formField,
                            caller = tag,
                            actualLoopCount = actualLoopCount
                        )
                        val isResponseNeededToProceed = createAndAddFormControl(
                            formField,
                            isSyncToQueue || isFormSaved,
                            formFieldsValuesMap
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
                R.color.color_white
            )
        )

        if (isDriverInImessageReplyForm.not()) {
            binding.ivCancel.setOnClickListener {
                Log.logUiInteractionInInfoLevel(TRIP_FORM, "$tag: Cancel button clicked. isDriverInImessageReplyForm: $isDriverInImessageReplyForm")
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
                        this@FormActivity,
                        R.drawable.ic_back_white
                    )
                )
                setOnClickListener {
                    Log.logUiInteractionInInfoLevel(
                        TRIP_FORM,
                        "$tag: Cancel button clicked. isDriverInImessageReplyForm: $isDriverInImessageReplyForm"
                    )
                    // Only available on reply screen
                    traverseBetweenDriverAndReplyMessageIntent(1)
                }
            }
        }

        binding.btnSaveToDrafts.setDebounceClickListener {
            Log.logUiInteractionInInfoLevel(TRIP_FORM, "$tag: Save To Draft button clicked.")
            hideSaveAndDraftButtons()
            returnToStopDetailView(stopId)
        }

        binding.btnSave.setDebounceClickListener {
            if (binding.btnSave.text.toString().equals(getString(R.string.send), true)) {
                Log.logUiInteractionInInfoLevel(TRIP_FORM, "$tag: Send button clicked.")
                constructFormTemplateData()
                val errorFormFields = FormUtils.getErrorFields(formTemplateData)
                if (errorFormFields.isEmpty()) {
                    hideSaveAndDraftButtons()
                    with(binding) {
                        svContainer.hide()
                        progressErrorViewForm.show()
                        progressErrorViewForm.setProgressState(getString(R.string.sending_text))
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
                                    formTemplateData, path,
                                    true,
                                    latestFormRecipients, stopActionFormKey
                                )
                                // Logging event to record the time taken from arrival to form submission
                                calculateAndRecordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
                                    TIME_TAKEN_FROM_ARRIVAL_TO_FORM_SUBMISSION, dataStoreManager
                                )
                            }
                            ToastCompat.makeText(
                                this@FormActivity,
                                R.string.form_sent_successfully,
                                Toast.LENGTH_LONG
                            ).show()
                            applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, false)
                        }
                        stopActionFormKey = DispatchFormPath()
                        formViewModel.dismissTripPanelMessage()
                        if (isFromTripPanel) {
                            Log.d(tag, "onForm save -> isFromTripPanel is true")
                            formViewModel.restoreSelectedDispatch{
                                formViewModel.viewModelScope.launch {
                                    Intent(
                                        this@FormActivity,
                                        DispatchDetailActivity::class.java
                                    ).apply {
                                        this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also {
                                            putExtra(DISPATCH_ID_TO_RENDER,dataStoreManager.getValue(
                                                DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING))
                                            startActivity(it)
                                        }
                                    }
                                    finish()
                                }
                            }
                        } else {
                            returnToStopDetailView(stopId)
                        }
                    }
                } else {
                    dialog = null
                    formManager.highlightErrorField(
                        formId!!,
                        formClass,
                        errorFormFields,
                        formTemplate.formFieldsList.size,
                        binding.llLeftLayout,
                        binding.llRightLayout,
                        this@FormActivity
                    )
                }
            } else if (binding.btnSave.text.toString()
                    .equals(getString(R.string.reply_button_formactivity), true)
            ) {
                Log.logUiInteractionInInfoLevel(TRIP_FORM, "$tag: Next button clicked.")
                traverseBetweenDriverAndReplyMessageIntent(2)
            }
        }
    }

    override fun onStop() {
        Log.logLifecycle(tag, "$tag onStop")
        if (intent?.getBooleanExtra(CAN_SHOW_CANCEL, false)!!) {
            binding.ivCancel.visibility = View.INVISIBLE
        }
        if (isChangingConfigurations.not()) formManager.removeKeyForImage()
        if (formViewModel.shouldSaveFormData(
                isSyncToQueue = isSyncToQueue,
                isDriverInSingleForm = isDriverInSingleForm,
                isDriverInImessageReplyForm = isDriverInImessageReplyForm,
                cid = formTemplate.formDef.cid
            )
        ) {
            if(draftingViewModel.getSaveToDraftsFeatureFlag()){
                Log.d(DISPATCH_FORM_DRAFT, "Drafting form data onStop, actionId : $actionId isSyncToQueue : $isSyncToQueue")
                saveAsDraft(ON_STOP_CALLBACK)
            }
        }
        if(isChangingConfigurations and isSyncToQueue.not()) {
            constructFormTemplateData()
            formViewModel.saveFormData(
                formTemplateData,
                path,
                isSyncToQueue,
                latestFormRecipients,
                stopActionFormKey
            )
           cacheForms()
        }
        super.onStop()
    }

    override fun onPause() {
        Log.logLifecycle(tag, "$tag onPause")
        if (isSyncToQueue.not()) {
            formViewModel.checkForCompleteFormMessages()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if(isChangingConfigurations and isSyncToQueue.not()) {
            constructFormTemplateData()
            formViewModel.saveFormData(
                formTemplateData,
                path,
                isSyncToQueue,
                latestFormRecipients,
                stopActionFormKey
            )
        }
        Log.logLifecycle(tag, "$tag onDestroy")
        super.onDestroy()
        if(::onBackPressedCallback.isInitialized.not()){
            onBackPressedCallback.remove() // Remove the callback when the activity is destroyed
        }
    }

    private fun cacheForms() {
        if (formTemplate.formDef.cid.isLessThan(ZERO)) return
        messageFormViewModel.draftFormLocally(formTemplateData)
    }

    private fun softOrHardBackButtonPress() {
        if (isSyncToQueue) {
            closeApplicationIfStandAloneForm()
        } else {
                if (isDriverInImessageReplyForm.not() && isDriverInSingleForm.not()) {
                    returnToStopDetailView(stopId)
                } else {
                    if (draftingViewModel.getSaveToDraftsFeatureFlag()) {
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
            if (isSyncToQueue){
                returnToStopDetailView(stopId)
            }
        }
    }

    private suspend fun createAndAddFormControl(
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
            this,
            isFormReadOnly
        )
        var view: View? = null
        var isResponseNeeded = false
        formField.isInDriverForm =
            (isDriverInImessageReplyForm || isDriverInSingleForm || isSyncToQueue)
        val nextQNumAndInflationRequirement =
            isViewInflationRequired(formManager.branchTo, formField, formTemplate)
        val nextQNumToShow = nextQNumAndInflationRequirement.first
        formField.viewId = viewId
        Log.d(tag, "Field to be processed, qnum : ${formField.qnum} fieldId : ${formField.fieldId} " +
                "nextQNumToShow : $nextQNumToShow inflationRequirement : ${nextQNumAndInflationRequirement.second} qType : ${formField.qtype}")
        if (nextQNumAndInflationRequirement.second) {
            when (formField.qtype) {
                FormFieldType.TEXT.ordinal -> view = if ((
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
                    FreeFormEditText(
                        this,
                        textInputLayout,
                        formField,
                        isFormSaved,
                        (displayHeight - toolbarHeightInPixels.toInt()) / 2
                    ).apply {
                        filters =
                            arrayOf(InputFilter.LengthFilter(formTemplate.formFieldsList[0].ffmLength))
                    }.also {
                        if (replyFormDef.isValidForm().not()) {
                            // No reply form. Only single form which is corporate free form
                            formViewModel.getFreeFormEditTextHintAndMessage(
                                formTemplate.formFieldsList[0], actionPayload.freeFormMessage
                            ).also { hintAndText ->
                                textInputLayout.hint = hintAndText.first
                                formField.uiData = hintAndText.second
                                it.setText(hintAndText.second)
                            }
                        } else {
                            if (isSyncToQueue) {
                                textInputLayout.hint =
                                    getString(R.string.freeform_non_editable_input_field_hint_text)
                            } else {
                                if (isDriverInImessageReplyForm) {
                                    formViewModel.getFreeFormEditTextHintAndMessage(
                                        formTemplate.formFieldsList[0],
                                        actionPayload.freeFormMessage,
                                        shouldDisplayOriginalMessage()
                                    ).also { hintAndText ->
                                        textInputLayout.hint = hintAndText.first
                                        it.isFocusable = true
                                        it.isEnabled = true
                                        it.isFocusableInTouchMode = true
                                        it.makeEditTextEditable()
                                    }
                                } else {
                                    textInputLayout.hint =
                                        getString(R.string.freeform_non_editable_input_field_hint_text)
                                    formField.uiData = actionPayload.freeFormMessage
                                    it.setText(actionPayload.freeFormMessage)
                                }
                            }
                        }
                    }
                } else {
                    FormEditText(
                        this,
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
                    this.isFormSaved = isFormSaved
                    view = FormMultipleChoice(
                        this,
                        textInputLayout,
                        formField,
                        isFormSaved,
                        processMultipleChoice
                    )
                        isResponseNeeded = formField.multipleChoiceDriverInputNeeded()

                }
                else -> {
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

        if (messageFormViewModel.isOfTextInputLayoutViewType(formField = formField)) {
            addViewToLayout(view, nextQNumToShow, formField, textInputLayout)
        } else {
            view?.let {
                addFormComponentToLayout(formField, view)
            }
        }

        if (messageFormViewModel.requiresProcessingForMultipleChoiceField(formField, processMultipleChoice)) return true

        formManager.assignPendingClickListener(view)

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
                isFormSaved = isFormSaved,
                renderForm = { branchTargetId: Int, selectedViewId: Int, loopEndId: Int, isFormSaved: Boolean, actualLoopCount : Int, _ : Int ->
                    renderForms(
                        branchTargetId = branchTargetId,
                        selectedViewId = selectedViewId,
                        loopEndId = loopEndId,
                        isFormSaved = isFormSaved,
                        actualLoopCount = actualLoopCount
                    )
                }
            )
            return true
        }

        return isResponseNeeded
    }

    private fun shouldDisplayOriginalMessage(): Boolean {
        return actionPayload.forcedFormClass != FREE_FORM_FORM_CLASS && actionPayload.forcedFormClass != actionPayload.driverFormClass
    }

    private var processMultipleChoice: (FormChoice?) -> Unit = {
        messageFormViewModel.processMultipleChoice(it, formTemplate)
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

    private fun isFormInEditMode(formField: FormField, formFieldValuesMap: HashMap<String, ArrayList<String>>): Boolean {
        return (
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(
                formField = formField,
                makeFieldsNonEditable = isReadOnlyForm(),
                isFormSaved = isSyncToQueue
            ) || (formTemplate.formFieldsList.size == 1 && formField.uiData.isEmpty() && formFieldValuesMap[formField.qnum.toString()]?.isEmpty() == true)
            )
    }

    private fun addFormComponentToLayout(
        formField: FormField,
        view: View
    ) {
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(
                tag
            )
        ) {
            formField.viewId = viewId++
            // Map between viewId and FormField
            viewIdToFormFieldMap[formField.viewId] = formField
            formField.formChoiceList?.forEach {
                it.viewId = formField.viewId
            }
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
                addFreeFormView(view)
            } else {
                checkForOrientationAndAddFields(view)
                mapFieldIdsToViews[view.id] = view
                formManager.setNextActionInKeypad(mapFieldIdsToViews, this@FormActivity)
            }
            // To make driver clear that this form is non-editable, The alpha is reduced in first form
            if (isReadOnlyForm()) view.alpha =
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

    private fun isReadOnlyForm() = driverFormDef.isValidForm() && replyFormDef.isValidForm() && isDriverInImessageReplyForm.not() && isSyncToQueue.not()

    private fun addFreeFormView(view: View) {
        binding.llRightLayout.visibility = View.GONE
        val llLeftLayoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llLeftLayoutParams.weight = 2.0f
        binding.llLeftLayout.layoutParams = llLeftLayoutParams
        binding.llLeftLayout.addView(view)
    }

    private fun checkForOrientationAndAddFields(view: View) {
        Log.d(tag, "Field rendered, fieldId : ${viewIdToFormFieldMap[view.id]?.fieldId} viewID : ${view.id}")
        if (Utils.isTablet(applicationContext) && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (view.id % 2 == 0) {
                binding.llLeftLayout.addView(view)
            } else {
                binding.llRightLayout.addView(view)
            }
        } else {
            binding.llLeftLayout.addView(view)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    formManager.focusNextEditableFormField(this, mapFieldIdsToViews)
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        super.dispatchTouchEvent(ev)
        val v: View? = currentFocus
        /**
         * this method hides the keyboard when the current focus is on EditText.
         * if another edit text is clicked the focus shifts to that EditText and pops up the keyboard.
         * if the focus is not on EditText the keyboard does not popup.
         */
        FormUtils.checkIfEditTextIsFocusedAndHideKeyboard(this, v, ev)
        return true
    }

    private fun setIsSyncToQueue(syncToQueue: Boolean): Boolean {
        isSyncToQueue = syncToQueue
        return isSyncToQueue
    }

    private fun showFirstDisplayScreen(): Boolean {
        val notInReplyForm = isDriverInImessageReplyForm.not()
        return (notInReplyForm && actionPayload.replyActionType != REPLY_WITH_SAME)
    }

    private fun constructFormTemplateData() {
            formManager.iterateViewsToFetchDataFromFormFields(
                binding.llLeftLayout,
                binding.llRightLayout,
                viewIdToFormFieldMap,
                tag
            ).let { constructedFormFieldList ->
                messageFormViewModel.constructFormFieldsWithAutoFields(this@FormActivity.formTemplate, constructedFormFieldList)
                formTemplateData = FormTemplate(
                    formTemplate.formDef,
                    constructedFormFieldList
                )
            }
    }

    private fun traverseBetweenDriverAndReplyMessageIntent(page: Int) {
        if (formViewModel.isFormOpenedFromDraft && draftingViewModel.getSaveToDraftsFeatureFlag()) {
            returnToStopDetailView(stopId)
            return
        }
        val driverFormPath = dispatchFormPathSaved.let {
            DispatchFormPath(
                stopName = it.stopName,
                stopId = it.stopId,
                actionId = it.actionId,
                formId = replyFormDef.formid,
                formClass = replyFormDef.formClass,
                dispatchName = it.dispatchName
            )
        }
        Log.n(TRIP_FORM,"DispatchFromPath values stopName:${dispatchFormPathSaved.stopName}, stopId:${dispatchFormPathSaved.stopId}, actionId:${dispatchFormPathSaved.actionId}, formId:${dispatchFormPathSaved.formId}, formClass:${dispatchFormPathSaved.formClass}")
        // When in reply screen, Form sent with no reply action and driver not editable forms
        if(page == 2 && actionPayload.replyActionType == NO_REPLY_ACTION && !(formTemplate.formDef.driverEditable == 1))
        {
                Log.d(TRIP_FORM,"Driver editable and actionType: ${actionPayload.replyActionType} Form library opened")
                Intent(this@FormActivity,FormLibraryActivity::class.java).apply {
                    putExtra(SHOULD_NOT_RETURN_TO_FORM_LIST, true)
                }.also {
                    startActivity(it)
                }.also{
                    it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or  Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
        }
        else {
            // Forms with reply action / without reply action and driver not editable forms
            Log.d(TRIP_FORM,"actionType: ${actionPayload.replyActionType} New form/Same form will open")
            Intent(this@FormActivity, FormActivity::class.java).apply {
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
        }
        finish()
    }

    private fun saveAsDraft(caller: String) {
        Log.d(DISPATCH_FORM_DRAFT , "Caller $caller Form Saved to draft fieldList:${formTemplateData.formFieldsList}")
        constructFormTemplateData()
        formViewModel.saveDispatchFormDataToDraft(formTemplateData, stopActionFormKey, path, stopId, messageFormViewModel.isReplayWithSameFormType(
            driverFormId = driverFormDef.formid,
            replyFormId = replyFormDef.formid
        ))
        applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, true)
            ToastCompat.makeText(
                this@FormActivity,
                R.string.form_saved_to_drafts,
                Toast.LENGTH_LONG
            ).show()
    }

    private  fun returnToStopDetailView(stopId: Int) {
        Log.d(tag, "Send to stopDetailActivity Intent stopId=$stopId")
        formViewModel.viewModelScope.launch {
            val activeDispatchId = dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
            formViewModel.restoreSelectedDispatch {
                Intent(this@FormActivity, StopDetailActivity::class.java).apply {
                    putExtra(DISPATCH_ID_TO_RENDER, activeDispatchId)
                    putExtra(SELECTED_STOP_ID, stopId)
                    this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(this)
                }
                finish()
            }
        }
    }
}
