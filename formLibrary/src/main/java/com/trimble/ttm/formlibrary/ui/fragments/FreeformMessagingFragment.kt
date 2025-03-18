package com.trimble.ttm.formlibrary.ui.fragments

import android.app.Activity
import android.content.Intent
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.customViews.FreeFormEditText
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.ActivityFormBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.ui.activities.ContactListActivity
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FREE_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.IS_FROM_SENT_FORM
import com.trimble.ttm.formlibrary.utils.IS_HOME_PRESSED
import com.trimble.ttm.formlibrary.utils.SELECTED_USERS_KEY
import com.trimble.ttm.formlibrary.utils.UiUtil
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
import com.trimble.ttm.formlibrary.utils.isGreaterThanAndEqualTo
import com.trimble.ttm.formlibrary.utils.isLessThan
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.utils.toSafeInt
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class FreeformMessagingFragment : Fragment(), KoinComponent {

    private val logTag = "FreeformMessagingFragment"

    private lateinit var fragmentView: View
    private var formTemplateData: FormTemplate =
        FormTemplate()
    private var formName = "New Message"
    private var formTemplate: FormTemplate =
        FormTemplate()
    private var viewId = 0
    private var viewIdToFormFieldMap = HashMap<Int, FormField>()
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val messagingViewModel: MessagingViewModel by sharedViewModel()
    private val draftingViewModel: DraftingViewModel by sharedViewModel()
    private val displayHeight
        get() = UiUtil.getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = context?.let { context -> UiUtil.convertDpToPixel(60.0f, context) } ?: 0.0f
    private val formDataStoreManager: FormDataStoreManager by inject()
    private var navigateUpDialog: AlertDialog? = null
    private var multipleInvocationLock: Boolean = false
    private val formManager: FormManager by lazy {
        FormManager()
    }

    private val appModuleCommunicator: AppModuleCommunicator by inject()

    private var isSendingOrDraftingInProgress = false
    private var isLoadingFormTemplateInProgress = false
    private var isOpenedFromSent = false


    private lateinit var activityFormBinding: ActivityFormBinding
    private var formFieldsValuesMap: HashMap<String, ArrayList<String>> = HashMap()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
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
        arguments?.getBoolean(IS_FROM_SENT_FORM)?.let {
            if (it) isOpenedFromSent = it
        }
        isLoadingFormTemplateInProgress = true
        activityFormBinding.progressErrorViewForm.setProgressState(getString(R.string.loading_text))
        multipleInvocationLock = false
        observeForNetworkConnectivityChange()
    }

    private fun observeForNetworkConnectivityChange() {
        (activity as? MessagingActivity)?.run {
            if (multipleInvocationLock.not()) {
                multipleInvocationLock = true
                if (activityFormBinding.llLeftLayout.childCount == 0 && activityFormBinding.llRightLayout.childCount == 0) loadFormTemplate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        lifecycleScope.safeLaunch(
            Dispatchers.Main + CoroutineName("$tag On resume")
        ) {
            if (isOpenedFromSent.not()) {
                formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, true)
            }
            draftingViewModel.initDraftProcessing.safeCollect(javaClass.name) {
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
                    draftingViewModel.restoreInitDraftProcessing(appModuleCommunicator.getAppModuleApplicationScope())
                    draftingViewModel.setDraftProcessAsFinished(appModuleCommunicator.getAppModuleApplicationScope())
                    draftingViewModel.showDraftMessage = false
                }
            }
        }
        lifecycleScope.safeLaunch(
            Dispatchers.Main + CoroutineName("$tag draft process finished")
        ) {
            draftingViewModel.draftProcessFinished.safeCollect(javaClass.name) {
                Log.i(logTag, "draftProcessFinished: has to draft $it")
                //we finished the draft process, we need to close the fragment
                if (it) {
                    draftingViewModel.restoreDraftProcessFinished(appModuleCommunicator.getAppModuleApplicationScope())
                }
            }
        }
        //Hides the toolbar of the MessageActivity to show custom toolbar
        activity?.findViewById<ComposeView>(R.id.toolbar)?.visibility = View.GONE
        (activity as? MessagingActivity)?.lockDrawer()
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag back button clicked")
                    handleCancelOrBackButtonPressAction()
                }
            })
    }

    private fun needToDraft(): Boolean {
        constructFormTemplateData()
        return messageFormViewModel.hasSomethingToDraft(
            formTemplateData,
            true
        )
    }

    private fun handleCancelOrBackButtonPressAction() {
        if (
            needToDraft() &&
            isLoadingFormTemplateInProgress.not() &&
            isSendingOrDraftingInProgress.not() &&
            draftingViewModel.getSaveToDraftsFeatureFlag()
        ) {
            promptOnNavigationUp()
        } else {
            navigateUpAndDismissDialog()
        }
    }

    private fun positiveActionOfAlertDialog() {
        lifecycleScope.safeLaunch(Dispatchers.Main + CoroutineName("$tag Positive action - alert dialog")) {
            if (appModuleCommunicator.doGetCid().toSafeInt().isGreaterThanAndEqualTo(ZERO)) {
                constructFormTemplateData()
                val cid = appModuleCommunicator.doGetCid()
                showSendingOrDraftingProgress(false)
                formTemplate.formDef.recipients = messageFormViewModel.selectedRecipients
                val obcId = appModuleCommunicator.doGetObcId()
                val customerId = appModuleCommunicator.doGetCid()
                val truckNumber = appModuleCommunicator.doGetTruckNumber()
                val formDataToSave = FormDataToSave(
                    formTemplateData,
                    "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/${customerId}/${truckNumber}",
                    formTemplate.formDef.formid.toString(),
                    INBOX_FREE_FORM_RESPONSE_TYPE, formName, formTemplate.formDef.formClass,cid, obcId = obcId
                )
                //we cannot show the toast in the fragment because the fragment dies to fast
                draftingViewModel.showDraftMessage = true
                draftingViewModel.makeDraft(
                    formDataToSave, logTag
                )
            }
            navigateUpAndDismissDialog()
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

    private fun loadFormTemplate() {
        setupToolbar()
        lifecycleScope.safeLaunch(Dispatchers.Main + CoroutineName("$tag Load form template")) {
            val corporateFreeForm =
                messageFormViewModel.getFreeForm(messageFormViewModel.getSavedUIDataDuringOrientationChange())
            formFieldsValuesMap = corporateFreeForm.formFieldValuesMap
            processForm(
                corporateFreeForm.formTemplate
            )
            withContext(Dispatchers.Main) {
                showRecipientSelectView()
            }
            isLoadingFormTemplateInProgress = false
        }
    }

    private fun showRecipientSelectView() {
        with(activityFormBinding) {
            llSelectRecipient.show()
            setRecipientText(messageFormViewModel.selectedUsers)
            if (messageFormViewModel.selectedRecipients.size.isLessThanAndEqualTo(ZERO))
                tvRecipients.text = getString(R.string.tap_to_select_recipients)
            tvRecipients.isEnabled = true
            tvRecipients.isClickable = true
            tvRecipients.setDebounceClickListener {
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
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            result.data?.extras?.let { bundle ->
                bundle.getParcelableArrayList<User>(SELECTED_USERS_KEY)?.let { selectedUsers ->
                    if (selectedUsers.isNotEmpty() || (selectedUsers.isEmpty() && bundle.getBoolean(
                            IS_HOME_PRESSED
                        ))
                    ) {
                        messageFormViewModel.selectedUsers = selectedUsers
                        messageFormViewModel.selectedRecipients =
                            selectedUsers.map { "${it.uID}" to it.uID }.toMap()
                        formTemplate.formDef.recipients =
                            messageFormViewModel.selectedRecipients
                        setRecipientText(selectedUsers)
                    }
                }
            }
        }

    private fun promptOnNavigationUp() {
        context?.let { context ->
            navigateUpDialog = UiUtils.showAlertDialog(
                AlertDialogData(
                    context = context,
                    message = getString(
                        R.string.draft_message
                    ),
                    title = getString(R.string.alert),
                    positiveActionText = getString(R.string.yes),
                    negativeActionText = getString(R.string.no),
                    isCancelable = false,
                    positiveAction = {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag save to draft positive action button clicked")
                        messageFormViewModel.resetIsDraftView()
                        positiveActionOfAlertDialog()
                    },
                    negativeAction = {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag save to draft negative action button clicked")
                        messageFormViewModel.resetIsDraftView()
                        navigateUpAndDismissDialog()
                    })
            ).also { alertDialog ->
                alertDialog.setOnKeyListener { _, keyCode, _ ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && alertDialog.isShowing) {
                        alertDialog.dismiss()
                    }
                    true
                }
            }
        }
    }

    private fun navigateUpAndDismissDialog() {
        findNavControllerSafely()?.navigateBack(R.id.vanillaMessagingFragment)
        //Hides the custom toolbar of this fragment
        (activity as AppCompatActivity).supportActionBar?.hide()
        navigateUpDialog?.dismiss()
        navigateUpDialog = null
    }

    private fun setRecipientText(selectedUsers: ArrayList<User>) {
        with(activityFormBinding) {
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

    private suspend fun processForm(formTemplate: FormTemplate) {
        if (formTemplate.formDef.cid.isGreaterThanAndEqualTo(ZERO)) {
            this.formTemplate = formTemplate
            withContext(Dispatchers.Main) { activityFormBinding.progressErrorViewForm.setNoState() }

            this.formTemplate.formFieldsList.forEach { formField ->
                formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
                messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                    formFieldValuesMap = formFieldsValuesMap,
                    formField  = formField,
                    caller = logTag,
                    actualLoopCount = -1,
                )
                var isResponseNeededToProceed: Boolean
                withContext(Dispatchers.Main) {
                    isResponseNeededToProceed = createAndAddFormControl(
                        formField = formField,
                        formClass = this@FreeformMessagingFragment.formTemplate.formDef.formClass
                    )
                }
                if (isResponseNeededToProceed) return
            }
            messageFormViewModel.saveFormTemplateCopy(formTemplate)
        } else withContext(Dispatchers.Main) {
            activityFormBinding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
        }
    }

    override fun onPause() {
        Log.logLifecycle(logTag, "$logTag onPause")
        (activity as? MessagingActivity)?.unlockDrawer()
        navigateUpDialog?.dismiss()
        draftFormLocally()
        super.onPause()
    }

    private fun draftFormLocally() {
        if (formTemplate.formDef.cid.isLessThan(ZERO)) return
        constructFormTemplateData()
        messageFormViewModel.draftFormLocally(formTemplateData)
    }
    private fun constructFormTemplateData() {
        formManager.iterateViewsToFetchDataFromFormFields(
            activityFormBinding.llLeftLayout,
            activityFormBinding.llRightLayout,
            viewIdToFormFieldMap,
            logTag
        ).let { constructedFormFieldList ->
            messageFormViewModel.constructFormFieldsWithAutoFields(formTemplate, constructedFormFieldList)
            formTemplateData =
                FormTemplate(
                    formTemplate.formDef,
                    constructedFormFieldList
                )
        }
    }

    private fun createAndAddFormControl(
        formField: FormField,
        isFormSaved: Boolean = false,
        formClass: Int
    ): Boolean {
        val textInputLayout = formManager.createTextInputLayout(
            qType = formField.qtype,
            formId = formField.formid,
            formClass = formClass,
            formFieldListSize = formTemplate.formFieldsList.size,
            context = requireContext(),
            isRequired = messageFormViewModel.isFormFieldRequiredAndReadOnlyView(formField = formField)
        )
        var view: View?
        val isResponseNeeded = false
        //driver needs to fill something in the form to move forward
        formField.required = 1
        // Set counter max length
        textInputLayout.counterMaxLength =
            formTemplate.formFieldsList[0].ffmLength
        formField.isInDriverForm = true
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
                messageFormViewModel.getFreeFormEditTextHintAndMessage(
                    formTemplate.formFieldsList[0],
                    EMPTY_STRING
                ).also { hintAndText ->
                    textInputLayout.hint = hintAndText.first
                    it.setText(hintAndText.second)
                }
                view = it
            }
            view?.let {
                textInputLayout.addView(view)
                addFormComponentToLayout(formField, textInputLayout)
            }
        }
        //return if response needed
        return isResponseNeeded
    }

    private fun addFormComponentToLayout(formField: FormField, view: View) {
        lifecycleScope.safeLaunch(Dispatchers.Main + CoroutineName("$tag Add forms fields to layouts")) {
            formField.viewId = viewId++
            //Map between viewId and FormField
            viewIdToFormFieldMap[formField.viewId] = formField
            view.id = formField.viewId
            view.tag = formField.viewId
            activityFormBinding.llRightLayout.visibility = View.GONE
            val llLeftLayoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            llLeftLayoutParams.weight = 2.0f
            activityFormBinding.llLeftLayout.layoutParams = llLeftLayoutParams
            activityFormBinding.llLeftLayout.addView(view)
        }
    }

    private fun setupToolbar() {
        context?.let { context ->
            activityFormBinding.toolbar.setTitleTextColor(
                ContextCompat.getColor(
                    context.applicationContext,
                    R.color.textColor
                )
            )
        }

        with(activityFormBinding) {
            tvToolbarTitle.text = formName
            btnSave.visibility = View.VISIBLE
            btnSave.setText(R.string.send)
            if(draftingViewModel.getSaveToDraftsFeatureFlag()) {
                btnSaveToDrafts.visibility = View.VISIBLE
                btnSaveToDrafts.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag save to draft button clicked")
                    handleCancelOrBackButtonPressAction()
                }
            }
            ivCancel.apply {
                context?.let { context ->
                    setImageDrawable(
                        ContextCompat.getDrawable(
                            context.applicationContext,
                            R.drawable.ic_back_white
                        )
                    )
                }
                setDebounceClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag back button clicked")
                    handleCancelOrBackButtonPressAction()
                }
            }
        }

        activityFormBinding.btnSave.setDebounceClickListener {
            Log.logUiInteractionInNoticeLevel(logTag, "$logTag send button clicked")
            lifecycleScope.safeLaunch(CoroutineName("$tag Save click")) {
                if (isSendingOrDraftingInProgress.not()) {
                    if (messageFormViewModel.selectedRecipients.isEmpty()) {
                        context?.showToast(getString(R.string.form_must_have_one_recipient_at_least))
                        return@safeLaunch
                    }
                    checkForErrorFormFields()
                }
            }
        }
    }

    private fun checkForErrorFormFields() {
        lifecycleScope.safeLaunch(CoroutineName("$tag Check form fields errors")) {
            constructFormTemplateData()
            val errorFormFields = FormUtils.getErrorFields(formTemplateData)
            if (errorFormFields.isEmpty()) {
                if (appModuleCommunicator.doGetCid().toSafeInt().isGreaterThanAndEqualTo(ZERO)) {
                    val cid = appModuleCommunicator.doGetCid()
                    val vehicleNumber = appModuleCommunicator.doGetTruckNumber()
                    showSendingOrDraftingProgress(true)
                    messageFormViewModel.isResponseDrafted.observe(viewLifecycleOwner) {
                        isSendingOrDraftingInProgress = false
                        activityFormBinding.progressErrorViewForm.setNoState()
                        context?.showLongToast(R.string.message_sent)
                        messageFormViewModel.resetIsDraftView()
                        findNavControllerSafely()?.navigateTo(
                            R.id.vanillaMessagingFragment,
                            R.id.action_vanillaMessagingFragment_to_messageViewPagerContainerFragment
                        )
                    }
                    formTemplate.formDef.recipients = messageFormViewModel.selectedRecipients
                    val obcId = appModuleCommunicator.doGetObcId()
                    val formDataToSave = FormDataToSave(
                        formTemplateData,
                        "$INBOX_FORM_RESPONSE_COLLECTION/${cid}/${vehicleNumber}",
                        formTemplate.formDef.formid.toString(),
                        INBOX_FREE_FORM_RESPONSE_TYPE,
                        formName,
                        formTemplate.formDef.formClass,
                        cid,
                        obcId = obcId
                    )
                    messageFormViewModel.saveFormData(
                        formDataToSave
                    )
                } else {
                    findNavControllerSafely()?.navigateTo(
                        R.id.vanillaMessagingFragment,
                        R.id.action_vanillaMessagingFragment_to_messageViewPagerContainerFragment
                    )
                }
            } else {
                activity?.let { activity ->
                    formManager.highlightErrorField(
                        formId = formTemplate.formDef.formid,
                        formClass = formTemplate.formDef.formClass,
                        errorFormFields,
                        formTemplate.formFieldsList.size,
                        activityFormBinding.llLeftLayout,
                        activityFormBinding.llRightLayout,
                        activity as AppCompatActivity
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
    }

}