package com.trimble.ttm.routemanifest.usecases

import androidx.datastore.preferences.core.intPreferencesKey
import com.google.gson.Gson
import com.trimble.ttm.commons.analytics.FirebaseAnalyticEventRecorder
import com.trimble.ttm.commons.logger.FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
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
import com.trimble.ttm.commons.utils.ZERO_TIME_DURATION
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.repo.MessageFormRepo
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FORM_CLASS_KEY
import com.trimble.ttm.formlibrary.utils.FORM_ID_KEY
import com.trimble.ttm.formlibrary.utils.FORM_NAME
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.HAS_PRE_DEFINED_RECIPIENTS
import com.trimble.ttm.formlibrary.utils.RECIPIENT_USERNAME
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY
import com.trimble.ttm.formlibrary.utils.toSafeLong
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.FormData
import com.trimble.ttm.routemanifest.model.getArrivedActionForGivenStop
import com.trimble.ttm.routemanifest.repo.FormsRepository
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.commons.utils.DRAFT_KEY
import com.trimble.ttm.commons.utils.SENT_KEY
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSES
import com.trimble.ttm.routemanifest.utils.FORM_COUNT_FOR_STOP
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

class FormUseCase(
    private val formsRepository: FormsRepository,
    private val encodedImageRefUseCase: EncodedImageRefUseCase,
    private val dispatchFormUseCase: DispatchFormUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val firebaseAnalyticEventRecorder: FirebaseAnalyticEventRecorder,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val formFieldDataUseCase: FormFieldDataUseCase,
    private val fetchDispatchStopsAndActionsUseCase: FetchDispatchStopsAndActionsUseCase,
    private val dispatcherFormValuesUseCase: DispatcherFormValuesUseCase,
    private val messageFormRepo: MessageFormRepo
) {
    private val tag = "FormUseCase"

    suspend fun getForm(
        formData: FormData,
        isActionResponseSentToServer: Boolean,
        shouldFillUiResponse: Boolean,
        isReplyWithSameForm: Boolean,
        uiFormResponse: UIFormResponse = UIFormResponse()
    ): Form = coroutineScope {
        if (formData.isFreeForm) {
            val formTemplateFetchJob = async {
                formsRepository.getFreeForm(formData.formId.toInt())
            }
            val uiResponseFetchJob = async { checkCacheAndFetchUIResponse(uiFormResponse, formData, isActionResponseSentToServer) }
            val formDataJobsResult = awaitAll(
                formTemplateFetchJob,
                uiResponseFetchJob
            )
            if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE) {
                formFieldDataUseCase.createFormFromResult(formDataJobsResult, true)
            } else {
                Log.d(FORM_DATA_RESPONSE, "Invalid result size while fetching freeform data ${formDataJobsResult.size}")
                Form()
            }
        } else {
            val formTemplateFetchJob = async {
                formsRepository.getForm(formData.customerId, formData.formId.toInt())
                    .apply {
                        formFieldsList.sortWith(compareBy { it.qnum })
                    }
            }
            val uiResponseFetchJob = async { checkCacheAndFetchUIResponse(uiFormResponse, formData, isActionResponseSentToServer) }
            val formDispatcherValuesFetchJob = async {
                dispatcherFormValuesUseCase.getDispatcherFormValues(
                    formData.customerId,
                    formData.vehicleId,
                    formData.dispatchId,
                    formData.stopId,
                    formData.actionId
                )
            }
            val formDataJobsResult = awaitAll(
                formTemplateFetchJob,
                uiResponseFetchJob,
                formDispatcherValuesFetchJob
            )

            if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE_DEFVALUE) {
                formFieldDataUseCase.createFormFromResult(formDataJobsResult, false, shouldFillUiResponse, isReplyWithSameForm)
            } else {
                Log.d(FORM_DATA_RESPONSE, "Invalid result size while fetching form data ${formDataJobsResult.size}")
                Form()
            }

        }
    }

    private suspend fun checkCacheAndFetchUIResponse(uiFormResponse: UIFormResponse, formData: FormData, isActionResponseSentToServer: Boolean) =
        if (uiFormResponse.formData.fieldData.isEmpty()) getSavedFormResponse(
            formData.formTemplatePath,
            formData.actionId,
            shouldFetchFromServer = isActionResponseSentToServer
        ) else uiFormResponse

    suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): ArrayList<Recipients> = formsRepository.getLatestFormRecipients(customerId, formID)

    suspend fun getFormData(
        path: String,
        actionId: String,
        isActionResponseSentToServer: Boolean
    ): UIFormResponse = getSavedFormResponse(
        path,
        actionId,
        shouldFetchFromServer = isActionResponseSentToServer
    )

    suspend fun saveFormData(
        path: String,
        formResponse: FormResponse,
        formDataToSave: FormDataToSave,
        caller: String
    ): Boolean {
        val users = messageFormRepo.getRecipientUserName(
            formResponse.recipients,
            formDataToSave.cid.toSafeLong()
        )
        val curTimeUtcInMillis = DateUtil.getCurrentTimeInMillisInUTC()
        val data = HashMap<String, Any>().apply {
            this[FORM_DATA_KEY] = formResponse
            this[FORM_ID_KEY] = formDataToSave.formId
            this[FORM_CLASS_KEY] = formDataToSave.formClass
            this[CREATED_AT] = curTimeUtcInMillis
            this[FORM_RESPONSE_TYPE] = formDataToSave.typeOfResponse
            this[FORM_NAME] = formDataToSave.formName
            this[RECIPIENT_USERNAME] = users.toList()
            this[HAS_PRE_DEFINED_RECIPIENTS] = formDataToSave.hasPredefinedRecipients
            this[UNCOMPLETED_DISPATCH_FORM_PATH] = formDataToSave.unCompletedDispatchFormPath
            this[DISPATCH_FORM_SAVE_PATH] = formDataToSave.dispatchFormSavePath
        }

        return dispatchFormUseCase.saveDispatchFormResponse(
            path,
            data,
            caller
        )
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

    fun mapImageUniqueIdentifier(
        formTemplateData: FormTemplate
    ) {
        encodedImageRefUseCase.mapImageUniqueIdentifier(
            formTemplateData = formTemplateData
        )
    }

    suspend fun getStopAction(
        vehicleId: String,
        customerId: String,
        dispatchId: String,
        stopId: String,
        actionId: String
    ): Action = formsRepository.getActionForStop(
        vehicleId,
        customerId,
        dispatchId,
        stopId,
        actionId
    )

    suspend fun removeFormFromPreference(
        stopId: Int,
        dataStoreManager: DataStoreManager
    ): String {
        val formList = UncompletedFormsUseCase.removeForm(
            dataStoreManager.getValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING),
            stopId
        )
        if (formList.isNotEmpty()) {
            Gson().toJson(formList).let { formListJson ->
                dataStoreManager.setValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, formListJson)
                Log.i(tag, "FORM_STACK_KEY -> $formListJson")
            }
        } else {
            dataStoreManager.setValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
        }
        removeFormFromPreferencesForStop(stopId)

        return if (formList.isNotEmpty()) Gson().toJson(formList) else ""
    }

    suspend fun removeFormFromPreferencesForStop(stopId: Int) {
        localDataSourceRepo.getFromFormLibModuleDataStore(
            intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
            ZERO
        )
            .also {
                if (it > ZERO) {
                    localDataSourceRepo.setToFormLibModuleDataStore(
                        intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
                        it.minus(it)
                    )
                }
            }
    }

    suspend fun getFreeForm(formTemplatePath: String, actionId: String, formId: Int): Form {
        val formTemplate = formsRepository.getFreeForm(formId)
        val uiResponse = getSavedFormResponse(
            formTemplatePath,
            actionId,
            shouldFetchFromServer = true
        )
        return Form(formTemplate, uiResponse, HashMap())
    }

    suspend fun getEncodedImage(
        companyId: String,
        vehicleId: String,
        imageID: String,
        caller: String
    ): String =
        encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(
            cid = companyId,
            truckNum = vehicleId,
            imageID = imageID,
            caller = caller
        )

    fun getFormsTemplateListFlow(): Flow<ArrayList<FormTemplate>> {
        return formsRepository.getFormsTemplateListFlow()
    }

    suspend fun formsSync(customerId: String, formDefList: ArrayList<FormDef>) {
        formsRepository.formsSync(customerId, formDefList)
    }

    suspend fun isFormSaved(path: String, actionId: String): Boolean {
        Log.d(tag, "Checking for PFMFormResponses isFormSaved method on querying")
        val (_, isPresentInSent) = formsRepository.getSavedFormResponseFromDraftOrSent(path, true, SENT_KEY)
        if (isPresentInSent) {
            return true
        } else {
            Log.d(
                tag,
                "Checking for FormResponses isFormSaved method on querying"
            )
            val (response, isPresentInFormResponse) = formsRepository.getFromFormResponses(
                path,
                actionId,
                true,
                FORM_RESPONSES
            )
            if (isPresentInFormResponse) {
                return response.isSyncDataToQueue
            }
        }
        return false
    }

    fun recordTimeDifferenceBetweenArriveAndFormSubmissionEvent(
        eventName: String,
        timeDuration: String
    ) {
        if (timeDuration.isNotEmpty() && timeDuration != ZERO_TIME_DURATION) {
            firebaseAnalyticEventRecorder.logCustomEventWithCustomAndTimeDurationParameters(
                eventName = eventName,
                duration = timeDuration
            )
        }
    }

    suspend fun doesStopHasUncompletedForm(stopId: Int): Boolean {
        val formCountOfStops = localDataSourceRepo.getFromFormLibModuleDataStore(
            intPreferencesKey(name = "$FORM_COUNT_FOR_STOP$stopId"),
            ZERO
        )
        return localDataSourceRepo.hasActiveDispatch() && formCountOfStops > 0
    }

    suspend fun onInvalidForm(
        caller: String,
        stopId: Int,
        actionId: Int
    ) {
        removeFormFromPreferencesForStop(stopId)
        logErrorDetailsIfInvalidForm(caller, stopId, actionId)
    }

    private suspend fun logErrorDetailsIfInvalidForm(
        caller: String,
        stopId: Int,
        actionId: Int
    ) {
        val currentDispatchId = appModuleCommunicator.getCurrentWorkFlowId(caller)
        val stopList = fetchDispatchStopsAndActionsUseCase.getAllActiveStopsAndActions("logErrorDetailsIfInvalidForm")
        val arrivedAction = stopList.getArrivedActionForGivenStop(stopId)
        Log.w(
            tag,
            "Invalid form for $currentDispatchId on stop: $stopId with action $actionId from caller: $caller " +
                    "Current stop detail in datastore activeTripId:$currentDispatchId, actionId:$actionId stopTripId:${arrivedAction?.dispid}, " +
                    "stopId: ${arrivedAction?.stopid}, actionId: ${arrivedAction?.actionid}, formID : ${arrivedAction?.driverFormid}, replyFormId :${arrivedAction?.forcedFormId}"
        )
    }

    suspend fun getSavedFormResponse(
        path: String,
        actionId: String,
        shouldFetchFromServer: Boolean
    ): UIFormResponse {
        //Check the data if it's present in PFMFormDraftResponses
        val (responseFromDraft, isPresentInDraft) =
            formsRepository.getSavedFormResponseFromDraftOrSent(
                path,
                shouldFetchFromServer,
                DRAFT_KEY
            )
        if (isPresentInDraft) return responseFromDraft

        //Check if the data is present in PFMFormResponses
        val (responseFromSent, isPresentInSent) =
            formsRepository.getSavedFormResponseFromDraftOrSent(
                path,
                shouldFetchFromServer,
                SENT_KEY
            )
        if (isPresentInSent) return responseFromSent

        //Check the data in FormResponses
        val (responseFromFormResponse, isPresentInFormResponse) = formsRepository.getFromFormResponses(
            path,
            actionId,
            shouldFetchFromServer,
            FORM_RESPONSES
        )
        if (isPresentInFormResponse) return responseFromFormResponse

        Log.d(tag, "No Form Response found for $path")
        return UIFormResponse()
    }
}