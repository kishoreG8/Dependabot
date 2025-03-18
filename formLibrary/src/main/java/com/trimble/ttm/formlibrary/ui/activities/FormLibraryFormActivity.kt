package com.trimble.ttm.formlibrary.ui.activities

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.getImageNames
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.FORM_RESPONSE_PATH
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.customViews.FormEditText
import com.trimble.ttm.formlibrary.customViews.FormMultipleChoice
import com.trimble.ttm.formlibrary.customViews.FreeFormEditText
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.ActivityFormBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.manager.workmanager.scheduleOneTimeImageDelete
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.utils.COMPLETE_FORM_DETAIL
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_DRAFT_RESPONSE_PATH
import com.trimble.ttm.formlibrary.utils.FORM_LIBRARY_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.IS_HOME_PRESSED
import com.trimble.ttm.formlibrary.utils.SELECTED_USERS_KEY
import com.trimble.ttm.formlibrary.utils.TOOLBAR_HEIGHT
import com.trimble.ttm.formlibrary.utils.UiUtil.convertDpToPixel
import com.trimble.ttm.formlibrary.utils.UiUtil.getDisplayHeight
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.Utils.scheduleOneTimeImageUploadIfImagesAvailable
import com.trimble.ttm.formlibrary.utils.Utils.setVisibilityWhenSendingOrDraftingForm
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.setDebounceClickListener
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.ext.showLongToast
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.utils.isGreaterThanAndEqualTo
import com.trimble.ttm.formlibrary.utils.isLessThan
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.utils.isNotEqualTo
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent


class FormLibraryFormActivity : AppCompatActivity(), KoinComponent {

