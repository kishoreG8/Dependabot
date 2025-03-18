package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DTFConditions
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.model.isDriverEditable
import com.trimble.ttm.commons.model.multipleChoiceDriverInputNeeded
import com.trimble.ttm.commons.usecase.DeepLinkUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.DateUtil.convertToServerDateFormat
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.NEW_MESSAGE_NOTIFICATION_ID
import com.trimble.ttm.commons.utils.STORAGE
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.customViews.REQUIRED
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.getNotificationManager
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.DriverMessageFormData
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.Message.Companion.getFormattedUiString
import com.trimble.ttm.formlibrary.model.MessageFormData
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.usecases.DraftUseCase
import com.trimble.ttm.formlibrary.usecases.FirebaseCurrentUserTokenFetchUseCase
import com.trimble.ttm.formlibrary.usecases.FormRenderUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.utils.DVIR_START
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.Utils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import java.util.UUID
import kotlin.collections.set

class MessageFormViewModel(
    private val application: Application,
    private val messageFormUseCase: MessageFormUseCase,
    private val draftUseCase: DraftUseCase,
    private val firebaseCurrentUserTokenFetchUseCase: FirebaseCurrentUserTokenFetchUseCase,
    private val formRenderUseCase: FormRenderUseCase,
    val coroutineDispatcherProvider: DispatcherProvider,
    private val formDataStoreManager: FormDataStoreManager,
    private val deepLinkUseCase: DeepLinkUseCase,
    private val formFieldDataUseCase: FormFieldDataUseCase
) : ViewModel() {
    private val tag: String = "MessageFormViewModel"
    var ttsInputList = ArrayList<String>()

    // Variables to hold entered ui data during orientation change in the view model
    private var uiFormResponse: UIFormResponse =
        UIFormResponse()
    private var imageRefFieldsUiData: HashMap<String, Pair<String,Boolean>> = HashMap()

    private var previousSelectedRecipients: Map<String, Any> = mutableMapOf()

    var selectedRecipients: Map<String, Any> = mutableMapOf()
    var selectedUsers: ArrayList<User> = ArrayList()
    private val _isResponseDrafted = MutableLiveData<Boolean>()
    val isResponseDrafted: LiveData<Boolean> = _isResponseDrafted
    private var formFieldStack = Stack<FormField>()
    private var viewIdToFormFieldStackMap = HashMap<Int, Stack<FormField>>()
    private val _renderValues = MutableStateFlow(DTFConditions(-1,-1,-1,-1,-1))
    val renderValues: StateFlow<DTFConditions> = _renderValues
    private var appModuleCommunicator = messageFormUseCase.getAppModuleCommunicator()
    private var formTemplateSerializedString: String = ""
    var shouldSaveOldState = true
    private val _inboxDeleteAllMessagesResponse = MutableLiveData<CollectionDeleteResponse>()
    val inboxDeleteAllMessagesResponse: LiveData<CollectionDeleteResponse> = _inboxDeleteAllMessagesResponse

    suspend fun getForm(formId: String,
                        isFreeForm: Boolean,
                        uiFormResponse: UIFormResponse,
                        formResponseType : String = EMPTY_STRING,
                        asn: String = EMPTY_STRING,
                        dispatchFormSavePath:String = EMPTY_STRING,
                        unCompletedDispatchFormPath: DispatchFormPath = DispatchFormPath(),
                        dispatcherFormValuesFormFieldMap : HashMap<String, ArrayList<String>> = hashMapOf(),
                        savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String, Boolean>> = hashMapOf(),
                        shouldFillUiResponse: Boolean= false,
                        isFormSaved: Boolean = false,
                        isReplayWithSame: Boolean = false
    ): Form {
        if (appModuleCommunicator.doGetCid().isEmpty()) {
            Log.e(tag, "customer id is empty")
            return Form()
        }

        return messageFormUseCase.getForm(
            DriverMessageFormData(
                formId, isFreeForm, uiFormResponse, formResponseType, asn,
                messageFormUseCase.splitDataFromDispatchFormSavePath(
                    dispatchFormSavePath,
                    unCompletedDispatchFormPath
                ),
                dispatcherFormValuesFormFieldMap,
                savedImageUniqueIdentifierToValueMap
            ),
            shouldFillUiResponse, isFormSaved, isReplayWithSame
        )
    }

    suspend fun getFreeForm(uiFormResponse: UIFormResponse): Form = withContext(coroutineDispatcherProvider.io()){
        messageFormUseCase.getFreeForm(uiFormResponse)
    }

    fun getFormFieldStack() = formFieldStack

    fun getViewIdToFormFieldStackMap() = viewIdToFormFieldStackMap

    fun createTtsString(formField: FormField){
        if(Utils.checkforLabel(formField) && formField.qtext!= DVIR_START)
        {
            if(formField.qtext.isNotEmpty()) ttsInputList.add(formField.qtext)
            if(Utils.checkforUiData(formField)) {
                ttsInputList.add(formField.uiData.getFormattedUiString())
            }
        }
    }
    fun saveFormData(
        formDataToSave: FormDataToSave
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(coroutineDispatcherProvider.io() + CoroutineName("saveMessagingForm")) {
            val cid = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            messageFormUseCase.mapImageUniqueIdentifier(formDataToSave.formTemplate)
            messageFormUseCase.addFieldDataInFormResponse(
                formDataToSave.formTemplate,
                FormResponse(),
                obcId = formDataToSave.obcId
            ).collect { formResponse ->
                messageFormUseCase.saveFormData(
                    MessageFormData(
                        formDataToSave.path,
                        formResponse,
                        formDataToSave.formId,
                        formDataToSave.typeOfResponse,
                        formDataToSave.formName,
                        formDataToSave.formTemplate.formDef.formClass,
                        formDataToSave.cid.toLong(),
                        formDataToSave.hasPredefinedRecipients,
                        formDataToSave.unCompletedDispatchFormPath,
                        formDataToSave.dispatchFormSavePath
                    ),
                ).also { isFormSaved ->
                    Log.i(tag, "Message FormResponse: $formResponse. isFormSaved: $isFormSaved")
                    _isResponseDrafted.postValue(true)
                    launchVectorOnFormSubmission(formDataToSave.formTemplate, "saveFormData")
                    this.coroutineContext.job.cancel()
                }
            }
        }
    }

    fun saveEncodedImageData(
        formTemplateData: FormTemplate,
        cid: String,
        truckNumber: String,
    ) {
        messageFormUseCase.mapImageUniqueIdentifier(formTemplateData)
    }

    suspend fun getEncodedImage(imageId: String): String {
        return messageFormUseCase.getEncodedImage(
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetTruckNumber(),
            imageId,
            tag
        )
    }


    fun getFreeFormEditTextHintAndMessage(
        formField: FormField,
        freeFormMessage: String
    ): Pair<String, String> {
        return if (formField.isDriverEditable()) Pair(
            application.getString(R.string.freeform_editable_input_field_hint_text),
            formField.uiData.ifEmpty { "" }
        )
        else Pair(
            application.getString(R.string.freeform_non_editable_input_field_hint_text),
            formField.uiData.ifEmpty { freeFormMessage }
        )
    }

    fun markMessageAsRead(
        caller: String,
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String,
        callSource: String
    ) {
        appModuleCommunicator.getAppModuleApplicationScope()
            .launch(coroutineDispatcherProvider.io() + SupervisorJob()) {
                if (messageFormUseCase.isMessageAlreadyRead(
                        customerId,
                        vehicleId,
                        obcId,
                        asn,
                        callSource
                    )
                ) return@launch
                cancelInboxMessageNotificationFromSystemTray()
                messageFormUseCase.markMessageAsRead(
                    caller,
                    customerId,
                    vehicleId,
                    obcId,
                    asn,
                    operationType,
                    callSource
                )
            }
    }

    private fun cancelInboxMessageNotificationFromSystemTray() {
        getNotificationManager(application).cancel(
            NEW_MESSAGE_NOTIFICATION_ID
        )
    }

    fun markMessageAsDeleted(
        asn: String,
        operationType: String
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(coroutineDispatcherProvider.io()) {
            val customerId = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            val obcId = appModuleCommunicator.doGetObcId()
            messageFormUseCase.markMessageAsDeleted(
                customerId,
                truckNumber,
                obcId,
                asn,
                operationType
            )
        }
    }

    fun observeDeletedInboxMessagesASN() = messageFormUseCase.getDeletedInboxMessageASN()

    fun markAllTheMessagesAsDeleted() {
        viewModelScope.launch(coroutineDispatcherProvider.io()) {
            val customerId = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            observeDeleteAllInboxMessagesResponse()
            messageFormUseCase.markAllTheMessagesAsDeleted(
                customerId,
                truckNumber,
                firebaseCurrentUserTokenFetchUseCase.getIDTokenOfCurrentUser(),
                firebaseCurrentUserTokenFetchUseCase.getAppCheckToken()
            )
        }
    }

    private fun observeDeleteAllInboxMessagesResponse() {
        viewModelScope.launch(coroutineDispatcherProvider.io()) {
            messageFormUseCase.observeDeleteAllResponse().collect {
                _inboxDeleteAllMessagesResponse.postValue(it)
            }
        }
    }

    fun deleteSavedMessageResponseOfDraft(
        customerId: String,
        vehicleId: String,
        createdTime: Long,
        coroutineScope: CoroutineScope = appModuleCommunicator.getAppModuleApplicationScope()
    ) {
        coroutineScope.launch(coroutineDispatcherProvider.io() + CoroutineName("deleteMsg")) {
            draftUseCase.deleteMessage(
                customerId,
                vehicleId,
                createdTime
            )
        }
    }

    fun checkIfFormIsValid(formTemplate: FormTemplate): Boolean =
        formRenderUseCase.checkIfFormIsValid(formTemplate = formTemplate)

    fun checkIfFormIsDtf(formTemplate: FormTemplate): Boolean =
        formRenderUseCase.checkIfFormIsDtf(formTemplate = formTemplate)

    fun checkIfFormIsSaved(uiFormResponse: UIFormResponse): Boolean =
        formRenderUseCase.checkIfFormIsSaved(uiFormResponse = uiFormResponse)

    fun serializeFormTemplate(formTemplate: FormTemplate): String =
        formRenderUseCase.serializeFormTemplate(formTemplate = formTemplate)

        fun fetchFormTemplate(
            formTemplateSerializedString: String
        ): FormTemplate = formRenderUseCase.fetchFormTemplate(
            formTemplateSerializedString = formTemplateSerializedString
        )

    fun isReplayWithNewFormType(driverFormId: Int, replyFormId: Int): Boolean =
        formRenderUseCase.isReplayWithNewFormType(
            driverFormId = driverFormId,
            replyFormId = replyFormId
        )

    fun isReplayWithSameFormType(driverFormId: Int, replyFormId: Int): Boolean =
        formRenderUseCase.isReplayWithSameFormType(
            driverFormId = driverFormId,
            replyFormId = replyFormId
        )

    fun countOfAutoFields(formFieldList: ArrayList<FormField>): Int =
        formRenderUseCase.countOfAutoFields(formFieldList = formFieldList)

    fun areAllAutoFields(formFieldSize: Int, autoFieldCount: Int): Boolean =
        formRenderUseCase.areAllAutoFields(
            formFieldSize = formFieldSize,
            autoFieldCount = autoFieldCount
        )

    fun getFormTemplateBasedOnBranchTargetId(
        branchTargetId: Int,
        loopEndId: Int,
        isDTF: Boolean,
        formTemplate: FormTemplate,
        formTemplateSerializedString: String,
        formFieldStack: Stack<FormField> = this.formFieldStack
    ): FormTemplate = formRenderUseCase.getFormTemplateBasedOnBranchTargetId(
        branchTargetId = branchTargetId,
        loopEndId = loopEndId,
        isDTF = isDTF,
        formTemplate = formTemplate,
        formTemplateSerializedString = formTemplateSerializedString,
        formFieldStack = formFieldStack
    )

    fun getLastIndexOfBranchTargetId(formTemplate: FormTemplate, branchTargetId: Int): Int =
        formRenderUseCase.getLastIndexOfBranchTargetId(
            formTemplate = formTemplate,
            branchTargetId = branchTargetId
        )


    fun saveEnteredUIDataDuringOrientationChange(uiFormResponse: UIFormResponse) {
        this.uiFormResponse = uiFormResponse
    }

    fun getSavedUIDataDuringOrientationChange(): UIFormResponse {
        return uiFormResponse
    }

    private fun cacheImageRefFieldsInViewModel(formFieldList: ArrayList<FormField>) {
        formFieldList.filter { formField -> formField.qtype == FormFieldType.IMAGE_REFERENCE.ordinal }.let { imageFormFields ->
            imageFormFields.forEach { formField ->
                if(formField.uiData.isNotEmpty() && formField.needToSyncImage){
                    var uniqueId = formField.uniqueIdentifier
                    if (uniqueId.isEmpty()) uniqueId = STORAGE + UUID.randomUUID().toString()
                    imageRefFieldsUiData[uniqueId] = Pair(formField.uiData, true)
                    formField.uniqueIdentifier = uniqueId
                }
            }
        }
    }
    
    internal fun getLocallyCachedImageUniqueIdentifierToValueMap() = imageRefFieldsUiData

    suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): HashMap<String, Any> = messageFormUseCase.getLatestFormRecipients(customerId, formID)

    fun getFormFieldStackCopy(formFieldStack: Stack<FormField> = this.formFieldStack): Stack<FormField> =
        messageFormUseCase.getFormFieldStackCopy(formFieldStack)

    fun getFormFieldCopy(formField: FormField): FormField =
        messageFormUseCase.getFormFieldCopy(formField)

    fun saveFormTemplateCopy(formTemplate: FormTemplate) {
        formTemplateSerializedString = serializeFormTemplate(formTemplate)
    }

    private fun getFormTemplateCopy(formTemplateSerializedString: String): FormTemplate =
        messageFormUseCase.getFormTemplateCopy(formTemplateSerializedString)

    // hasCheckRecipients has to be false when is a reply form,
    // otherwise, you need to check the recipients
    fun hasSomethingToDraft(
        formTemplateData: FormTemplate,
        hasCheckRecipients: Boolean = false
    ) = checkContentChange(formTemplateData) || checkRecipientsChange(hasCheckRecipients)

    //you need to check the difference only in the uiData, otherwise,
    // the old is going to be always different from the new
    // we need to add id validation when are the same for the content
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkContentChange(
        lastFormTemplateData: FormTemplate
    ) : Boolean {
        val oldFormTemplateData = getFormTemplateCopy(formTemplateSerializedString)
        val result = lastFormTemplateData.formFieldsList.filterIndexed { index, formField ->
            index>=oldFormTemplateData.formFieldsList.size ||
                    hasSomeDifferencesInContent(
                        oldFormTemplateData.formFieldsList[index].uiData,
                        formField.uiData,
                        oldFormTemplateData.formFieldsList[index].qtype,
                        formField.qtype
                    )
        }
        return result.isNotEmpty()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun hasSomeDifferencesInContent(
        oldData: String,
        newData: String,
        fieldTypeOld: Int,
        fieldTypeNew: Int
    ) : Boolean {
        if(fieldTypeNew!=fieldTypeOld) return true
        return when(fieldTypeNew){
            FormFieldType.DATE.ordinal -> {
                oldData != convertToServerDateFormat(newData,application)
            }
            else -> {
                oldData != newData
            }
        }
    }

    // you need to call this method after recover all the recipients when the
    // form is displayed
    fun savePreviousSelectedRecipients(){
        this.previousSelectedRecipients = messageFormUseCase.makeRecipientsCopy(
            this.selectedRecipients
        )
    }

    //this checks the difference not the emptiness
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkRecipientsChange(hasCheckRecipients: Boolean) : Boolean{
        if(hasCheckRecipients){
            return selectedRecipients!=previousSelectedRecipients
        }
        return false
    }

    /*
    this method is used on the views that are not checking the if your content
     is different.
     A form is empty if don't have data and don't have recipients
    * */
    fun isAnEmptyForm(
        formTemplateData: FormTemplate,
        hasCheckRecipients: Boolean = false
    ):Boolean{
        if(hasCheckRecipients){
            return FormUtils.areAllFieldsEmpty(formTemplateData) &&
                    checkRecipientsChange(true).not()
        }
        return FormUtils.areAllFieldsEmpty(formTemplateData)
    }

    fun isAnEmptyForm(formTemplateData: FormTemplate): Boolean {
        return FormUtils.areAllFieldsEmpty(formTemplateData) &&
                this.selectedRecipients.isEmpty()
    }

    fun setDataFromDefaultValueOrFormResponses(
        formFieldValuesMap: HashMap<String, ArrayList<String>> = HashMap(),
        formField: FormField,
        caller: String,
        actualLoopCount: Int,
    ): Boolean = messageFormUseCase.setDataFromDefaultValueOrUiFormResponsesValue(
        formFieldValuesMap = formFieldValuesMap,
        formField = formField,
        actualLoopCount = actualLoopCount,
        currentLoopCount = getFormFieldCurrentLoopCount(),
        caller = caller
    )

    private fun getFormFieldCurrentLoopCount(): Int {
        val stackFormField = if (formFieldStack.isNotEmpty()) formFieldStack.peek() else null
        return stackFormField?.loopcount ?: 0
    }

    fun processDispatcherFormValues(
        formClass: Int = -1,
        fieldList: ArrayList<MessageFormField>
    ): HashMap<String, ArrayList<String>> = messageFormUseCase.processDispatcherFormValues(formClass, fieldList)

    fun processMultipleChoice(
        formChoice: FormChoice?,
        formTemplate: FormTemplate,
        mainDispatcher: CoroutineDispatcher = coroutineDispatcherProvider.main(),
        defaultDispatcher: CoroutineDispatcher = coroutineDispatcherProvider.default()
    ) {
        viewModelScope.launch(mainDispatcher + CoroutineName("processMC")) {
            //Triggered when decision making multiple choice is selected
            formChoice?.let { selectedFormChoice ->
                if (viewIdToFormFieldStackMap.containsKey(selectedFormChoice.viewId)) {
                    val restoredFormFieldStack =
                        viewIdToFormFieldStackMap[selectedFormChoice.viewId] as Stack<FormField>
                    withContext(defaultDispatcher) {
                        formFieldStack = getFormFieldStackCopy(
                            restoredFormFieldStack
                        )
                    }
                }
                formRenderUseCase.processNextFieldToBeRendered(
                    selectedFormChoice = selectedFormChoice,
                    formTemplate = formTemplate,
                    formFieldStack = formFieldStack,
                    _renderValues = _renderValues
                )
            }
        }
    }

    fun processComposeMultipleChoice(
        formChoice: FormChoice?,
        formTemplate: FormTemplate
    ): Triple<Int, Int, Int> {
        var renderValue = Triple(-1,-1,-1)
        formChoice?.let { selectedFormChoice ->
            if (viewIdToFormFieldStackMap.containsKey(selectedFormChoice.viewId)) {
                val restoredFormFieldStack =
                    viewIdToFormFieldStackMap[selectedFormChoice.viewId] as Stack<FormField>
                formFieldStack = getFormFieldStackCopy(
                    restoredFormFieldStack
                )
            }
            renderValue = formRenderUseCase.processNextComposeFieldToBeRendered(
                selectedFormChoice = selectedFormChoice,
                formTemplate = formTemplate,
                formFieldStack = formFieldStack
            )
        }
        return renderValue
    }


    fun draftFormLocally(formTemplateData: FormTemplate) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(coroutineDispatcherProvider.io() + CoroutineName("localDraft")) {
            cacheImageRefFieldsInViewModel(formTemplateData.formFieldsList)
            messageFormUseCase.addFieldDataInFormResponse(
                formTemplateData,
                UIFormResponse(
                    false,
                    FormResponse()
                ).formData, appModuleCommunicator.doGetObcId()
            ).collect { formResponse ->
                UIFormResponse(false, formResponse).also { uiFormResponse ->
                    saveEnteredUIDataDuringOrientationChange(uiFormResponse)
                    this.coroutineContext.job.cancel()
                }
            }
        }
    }

    fun isOfTextInputLayoutViewType(formField: FormField): Boolean {
        return when (formField.qtype) {
            FormFieldType.SIGNATURE_CAPTURE.ordinal,
            FormFieldType.IMAGE_REFERENCE.ordinal -> false
            else -> true
        }
    }

    fun isFormFieldRequiredAndReadOnlyView(formField: FormField, makeFieldsNonEditable: Boolean = false, isFormSaved:Boolean = false) =
        formField.required == REQUIRED && makeFieldsNonEditable.not() && isFormSaved.not()


    private fun addAutoFieldsToList(
        formFieldList: ArrayList<FormField>,
        formTemplate: FormTemplate
    ) {
        formFieldList.addAll(formTemplate.formFieldsList.filter { (it.qtype == FormFieldType.AUTO_VEHICLE_FUEL.ordinal || it.qtype == FormFieldType.AUTO_VEHICLE_LATLONG.ordinal || it.qtype == FormFieldType.AUTO_VEHICLE_LOCATION.ordinal || it.qtype == FormFieldType.AUTO_VEHICLE_ODOMETER.ordinal) })
    }

    private fun iterateMapAndGetList(parentMap: HashMap<Int,FormField>): ArrayList<FormField> {
        return ArrayList(parentMap.values)
    }

    fun getFormTemplateForDTFForms(
        formTemplate: FormTemplate,
        viewIdToFormFieldMap: HashMap<Int,FormField>
    ): FormTemplate {
        val formFieldList = iterateMapAndGetList(parentMap = viewIdToFormFieldMap)
        addAutoFieldsToList(formFieldList = formFieldList, formTemplate = formTemplate)
        return FormTemplate(formDef = formTemplate.formDef, formFieldsList = formFieldList)
    }

    fun filterImageFormField(formFieldList: ArrayList<FormField>): List<FormField> {
        return formFieldList.filter { it.qtype == FormFieldType.IMAGE_REFERENCE.ordinal }
    }

    fun isProcessingMultipleChoiceFieldRequired(
        formField: FormField,
        isSyncToQueue: Boolean,
        processMultipleChoice: (FormChoice?) -> Unit
    ): Boolean {
        if (formField.qtype == FormFieldType.MULTIPLE_CHOICE.ordinal && isSyncToQueue.not() && formField.uiData.isNotEmpty()) {
            val formChoice = formField.formChoiceList?.find { formField.uiData == it.value }
            formChoice?.viewId = formField.viewId
            return if (formField.multipleChoiceDriverInputNeeded()) {
                processMultipleChoice(formChoice)
                true
            } else {
                false
            }
        }
        return false
    }

    fun requiresProcessingForMultipleChoiceField(
        formField: FormField,
        processMultipleChoice: (FormChoice?) -> Unit
    ): Boolean {
        if (formField.qtype == FormFieldType.MULTIPLE_CHOICE.ordinal  && formField.uiData.isNotEmpty()) {
            val formChoice = formField.formChoiceList?.find { formField.uiData == it.value }
            formChoice?.viewId = formField.viewId
            return if (formField.multipleChoiceDriverInputNeeded()) {
                processMultipleChoice(formChoice)
                true
            } else {
                false
            }
        }
        return false
    }

    fun saveDispatchFormResponse(
        path: String,
        formTemplate: FormTemplate,
        formDataToSave: FormDataToSave,
        isSyncToQueue: Boolean,
        caller: String
    ) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(coroutineDispatcherProvider.io()) {
            val cid = appModuleCommunicator.doGetCid()
            val truckNumber = appModuleCommunicator.doGetTruckNumber()
            val pathToSave = formDataToSave.path
            saveEncodedImageData(formTemplate, cid,truckNumber,)
            val formResponse = FormResponse()
            messageFormUseCase.addFieldDataInFormResponse(
                formTemplate,
                formResponse,
                appModuleCommunicator.doGetObcId()
            ).collect { processedFormResponse ->
                messageFormUseCase.saveDispatchFormResponse(
                    path = pathToSave,
                    formResponse = processedFormResponse,
                    formDataToSave = formDataToSave,
                    caller = caller
                ).also { isFormSaved ->
                    Log.i(tag, "DispatchFormResponseSaved: $isFormSaved")
                    if (isSyncToQueue) {
                        Log.i(tag, "Delete from draft after form send: $pathToSave. isFormSaved: $isFormSaved")
                        launchVectorOnFormSubmission(formTemplate, "saveDispatchForm")
                        draftUseCase.deleteDraftMsgOfDispatchFormSavePath(path, appModuleCommunicator.doGetCid(), appModuleCommunicator.doGetTruckNumber())
                    }
                    _isResponseDrafted.postValue(true)
                    this.coroutineContext.job.cancel()
                }
            }
        }
    }

    fun constructFormFieldsWithAutoFields(formTemplate : FormTemplate, constructedFormFieldList : ArrayList<FormField>) = formRenderUseCase.constructFormFieldsListForDTFWithAutoFieldsToSend(formTemplate, constructedFormFieldList)

    private fun launchVectorOnFormSubmission(formTemplate: FormTemplate, caller: String) {
        deepLinkUseCase.checkAndHandleDeepLinkConfigurationForFormSubmission(application.applicationContext, formTemplate, caller)
    }

    fun resetIsDraftView() {
        // setting IS_DRAFT_VIEW to false on responding yes/no in draft pop up and on form/message sent
        val coroutineScope = CoroutineScope(coroutineDispatcherProvider.io() + CoroutineName(tag))
        coroutineScope.launch {
            formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, false)
            coroutineScope.cancel()
        }
    }

    fun showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag : String) = messageFormUseCase.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag)

    fun deleteMessageForTrash(asn: String, caller: String) {
        appModuleCommunicator.getAppModuleApplicationScope().launch(coroutineDispatcherProvider.io()) {
            messageFormUseCase.deleteSelectedTrashMessage(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber(),
                asn,
                caller
            )
        }
    }
}