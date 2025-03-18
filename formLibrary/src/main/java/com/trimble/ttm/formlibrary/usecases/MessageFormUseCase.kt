package com.trimble.ttm.formlibrary.usecases

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.logger.FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.FREE_FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.DispatcherFormValuesPath
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormChoice
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.usecase.DispatchFormUseCase
import com.trimble.ttm.commons.usecase.DispatcherFormValuesUseCase
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.DISPATCH_FORM_SAVE_PATH
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE
import com.trimble.ttm.commons.utils.FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE_DEFVALUE
import com.trimble.ttm.commons.utils.FORM_DATA_KEY
import com.trimble.ttm.commons.utils.UNCOMPLETED_DISPATCH_FORM_PATH
import com.trimble.ttm.commons.utils.Utils
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.http.BuildEnvironment
import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import com.trimble.ttm.formlibrary.model.DriverMessageFormData
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.MessageFormData
import com.trimble.ttm.formlibrary.model.MessageFormField
import com.trimble.ttm.formlibrary.model.SaveMessageFormResponse
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.utils.CORPORATE_FREE_FORM_ID_DEV_AND_PROD
import com.trimble.ttm.formlibrary.utils.CORPORATE_FREE_FORM_ID_QA_AND_STAGE
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.DISPATCH_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FLAVOR_DEV
import com.trimble.ttm.formlibrary.utils.FLAVOR_PROD
import com.trimble.ttm.formlibrary.utils.FLAVOR_QA
import com.trimble.ttm.formlibrary.utils.FLAVOR_STG
import com.trimble.ttm.formlibrary.utils.FORM_CLASS_KEY
import com.trimble.ttm.formlibrary.utils.FORM_ID_KEY
import com.trimble.ttm.formlibrary.utils.FORM_NAME
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.HAS_PRE_DEFINED_RECIPIENTS
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.RECIPIENT_USERNAME
import com.trimble.ttm.formlibrary.utils.SKIPPED
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Stack

