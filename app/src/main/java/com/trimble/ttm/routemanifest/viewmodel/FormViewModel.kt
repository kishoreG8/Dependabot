package com.trimble.ttm.routemanifest.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.logger.DISPATCH_FORM_SAVE
import com.trimble.ttm.commons.logger.FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.EDITABLE
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldAttribute
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.model.isDriverEditable
import com.trimble.ttm.commons.model.isValidForm
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.DateUtil.calculateTimeDifference
import com.trimble.ttm.commons.utils.DateUtil.getCalendar
import com.trimble.ttm.commons.utils.DateUtil.getDate
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.getDriverFormID
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.DraftingUseCase
import com.trimble.ttm.formlibrary.utils.DISPATCH_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_COLLECTION
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FormData
import com.trimble.ttm.routemanifest.usecases.FormUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.utils.GMT_DATE_TIME_FORMAT
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class FormViewModel(
    private val application: Application,
    private val formUseCase: FormUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val tripPanelUseCase: TripPanelUseCase,
    private val draftingUseCase: DraftingUseCase,
    private val draftUseCase: DraftUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val deepLinkUseCase: DeepLinkUseCase,
    private val formFieldDataUseCase: FormFieldDataUseCase
) : ViewModel() {
    private val tag: String = "FormViewModel"
    private var formResponse: FormResponse =
        FormResponse()
    private val _formUiState = MutableStateFlow<ScreenContentState>(ScreenContentState.Loading())
    internal val formUiState: StateFlow<ScreenContentState> = _formUiState
    private val _sendButtonClickEventListener = MutableSharedFlow<Boolean>()
    internal val sendButtonClickEventListener: SharedFlow<Boolean> = _sendButtonClickEventListener

    internal var viewId = 0
    internal var viewIdToFormFieldMap = HashMap<Int, FormField>()
    internal var isUIResponseAvailableInFireStore: Boolean = false
    internal var isFormOpenedFromDraft = false

    init {
        if (formResponse.fieldData.size == 0) {
            formResponse = FormResponse()
        }
    }

    internal fun getDraftedFormFieldMap(formResponse: FormResponse?): HashMap<String, FormFieldAttribute> {
        return if (formResponse == null) hashMapOf()
        else {
            val formFieldResponseMap = hashMapOf<String, FormFieldAttribute>()
            formResponse.fieldData.forEach { fieldData ->
                formFieldDataUseCase.getFormFieldAttributeFromFieldDatum("$fieldData").let { formFieldAttribute ->
                    formFieldResponseMap[formFieldAttribute.uniqueTag] = formFieldAttribute
                }
            }
            formFieldResponseMap
        }
    }

    fun canShowCancel(isDriverInImessageReplyForm: Boolean, canShow: Boolean): Boolean =
        (isDriverInImessageReplyForm || (isDriverInImessageReplyForm.not() && canShow))

    fun getDispatchFormPath(
        isDriverInImessageReplyForm: Boolean,
        intent: Intent,
        dispatchFormPath: DispatchFormPath
    ): DispatchFormPath =
        if (isDriverInImessageReplyForm.not()) dispatchFormPath else intent.getDriverFormID()

    fun restoreSelectedDispatch(
        executeAction: () -> Unit
    ) {
        viewModelScope.launch(
            CoroutineName(tag) + dispatcherProvider.io()
        ) {
            appModuleCommunicator.restoreSelectedDispatch()
            executeAction()
        }
    }
    suspend fun getFormActivityLaunchAttributes(
        dataStoreManager: DataStoreManager,
        stopId: Int
    ): Triple<Boolean, DispatchFormPath, String> {
        var formList: ArrayList<DispatchFormPath> = arrayListOf()
        dataStoreManager.getValue(DataStoreManager.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING).let {
            if (it.isNotEmpty()) {
                formList = Gson().fromJson(
                    it,
                    object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
                )
            }
        }
        return if (formList.size > 1 && stopId != formList.first().stopId) {
            Triple(
                true,
                formList.first(),
                getFormResponsePath(formList.first().stopId, formList.first().actionId)
            )
        } else {
            Triple(false, DispatchFormPath(), "")
        }
    }

    suspend fun setFormUiState(screenContentState: ScreenContentState) {
        _formUiState.emit(screenContentState)
    }

    suspend fun getFormResponsePath(stopId: Int, actionId: Int): String {
        return "$INBOX_FORM_RESPONSE_COLLECTION/${appModuleCommunicator.doGetCid()}/${appModuleCommunicator.doGetTruckNumber()}/${
            appModuleCommunicator.getCurrentWorkFlowId("getFormResponsePath")
        }/$stopId/$actionId"
    }

    fun saveFormData(
        formTemplate: FormTemplate,
        path: String,
        isSyncToQueue: Boolean,
        latestFormRecipients: ArrayList<Recipients>,
        stopActionFormKey: DispatchFormPath
    ) {
        formResponse = FormResponse()
        formResponse.let { response ->
            appModuleCommunicator.getAppModuleApplicationScope().launch(dispatcherProvider.io() + CoroutineName(tag)) {
                val cid = appModuleCommunicator.doGetCid()
                val truckNumber = appModuleCommunicator.doGetTruckNumber()
                var pathToSave = "$INBOX_FORM_RESPONSE_COLLECTION/$cid/$truckNumber"
                if(isSyncToQueue.not()) pathToSave = "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/$cid/$truckNumber"
                formUseCase.mapImageUniqueIdentifier(formTemplate)
                val formDataToSave = constructFormDataToSave(formTemplateData = formTemplate,
                    cid = cid,
                    vehicleNumber = truckNumber,
                    documentFormPath = stopActionFormKey,
                    documentSavePath = path,
                    isReplyWithSame = false
                )
                formUseCase.addFieldDataInFormResponse(
                    formTemplate,
                    response,
                    appModuleCommunicator.doGetObcId()
                ).collect { formResponse ->
                    formResponse.recipients = latestFormRecipients
                    formUseCase.saveFormData(
                        pathToSave,
                        formResponse,
                        formDataToSave,
                        tag
                    ).also { isFormSaved ->
                        Log.i(DISPATCH_FORM_SAVE, "Dispatch FormResponse: $formResponse. isFormSaved: $isFormSaved")
                        if (isSyncToQueue) {
                            // check for deep link configuration on sending
                            checkForDeepLinkConfiguration(
                                context = application.applicationContext,
                                formTemplateData = formTemplate,
                                caller = tag
                            )
                            Log.i(DISPATCH_FORM_SAVE, "Delete from draft after form send: $path. isFormSaved: $isFormSaved")
                            draftUseCase.deleteDraftMsgOfDispatchFormSavePath(path, appModuleCommunicator.doGetCid(), appModuleCommunicator.doGetTruckNumber())
                        }
                        this.coroutineContext.job.cancel()
                    }
                }
            }
        }
    }

    fun saveDispatchFormDataToDraft(
        formTemplateData: FormTemplate,
        stopActionFormKey: DispatchFormPath,
        path: String,
        stopId: Int,
        isReplyWithSame: Boolean
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(CoroutineName(tag)) {
            formUseCase.removeFormFromPreferencesForStop(stopId)
            val cid: String = appModuleCommunicator.doGetCid()
            val vehicleNumber: String = appModuleCommunicator.doGetTruckNumber()
            val obcId: String = appModuleCommunicator.doGetObcId()
            val typeOfResponse: String = DISPATCH_FORM_RESPONSE_TYPE
            val replyFormName = formTemplateData.formDef.name
            draftUseCase.deleteDraftMsgOfDispatchFormSavePath(path, cid, vehicleNumber)
            draftingUseCase.makeDraft(
                formDataToSave = FormDataToSave(
                    formTemplateData,
                    "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/$cid/$vehicleNumber",
                    formTemplateData.formDef.formid.toString(),
                    typeOfResponse,
                    replyFormName,
                    if (isReplyWithSame)
                        -1
                    else
                        formTemplateData.formDef.formClass,
                    cid,
                    formTemplateData.formDef.recipients.isNotEmpty(),
                    obcId,
                    stopActionFormKey,
                    dispatchFormSavePath= path
                ), caller = tag, needToSendImages = false

            )
        }
    }

    private suspend fun constructFormDataToSave(
        formTemplateData: FormTemplate,
        cid: String,
        vehicleNumber: String,
        documentFormPath: DispatchFormPath,
        documentSavePath: String,
        isReplyWithSame: Boolean
    ): FormDataToSave {
        return FormDataToSave(
            formTemplate = formTemplateData,
            path = "$INBOX_FORM_RESPONSE_COLLECTION/$cid/$vehicleNumber",
            formId = formTemplateData.formDef.formid.toString(),
            typeOfResponse = DISPATCH_FORM_RESPONSE_TYPE,
            formName = formTemplateData.formDef.name,
            formClass = if (isReplyWithSame) -1 else formTemplateData.formDef.formClass,
            cid = cid,
            hasPredefinedRecipients = formTemplateData.formDef.recipients.isNotEmpty(),
            obcId = appModuleCommunicator.doGetObcId(),
            unCompletedDispatchFormPath = documentFormPath,
            dispatchFormSavePath = documentSavePath
        )
    }

    suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): ArrayList<Recipients> = formUseCase.getLatestFormRecipients(customerId, formID)

    fun removeFormFromStack(
        stopId: Int,
        dataStoreManager: DataStoreManager
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            formUseCase.removeFormFromPreference(stopId, dataStoreManager)
        }
    }

    suspend fun getFormData(
        path: String,
        actionId: String,
        isActionResponseSentToServer: Boolean
    ): UIFormResponse =
        formUseCase.getFormData(path, actionId, isActionResponseSentToServer)

    suspend fun getStopAction(
        stopId: String,
        actionId: String
    ): Action {
        val activeDispatch = appModuleCommunicator.getCurrentWorkFlowId("FVMGetStopAction")
        val cid = appModuleCommunicator.doGetCid()
        val truckNumber = appModuleCommunicator.doGetTruckNumber()
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetTruckNumber()
                .isEmpty() || activeDispatch.isEmpty()
        ) {
            Log.e(
                tag,
                "customer id or vehicle id or dispatch id is empty",
                throwable = null,
                "cid" to cid,
                "truck" to truckNumber,
                "trip id" to activeDispatch
            )
            return Action()
        }
        return formUseCase.getStopAction(
            appModuleCommunicator.doGetTruckNumber(),
            appModuleCommunicator.doGetCid(),
            activeDispatch,
            stopId,
            actionId
        )
    }

    suspend fun getForm(
        formData: FormData,
        isActionResponseSentToServer: Boolean,
        shouldFillUiResponse: Boolean,
        isReplyWithSameForm: Boolean,
        uiResponse: UIFormResponse = UIFormResponse()
    ): Form {
        val activeDispatch = appModuleCommunicator.getCurrentWorkFlowId("FVMGetForm")
        val cid = appModuleCommunicator.doGetCid()
        val truckNumber = appModuleCommunicator.doGetTruckNumber()
        if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetTruckNumber()
                .isEmpty() || activeDispatch.isEmpty()
        ) {
            Log.e(
                FORM_DATA_RESPONSE,
                "customer id or vehicle id or dispatch id is empty",
                throwable = null,
                "cid" to cid,
                "truck" to truckNumber,
                "trip id" to activeDispatch
            )
            return Form()
        }
        return formUseCase.getForm(
            FormData(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber(),
                activeDispatch,
                formData.formTemplatePath,
                formData.stopId,
                formData.actionId,
                formData.formId,
                formData.isFreeForm
            ),
            isActionResponseSentToServer,
            shouldFillUiResponse,
            isReplyWithSameForm,
            uiResponse
        )
    }

    suspend fun getFreeForm(formTemplatePath: String, actionId: String, formId: Int): Form =
        formUseCase.getFreeForm(formTemplatePath, actionId, formId)

    fun getFreeFormEditTextHintAndMessage(
        formField: FormField,
        actionPayloadFreeFormMessage: String,
        shouldDisplayOriginalMessage: Boolean = true
    ): Pair<String, String> {
        return if (formField.isDriverEditable() || !shouldDisplayOriginalMessage) {
            Pair(
                application.getString(R.string.freeform_editable_input_field_hint_text),
                formField.uiData.ifEmpty { "" }
            )
        } else {
            Pair(
                application.getString(R.string.freeform_non_editable_input_field_hint_text),
                formField.uiData.ifEmpty { actionPayloadFreeFormMessage }
            )
        }
    }

    fun fetchEncodedImgAndUpdateUiDataField(formField: FormField): Deferred<Boolean> {
        return viewModelScope.async(dispatcherProvider.io()) {
            formField.uiData = formUseCase.getEncodedImage(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber(),
                formField.uniqueIdentifier,
                tag
            )
            Log.i(
                tag,
                "fetchEncodedImgAndUpdateUiDataField: uiDataStringLength: ${formField.uiData.length} uniqueId: ${formField.uniqueIdentifier}"
            )
            return@async true
        }
    }

    fun setSendButtonClickEvent(value: Boolean) {
        viewModelScope.launch {
            _sendButtonClickEventListener.emit(value)
        }
    }

    fun isComposableView(formField: FormField): Boolean {
        return when (formField.qtype) {
            FormFieldType.NUMERIC.ordinal,
            FormFieldType.NUMERIC_ENHANCED.ordinal,
            FormFieldType.TEXT.ordinal,
            FormFieldType.DISPLAY_TEXT.ordinal,
            FormFieldType.PASSWORD.ordinal,
            FormFieldType.SIGNATURE_CAPTURE.ordinal,
            FormFieldType.BARCODE_SCAN.ordinal,
            FormFieldType.DATE.ordinal,
            FormFieldType.TIME.ordinal,
            FormFieldType.DATE_TIME.ordinal-> true
            else -> false
        }
    }

    // This function is to identify the non composable android views that are needs to be added in the textInputLayout
    fun isOfTextInputLayoutViewType(formField: FormField): Boolean {
        return when (formField.qtype) {
            FormFieldType.SIGNATURE_CAPTURE.ordinal,
            FormFieldType.IMAGE_REFERENCE.ordinal -> false
            else -> true
        }
    }

    fun checkIfImessageFormsAreValid(driverFormDef: FormDef, replyFormDef: FormDef, isDriverInImessageReplyForm: Boolean, isSyncToQueue: Boolean, isCannotSendAction: Boolean): Boolean {
        return driverFormDef.isValidForm() && (replyFormDef.isValidForm() || (isCannotSendAction)) && isDriverInImessageReplyForm.not() && isSyncToQueue.not()
    }

    fun checkForCompleteFormMessages() {
        viewModelScope.launch(CoroutineName("$tag Check for Complete Form") + dispatcherProvider.io()) {
            tripPanelUseCase.checkForCompleteFormMessages()
        }
    }

    fun dismissTripPanelMessage() {
        viewModelScope.launch(CoroutineName("$tag Dismiss Trip panel msg") + dispatcherProvider.default()) {
                tripPanelUseCase.dismissTripPanelMessage(tripPanelUseCase.lastSentTripPanelMessage.messageId)
        }
    }

    fun shouldSaveFormDataDuringConfigurationChange(
        isSyncToQueue: Boolean, isDriverInSingleForm: Boolean,
        isDriverInImessageReplyForm: Boolean, cid: Int, isChangingConfigurations: Boolean
    ): Boolean =
        shouldSaveFormData(
            isSyncToQueue = isSyncToQueue,
            isDriverInSingleForm = isDriverInSingleForm,
            isDriverInImessageReplyForm = isDriverInImessageReplyForm,
            cid = cid
        ) && isChangingConfigurations.not()

    fun shouldSaveFormData(
        isSyncToQueue: Boolean,
        isDriverInSingleForm: Boolean,
        isDriverInImessageReplyForm: Boolean,
        cid: Int
    ): Boolean =
        isSyncToQueue.not() && (isDriverInSingleForm || isDriverInImessageReplyForm) && cid >= 0

    fun calculateAndRecordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
        eventName: String,
        dataStoreManager: DataStoreManager
    ) {
        if (eventName.isNotEmpty()) {
            viewModelScope.launch(dispatcherProvider.io()) {
                val timeDuration = calculateTimeDifference(
                    getDate(
                        GMT_DATE_TIME_FORMAT,
                        dataStoreManager.getValue(
                            DataStoreManager.ARRIVAL_TIME,
                            EMPTY_STRING
                        )
                    ),
                    getCalendar().time
                )
                formUseCase.recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
                    eventName,
                    timeDuration
                )
            }
        }
    }

    // This function is to set the default value for the free form reply form
    fun setDefaultValueForFreeFormMessage(isReplyWithSame: Boolean, formField: FormField, freeFormMessage : String, isDriverInImessageReplyForm: Boolean, isFormSaved : Boolean) {
        if(isFormSaved.not() && isDriverInImessageReplyForm && isReplyWithSame && formField.uiData.isEmpty()) {
            formField.uiData = freeFormMessage
        }
    }

    //Temporary workaround for reply with same CFF issue - pending clarifications from PFM.
    fun changeDriverEditableValueForReplyFreeForm(
        isDriverInImessageReplyForm: Boolean,
        formField: FormField
    ) {
        if(isDriverInImessageReplyForm) {
            formField.driverEditable = EDITABLE
        }
    }

    fun checkForDeepLinkConfiguration(
        context: Context,
        formTemplateData: FormTemplate,
        caller: String
    ) {
        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(
            context = context,
            formTemplateData = formTemplateData,
            caller = caller
        )
    }

    fun shouldFormBeDrafted(
        isSyncToQueue: Boolean,
        isDriverInSingleForm: Boolean,
        isDriverInImessageReplyForm: Boolean,
        isDraftFeatureFlagEnabled: Boolean,
        cid: Int
    ) : Boolean =
        (shouldSaveFormData(isSyncToQueue, isDriverInSingleForm, isDriverInImessageReplyForm, cid) && isDraftFeatureFlagEnabled)

    suspend fun onInvalidForm(
        caller: String,
        stopId: Int,
        actionId: Int
    ) {
        formUseCase.onInvalidForm(caller, stopId, actionId)
    }

}
