package com.trimble.ttm.formlibrary.ui.fragments

import android.app.Activity
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.model.isValidForm
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DISPATCH_FORM_SAVE_PATH
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.ext.getUnCompletedDispatchFormPath
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
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.ui.activities.ContactListActivity
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.COMES_FROM_DETAIL
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.DISPATCH_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.DRAFTED_USERS
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_LIBRARY_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.FORM_UI_RESPONSE
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.HAS_PRE_DEFINED_RECIPIENTS
import com.trimble.ttm.formlibrary.utils.INBOX_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.IS_FROM_DRAFTED_FORM
import com.trimble.ttm.formlibrary.utils.IS_FROM_SENT_FORM
import com.trimble.ttm.formlibrary.utils.IS_HOME_PRESSED
import com.trimble.ttm.formlibrary.utils.IS_REPLY_WITH_SAME
import com.trimble.ttm.formlibrary.utils.MARK_READ
import com.trimble.ttm.formlibrary.utils.MESSAGE
import com.trimble.ttm.formlibrary.utils.SELECTED_USERS_KEY
import com.trimble.ttm.formlibrary.utils.SENT_USERS
import com.trimble.ttm.formlibrary.utils.SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH
import com.trimble.ttm.formlibrary.utils.UiUtil
import com.trimble.ttm.formlibrary.utils.Utils.customGetSerializable
import com.trimble.ttm.formlibrary.utils.Utils.scheduleOneTimeImageUploadIfImagesAvailable
import com.trimble.ttm.formlibrary.utils.Utils.setVisibilityWhenSendingOrDraftingForm
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.findNavControllerSafely
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.navigateBack
import com.trimble.ttm.formlibrary.utils.ext.navigateTo
import com.trimble.ttm.formlibrary.utils.ext.setDebounceClickListener
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.ext.showLongToast
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.utils.isEqualTo
import com.trimble.ttm.formlibrary.utils.isGreaterThan
import com.trimble.ttm.formlibrary.utils.isGreaterThanAndEqualTo
import com.trimble.ttm.formlibrary.utils.isLessThan
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Suppress("UNCHECKED_CAST")
class MessageReplyFragment : Fragment(), KoinComponent {
    private val logTag = "MessageReplyFrag"
    private lateinit var fragmentView: View
    private var formTemplateData: FormTemplate =
        FormTemplate()
    private var formId: Int = -1
    private var replyFormName = EMPTY_STRING
    private var formClass: Int = -1
    private var isDTF = false
    private var formTemplate: FormTemplate =
        FormTemplate()
    private var viewId = 0
    private var asn = ""
    private var replyActionType: Int = -1
    private var drivereditable: Int = -1
    private var formTemplateSerializedString: String = ""
    private var viewIdToFormFieldMap = HashMap<Int, FormField>()
    private var formFieldsValuesMap: HashMap<String, ArrayList<String>> = HashMap()
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val messagingViewModel: MessagingViewModel by sharedViewModel()
    private val draftingViewModel: DraftingViewModel by sharedViewModel()
    private val displayHeight
        get() = UiUtil.getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = context?.let { UiUtil.convertDpToPixel(60.0f, it) } ?: 0.0f
    private val formDataStoreManager: FormDataStoreManager by inject()
    private var navigateUpDialog: AlertDialog? = null
    private var multipleInvocationLock: Boolean = false
    // lazy init of this will throw NoSuchMethodException
    private val formManager: FormManager = FormManager()
    private var isOpenedFromDraft = false
    private var isOpenedFromSent = false
    private var oldFormDraftedUnixTime = 0L
    private var formResponseType = EMPTY_STRING
    private var messageReplySenderUID = 0L
    private var isSendingOrDraftingInProgress = false
    private var hasPredefinedRecipients = false
    private var isReplyWithSame = false
    private var formFieldList: ArrayList<MessageFormField> = arrayListOf()

    private lateinit var activityFormBinding: ActivityFormBinding