class MessageFormUseCase(
    private val encodedImageRefUseCase: EncodedImageRefUseCase,
    private val dispatchFormUseCase: DispatchFormUseCase,
    private val messageFormRepo: MessageFormRepo,
    private val formFieldDataUseCase: FormFieldDataUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dispatcherFormValuesUseCase: DispatcherFormValuesUseCase
) {
    private val appModuleCommunicator = messageFormRepo.getAppModuleCommunicator()
    private val getEnqueuedNotificationScope : CoroutineScope = CoroutineScope(Job())
    private var getEnqueuedNotificationJob : Job ?= null

    suspend fun getForm(
        formId: String,
        isFreeForm: Boolean,
    ): Form {
        return if (isFreeForm) {
            val formTemplate = messageFormRepo.getFreeForm(
                formId.toInt(),
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber()
            )
            Form(formTemplate, UIFormResponse(), HashMap())
        } else {
            val formTemplate =
                messageFormRepo.getForm(appModuleCommunicator.doGetCid(), formId.toInt())
                    .apply {
                        formFieldsList.sortWith(compareBy { it.qnum })
                    }
            Form(formTemplate, UIFormResponse(), HashMap())
        }
    }

    suspend fun getForm(
        getMessageFormData: DriverMessageFormData,
        shouldFillUiResponse : Boolean = false,
        isFormSaved: Boolean = false,
        isReplayWithSame: Boolean = false
    ): Form = coroutineScope {
        if (getMessageFormData.isFreeForm) {
            val formTemplateFetchJob = async { messageFormRepo.getFreeForm(
                getMessageFormData.formId.toInt(),
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetTruckNumber()
            )}

            val uiResponseFetchJob = async {
                getMessageFormData.uiFormResponse
            }
            val formDataJobsResult = awaitAll(
                formTemplateFetchJob,
                uiResponseFetchJob
            )
            if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE) {
                formFieldDataUseCase.createFormFromResult(formDataJobsResult,true, savedImageUniqueIdentifierToValueMap = getMessageFormData.savedImageUniqueIdentifierToValueMap)
            }else{
                Log.e(FORM_DATA_RESPONSE,"Invalid result size while fetching form data ${formDataJobsResult.size}")
                Form()
            }
        } else {
            val formTemplateFetchJob = async {
                messageFormRepo.getForm(appModuleCommunicator.doGetCid(), getMessageFormData.formId.toInt())
                    .apply {
                        formFieldsList.sortWith(compareBy { it.qnum })
                    }
            }
            val uiResponseFetchJob = async {
                getMessageFormData.uiFormResponse
            }

            val formDispatcherValuesFetchJob = async {
                    when (getMessageFormData.formResponseType) {
                        DISPATCH_FORM_RESPONSE_TYPE -> {
                            dispatcherFormValuesUseCase.getDispatcherFormValues(
                                getMessageFormData.dispatcherFormValuesPath.cid,
                                getMessageFormData.dispatcherFormValuesPath.vehicleId,
                                getMessageFormData.dispatcherFormValuesPath.dispatchId,
                                getMessageFormData.dispatcherFormValuesPath.stopId,
                                getMessageFormData.dispatcherFormValuesPath.actionId
                            )
                        }
                        INBOX_FORM_RESPONSE_TYPE -> {
                            if (getMessageFormData.dispatcherFormValuesFormFieldMap.size>0){
                                getMessageFormData.dispatcherFormValuesFormFieldMap
                            }else{
                                val message = messageFormRepo.getDispatcherFormValuesFromInbox(
                                    appModuleCommunicator.doGetCid(),
                                    appModuleCommunicator.doGetTruckNumber(),
                                    getMessageFormData.asn
                                )
                                if (message.formId.isNotEmpty() && (message.replyFormClass == "-1" || (message.formId == message.replyFormId && message.replyFormClass != "1"))) {
                                    val defValues = processDispatcherFormValues(message.formClass.toFloat().toInt(), message.formFieldList)
                                    defValues
                                }else{
                                    hashMapOf()
                                }
                            }
                        }
                        else ->{
                            hashMapOf()
                        }
                    }


            }
            val formDataJobsResult = awaitAll(
                formTemplateFetchJob,
                uiResponseFetchJob,
                formDispatcherValuesFetchJob
            )
            if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE_DEFVALUE) {
                formFieldDataUseCase.createFormFromResult(formDataJobsResult,false,
                    shouldFillUiResponse, isReplayWithSame, isFormSaved, savedImageUniqueIdentifierToValueMap = getMessageFormData.savedImageUniqueIdentifierToValueMap)
            }else{
                Log.e(FORM_DATA_RESPONSE,"Invalid result size while fetching form data ${formDataJobsResult.size}")
                Form()
            }
        }
    }

    suspend fun getFreeForm(uiFormResponse: UIFormResponse): Form = coroutineScope{
        appModuleCommunicator.getAppFlavor().let {
            val formId = when (it) {
                FLAVOR_DEV, FLAVOR_PROD -> {
                    CORPORATE_FREE_FORM_ID_DEV_AND_PROD
                }
                FLAVOR_QA, FLAVOR_STG -> {
                    CORPORATE_FREE_FORM_ID_QA_AND_STAGE
                }
                else -> {
                    0
                }
            }

            val formTemplateFetchJob = async {
                messageFormRepo.getFreeForm(
                    formId,
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber()
                )
            }

            val uiResponseFetchJob = async {
                uiFormResponse
            }
            val formDataJobsResult = awaitAll(
                formTemplateFetchJob,
                uiResponseFetchJob
            )
            if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE) {
                formFieldDataUseCase.createFormFromResult(formDataJobsResult,true)
            }else{
                Log.d(FREE_FORM_DATA_RESPONSE,"Invalid result size while fetching form data ${formDataJobsResult.size}")
                Form()
            }
        }
    }

    fun cacheFormTemplate(formId: String, isFreeForm: Boolean, tag: String) {
        //Caches FormDef, FormField and FormChoices
        appModuleCommunicator.getAppModuleApplicationScope()
            .safeLaunch(ioDispatcher + SupervisorJob()) {
                if (formId.isEmpty()) return@safeLaunch
                val form = getForm(formId, isFreeForm)
                Log.i(
                    tag,
                    "FormTemplate cache complete for formId: ${form.formTemplate.formDef.formid} isFreeForm: $isFreeForm"
                )
            }
    }

    suspend fun saveFormData(
        saveFormData: MessageFormData
    ): Boolean {
        val users = messageFormRepo.getRecipientUserName(
            saveFormData.formResponse.recipients,
            saveFormData.cid
        )
        val saveMessageFormResponse = SaveMessageFormResponse(
            saveFormData.path,
            saveFormData.formResponse,
            saveFormData.formId,
            saveFormData.typeOfResponse,
            saveFormData.formName,
            users,
            saveFormData.formClass,
            saveFormData.hasPredefinedRecipients,
            saveFormData.unCompletedDispatchFormPath,
            saveFormData.dispatchFormSavePath
        )
        val curTimeUtcInMillis = DateUtil.getCurrentTimeInMillisInUTC()
        val cid = appModuleCommunicator.doGetCid()
        val truckNum = appModuleCommunicator.doGetTruckNumber()
        return messageFormRepo.saveFormResponse(
            HashMap<String, Any>().apply {
                this[FORM_DATA_KEY] = saveMessageFormResponse.formResponse
                this[FORM_ID_KEY] = saveMessageFormResponse.formId
                this[FORM_CLASS_KEY] = saveMessageFormResponse.formClass
                this[CREATED_AT] = curTimeUtcInMillis
                this[FORM_RESPONSE_TYPE] = saveMessageFormResponse.typeOfResponse
                this[FORM_NAME] = saveMessageFormResponse.formName
                this[RECIPIENT_USERNAME] = saveMessageFormResponse.userNames.toList()
                this[HAS_PRE_DEFINED_RECIPIENTS] = saveMessageFormResponse.hasPredefinedRecipients
                this[UNCOMPLETED_DISPATCH_FORM_PATH] = saveMessageFormResponse.uncompletedDispatchFormPath
                this[DISPATCH_FORM_SAVE_PATH] = saveMessageFormResponse.dispatchFormSavePath
            },
            saveMessageFormResponse.path,
            cid,
            truckNum
        )
    }

    fun mapImageUniqueIdentifier(
        formTemplateData: FormTemplate
    ) {
        encodedImageRefUseCase.mapImageUniqueIdentifier(formTemplateData)
    }

    suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): HashMap<String, Any> = messageFormRepo.getLatestFormRecipients(customerId, formID)

    fun getFormFieldStackCopy(formFieldStack: Stack<FormField>): Stack<FormField> =
        Gson().toJson(formFieldStack).let {
            Gson().fromJson(
                it,
                object : TypeToken<Stack<FormField>>() {}.type
            )
        }

    fun getFormFieldCopy(formField: FormField): FormField = Gson().toJson(formField).let {
        Gson().fromJson(it, FormField::class.java)
    }

    fun getFormTemplateCopy(formTemplateSerializedString: String): FormTemplate = Utils.fromJsonString<FormTemplate>(formTemplateSerializedString) ?: FormTemplate()

    fun makeRecipientsCopy(recipients: Map<String, Any>): Map<String, Any> {
        val copy: MutableMap<String, Any> = HashMap()
        copy.putAll(recipients)
        return copy
    }

    suspend fun markMessageAsRead(
        caller: String,
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String,
        callSource: String
    ) = messageFormRepo.markMessageAsRead(
        caller,
        customerId,
        vehicleId,
        obcId,
        asn,
        operationType,
        callSource
    )

    suspend fun isMessageAlreadyRead(
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        callSource: String
    ): Boolean = messageFormRepo.isMessageAlreadyRead(customerId, vehicleId, obcId, asn, callSource)

    suspend fun markMessageAsDeleted(
        customerId: String,
        vehicleId: String,
        obcId: String,
        asn: String,
        operationType: String
    ) = messageFormRepo.markMessageAsDeleted(customerId, vehicleId, obcId, asn, operationType)

    fun getDeletedInboxMessageASN() = messageFormRepo.getDeletedInboxMessageASNFlow()

    suspend fun markAllTheMessagesAsDeleted(
        customerId: String,
        vehicleId: String, token: String?, appCheckToken : String
    ) {
        messageFormRepo.markAllTheMessagesAsDeleted(
            customerId, vehicleId, when (appModuleCommunicator.getAppFlavor()) {
                FLAVOR_DEV -> BuildEnvironment.Dev
                FLAVOR_QA -> BuildEnvironment.Qa
                FLAVOR_PROD -> BuildEnvironment.Prod
                else -> BuildEnvironment.Stg
            }, token, appCheckToken
        )
    }

    fun observeDeleteAllResponse(): SharedFlow<CollectionDeleteResponse> = messageFormRepo.getMessagesDeleteAllFlow()

    suspend fun getRecipientUserName(
        recipients: MutableList<Recipients>,
        cid: Long
    ): Set<User> =
        messageFormRepo.getRecipientUserName(recipients, cid)

    suspend fun getEncodedImage(companyId: String, vehicleId: String, imageID: String, caller: String): String =
        encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(
            cid = companyId,
            truckNum = vehicleId,
            imageID = imageID,
            caller = caller
        )

    fun getAppModuleCommunicator() = messageFormRepo.getAppModuleCommunicator()

    fun isProcessingMultipleChoiceFieldRequired(
        formField: FormField,
        isSyncToQueue: Boolean,
        processMultipleChoice: (FormChoice?) -> Unit
    ) : Boolean {
        if (formField.qtype == FormFieldType.MULTIPLE_CHOICE.ordinal && isSyncToQueue.not() && formField.uiData.isNotEmpty()) {
            val formChoice = formField.formChoiceList?.find { formField.uiData == it.value }
            formChoice?.viewId = formField.viewId
            processMultipleChoice(formChoice)
            return true
        }
        return false
    }

    fun setDataFromDefaultValueOrUiFormResponsesValue(
        formFieldValuesMap: HashMap<String, ArrayList<String>> = HashMap(),
        formField: FormField,
        actualLoopCount: Int = -1,
        currentLoopCount: Int = -1,
        caller: String
    ): Boolean {
        if (formFieldValuesMap.isNotEmpty()) {
            //New way of handling the default values for fields inside the loop
            val dispatcherFormValues = formFieldValuesMap[formField.qnum.toString()]
            return populateFormFieldUIDataFromDefaultValueOrUIFormResponses(
                formField,
                dispatcherFormValues,
                actualLoopCount,
                currentLoopCount
            )
        }
        return false
    }

    suspend fun saveDispatchFormResponse(
        path: String,
        formResponse: FormResponse,
        formDataToSave: FormDataToSave,
        caller: String
    ): Boolean {
        val users = messageFormRepo.getRecipientUserName(
            formResponse.recipients,
            formDataToSave.cid.toSafeLong()
        )
        val timeInMills = DateUtil.getCurrentTimeInMillisInUTC()
        val data = HashMap<String, Any>().apply {
            this[FORM_DATA_KEY] = formResponse
            this[FORM_ID_KEY] = formDataToSave.formId
            this[FORM_CLASS_KEY] = formDataToSave.formClass
            this[CREATED_AT] = timeInMills
            this[FORM_RESPONSE_TYPE] = formDataToSave.typeOfResponse
            this[FORM_NAME] = formDataToSave.formName
            this[RECIPIENT_USERNAME] = users.toList()
            this[HAS_PRE_DEFINED_RECIPIENTS] = formDataToSave.hasPredefinedRecipients
            this[UNCOMPLETED_DISPATCH_FORM_PATH] = formDataToSave.unCompletedDispatchFormPath
            this[DISPATCH_FORM_SAVE_PATH] = formDataToSave.dispatchFormSavePath
        }

        return dispatchFormUseCase.saveDispatchFormResponse(path, data, caller)
    }

    fun addFieldDataInFormResponse(
        formTemplate: FormTemplate,
        formResponse: FormResponse,
        obcId: String
    ): Flow<FormResponse> = dispatchFormUseCase.addFieldDataInFormResponse(
        formTemplate,
        formResponse,
        obcId
    )

    /**
     * This method is used to populate the form field uiData from the default value map
     * @param formField
     * @param dispatcherFormValues
     * @param actualLoopCount
     * @param currentLoopCount
     * @return A Boolean, true if successfully populated or false if there is an error is populating default value.
     */
    private fun populateFormFieldUIDataFromDefaultValueOrUIFormResponses(
        formField: FormField,
        dispatcherFormValues: ArrayList<String>?,
        actualLoopCount: Int,
        currentLoopCount: Int
    ) : Boolean {
        if (dispatcherFormValues.isNotNull() && dispatcherFormValues!!.isNotEmpty()) {
            return if (actualLoopCount != -1 && currentLoopCount != -1 && actualLoopCount > currentLoopCount) {
                val index = actualLoopCount - currentLoopCount
                if (dispatcherFormValues.size > index) {
                    formField.uiData = dispatcherFormValues[index]
                } else {
                    formField.uiData = EMPTY_STRING
                }
                true
            } else {
                formField.uiData = dispatcherFormValues.first()
                true
            }
        }
        return false
    }

    fun processDispatcherFormValues(formClass: Int = -1,
                                    fieldList: ArrayList<MessageFormField>): HashMap<String, ArrayList<String>>{
        val dispatcherFormValuesFormFieldMap: HashMap<String, ArrayList<String>> = HashMap()
        // -1 reply with the same
        // 0 reply with new

        if (formClass > 0) return dispatcherFormValuesFormFieldMap

        fieldList.forEach { messageFormField ->
            if (messageFormField.text.isNotEmpty() && messageFormField.text.contains(SKIPPED, true)
                    .not()
            ) {
                if(dispatcherFormValuesFormFieldMap[messageFormField.qNum].isNull()){
                    val messageFormFieldValueList = ArrayList<String>()
                    messageFormFieldValueList.add(messageFormField.text)
                    dispatcherFormValuesFormFieldMap[messageFormField.qNum] = messageFormFieldValueList
                }else{
                    val valueList : ArrayList<String>? = dispatcherFormValuesFormFieldMap[messageFormField.qNum]
                    valueList?.let {
                        it.add(messageFormField.text)
                        dispatcherFormValuesFormFieldMap[messageFormField.qNum] = it
                    }
                }
            }
        }
        return dispatcherFormValuesFormFieldMap
    }

    fun splitDataFromDispatchFormSavePath(dispatchFormSavePath: String, unCompletedDispatchFormPath : DispatchFormPath) : DispatcherFormValuesPath {
        var dispatcherFormValuesPath = DispatcherFormValuesPath(
            EMPTY_STRING,
            EMPTY_STRING,
            EMPTY_STRING,
            EMPTY_STRING,
            EMPTY_STRING
        )
        dispatchFormSavePath.split("/").let { splitDispatchFormSavePath ->
            if (splitDispatchFormSavePath.size == 6) {
                val cidOfDraftedForm = splitDispatchFormSavePath[1]
                val vehicleIdOfDraftedForm = splitDispatchFormSavePath[2]
                val dispatchIdOfDraftedForm = splitDispatchFormSavePath[3]
                val stopIdOfDraftedForm = splitDispatchFormSavePath[4]
                val actionIdOfDraftedForm = splitDispatchFormSavePath[5]
                dispatcherFormValuesPath = DispatcherFormValuesPath(
                    cidOfDraftedForm, vehicleIdOfDraftedForm,
                    dispatchIdOfDraftedForm, stopIdOfDraftedForm, actionIdOfDraftedForm
                )
            }
        }
        return dispatcherFormValuesPath
    }

    fun showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection(tag : String) {
        getEnqueuedNotificationJob?.cancel()
        getEnqueuedNotificationJob = getEnqueuedNotificationScope.launch(CoroutineName(tag) + ioDispatcher) {
            appModuleCommunicator.showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection()
        }
        getEnqueuedNotificationJob?.invokeOnCompletion {
            cancelEnqueuedNotificationScope()
        }
    }

    fun cancelEnqueuedNotificationScope() = getEnqueuedNotificationScope.cancel()

    suspend fun deleteAllTrashMessages(customerId: String, vehicleId: String, token:String?, appCheckToken : String): CollectionDeleteResponse {
        return if (token != null && appCheckToken.isNotEmpty() && appModuleCommunicator.getAppFlavor().isNotNull()) {
            return messageFormRepo.deleteAllMessageInTrash(
                customerId, vehicleId, when (appModuleCommunicator.getAppFlavor()) {
                    FLAVOR_DEV -> BuildEnvironment.Dev
                    FLAVOR_QA -> BuildEnvironment.Qa
                    FLAVOR_PROD -> BuildEnvironment.Prod
                    else -> BuildEnvironment.Stg
                }, token, appCheckToken
            )
        } else {
            CollectionDeleteResponse(
                false,
                "Cid $customerId Vehicle $vehicleId either token or app flavor from app module communicator is null"
            )
        }
    }

    suspend fun deleteSelectedTrashMessage(customerId: String, vehicleId: String, asn: String, caller: String) {
        messageFormRepo.deleteSelectedMessageInTrash(customerId, vehicleId, asn, caller)
    }
}