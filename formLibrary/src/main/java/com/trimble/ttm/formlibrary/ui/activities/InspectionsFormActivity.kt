package com.trimble.ttm.formlibrary.ui.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.logger.INSPECTION_FLOW
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.model.AuthenticationState
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.model.isValidForm
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.AUTH_DEVICE_ERROR
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
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.model.EDVIRFormData
import com.trimble.ttm.formlibrary.utils.ANNOTATION_INVALID_REGEX
import com.trimble.ttm.formlibrary.utils.DRIVER_ACTION
import com.trimble.ttm.formlibrary.utils.DRIVER_ID
import com.trimble.ttm.formlibrary.utils.DRIVER_NAME
import com.trimble.ttm.formlibrary.utils.EDVIRFormUtils.createAnnotationText
import com.trimble.ttm.formlibrary.utils.EDVIRFormUtils.isAnnotationValid
import com.trimble.ttm.formlibrary.utils.EDVIR_INSPECTION_STATUS
import com.trimble.ttm.formlibrary.utils.EDVIR_INSPECTION_STATUS_FAILURE
import com.trimble.ttm.formlibrary.utils.EDVIR_INSPECTION_STATUS_MESSAGE
import com.trimble.ttm.formlibrary.utils.EDVIR_INSPECTION_STATUS_SUCCESS
import com.trimble.ttm.formlibrary.utils.EFS_ADD_ANNOTATION
import com.trimble.ttm.formlibrary.utils.EFS_ADD_ANNOTATION_SUCCESSFULL
import com.trimble.ttm.formlibrary.utils.EFS_ANNOTATION_TEXT_KEY
import com.trimble.ttm.formlibrary.utils.EFS_DRIVER_ID_KEY
import com.trimble.ttm.formlibrary.utils.EFS_DRIVER_NOT_SIGNED_IN
import com.trimble.ttm.formlibrary.utils.EFS_FAILED_ANNOTATION_INVALID
import com.trimble.ttm.formlibrary.utils.EFS_FAILED_ANNOTATION_LOG_EVENT_CHANGED
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_CLASS
import com.trimble.ttm.formlibrary.utils.FORM_ID
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.INSPECTION_CREATED_AT_KEY
import com.trimble.ttm.formlibrary.utils.INVALID_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.INVALID_FORM_ID
import com.trimble.ttm.formlibrary.utils.IS_INSPECTION_FORM_VIEW_ONLY_KEY
import com.trimble.ttm.formlibrary.utils.MANDATORY_EDVIR_INSPECTION_RESULT_INTENT_ACTION
import com.trimble.ttm.formlibrary.utils.MANDATORY_EDVIR_INTENT_ACTION
import com.trimble.ttm.formlibrary.utils.SOURCE_ACTIVITY_KEY
import com.trimble.ttm.formlibrary.utils.UiUtil.convertDpToPixel
import com.trimble.ttm.formlibrary.utils.UiUtil.getDisplayHeight
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.Utils.scheduleOneTimeImageUploadIfImagesAvailable
import com.trimble.ttm.formlibrary.utils.VEHICLE_DSN
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.setDebounceClickListener
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.utils.getInspectionTypeUIText
import com.trimble.ttm.formlibrary.utils.isLessThan
import com.trimble.ttm.formlibrary.utils.toInspectionFormDef
import com.trimble.ttm.formlibrary.viewmodel.EDVIRFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.IOException
import java.util.Calendar

open class InspectionsFormActivity : AppCompatActivity(), KoinComponent {