    private val appModuleCommunicator: AppModuleCommunicator by inject()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        Log.d(logTag, "is orientation Landscape : ${resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE}")
        draftingViewModel.lookupSaveToDraftsFeatureFlag(appModuleCommunicator.getAppModuleApplicationScope())
        activityFormBinding = ActivityFormBinding.inflate(inflater, container, false)
        return activityFormBinding.root
            .apply {
                fragmentView = this
                activity?.applicationContext?.let { appContext ->
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        (displayHeight - UiUtil.convertDpToPixel(
                            resources.getDimension(R.dimen.formsLayoutMargin),
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
                handleCancelOrBackButtonPressAction()
            }
        }
        activityFormBinding.progressErrorViewForm.setProgressState(getString(R.string.loading_text))
        arguments?.getBoolean(IS_FROM_DRAFTED_FORM)?.let { isFromDraftForm ->
            if (isFromDraftForm) isOpenedFromDraft = true
        }
        arguments?.getBoolean(IS_FROM_SENT_FORM)?.let { isFromSentForm ->
            if (isFromSentForm) isOpenedFromSent = true
        }
        arguments?.getSerializable(DRAFTED_USERS)?.let { draftedUsers ->
            with(draftedUsers as LinkedHashSet<User>) {
                if (messageFormViewModel.selectedUsers.size.isEqualTo(ZERO)) messageFormViewModel.selectedUsers =
                    ArrayList(this)
                messageFormViewModel.selectedRecipients =
                    messageFormViewModel.selectedUsers.associate { "${it.uID}" to it.uID }
            }
        }
        arguments?.getSerializable(SENT_USERS)?.let { sentUsers ->
            with(sentUsers as LinkedHashSet<User>) {
                messageFormViewModel.selectedUsers = ArrayList(this)
                messageFormViewModel.selectedRecipients =
                    messageFormViewModel.selectedUsers.associate { "${it.uID}" to it.uID }
            }
        }
        arguments?.getString(FORM_RESPONSE_TYPE)?.let { formResponseType = it }
        arguments?.getBoolean(HAS_PRE_DEFINED_RECIPIENTS)?.let { hasPredefinedRecipients = it }
        arguments?.getBoolean(IS_REPLY_WITH_SAME)?.let { isReplyWithSame = it}
        getMessageData()
        arguments?.getLong(CREATED_AT)?.let {
            oldFormDraftedUnixTime = it
        }
        arguments?.getUnCompletedDispatchFormPath()?.let {
            draftingViewModel.unCompletedDispatchFormPath = it
        }
        arguments?.getString(DISPATCH_FORM_SAVE_PATH)?.let {
            draftingViewModel.dispatchFormSavePath = it
        }
        multipleInvocationLock = false
        loadMessage(
            messageFormViewModel.shouldSaveOldState
        )
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

    private fun getMessageData() {
        arguments?.getSerializable(MESSAGE)?.let {
            with(it as Message) {
                if(isReplyWithSame){
                    this@MessageReplyFragment.formId =  if (formId.isNotEmpty()) formId.toInt() else -1
                    this@MessageReplyFragment.formClass =  if (formClass.isNotEmpty()) formClass.toFloat().toInt() else -1
                    this@MessageReplyFragment.replyFormName = formName.ifEmpty { "" }
                }
                else{
                    this@MessageReplyFragment.formId = if (replyFormId.isNotEmpty()) replyFormId.toInt() else -1
                    if (isOpenedFromDraft or isOpenedFromSent) {
                        this@MessageReplyFragment.replyFormName = formName
                        showHeaderSelectView()
                    } else {
                        this@MessageReplyFragment.replyFormName = replyFormName
                    }
                    this@MessageReplyFragment.formClass =
                        if (replyFormClass.isNotEmpty()) replyFormClass.toInt() else -1
                }
                this@MessageReplyFragment.asn = asn
                this@MessageReplyFragment.messageReplySenderUID = this.uid
                this@MessageReplyFragment.formFieldList = formFieldList
                this@MessageReplyFragment.replyActionType= if (replyActionType.isNotEmpty()) replyActionType.toFloat().toInt() else -1
            }

            formFieldsValuesMap = messageFormViewModel.processDispatcherFormValues(
                this@MessageReplyFragment.formClass,
                formFieldList
            )
        }
    }

    private fun showHeaderSelectView() {
        activityFormBinding.llSelectRecipient.show()
        activityFormBinding.llSelectDate.show()

        if (formResponseType == FORM_LIBRARY_RESPONSE_TYPE) {
            activityFormBinding.llSelectRecipient.hide()
            activityFormBinding.llSelectDate.hide()
        }
        setRecipientText(messageFormViewModel.selectedUsers)
        setDateText()
        if (isOpenedFromSent.not() && messageFormViewModel.selectedRecipients.size.isLessThanAndEqualTo(
                ZERO
            ) && hasPredefinedRecipients.not()
        ) {
            activityFormBinding.tvRecipients.text = getString(R.string.tap_to_select_recipients)
        }
        if (isOpenedFromSent.not() && formResponseType != INBOX_FORM_RESPONSE_TYPE) {
            activityFormBinding.tvRecipients.isEnabled = true
            activityFormBinding.tvRecipients.isClickable = true
            activityFormBinding.tvRecipients.setDebounceClickListener {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag Select Recipients clicked")
                startForResult.launch(
                    context?.let { context ->
                        Intent(
                            context,
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
                    }
                )
            }
        }
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != Activity.RESULT_OK) {
                loadMessage(false)
                return@registerForActivityResult
            }
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
                        loadMessage(false)
                    }
                }
            }
        }

    private fun setRecipientText(selectedUsers: ArrayList<User>) {
        with(activityFormBinding) {
            if (hasPredefinedRecipients || (selectedUsers.isEmpty() && formResponseType == DISPATCH_FORM_RESPONSE_TYPE && isOpenedFromSent)) {
                tvRecipients.text = getString(R.string.predefined_recipients)
            } else {
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
    }

    private fun setDateText() {
        arguments?.customGetSerializable<Message>(MESSAGE)?.let {
            activityFormBinding.tvDate.text = it.dateTime
        }
    }

    private fun showSendingOrDraftingProgress(isSending: Boolean) {
        isSendingOrDraftingInProgress = true
        with(activityFormBinding) {
            llSelectRecipient.hide()
            svContainer.hide()
            progressErrorViewForm.setProgressState(
                if (isSending) getString(R.string.sending_text)
                else getString(R.string.drafting_text)
            )
            btnSave.setVisibilityWhenSendingOrDraftingForm(true)
        }

    }

    private fun loadMessage(updateOldState: Boolean) {
        if (multipleInvocationLock.not()) {
            multipleInvocationLock = true
            if (activityFormBinding.llLeftLayout.childCount == 0 && activityFormBinding.llRightLayout.childCount == 0) loadFormTemplate(
                updateOldState
            )
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        if (isOpenedFromSent.not()){
            lifecycleScope.launch(
                messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(logTag)
            ) {
                multipleInvocationLock = false
                formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, true)
                draftingViewModel.initDraftProcessing.collect {
                    constructFormTemplateData()
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
                        draftingViewModel.restoreInitDraftProcessing(appModuleCommunicator.getAppModuleApplicationScope())
                        draftingViewModel.setDraftProcessAsFinished(appModuleCommunicator.getAppModuleApplicationScope())
                        draftingViewModel.showDraftMessage = false
                    }
                }
            }
            lifecycleScope.launch(
                messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(logTag)
            ) {
                draftingViewModel.draftProcessFinished.collect {
                    Log.i(logTag, "draftProcessFinished: has to draft $it")
                    //we finished the draft process, we need to close the fragment
                    if (it) {
                        draftingViewModel.restoreDraftProcessFinished(appModuleCommunicator.getAppModuleApplicationScope())
                        navigateUpAndDismissDialog()
                    }
                }
            }
        }

        //Hides the toolbar of the MessageActivity to show custom toolbar
        activity?.findViewById<ComposeView>(R.id.toolbar)?.visibility = View.GONE
        (activity as? MessagingActivity)?.lockDrawer()
        activityFormBinding.ivCancel.setOnClickListener {
            Log.logUiInteractionInInfoLevel(logTag, "$logTag Cancel clicked")
            handleCancelOrBackButtonPressAction()
        }
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag Back pressed")
                    handleCancelOrBackButtonPressAction()
                }
            })
    }

    private fun handleCancelOrBackButtonPressAction() {
        if (isOpenedFromSent || draftingViewModel.getSaveToDraftsFeatureFlag().not()) {
            navigateUpAndDismissDialog()
        } else {
            if (activityFormBinding.progressErrorViewForm.currentState == STATE.NONE) {
                constructFormTemplateData()
                if (needToDraft() && isSendingOrDraftingInProgress.not()) {
                    promptOnNavigationUp()
                } else {
                    navigateUpAndDismissDialog()
                }
            } else {
                navigateUpAndDismissDialog()
            }
        }
    }

    private fun needToDraft(): Boolean {
        return messageFormViewModel.hasSomethingToDraft(
            formTemplateData,
            true
        )
    }

    private fun loadFormTemplate(
        updateOldState: Boolean
    ) {
        if (formId.isGreaterThan(ZERO)) {
            lifecycleScope.launch(
                messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(
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
                    INBOX_COLLECTION
                )

                var uiFormResponse: UIFormResponse = messageFormViewModel.getSavedUIDataDuringOrientationChange()

                if (isOpenedFromDraft || isOpenedFromSent && (arguments?.getParcelable<FormResponse>(FORM_UI_RESPONSE)) != null) {
                    (arguments?.getParcelable<FormResponse>(FORM_UI_RESPONSE))?.let { formResponse ->
                        uiFormResponse = if (messageFormViewModel.getSavedUIDataDuringOrientationChange().formData.fieldData.isEmpty()) {
                            UIFormResponse(formData = formResponse)
                        } else {
                            messageFormViewModel.getSavedUIDataDuringOrientationChange()
                        }
                    }
                }

                val form: Form = messageFormViewModel.getForm(
                    formId.toString(),
                    FormDef(formid = formId, formClass = formClass).isFreeForm(),
                    uiFormResponse,
                    formResponseType,
                    asn,
                    dispatchFormSavePath= draftingViewModel.dispatchFormSavePath,
                    draftingViewModel.unCompletedDispatchFormPath,
                    formFieldsValuesMap,
                    shouldFillUiResponse = true,
                    isFormSaved = false,
                    isReplayWithSame = (formClass == -1 || isReplyWithSame),
                    savedImageUniqueIdentifierToValueMap = messageFormViewModel.getLocallyCachedImageUniqueIdentifierToValueMap()

                )
                formFieldsValuesMap = form.formFieldValuesMap
                getRecipientsOfForm().let { recipients ->
                    form.formTemplate.formDef.recipients = recipients
                }
                savePreviousSelectedRecipients(updateOldState)
                processForm(
                    asn,
                    updateOldState,
                    form.formTemplate
                )
            }
        }
    }

    private fun savePreviousSelectedRecipients(updateOldState: Boolean){
        if (updateOldState) {
            messageFormViewModel.savePreviousSelectedRecipients()
        }
    }

    private suspend fun getRecipientsOfForm(): MutableMap<String, Any> {
        val recipients = mutableMapOf<String, Any>()
        //gets latest recipients for the form from the forms collection
        messageFormViewModel.getLatestFormRecipients(
            appModuleCommunicator.doGetCid().toInt(), formId
        ).also {
            if (it.isNotEmpty() && formResponseType == FORM_LIBRARY_RESPONSE_TYPE) {
                with(activityFormBinding.tvRecipients) {
                    text = getString(R.string.predefined_recipients)
                    isEnabled = false
                    isClickable = false
                }
                recipients.putAll(it)
            }
        }

        //gets the recipients from the PFMFormDraftResponse collection.
        // The users are passed to the viewModel in onViewCreated
        recipients.putAll(messageFormViewModel.selectedRecipients)

        //If the reply type is inbox message reply, add the From recipient to To.
        //Because the reply form should have the sender address as To recipient.
        if (formResponseType == INBOX_FORM_RESPONSE_TYPE) {
            hashMapOf<String, Any>().let {
                if (messageReplySenderUID > 0L) {
                    it[ZERO.toString()] = messageReplySenderUID
                    recipients.putAll(it)
                }
            }
        }
        return recipients
    }

    private suspend fun processForm(
        asn: String,
        updateOldState: Boolean,
        formTemplate: FormTemplate
    ) {
        if (messageFormViewModel.checkIfFormIsValid(formTemplate)) {
            isDTF = messageFormViewModel.checkIfFormIsDtf(formTemplate)
            this.formTemplate = formTemplate
            addAsnToFormTemplateDataIfNotEmpty(formTemplate, asn)
            formTemplateSerializedString =
                messageFormViewModel.serializeFormTemplate(formTemplate)
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                setupToolbar()
            }
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                showToolBarTitleAndToSection(formTemplate)
            }
            if (isDTF) {
                (activity as? Activity)?.let { formManager.restrictOrientationChange(it) }
            }
            if (updateOldState) {
                messageFormViewModel.saveFormTemplateCopy(formTemplate)
            }
            getFormFields()
        } else withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
            activityFormBinding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
        }
    }

    private fun showToolBarTitleAndToSection(formTemplate: FormTemplate) {
        activityFormBinding.tvToolbarTitle.text = getToolbarTitle(formTemplate)
        if (isOpenedFromDraft || isOpenedFromSent) {
            activityFormBinding.llSelectRecipient.show()
            activityFormBinding.llSelectDate.show()
        }

        activityFormBinding.progressErrorViewForm.setNoState()
    }

    private fun getToolbarTitle(formTemplate: FormTemplate): String {
        return if (isOpenedFromSent && draftingViewModel.unCompletedDispatchFormPath.dispatchName.isNotEmpty()) {
            val dispatchName = draftingViewModel.unCompletedDispatchFormPath.dispatchName
            val stopName = draftingViewModel.unCompletedDispatchFormPath.stopName
            if (stopName.isNotEmpty()) "$dispatchName - $stopName" else dispatchName
        } else {
            formTemplate.formDef.name
        }
    }

    private suspend fun getFormFields() {
        val formTemplateToRender: FormTemplate = messageFormViewModel.fetchFormTemplate(
            formTemplateSerializedString
        )
        formTemplateToRender.formFieldsList.forEach { formField ->
            formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
            messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                formFieldValuesMap = formFieldsValuesMap,
                formField  = formField,
                caller = logTag,
                actualLoopCount = -1,
            )
            var isResponseNeededToProceed: Boolean
            withContext(messageFormViewModel.coroutineDispatcherProvider.main()) {
                isResponseNeededToProceed = if (isOpenedFromSent) {
                    createAndAddFormControl(formField, true)
                } else
                    createAndAddFormControl(formField)
            }
            if (isResponseNeededToProceed) return
        }
        formManager.checkAndResetBranchTarget()
    }

    private fun promptOnNavigationUp() {
        context?.let { context ->
            navigateUpDialog = UiUtils.showAlertDialog(
                AlertDialogData(
                    context = context,
                    message = getString(R.string.draft_message),
                    title = getString(R.string.alert),
                    positiveActionText = getString(R.string.yes),
                    negativeActionText = getString(R.string.no),
                    isCancelable = false,
                    positiveAction = {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag Draft dialog Yes clicked")
                        messageFormViewModel.resetIsDraftView()
                        positiveActionOfAlertDialog()
                    },
                    negativeAction = {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag Draft dialog No clicked")
                        messageFormViewModel.resetIsDraftView()
                        navigateUpAndDismissDialog()
                        formManager.removeKeyForImage()
                    })
            ).also { alertDialog ->
                setAlertDialogKeyListener(alertDialog)
            }
        }
    }

    private fun positiveActionOfAlertDialog() {
        appModuleCommunicator.getAppModuleApplicationScope().launch(
            messageFormViewModel.coroutineDispatcherProvider.main() + CoroutineName(logTag)
        ) {
                if (appModuleCommunicator.doGetCid().toInt().isGreaterThanAndEqualTo(ZERO)) {
                    showSendingOrDraftingProgress(false)
                    if (isOpenedFromDraft) {
                        messageFormViewModel.deleteSavedMessageResponseOfDraft(
                            appModuleCommunicator.doGetCid(),
                            appModuleCommunicator.doGetTruckNumber(),
                            oldFormDraftedUnixTime
                        )
                    }
                    constructFormTemplateData()
                    savePreDraft()
                    requireActivity().applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, true)
                }

            formManager.removeKeyForImage()
        }
    }

    private fun setAlertDialogKeyListener(alertDialog: AlertDialog) {
        alertDialog.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && alertDialog.isShowing) {
                alertDialog.dismiss()
            }
            true
        }
    }

    private fun navigateUpAndDismissDialog() {
        if (arguments?.getBoolean(COMES_FROM_DETAIL) == true) {
            findNavControllerSafely()?.navigateTo(
                R.id.messageReplyFragment,
                R.id.action_messageReplyFragment_to_messageDetailFragment,
                bundleOf(
                    MESSAGE to arguments?.getSerializable(MESSAGE),
                    SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH to false
                )
            )
        } else {
            findNavControllerSafely()?.navigateBack(R.id.messageReplyFragment)
        }
        //Hides the custom toolbar of this fragment
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        navigateUpDialog?.dismiss()
        navigateUpDialog = null
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
        formManager.removeViews(
            viewId,
            activityFormBinding.llLeftLayout,
            activityFormBinding.llRightLayout,
            logTag
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
                            formField  = formField,
                            caller = logTag,
                            actualLoopCount = actualLoopCount,
                        )
                        val isResponseNeededToProceed: Boolean = if (isOpenedFromSent) {
                            createAndAddFormControl(formField, true)
                        } else {
                            createAndAddFormControl(formField)
                        }
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
        Log.logLifecycle(logTag, "$logTag onPause")
        navigateUpDialog?.dismiss()
        draftFormLocally()
        messageFormViewModel.shouldSaveOldState = false
        (activity as? MessagingActivity)?.unlockDrawer()
        super.onPause()
    }

    private fun savePreDraft() {
        //we cannot show the toast in the fragment because the fragment dies too fast
        draftingViewModel.showDraftMessage = true
        addAsnToFormTemplateDataIfNotEmpty(formTemplateData, asn)
        draftingViewModel.makeDraft(
            appModuleCommunicator.getAppModuleApplicationScope(),
            formTemplateData,
            isOpenedFromDraft,
            this@MessageReplyFragment.formResponseType,
            this@MessageReplyFragment.replyFormName,
            hasPredefinedRecipients,
            if (this@MessageReplyFragment.isReplyWithSame) {
                // Saving the formClass value as -1 in the PFMFormDraftResponses
                // when the Driver formId and replyFormId are same in the reply_with_new category
                -1
            } else {
                this@MessageReplyFragment.formClass
            }
        )
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        draftFormLocally()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        mapFieldIdsToViews(isAddingFields = false)
        if (::activityFormBinding.isInitialized)
            activityFormBinding.unbind()
    }

    private fun draftFormLocally() {
        if (isDTF || formTemplate.formDef.cid.isLessThan(ZERO)) return
        constructFormTemplateData()
        messageFormViewModel.draftFormLocally(formTemplateData)
    }

    private suspend fun createAndAddFormControl(
        formField: FormField,
        isFormSaved: Boolean = false
    ): Boolean {
        val textInputLayout = formManager.createTextInputLayout(
            formField.qtype,
            formId,
            formClass,
            formTemplate.formFieldsList.size,
            activity as AppCompatActivity,
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(formField = formField, isFormSaved = isOpenedFromSent)
        )
        var view: View? = null
        var isResponseNeeded = false
        formField.isInDriverForm = true
        val nextQNumAndInflationRequirement =
            FormUtils.isViewInflationRequired(formManager.branchTo, formField, formTemplate)
        val nextQNumToShow = nextQNumAndInflationRequirement.first
        formField.viewId = viewId
        Log.d(logTag, "Field to be processed, qnum : ${formField.qnum} fieldId : ${formField.fieldId} " +
                "nextQNumToShow : $nextQNumToShow inflationRequirement : ${nextQNumAndInflationRequirement.second} qType : ${formField.qtype}")
        if (nextQNumAndInflationRequirement.second) {
            when (formField.qtype) {
                FormFieldType.TEXT.ordinal ->
                    view = if (formManager.isFreeForm(
                            FormDef(formid = formId, formClass = formClass)
                        ) && formTemplate.formFieldsList.size == 1
                    ) {
                        //cff id is hardcoded in cloud function.
                        // Sometimes, the driverEditable field is Non-Editable. So, manipulating locally for enabling edit.
                        if (isOpenedFromSent) {
                            formField.driverEditable = 0
                            formField.dispatchEditable = 1
                        } else formField.driverEditable = 1
                        //driver needs to fill something in the form to move forward
                        formField.required = 1
                        // Set counter max length
                        textInputLayout.counterMaxLength =
                            formTemplate.formFieldsList[0].ffmLength
                        formField.isInDriverForm = !isOpenedFromSent
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
                                if (FormDef(formid = formId, formClass = formClass).isValidForm()) {
                                    messageFormViewModel.getFreeFormEditTextHintAndMessage(
                                        formTemplate.formFieldsList[0],
                                        ""
                                    ).also { hintAndText ->
                                        textInputLayout.hint = hintAndText.first
                                    }
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
                    isResponseNeeded = formField.multipleChoiceDriverInputNeeded()
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
        }

        if(messageFormViewModel.isOfTextInputLayoutViewType(formField = formField))
            addViewToLayout(view, nextQNumToShow, formField, textInputLayout)
        else view?.let {
            addFormComponentToLayout(formField,it)
        }

        if(isOpenedFromSent.not()){
            if(messageFormViewModel.isProcessingMultipleChoiceFieldRequired(formField,false,processMultipleChoice)) return true
        }

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
                renderForm = {  branchTargetId:Int,selectedViewId:Int,loopEndId:Int,_:Boolean, actualLoopCount : Int, _ : Int ->
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

    private var processMultipleChoice: (FormChoice?) -> Unit = {
        messageFormViewModel.processMultipleChoice(it, formTemplate)
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
        with(activityFormBinding) {
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
        Log.d(logTag, "Field rendered, fieldId : ${viewIdToFormFieldMap[view.id]?.fieldId} viewID : ${view.id}")
        with(activityFormBinding) {
            if (context?.let { context -> UiUtil.isTablet(context) } == true
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

    private fun setupToolbar() {
        context?.let { context ->
            activityFormBinding.toolbar.setTitleTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.textColor
                )
            )
        }
        with(activityFormBinding) {
            if (isOpenedFromSent) {
                btnSave.visibility = View.INVISIBLE
                btnSave.isClickable = false
            } else {
                btnSave.visibility = View.VISIBLE
                btnSave.setText(R.string.send)
                if(draftingViewModel.getSaveToDraftsFeatureFlag()) {
                    btnSaveToDrafts.visibility = View.VISIBLE
                    btnSaveToDrafts.setOnClickListener {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag Save to Drafts clicked")
                        handleCancelOrBackButtonPressAction()
                    }
                }
            }
            ivCancel.apply {
                context?.let { context ->
                    setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_back_white
                        )
                    )
                }
                setDebounceClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag Cancel clicked")
                    handleCancelOrBackButtonPressAction()
                }
            }
        }

        activityFormBinding.btnSave.setDebounceClickListener {
            Log.logUiInteractionInInfoLevel(logTag, "$logTag Send clicked")
            //If DTF, then construct new FormField list
            constructFormTemplateData()
            checkForErrorFormFields()
            formManager.removeKeyForImage()
            requireActivity().applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, false)
        }
    }

    private fun constructFormTemplateData() {
        formManager.iterateViewsToFetchDataFromFormFields(
            activityFormBinding.llLeftLayout,
            activityFormBinding.llRightLayout,
            viewIdToFormFieldMap,
            logTag
        ).let { constructedFormFieldList ->
            messageFormViewModel.constructFormFieldsWithAutoFields(this.formTemplate, constructedFormFieldList)
            formTemplateData =
                FormTemplate(
                    formTemplate.formDef,
                    constructedFormFieldList
                )
            addAsnToFormTemplateDataIfNotEmpty(formTemplateData, asn)
        }
    }


    private fun checkForErrorFormFields() {
        val errorFormFields = FormUtils.getErrorFields(formTemplateData)
        if (errorFormFields.isEmpty()) {
            if (formTemplate.formDef.recipients.isEmpty()) {
                context?.showToast(getString(R.string.form_must_have_one_recipient_at_least))
                return
            }
            if (formTemplate.formDef.cid.isGreaterThanAndEqualTo(ZERO)) {
                sendReplyMessage()
            } else {
                navigateUpAndDismissDialog()
            }

        } else {
            activity?.let { activity ->
                formManager.highlightErrorField(
                    formId,
                    formClass,
                    errorFormFields,
                    formTemplate.formFieldsList.size,
                    activityFormBinding.llLeftLayout,
                    activityFormBinding.llRightLayout,
                    activity as AppCompatActivity
                )
            }
        }
    }

    private fun sendReplyMessage() {
        lifecycleScope.launch(CoroutineName(logTag)) {
            if (isSendingOrDraftingInProgress.not()) {
                showSendingOrDraftingProgress(true)
                messageFormViewModel.isResponseDrafted.observe(viewLifecycleOwner) {
                    lifecycleScope.launch(CoroutineName(logTag)) {
                        isSendingOrDraftingInProgress = false
                        context?.showLongToast(R.string.message_sent)
                        messageFormViewModel.resetIsDraftView()
                        withContext(messageFormViewModel.coroutineDispatcherProvider.io()) {
                            if (isOpenedFromDraft) {
                                //deletes already sent draft msg
                                messageFormViewModel.deleteSavedMessageResponseOfDraft(
                                    appModuleCommunicator.doGetCid(),
                                    appModuleCommunicator.doGetTruckNumber(),
                                    oldFormDraftedUnixTime
                                )
                            }
                        }
                        navigateUpAndDismissDialog()
                    }
                }
                saveForm()
            }
        }
    }

    private suspend fun saveForm() {
        val cid = appModuleCommunicator.doGetCid()
        val vehicleNumber = appModuleCommunicator.doGetTruckNumber()
        val obcId = appModuleCommunicator.doGetObcId()
        val formDataToSave = FormDataToSave(
            formTemplateData,
            "$INBOX_FORM_RESPONSE_COLLECTION/${cid}/${vehicleNumber}",
            formTemplate.formDef.formid.toString(),
            if (isOpenedFromDraft) this@MessageReplyFragment.formResponseType
            else INBOX_FORM_RESPONSE_TYPE, replyFormName,
            formTemplate.formDef.formClass,
            cid,
            hasPredefinedRecipients, obcId,
            if (isOpenedFromDraft) draftingViewModel.unCompletedDispatchFormPath else DispatchFormPath(),
            if (isOpenedFromDraft) draftingViewModel.dispatchFormSavePath else EMPTY_STRING
        )
        // For dispatch drafted forms. Delete the draft item and save response data to FormResponses Collection to send data to pfm
        if (formResponseType == DISPATCH_FORM_RESPONSE_TYPE) {
            messageFormViewModel.saveDispatchFormResponse(
                path = draftingViewModel.dispatchFormSavePath,
                formTemplate = formTemplateData,
                formDataToSave = formDataToSave,
                isSyncToQueue = true,
                caller = logTag
            )
        } else {
            messageFormViewModel.saveFormData(
                formDataToSave
            )
        }
    }

    private fun addAsnToFormTemplateDataIfNotEmpty(formTemplateData: FormTemplate, asn: String) {
        if (this.asn.isNotEmpty() && this.asn.toLong() > 0)
            formTemplateData.asn = asn.toLong()
    }

}