    private val tag = "FormLibraryFormActivity"
    private var formTemplateData: FormTemplate =
        FormTemplate()
    private var responsePath: String = ""
    private var draftPath: String = ""
    private var formName = ""
    private var customerId = ""
    private var formId: Int = -1
    private var formClass: Int = -1
    private var dialog: AlertDialog? = null
    private var processMultipleChoice: (FormChoice?) -> Unit = {
        messageFormViewModel.processMultipleChoice(it, formTemplate)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var isDTF = false

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var formTemplate: FormTemplate =
        FormTemplate()
    private var viewId = 0

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var formTemplateSerializedString: String = ""
    private var viewIdToFormFieldMap = HashMap<Int, FormField>()
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val displayHeight
        get() = getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = convertDpToPixel(TOOLBAR_HEIGHT, this)
    private var driverFormDef = FormDef(formid = -1, formClass = -1)
    private val formDataStoreManager: FormDataStoreManager by inject()
    private var formCloseAlertDialog: AlertDialog? = null
    private var isSendingOrDraftingInProgress = false
    private var hasPredefinedRecipients = false
    private val mapFieldIdsToViews = mutableMapOf<Int, View>()

    private val appModuleCommunicator: AppModuleCommunicator by inject()

    private val draftingViewModel: DraftingViewModel by viewModel()

    private val formManager: FormManager by lazy {
        FormManager()
    }
    private val formTag = "FormTag"
    private lateinit var binding: ActivityFormBinding
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private var formFieldsValuesMap: HashMap<String, ArrayList<String>> = HashMap()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        Log.d(tag, "is orientation Landscape : ${resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE}")
        draftingViewModel.lookupSaveToDraftsFeatureFlag(appModuleCommunicator.getAppModuleApplicationScope())
        binding = DataBindingUtil.setContentView(this, R.layout.activity_form)
        binding.root.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (displayHeight - convertDpToPixel(
                    resources.getDimension(R.dimen.formsLayoutMargin),
                    applicationContext
                ).toInt())
            )
        }.also {
            responsePath = intent.getStringExtra(FORM_RESPONSE_PATH) ?: ""
            draftPath = intent.getStringExtra(FORM_DRAFT_RESPONSE_PATH) ?: ""
            intent.getStringArrayListExtra(COMPLETE_FORM_DETAIL)?.let { formDetails ->
                if (formDetails.size < 4) return@let
                customerId = formDetails[0]
                formId = formDetails[1].toIntOrNull() ?: -1
                formName = formDetails[2]
                formClass = formDetails[3].toIntOrNull() ?: -1
                binding.progressErrorViewForm.setProgressState(getString(R.string.loading_text))
                lifecycleScope.launch(CoroutineName("loadForm") + messageFormViewModel.coroutineDispatcherProvider.io()) {
                    if (binding.llLeftLayout.childCount == 0 && binding.llRightLayout.childCount == 0) {
                        loadFormTemplate()
                    }
                }
            }
            checkIntentErrorData(
                responsePath,
                draftPath,
                intent.getStringArrayListExtra(COMPLETE_FORM_DETAIL),
                intent.action ?: "no_action"
            )
        }
        observeAndRenderForm()
        observeOnBackPress()
    }

    private fun observeOnBackPress() {
        onBackPressedCallback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                // Handle the back button press
                callOnBackPressed()
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun observeAndRenderForm(){
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(
                "observeForm"
            )
        ) {
            messageFormViewModel.renderValues.collect {
                if (!(it.branchTargetId == -1 && it.selectedViewId == -1 && it.loopEndId == -1)) {
                    renderForms(
                        it.branchTargetId,
                        it.selectedViewId,
                        it.loopEndId
                    )
                }
            }
        }
    }

    private fun checkIntentErrorData(
        responsePath: String,
        draftPath: String,
        stringArrayListExtra: ArrayList<String>?,
        action: String,
    ) {
        if (responsePath.isEmpty()) {
            Log.e(
                tag,
                Utils.getIntentDataErrorString(
                    this,
                    "responsePath",
                    "String",
                    "empty",
                    action
                )
            )
        }
        if (draftPath.isEmpty()) {
            Log.e(
                tag,
                Utils.getIntentDataErrorString(
                    this,
                    "draftPath",
                    "String",
                    "empty",
                    action
                )
            )
        }
        if (stringArrayListExtra.isNullOrEmpty()) {
            Log.e(
                tag,
                Utils.getIntentDataErrorString(
                    this,
                    "stringArrayListExtra",
                    "ArrayListExtra",
                    "null",
                    action
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName("initDraft")
        ) {
            formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, true)
            draftingViewModel.initDraftProcessing.collect {
                //we have something write it. we need to draft
                val hasSomeModification = needToDraft()
                if (
                    it &&
                    hasSomeModification &&
                    isSendingOrDraftingInProgress.not()
                ) {
                    draftingViewModel.restoreInitDraftProcessing(appModuleCommunicator.getAppModuleApplicationScope())
                    positiveActionOfAlertDialog()
                }
                //we don't have something write it. we don't need to draft
                if (
                    it &&
                    hasSomeModification.not() &&
                    isSendingOrDraftingInProgress.not()
                ) {
                    draftingViewModel.showDraftMessage = false
                    draftingViewModel.restoreInitDraftProcessing(appModuleCommunicator.getAppModuleApplicationScope())
                    draftingViewModel.setDraftProcessAsFinished(appModuleCommunicator.getAppModuleApplicationScope())
                }
            }
        }
        lifecycleScope.launch(
            messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName("endDraft")
        ) {
            draftingViewModel.draftProcessFinished.collect {
                if (it) {
                    //show the toast when the draft process is finished
                    if (draftingViewModel.showDraftMessage) {
                        draftingViewModel.showDraftMessage = false
                        this@FormLibraryFormActivity.showToast(
                            getString(R.string.draft_saved)
                        )
                    }
                    draftingViewModel.restoreDraftProcessFinished(appModuleCommunicator.getAppModuleApplicationScope())
                    finish()
                }
            }
        }
    }

    private fun needToDraft(): Boolean {
        constructFormTemplateData()
        return messageFormViewModel.hasSomethingToDraft(
            formTemplateData,
            true
        )
    }

    private fun loadFormTemplate() {
        hideRecipientSelectView()
        setUpToolbar()
        if (formId.isNotEqualTo(ZERO)) {
            driverFormDef = FormDef(
                formid = formId,
                formClass = formClass
            )
            lifecycleScope.launch(
                messageFormViewModel.coroutineDispatcherProvider.default() + CoroutineName(
                    "getForm"
                )
            ) {


                val form: Form = messageFormViewModel.getForm(
                    formId = formId.toString(),
                    isFreeForm = FormDef(formid = formId, formClass = formClass).isFreeForm(),
                    uiFormResponse = messageFormViewModel.getSavedUIDataDuringOrientationChange(),
                    savedImageUniqueIdentifierToValueMap = messageFormViewModel.getLocallyCachedImageUniqueIdentifierToValueMap(),
                    shouldFillUiResponse = true
                )
                formFieldsValuesMap = form.formFieldValuesMap

                form.formTemplate.formDef.recipients =
                    messageFormViewModel.getLatestFormRecipients(
                        appModuleCommunicator.doGetCid().toInt(), formId
                    )
                processForm(
                    form.formTemplate
                )
                withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                    showRecipientSelectView(form)
                }
            }
        }
    }

    private fun hideRecipientSelectView() = binding.llSelectRecipient.hide()

    private fun hideFormContainerView() = binding.svContainer.hide()

    private fun showSendingOrDraftingProgress(isSending: Boolean) {
        isSendingOrDraftingInProgress = true
        hideRecipientSelectView()
        hideFormContainerView()
        binding.progressErrorViewForm.setProgressState(
            if (isSending) getString(R.string.sending_text)
            else getString(R.string.drafting_text)
        )
        binding.btnSave.setVisibilityWhenSendingOrDraftingForm(true)
    }

    private fun showRecipientSelectView(form: Form) {
        with(binding) {
            llSelectRecipient.show()
            if (form.formTemplate.formDef.recipients.isNotEmpty()) {
                hasPredefinedRecipients = true
                tvRecipients.text = getString(R.string.predefined_recipients)
                tvRecipients.isEnabled = false
                tvRecipients.isClickable = false
            } else {
                setRecipientText(messageFormViewModel.selectedUsers)
                if (messageFormViewModel.selectedRecipients.size.isLessThanAndEqualTo(ZERO))
                    tvRecipients.text = getString(R.string.tap_to_select_recipients)
                tvRecipients.isEnabled = true
                tvRecipients.isClickable = true
                tvRecipients.setDebounceClickListener {
                    Log.logUiInteractionInInfoLevel(tag, "$tag recipients clicked to select contacts from contact list screen")
                    startForResult.launch(
                        Intent(
                            this@FormLibraryFormActivity,
                            ContactListActivity::class.java
                        ).apply {
                            if (messageFormViewModel.selectedUsers.isNotEmpty()) {
                                putExtras(Bundle().apply {
                                    putParcelableArrayList(
                                        SELECTED_USERS_KEY,
                                        messageFormViewModel.selectedUsers
                                    )
                                })
                            }
                        }
                    )
                }
            }
        }
        messageFormViewModel.savePreviousSelectedRecipients()
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->

            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            result.data?.extras?.let { bundle ->
                bundle.getParcelableArrayList<User>(SELECTED_USERS_KEY)?.let { selectedUsers ->
                    if (selectedUsers.isNotEmpty() || (selectedUsers.isEmpty() && bundle.getBoolean(
                            IS_HOME_PRESSED
                        ))
                    ) {
                        messageFormViewModel.selectedUsers = selectedUsers
                        messageFormViewModel.selectedRecipients =
                            selectedUsers.associate { "${it.uID}" to it.uID }
                        formTemplate.formDef.recipients =
                            messageFormViewModel.selectedRecipients
                        setRecipientText(selectedUsers)
                    }
                }
            }

        }


    private fun setRecipientText(selectedUsers: ArrayList<User>) {
        with(binding) {
            if (selectedUsers.isEmpty()) {
                tvRecipients.text =
                    getString(R.string.tap_to_select_recipients)
                return
            }
            tvRecipients.text = selectedUsers.joinToString(
                "; ",
                prefix = "",
                postfix = "",
                transform = { user -> user.username.trimEnd() })
        }
    }

    private suspend fun processForm(
        formTemplate: FormTemplate,
    ) {
        if (!messageFormViewModel.checkIfFormIsValid(formTemplate)) {
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
            }
            return
        }
        isDTF = messageFormViewModel.checkIfFormIsDtf(formTemplate)
        this.formTemplate = formTemplate
        formTemplateSerializedString = messageFormViewModel.serializeFormTemplate(formTemplate)
        withContext(messageFormViewModel.coroutineDispatcherProvider.main()) { binding.progressErrorViewForm.setNoState() }
        if (isDTF) {
            formManager.restrictOrientationChange(this)
        }

        withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
            binding.tvToolbarTitle.text = formName
            binding.btnSave.visibility = View.VISIBLE
            if(draftingViewModel.getSaveToDraftsFeatureFlag()) {
                binding.btnSaveToDrafts.visibility = View.VISIBLE
            }
        }
        messageFormViewModel.saveFormTemplateCopy(formTemplate)
        val formTemplateToRender: FormTemplate = messageFormViewModel.fetchFormTemplate(
            formTemplateSerializedString
        )

        formTemplateToRender.formFieldsList.forEach { formField ->
            messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                formFieldValuesMap = formFieldsValuesMap,
                formField  = formField,
                caller = tag,
                actualLoopCount = -1,
            )

            var isResponseNeededToProceed: Boolean
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                isResponseNeededToProceed = createAndAddFormControl(formField)
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
        loopEndId: Int
    ) = withContext(messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(tag)) {
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
                        val isResponseNeededToProceed =
                            createAndAddFormControl(formField)
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
        outState.putString(FORM_RESPONSE_PATH, responsePath)
        outState.putString(FORM_DRAFT_RESPONSE_PATH, draftPath)
        with(ArrayList<String>()) {
            add(customerId)
            add(formId.toString())
            add(formName)
            add(formClass.toString())
            outState.putStringArrayList(COMPLETE_FORM_DETAIL, this)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        responsePath = savedInstanceState.getString(FORM_RESPONSE_PATH).toString()
        draftPath = savedInstanceState.get(FORM_DRAFT_RESPONSE_PATH).toString()
        savedInstanceState.getStringArrayList(COMPLETE_FORM_DETAIL)?.let { formDetails ->
            if (formDetails.size < 4) return@let
            customerId = formDetails[0]
            formId = formDetails[1].toIntOrNull() ?: -1
            formName = formDetails[2]
            formClass = formDetails[3].toIntOrNull() ?: -1
        }
    }

    private fun setToolbarCancelButtonClickListener() {
        binding.ivCancel.setOnClickListener {
            callOnBackPressed()
        }
    }

    private fun constructFormTemplateData() {
            formManager.iterateViewsToFetchDataFromFormFields(
                binding.llLeftLayout,
                binding.llRightLayout,
                viewIdToFormFieldMap,
                tag
            ).let { constructedFormFieldList ->
                messageFormViewModel.constructFormFieldsWithAutoFields(formTemplate, constructedFormFieldList)
                formTemplateData =
                    FormTemplate(
                        formTemplate.formDef,
                        constructedFormFieldList
                    )
            }
    }

    private fun sendingOrDraftingProgress() {
        lifecycleScope.launch(CoroutineName("draftProgress")) {
            if (formTemplate.formDef.cid.isGreaterThanAndEqualTo(ZERO)) {
                showSendingOrDraftingProgress(true)
                messageFormViewModel.isResponseDrafted.observe(this@FormLibraryFormActivity) {
                    isSendingOrDraftingInProgress = false
                    binding.progressErrorViewForm.setNoState()
                    showLongToast(R.string.message_sent)
                    this@FormLibraryFormActivity.finish()
                }
                if (formTemplate.formDef.recipients.isEmpty())
                    formTemplate.formDef.recipients =
                        messageFormViewModel.selectedRecipients
                val obcId = appModuleCommunicator.doGetObcId()
                val formDataToSave = FormDataToSave(
                    formTemplateData,
                    responsePath,
                    formTemplate.formDef.formid.toString(),
                    FORM_LIBRARY_RESPONSE_TYPE,
                    formName,
                    formTemplate.formDef.formClass,
                    customerId,
                    hasPredefinedRecipients, obcId
                )
                messageFormViewModel.saveFormData(
                    formDataToSave
                )
            } else {
                finish()
            }
        }
    }

    private fun setToolbarSaveButtonClickListener() {
        binding.btnSave.setDebounceClickListener {
            Log.logUiInteractionInInfoLevel(tag, "$tag save button clicked")
            if (isSendingOrDraftingInProgress.not()) {
                if (messageFormViewModel.selectedRecipients.isEmpty() &&
                    formTemplate.formDef.recipients.isEmpty()
                ) {
                    showToast(getString(R.string.form_must_have_one_recipient_at_least))
                    return@setDebounceClickListener
                }

                //If DTF, then construct new FormField list
                constructFormTemplateData()
                val errorFormFields = FormUtils.getErrorFields(formTemplateData)
                if (errorFormFields.isEmpty()) {
                    sendingOrDraftingProgress()
                } else {
                    dialog = null
                    formManager.highlightErrorField(
                        formId,
                        formClass,
                        errorFormFields,
                        formTemplate.formFieldsList.size,
                        binding.llLeftLayout,
                        binding.llRightLayout,
                        this
                    )
                }
            }
            applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, false)
        }
    }

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setTitleTextColor(
            ContextCompat.getColor(
                applicationContext,
                R.color.textColor
            )
        )
        setToolbarCancelButtonClickListener()
        binding.btnSave.setText(R.string.send)
        setToolbarSaveButtonClickListener()
        binding.btnSaveToDrafts.setOnClickListener {
            showFormExitAlert()
        }
    }

    override fun onPause() {
        Log.logLifecycle(tag, "$tag onPause")
        // adding the below for safer side when activity is in onStop state but if system kills the activity, the current UIData might get lost, so leaving as is
        constructFormTemplateData()
        draftFormLocally()
        super.onPause()
    }

    private fun draftFormLocally() {
        if (formTemplate.formDef.cid.isLessThan(ZERO)) return
        messageFormViewModel.draftFormLocally(formTemplateData)
    }


    fun callOnBackPressed() {
        Log.logUiInteractionInInfoLevel(tag, "$tag onBackPressed")
        constructFormTemplateData()
        if (binding.progressErrorViewForm.currentState == STATE.NONE && draftingViewModel.getSaveToDraftsFeatureFlag()) {
            if (
                (messageFormViewModel.isAnEmptyForm(formTemplateData).not() &&
                        isSendingOrDraftingInProgress.not())
            ) {
                showFormExitAlert()
            } else {
                finishActivity()
            }
        } else {
            finishActivity()
        }
    }

    private suspend fun createAndAddFormControl(
        formField: FormField,
        isFormSaved: Boolean = false,
    ): Boolean {
        val textInputLayout = formManager.createTextInputLayout(
            formField.qtype,
            formId,
            formClass,
            formTemplate.formFieldsList.size,
            this,
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(formField = formField)
        )
        var view: View? = null
        var isResponseNeeded = false
        formField.isInDriverForm = true

        val nextQNumAndInflationRequirement =
            FormUtils.isViewInflationRequired(formManager.branchTo, formField, formTemplate)
        val nextQNumToShow = nextQNumAndInflationRequirement.first
        formField.viewId = viewId
        Log.d(tag, "Field to be processed, qnum : ${formField.qnum} fieldId : ${formField.fieldId} " +
                "nextQNumToShow : $nextQNumToShow inflationRequirement : ${nextQNumAndInflationRequirement.second} qType : ${formField.qtype}")
        if (nextQNumAndInflationRequirement.second) {
            when (formField.qtype) {
                FormFieldType.MULTIPLE_CHOICE.ordinal -> {
                    formField.formChoiceList?.find { formChoice -> formChoice.value == formField.uiData }
                        ?.let { matchedFormChoice ->
                            formField.uiData = matchedFormChoice.value
                        }
                    view = FormMultipleChoice(
                        this,
                        textInputLayout,
                        formField,
                        isFormSaved,
                        processMultipleChoice
                    )
                    isResponseNeeded = formField.multipleChoiceDriverInputNeeded()
                }
                FormFieldType.TEXT.ordinal ->
                    view = if (formManager.isFreeForm(
                            FormDef(formid = formId, formClass = formClass)
                        ) && formTemplate.formFieldsList.size == 1
                    ) {
                        //driver needs to fill something in the form to move forward
                        formField.required = 1
                        // Set counter max length
                        textInputLayout.counterMaxLength =
                            formTemplate.formFieldsList[0].ffmLength
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
                            messageFormViewModel.getFreeFormEditTextHintAndMessage(
                                formTemplate.formFieldsList[0],
                                EMPTY_STRING
                            ).also { hintAndText ->
                                textInputLayout.hint = hintAndText.first
                                it.setText(hintAndText.second)
                            }
                        }
                    } else
                        FormEditText(
                            this,
                            textInputLayout,
                            formField,
                            isFormSaved
                        )
                else -> {
                    view = formManager.createViewWithFormField(
                        formField = formField,
                        stack = messageFormViewModel.getFormFieldStack(),
                        context = this,
                        textInputLayout = textInputLayout,
                        isFormSaved = isFormSaved,
                        stopId = ZERO,
                        lifecycleScope= lifecycleScope,
                        supportFragmentManager = supportFragmentManager
                    ) {
                        messageFormViewModel.getFormFieldCopy(
                            formField
                        )
                    }
                }
            }
        }

        if(messageFormViewModel.isOfTextInputLayoutViewType(formField = formField))
            addViewToLayout(view, nextQNumToShow, formField, textInputLayout)
        else view?.let {
            addFormComponentToLayout(formField,it)
        }

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
                renderForm = {  branchTargetId:Int,selectedViewId:Int,loopEndId:Int,_:Boolean, _ : Int, _ : Int ->
                    renderForms(
                        branchTargetId = branchTargetId,
                        selectedViewId = selectedViewId,
                        loopEndId = loopEndId
                    )
                }
            )
            return true
        }

        //return if response needed
        return isResponseNeeded
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
        view: View,
    ) {
        lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName("addComponent")) {
            formField.viewId = viewId++
            //Map between viewId and FormField
            viewIdToFormFieldMap[formField.viewId] = formField
            formField.formChoiceList?.forEach {
                it.viewId = formField.viewId
            }
            view.id = formField.viewId
            view.tag = formField.viewId
            checkAndStoreMultipleChoiceFormFieldStack(formField, view)
            if (formManager.isFreeForm(
                    FormDef(
                        formid = formId,
                        formClass = formClass
                    )
                ) && formTemplate.formFieldsList.size == 1
            ) {
                addFreeFormView(view)
            } else {
                checkForOrientationAndAddFields(view)
            }
            mapFieldIdsToViews[view.id] = view
            formManager.setNextActionInKeypad(mapFieldIdsToViews, this@FormLibraryFormActivity)
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
        with(binding) {
            llRightLayout.visibility = View.GONE
            val llLeftLayoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            llLeftLayoutParams.weight = 2.0f
            llLeftLayout.layoutParams = llLeftLayoutParams
            llLeftLayout.addView(view)
        }
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

    private fun showFormExitAlert() {
        if (formCloseAlertDialog.isNull()) {
            formCloseAlertDialog = UiUtils.showAlertDialog(
                AlertDialogData(
                    context = this,
                    message = getString(R.string.draft_message),
                    title = getString(R.string.alert),
                    positiveActionText = getString(R.string.yes),
                    negativeActionText = getString(R.string.no),
                    isCancelable = false,
                    positiveAction = {
                        positiveActionOfAlertDialog()
                    },
                    negativeAction = {
                        negativeActionOfAlertDialog()
                    })
            ).also { alertDialog ->
                setAlertDialogKeyListener(alertDialog)
            }
        }
    }

    private fun negativeActionOfAlertDialog() {
        Log.logUiInteractionInInfoLevel(tag, "$tag draft dialog no button clicked")
        formCloseAlertDialog?.cancel()
        finishActivity()
        formCloseAlertDialog = null
        formTemplateData.formFieldsList.getImageNames()?.let { applicationContext.scheduleOneTimeImageDelete(it, shouldDeleteFromStorage = false) }
    }

    private fun setAlertDialogKeyListener(alertDialog: AlertDialog) {
        alertDialog.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && alertDialog.isShowing) {
                formCloseAlertDialog = null
                alertDialog.dismiss()
            }
            true
        }
    }

    private fun positiveActionOfAlertDialog() {
        lifecycleScope.launch(CoroutineName("dialogYes")) {
            Log.logUiInteractionInInfoLevel(tag, "$tag draft dialog yes button clicked")
            if (formTemplate.formDef.cid.isGreaterThanAndEqualTo(ZERO)) {
                constructFormTemplateData()
                showSendingOrDraftingProgress(false)
                setRecipients()
                savePreDraft()
                applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, true)
            }
        }
    }

    private suspend fun savePreDraft() {
        draftingViewModel.showDraftMessage = true
        val obcId = appModuleCommunicator.doGetObcId()
        draftingViewModel.makeDraft(
            formDataToSave = FormDataToSave(
                formTemplateData,
                draftPath,
                formTemplate.formDef.formid.toString(),
                FORM_LIBRARY_RESPONSE_TYPE,
                formName,
                formTemplate.formDef.formClass,
                customerId,
                hasPredefinedRecipients,
                obcId
            ), caller = tag
        )
    }

    private fun setRecipients() {
        if (formTemplate.formDef.recipients.isEmpty())
            formTemplate.formDef.recipients =
                messageFormViewModel.selectedRecipients
    }

    private fun finishActivity() {
        finish()
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
        if (isChangingConfigurations.not()) formManager.removeKeyForImage()
    }

    override fun onDestroy() {
        if(isChangingConfigurations) {
            Log.d(tag, "Configuration changed")
            constructFormTemplateData()
            draftFormLocally()
        }
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
        onBackPressedCallback.remove() // Remove the callback when the activity is destroyed
    }
}