    private val tag = "EDVIRFormAct"
    private val driverIdLogKey = "driver id"
    private val driverNameLogKey = "driver name"
    private var formTemplateData: FormTemplate = FormTemplate()
    private var formDef: FormDef = FormDef(formid = -1, formClass = -1)
    private var dialog: AlertDialog? = null
    private var isSyncToQueue = false
    private var isDTF = false
    private var formTemplate: FormTemplate = FormTemplate()
    private var viewId = 0
    private var formTemplateSerializedString: String = EMPTY_STRING
    private var viewIdToFormFieldMap = HashMap<Int, FormField>()
    private var driverId = ""
    private var driverName = ""
    private var inspectionType = ""
    private var vehicleDSN = ""
    private var isFormMandatory: Boolean = false
    private var alertDialog: AlertDialog? = null
    private val freeFormMaxCharLength = 2000
    private var annotationText = ""
    private var isFormSaved = false
    private val formDataStoreManager: FormDataStoreManager by inject()
    private val edvirFormViewModel: EDVIRFormViewModel by viewModel { parametersOf(this.hashCode()) }
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val displayHeight
        get() = getDisplayHeight()
    private val toolbarHeightInPixels: Float
        get() = convertDpToPixel(60.0f, this)
    private val formManager: FormManager by lazy {
        FormManager()
    }
    private var processMultipleChoice: (FormChoice?) -> Unit = {
        messageFormViewModel.processMultipleChoice(it, formTemplate)
    }
    private val mapFieldIdsToViews = mutableMapOf<Int, View>()
    private lateinit var binding: ActivityFormBinding
    private var drivers = mutableSetOf<String>()

    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private var formFieldsValuesMap: HashMap<String, ArrayList<String>> = HashMap()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        Log.d(tag, "is orientation Landscape : ${resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE}")
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
            intent?.extras.let { bundle ->
                lifecycleScope.launch(CoroutineName(tag)) {
                    if (bundle == null) {
                        binding.btnSave.visibility = View.INVISIBLE
                        binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                        Log.e(
                            tag, "Bundle is null", null,
                            "customer id" to appModuleCommunicator.doGetCid(),
                            "vehicle id" to appModuleCommunicator.doGetTruckNumber(),
                            "obc id" to appModuleCommunicator.doGetObcId()
                        )
                        finish()
                    } else {
                        bundle.getString(DRIVER_ACTION)
                            ?.let { inspectionType ->
                                this@InspectionsFormActivity.inspectionType = inspectionType
                            }
                        //we get the driver's id from backbone to reduce the coupling with
                        // appLaunchers intent information
                        getDriverIdFromBackboneOrBundle(bundle)
                        setToolbarTitle()
                        edvirFormViewModel.getCurrentDrivers().let {
                            drivers = it.toMutableSet()
                        }
                        isFormMandatory = isFormMandatory(bundle)
                        if (intent?.action == MANDATORY_EDVIR_INTENT_ACTION && isFormMandatory) {
                            acquireDockMode()
                            if (appModuleCommunicator.isFirebaseAuthenticated()) isEDVIRRequired()
                            else {
                                observeForNetworkConnectivityChange()
                            }
                        } else showEDVIRForm()
                    }
                }
            }
        }
        edvirFormViewModel.observeInspectionAlertDialog()
        observeAndRenderForm()
    }

    private fun observeAndRenderForm(){
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
                        it.actualLoopCount
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        edvirFormViewModel.shouldShowDialog.observe(this) {
            if (it) {
                createAndShowAlertDialog(true)
            }
        }
    }

    private suspend fun observeForNetworkConnectivityChange() {
        edvirFormViewModel.listenToNetworkConnectivityChange()
            .collectLatest { isAvailable ->
                if (isAvailable && (binding.progressErrorViewForm.currentState == STATE.ERROR || binding.progressErrorViewForm.currentState == STATE.NONE))
                    authenticate()
                else
                    binding.progressErrorViewForm.setErrorState(getString(R.string.no_internet_authentication_failed))
            }
    }

    private fun isFormMandatory(bundle: Bundle): Boolean {
        val srcActivityName = bundle.getString(SOURCE_ACTIVITY_KEY)
        return when {
            srcActivityName == null -> {
                true
            }
            else -> {
                //fallback- if there is an issue in dock mode release in turn resetting datastore key, opening inspection form will reset the key. calling these 2 functions wont create any performance overhead issues
                edvirFormViewModel.setMandatoryInspection(false)
                messageFormViewModel.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)
                false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let { mIntent ->
            mIntent.extras?.let { bundle ->
                isFormMandatory = isFormMandatory(bundle)
            }
            if (mIntent.action == MANDATORY_EDVIR_INTENT_ACTION && isFormMandatory) {
                acquireDockMode()
            }
        }
    }

    private fun authenticate() {
        edvirFormViewModel.authenticationState.observe(this) { authenticationState ->
            when (authenticationState) {
                is AuthenticationState.Loading -> binding.progressErrorViewForm.setProgressState(
                    getString(R.string.authenticate_progress_text)
                )
                is AuthenticationState.Error -> {
                    authenticationState.errorMsgStringRes.let { errorStr ->
                        binding.progressErrorViewForm.setErrorState(if (errorStr == AUTH_DEVICE_ERROR)getString(R.string.device_authentication_failure) else getString(R.string.firestore_authentication_failure))

                        Log.e(
                            tag, "AuthenticationState Error$errorStr")
                    }
                }
                is AuthenticationState.FirestoreAuthenticationSuccess -> {
                    lifecycleScope.launch(CoroutineName(tag)) {
                        //Used separate async job to get their work done
                        val fetchAndRegisterFcmDeviceSpecificTokenJob = async {
                                edvirFormViewModel.fetchAndRegisterFcmDeviceSpecificToken()
                            }

                            val getFeatureFlagUpdatesJob = async {
                                edvirFormViewModel.getFeatureFlagUpdates()
                            }
                        //Once all the the jobs are done, then next set of code will be executed
                        listOf(fetchAndRegisterFcmDeviceSpecificTokenJob, getFeatureFlagUpdatesJob).awaitAll()

                        edvirFormViewModel.startForegroundService()

                        if (intent?.action == MANDATORY_EDVIR_INTENT_ACTION && isFormMandatory) isEDVIRRequired()
                        else showEDVIRForm()
                        if (intent?.action == MANDATORY_EDVIR_INTENT_ACTION) {
                            with(formDataStoreManager) {
                                this.setValue(
                                    FormDataStoreManager.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU,
                                    true
                                )
                            }
                        }
                    }
                }
            }
        }
        edvirFormViewModel.doAuthentication("Inspection")
    }

    private fun isEDVIRRequired() {
        lifecycleScope.launch(CoroutineName(tag)) {
            if (edvirFormViewModel.isInspectionRequired(
                    edvirFormViewModel.getProperTypeByDriverAction(inspectionType)
                )
            ) {
                showEDVIRForm()
            } else {
                edvirFormViewModel.getPreviousAnnotation(
                    edvirFormViewModel.getProperTypeByDriverAction(inspectionType)
                ).let {
                    Log.i(
                        INSPECTION_FLOW,
                        "Previous Annotation text:$annotationText for inspection type:$inspectionType"
                    )
                    if (it.isNotEmpty()) {
                        this@InspectionsFormActivity.annotationText = it
                        sendAnnotation(annotationText, driverId)
                    }
                }
                appModuleCommunicator.getAppModuleApplicationScope()
                    .launch(CoroutineName(tag)) {
                        edvirFormViewModel.inspectionCompleted(
                            edvirFormViewModel.getProperTypeByDriverAction(inspectionType),
                            isFormMandatory
                        )
                    }
            }
        }
    }

    private fun showEDVIRForm() {
        binding.progressErrorViewForm.setProgressState(getString(R.string.loading_text))
        setUpToolbar()
        binding.btnSave.visibility = View.INVISIBLE
        intent?.extras?.let { bundle ->
            bundle.getString(DRIVER_ACTION)
                ?.let { inspectionType -> this.inspectionType = inspectionType }
            setToolbarTitle()
            isFormSaved = bundle.getBoolean(IS_INSPECTION_FORM_VIEW_ONLY_KEY, false)
            if (isFormSaved) {
                onOpenedInViewOnlyMode(bundle)
            } else {
                getDriverIdFromBackboneOrBundle(bundle)
                driverName = edvirFormViewModel.getCurrentDriverName(driverId)
                if (driverName.isEmpty()) {
                    bundle.getString(DRIVER_NAME)?.let { name -> driverName = name }
                }
                bundle.getString(VEHICLE_DSN)?.let { dsn -> vehicleDSN = dsn }
                if (driverId.isNotEmpty() || vehicleDSN.isNotEmpty() || inspectionType.isNotEmpty()) {
                    if (isFormMandatory) {
                        // Hide cancel button for mandatory forms
                        binding.ivCancel.visibility = View.INVISIBLE
                        formDef = bundle.toInspectionFormDef()
                        if (formDef.isValidForm()) {
                            lifecycleScope.launch(CoroutineName(tag)) {
                                edvirFormViewModel.hasValidBackboneData { hasValidData, customerId ->
                                    if (hasValidData)
                                        fetchAndRenderForm(
                                            customerId,
                                            messageFormViewModel.getSavedUIDataDuringOrientationChange(),
                                            true
                                        )
                                    else {
                                        binding.btnSave.visibility = View.INVISIBLE
                                        binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                                        Log.e(
                                            tag,
                                            "Mandatory Inspection - ${
                                                edvirFormViewModel.getProperTypeByDriverAction(
                                                    inspectionType
                                                )
                                            }. Form could not be displayed. Customer id is empty"
                                        )
                                        sendEDVIRResultOnError(
                                            getString(
                                                R.string.err_insp_could_not_be_initiated, getString(
                                                    R.string.err_insp_customer_id_empty
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            binding.btnSave.visibility = View.INVISIBLE
                            binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                            Log.e(
                                tag,
                                "Mandatory Inspection - ${
                                    edvirFormViewModel.getProperTypeByDriverAction(
                                        inspectionType
                                    )
                                }. Invalid form. Form id ${formDef.formid}. Form class ${formDef.formClass}"
                            )
                            sendEDVIRResultOnError(
                                getString(
                                    R.string.err_insp_could_not_be_initiated, getString(
                                        R.string.err_insp_invalid_form
                                    )
                                )
                            )
                        }
                    } else {
                        lifecycleScope.launch(CoroutineName(tag)) {
                            formDef = edvirFormViewModel.getFormIdForInspection(
                                appModuleCommunicator.doGetCid(),
                                appModuleCommunicator.doGetObcId(),
                                edvirFormViewModel.getProperTypeByDriverAction(inspectionType),
                                messageFormViewModel.getSavedUIDataDuringOrientationChange()
                            )
                            if (formDef.isValidForm()) {
                                edvirFormViewModel.hasValidBackboneData { hasValidData, customerId ->
                                    if (hasValidData)
                                        fetchAndRenderForm(
                                            customerId,
                                            messageFormViewModel.getSavedUIDataDuringOrientationChange(),
                                            false
                                        )
                                    else {
                                        binding.btnSave.visibility = View.INVISIBLE
                                        binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                                        Log.e(
                                            tag,
                                            "Manual Inspection. Form could not be displayed. Customer id is empty"
                                        )
                                        sendEDVIRResultOnError(
                                            getString(
                                                R.string.err_insp_could_not_be_initiated,
                                                getString(
                                                    R.string.err_insp_invalid_form
                                                )
                                            )
                                        )
                                    }
                                }
                            } else {
                                binding.btnSave.visibility = View.INVISIBLE
                                binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                                Log.e(
                                    tag,
                                    "Manual Inspection. Form could not be displayed. Invalid form. Form id ${formDef.formid}. Form class ${formDef.formClass}"
                                )
                                sendEDVIRResultOnError(
                                    getString(
                                        R.string.err_insp_could_not_be_initiated, getString(
                                            R.string.err_insp_invalid_form
                                        )
                                    )
                                )
                            }
                        }
                    }
                } else {
                    binding.btnSave.visibility = View.INVISIBLE
                    binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                    Log.e(tag, "Driver id or vehicle id or inspection type is empty")
                    sendEDVIRResultOnError(
                        getString(
                            R.string.err_insp_could_not_be_initiated, getString(
                                R.string.err_insp_driver_vehicle_details_insp_type_empty
                            )
                        )
                    )
                }
            }
        }
    }

    private fun getDriverIdFromBackboneOrBundle(bundle: Bundle) {
        driverId = edvirFormViewModel.getCurrentDriverId()
        if (driverId.isEmpty()) {
            bundle.getString(DRIVER_ID)?.let { id -> driverId = id }
        }
    }

    private fun setToolbarTitle() {
        if (inspectionType.isNotEmpty()) {
            binding.tvToolbarTitle.text = edvirFormViewModel.getProperTypeByDriverAction(inspectionType)
                .getInspectionTypeUIText(this)
        }
    }

    override fun onPause() {
        Log.logLifecycle(tag, "$tag onPause")
        draftFormLocally()
        super.onPause()
        (application as? AppModuleCommunicator)?.setIsInManualInspectionScreen(
            false
        )
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(tag)) {
            formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, false)
        }
        (application as? AppModuleCommunicator)?.setIsInManualInspectionScreen(
            true
        )
    }

    private fun onOpenedInViewOnlyMode(bundle: Bundle) {
        formDef = bundle.toInspectionFormDef()
        var inspectionRespCreatedAt = bundle.getString(INSPECTION_CREATED_AT_KEY)
        if (inspectionRespCreatedAt.isNullOrEmpty()) inspectionRespCreatedAt = ""
        lifecycleScope.launch(CoroutineName(tag)) {
            if (formDef.isValidForm().not() || inspectionRespCreatedAt.isEmpty()) {
                binding.btnSave.visibility = View.INVISIBLE
                binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                Log.e(
                    tag,
                    "Form could not be displayed. Company id ${appModuleCommunicator.doGetCid()} Vehicle id ${appModuleCommunicator.doGetTruckNumber()} Form id ${formDef.formid} Form class ${formDef.formClass} Inspection created date $inspectionRespCreatedAt"
                )
                return@launch
            }
            val formResponse = edvirFormViewModel.getEDVIRFormDataResponse(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId(),
                inspectionRespCreatedAt
            )
            edvirFormViewModel.hasValidBackboneData { hasValidData, cId ->
                if (hasValidData)
                    fetchAndRenderForm(cId, formResponse, false)
                else {
                    Log.e(
                        tag,
                        "Saved inspection form opened in view only mode. Form could not be displayed. Customer id is empty"
                    )
                    binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                }
            }
        }
    }

    private fun fetchAndRenderForm(
        customerId: String,
        uiFormResponse: UIFormResponse,
        isMandatoryInspection: Boolean
    ) {
        lifecycleScope.launch(CoroutineName(tag)) {
            isSyncToQueue = messageFormViewModel.checkIfFormIsSaved(uiFormResponse)
            edvirFormViewModel.fetchFormToBeRendered(customerId, formDef, isMandatoryInspection,
                if (isSyncToQueue) {uiFormResponse}
                    else { messageFormViewModel.getSavedUIDataDuringOrientationChange()
                    }, shouldFillUiResponse = true, savedImageUniqueIdentifierToValueMap = messageFormViewModel.getLocallyCachedImageUniqueIdentifierToValueMap()).let { form ->
                    if (messageFormViewModel.checkIfFormIsValid(form.formTemplate)) {
                        Log.d(
                            tag, "FormTemplate $form.formTemplate.",
                            throwable = null,
                            "customer id" to appModuleCommunicator.doGetCid(),
                            "obc id" to appModuleCommunicator.doGetObcId(),
                            "vehicle number" to appModuleCommunicator.doGetTruckNumber(),
                            "form id" to formDef.formid,
                            "form class" to formDef.formClass
                        )
                        //Process
                        this@InspectionsFormActivity.formTemplate = form.formTemplate
                        formFieldsValuesMap = form.formFieldValuesMap
                        isDTF = messageFormViewModel.checkIfFormIsDtf(formTemplate)
                        formTemplateSerializedString =
                            messageFormViewModel.serializeFormTemplate(formTemplate)
                        binding.progressErrorViewForm.setNoState()
                        if (isDTF) {
                            formManager.restrictOrientationChange(this@InspectionsFormActivity)
                        }
                        if (isSyncToQueue) {
                            binding.btnSave.visibility = View.INVISIBLE
                        } else
                            binding.btnSave.visibility = View.VISIBLE
                        val formTemplateToRender: FormTemplate =
                            messageFormViewModel.fetchFormTemplate(
                                formTemplateSerializedString
                            )
                        formTemplateToRender.formFieldsList.forEach { formField ->
                            formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
                            messageFormViewModel.setDataFromDefaultValueOrFormResponses(
                                formFieldValuesMap = formFieldsValuesMap,
                                formField  = formField,
                                caller = tag,
                                actualLoopCount = -1,
                            )
                            val isResponseNeededToProceed =
                                createAndAddFormControl(formField, isSyncToQueue)
                            if (isResponseNeededToProceed) {
                                return@let
                            }
                        }
                        formManager.checkAndResetBranchTarget()
                    } else {
                        binding.btnSave.visibility = View.INVISIBLE
                        binding.progressErrorViewForm.setErrorState(getString(R.string.form_not_displayed))
                        Log.e(tag, "Invalid FormTemplate. $formTemplate")
                        sendEDVIRResultOnError(
                            getString(
                                R.string.err_insp_could_not_be_initiated, getString(
                                    R.string.err_insp_invalid_form
                                )
                            )
                        )
                    }
                }
        }
    }

    private fun acquireDockMode() {
        intent?.extras?.let { bundle ->
            edvirFormViewModel.setMandatoryInspection(true)
            edvirFormViewModel.setDockMode(bundle)
        }
    }

    private fun releaseDockMode() {
        edvirFormViewModel.setMandatoryInspection(false)
        edvirFormViewModel.releaseDockMode()
        messageFormViewModel.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)
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
                        val isResponseNeededToProceed =
                            createAndAddFormControl(formField, isSyncToQueue)
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
        formDef.let { formDef ->
            outState.putInt(FORM_ID, formDef.formid)
            outState.putInt(FORM_CLASS, formDef.formClass)
        }
        outState.putString(DRIVER_ID, driverId)
        outState.putString(VEHICLE_DSN, vehicleDSN)
        outState.putString(DRIVER_ACTION, inspectionType)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        var formId: Int
        var formClass: Int
        savedInstanceState.getInt(FORM_ID, INVALID_FORM_ID).let {
            formId = it
        }
        savedInstanceState.getInt(FORM_CLASS, INVALID_FORM_CLASS).let {
            formClass = it
        }
        formDef = FormDef(formid = formId, formClass = formClass)
        driverId = savedInstanceState.getString(DRIVER_ID).toString()
        vehicleDSN = savedInstanceState.getString(VEHICLE_DSN).toString()
        inspectionType = savedInstanceState.getString(DRIVER_ACTION).toString()
    }
    private fun constructFormTemplateData(){
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

    private fun setUpToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setTitleTextColor(
            ContextCompat.getColor(
                applicationContext,
                R.color.textColor
            )
        )
        binding.ivCancel.setOnClickListener {
            Log.logUiInteractionInInfoLevel(tag, "$tag form cancel button clicked")
            onBackPressed()
        }
        binding.btnSave.setDebounceClickListener {
            Log.logUiInteractionInNoticeLevel(tag, "$tag form save button clicked")
            constructFormTemplateData()
            val errorFormFields = FormUtils.getErrorFields(formTemplateData)
            if (errorFormFields.isEmpty()) {
                formTemplateData.let { formTemplate ->
                    if (formDef.isValidForm()) {
                        appModuleCommunicator.getAppModuleApplicationScope().launch(messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(tag)) {
                            val cid = appModuleCommunicator.doGetCid()
                            val obcId = appModuleCommunicator.doGetObcId()
                            edvirFormViewModel.saveEDVIRFormData(
                                EDVIRFormData(
                                    formTemplate,
                                    cid,
                                    obcId,
                                    driverName,
                                    formDef.formid,
                                    formDef.formClass,
                                    edvirFormViewModel.getProperTypeByDriverAction(inspectionType),
                                    true
                                ), Calendar.getInstance().time
                            )
                            edvirFormViewModel.updateInspectionInformation(
                                edvirFormViewModel.getProperTypeByDriverAction(inspectionType)
                            )
                            applicationContext.scheduleOneTimeImageUploadIfImagesAvailable(formTemplateData, false)
                        }
                    } else {
                        Log.e(tag, "Form id is invalid while saving form data")
                    }
                }
                //TODO add failure case for annotation regex
                createAnnotationText(
                    edvirFormViewModel.getProperTypeByDriverAction(inspectionType),
                    driverId,
                    vehicleDSN
                ).let { annotationText ->
                    if (isAnnotationValid(
                            ANNOTATION_INVALID_REGEX,
                            annotationText
                        )
                    ) {
                        lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.io() + CoroutineName(tag)) {
                            edvirFormViewModel.updatePreviousAnnotationInformation(
                                edvirFormViewModel.getProperTypeByDriverAction(inspectionType),
                                annotationText
                            )
                            sendAnnotation(annotationText, driverId)
                        }
                    } else {
                        Log.e(
                            tag,
                            "Inspection failed - Invalid annotation text $annotationText"
                        )
                        sendEDVIRResult(
                            EDVIR_INSPECTION_STATUS_FAILURE,
                            "Inspection failed due to invalid annotation.Possible reason could be invalid regex or annotation text exceeds max char limit."
                        )
                    }
                }
            } else {
                dialog = null
                formManager.highlightErrorField(
                    formDef.formid,
                    formDef.formClass,
                    errorFormFields,
                    formTemplate.formFieldsList.size,
                    binding.llLeftLayout,
                    binding.llRightLayout,
                    this
                )
            }
        }
    }

    private fun sendAnnotation(annotationText: String, driverId: String) {
        Log.i(INSPECTION_FLOW, "annotation: $annotationText driverId: $driverId")
        this@InspectionsFormActivity.annotationText = annotationText
        try {
            startForResult.launch(
                Intent(EFS_ADD_ANNOTATION).apply {
                    putExtra(EFS_DRIVER_ID_KEY, driverId)
                    putExtra(
                        EFS_ANNOTATION_TEXT_KEY,
                        annotationText
                    )
                }
            )
        } catch (ex: ActivityNotFoundException) {
            Log.i(tag, "Application not installed : ${intent.action}")
        }
    }

    private fun draftFormLocally() {
        if (isDTF || formTemplate.formDef.cid.isLessThan(ZERO)) return
        constructFormTemplateData()
        messageFormViewModel.draftFormLocally(formTemplateData)
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
        if (isChangingConfigurations.not()) formManager.removeKeyForImage()
        alertDialog?.dismiss() //Preventing window leak
    }

    private suspend fun createAndAddFormControl(
        formField: FormField,
        isFormSaved: Boolean
    ): Boolean {
        val textInputLayout = formManager.createTextInputLayout(
            formField.qtype,
            formDef.formid,
            formDef.formClass,
            formTemplate.formFieldsList.size,
            this,
            messageFormViewModel.isFormFieldRequiredAndReadOnlyView(formField = formField, isFormSaved = isFormSaved)
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
        if(formField.qtype == FormFieldType.IMAGE_REFERENCE.ordinal) {
            restoreImageData(formField)
        }

        if (nextQNumAndInflationRequirement.second) {
            when (formField.qtype) {
                FormFieldType.TEXT.ordinal ->
                    view = if (formManager.isFreeForm(formDef)) {
                        // Set counter max length
                        textInputLayout.counterMaxLength = freeFormMaxCharLength
                        FreeFormEditText(
                            this,
                            textInputLayout,
                            formField,
                            isFormSaved,
                            (displayHeight - toolbarHeightInPixels.toInt()) / 2
                        ).apply {
                            filters = arrayOf(InputFilter.LengthFilter(freeFormMaxCharLength))
                        }
                    } else
                        FormEditText(this, textInputLayout, formField, isFormSaved)
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
                    isResponseNeeded=formField.multipleChoiceDriverInputNeeded()

                }
                else -> {
                    view = formManager.createViewWithFormField(
                        formField = formField,
                        stack = messageFormViewModel.getFormFieldStack(),
                        context = this,
                        textInputLayout = textInputLayout,
                        isFormSaved = isFormSaved,
                        stopId = ZERO,
                        lifecycleScope = lifecycleScope,
                        supportFragmentManager = supportFragmentManager
                    ) {
                        messageFormViewModel.getFormFieldCopy(formField)
                    }
                }
            }
        }

        if(messageFormViewModel.isOfTextInputLayoutViewType(formField = formField))
            addViewToLayout(view, nextQNumToShow, formField, textInputLayout)
        else view?.let {
            addFormComponentToLayout(formField,view)
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
                renderForm = { branchTargetId: Int, selectedViewId: Int, loopEndId: Int, _: Boolean, actualLoopCount : Int, currentLoopCount : Int ->
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
        return isResponseNeeded
    }

    private suspend fun restoreImageData(formField: FormField){
        if(formField.uiData.isEmpty()){
            formField.uiData = messageFormViewModel.getEncodedImage(formField.uniqueIdentifier)
        }
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

    private fun addFormComponentToLayout(formField: FormField, view: View) {
        formField.viewId = viewId++
        //Map between viewId and FormField
        viewIdToFormFieldMap[formField.viewId] = formField
        formField.formChoiceList?.forEach {
            it.viewId = formField.viewId
        }
        view.id = formField.viewId
        view.tag = formField.viewId

        checkAndStoreMultipleChoiceFormFieldStack(formField, view)

        if (formManager.isFreeForm(formDef)) {
            addFreeFormView(view)
        } else {
            checkForOrientationAndAddFields(view)
            mapFieldIdsToViews[view.id] = view
            formManager.setNextActionInKeypad(mapFieldIdsToViews, this)
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
        binding.llRightLayout.visibility = GONE
        val llLeftLayoutParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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

    private fun sendEDVIRResult(status: String, message: String) {
        Log.i(tag, "status: $status message: $message isMandatory: $isFormMandatory")
        if (isFormMandatory) {
            try {
                Intent().apply {
                    action = MANDATORY_EDVIR_INSPECTION_RESULT_INTENT_ACTION
                    putExtra(EDVIR_INSPECTION_STATUS, status)
                    putExtra(EDVIR_INSPECTION_STATUS_MESSAGE, message)
                }.also {
                    releaseDockMode()
                    sendBroadcast(it)
                    finishAndRemoveTask()
                }
            } catch (e: Exception) {
                Log.e(
                    tag,
                    Utils.getIntentSendErrorString(
                        this,
                        "impossible to send a EDVIRResult"
                    ),
                    e
                )
            }
        } else {
            lifecycleScope.launch(CoroutineName(tag)) {
                showToast(message)
                finish()
            }
        }
    }

    override fun onBackPressed() {
        if (binding.progressErrorViewForm.currentState == STATE.ERROR) {
            if (isFormMandatory.not()) {
                Log.e(
                    tag,
                    "Triggered from EDVIRInspectionsActivity - Form could not be displayed error - onBackPress event"
                )
                super.onBackPressed()
            } else {
                Log.e(
                    tag,
                    "Triggered from App Launcher - Form could not be displayed error - onBackPress event"
                )
                // If triggered from app launcher
                sendEDVIRResult(
                    EDVIR_INSPECTION_STATUS_FAILURE,
                    "Inspection failed.Possible reason could be back button press."
                )
            }
        } else {
            if (isSyncToQueue || intent?.extras?.getBoolean(
                    IS_INSPECTION_FORM_VIEW_ONLY_KEY,
                    false
                )!!
            )
                super.onBackPressed()
            else {
                createAndShowAlertDialog(isFormMandatory)
            }
        }
    }

    private fun createAndShowAlertDialog(isMandatoryInspection: Boolean) {
        val messageToShow: String
        val positiveActionText: String
        val negativeActionText: String

        if (isMandatoryInspection.not()) {
            messageToShow =
                getString(R.string.manual_inspection_complete_dialog_prompt_text)
            positiveActionText = getString(R.string.yes)
            negativeActionText = getString(R.string.no)
        } else {
            messageToShow =
                getString(R.string.mandatory_inspection_complete_dialog_prompt_text)
            positiveActionText = getString(R.string.ok_text)
            negativeActionText = ""
        }

        alertDialog = UiUtils.showAlertDialog(
            AlertDialogData(
                context = this,
                message = messageToShow,
                positiveActionText = positiveActionText,
                negativeActionText = negativeActionText,
                isCancelable = false,
                positiveAction = {
                    Log.logUiInteractionInNoticeLevel(tag, "$tag inspection form dialog positive button clicked")
                    onPositiveClick()
                },
                negativeAction = { onNegativeClick() })
        )
    }

    private fun writeAnnotationForDriver(dId: String) {
        Log.i(tag, "annotation: $dId driverId: $driverId drivers: ${drivers.count()}")
        drivers.remove(dId)
        if (drivers.isNotEmpty()) {
            driverId = drivers.elementAt(0)
            sendAnnotation(annotationText, driverId)
        } else {
            appModuleCommunicator.getAppModuleApplicationScope()
                .launch(CoroutineName(tag)) {
                    edvirFormViewModel.inspectionCompleted(
                        edvirFormViewModel.getProperTypeByDriverAction(inspectionType), isFormMandatory
                    )
                }
            sendEDVIRResult(
                EDVIR_INSPECTION_STATUS_SUCCESS,
                getString(R.string.inspection_success)
            )
        }
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            when (result.data?.action) {
                EFS_ADD_ANNOTATION_SUCCESSFULL -> {
                    isSyncToQueue = true
                    Log.i(
                        tag,
                        "Inspection success for driver: $driverId",
                        throwable = null,
                        "annotation" to annotationText,
                        driverIdLogKey to driverId,
                        driverNameLogKey to driverName
                    )
                    for (i in 1..4) {
                        try {
                            writeAnnotationForDriver(driverId)
                            break
                        } catch (e: Exception) {
                            if (e !is IOException) break
                            Thread.sleep(200)
                        }
                    }
                }
                EFS_DRIVER_NOT_SIGNED_IN -> {
                    Log.e(
                        tag,
                        "Inspection failed.Driver not signed in to eFleetSuite. Driver: $driverId",
                        null,
                        "annotation" to annotationText,
                        driverIdLogKey to driverId,
                        driverNameLogKey to driverName
                    )
                    sendEDVIRResult(
                        EDVIR_INSPECTION_STATUS_FAILURE,
                        getString(
                            R.string.inspection_failed,
                            getString(R.string.driver_not_signed_in_efs)
                        )
                    )
                }
                EFS_FAILED_ANNOTATION_INVALID -> {
                    Log.e(
                        tag,
                        "Inspection failed.Annotation text format is invalid.",
                        null,
                        "annotation" to annotationText,
                        driverIdLogKey to driverId,
                        driverNameLogKey to driverName
                    )
                    sendEDVIRResult(
                        EDVIR_INSPECTION_STATUS_FAILURE,
                        getString(
                            R.string.inspection_failed,
                            getString(R.string.annotation_failed_invalid_annotation_text)
                        )
                    )
                }
                EFS_FAILED_ANNOTATION_LOG_EVENT_CHANGED -> {
                    Log.e(
                        tag,
                        "Inspection failed.Log event changed.",
                        null,
                        "annotation" to annotationText,
                        driverIdLogKey to driverId,
                        driverNameLogKey to driverName
                    )
                    sendEDVIRResult(
                        EDVIR_INSPECTION_STATUS_FAILURE,
                        getString(
                            R.string.inspection_failed,
                            getString(R.string.annotation_failed_log_event_changed)
                        )
                    )
                }
                else -> {
                    Log.e(
                        tag,
                        "Inspection failed.Unexpected error.",
                        null,
                        "annotation" to annotationText,
                        driverIdLogKey to driverId,
                        driverNameLogKey to driverName,
                        "intent action" to result.data?.action
                    )
                    sendEDVIRResult(
                        EDVIR_INSPECTION_STATUS_FAILURE,
                        getString(
                            R.string.inspection_failed,
                            getString(R.string.annotation_failed_unexpected_error)
                        )
                    )
                }
            }
        }

    private fun onPositiveClick() {
        if (isFormMandatory.not()) {
            finish()
        }
    }

    private fun onNegativeClick() {
        //Not required
    }

    private fun sendEDVIRResultOnError(message: String) {
        if (isFormMandatory) {
            sendEDVIRResult(EDVIR_INSPECTION_STATUS_FAILURE, message)
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

    override fun onDestroy() {
        draftFormLocally()
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }
}