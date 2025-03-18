package com.trimble.ttm.formlibrary.usecases

import android.os.Bundle
import com.trimble.ttm.commons.logger.INSPECTION_FORM_DATA_RESPONSE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.repo.FirebaseAuthRepo
import com.trimble.ttm.commons.usecase.FormFieldDataUseCase
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE
import com.trimble.ttm.formlibrary.model.EDVIRFormResponseRepoData
import com.trimble.ttm.formlibrary.model.EDVIRFormResponseUsecasesData
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.model.MessageConfirmation
import com.trimble.ttm.formlibrary.repo.EDVIRFormRepoImpl
import com.trimble.ttm.formlibrary.repo.EDVIRInspectionsRepo
import com.trimble.ttm.formlibrary.utils.EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_ENABLED_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_MOBILE_ORIGINATED_MSG_COLLECTION
import com.trimble.ttm.formlibrary.utils.EDVIR_MSG_CONFIRMATION_COLLECTION
import com.trimble.ttm.formlibrary.utils.EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.MANDATORY_EDVIR_INTENT_ACTION
import com.trimble.ttm.formlibrary.utils.MOBILE_ORIGINATED_DOCUMENT_ID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Calendar
import java.util.Locale

class EDVIRFormUseCase(
    private val eDVIRFormRepository: EDVIRFormRepoImpl,
    private val eDVIRInspectionsRepo: EDVIRInspectionsRepo,
    private val firebaseAuthRepo: FirebaseAuthRepo,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val formFieldDataUseCase: FormFieldDataUseCase,
    private val messageConfirmationUseCase: MessageConfirmationUseCase,
) {

    suspend fun getForm(customerId: String, formId: Int, isMandatoryInspection:Boolean, uiFormResponse: UIFormResponse, shouldFillUiResponse: Boolean, savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf()) : Form = coroutineScope {

        val formTemplateFetchJob = async {
            eDVIRFormRepository.getForm(customerId, formId,isMandatoryInspection).apply {
                formFieldsList.sortWith(compareBy { it.qnum })
            }
        }
        val uiResponseFetchJob = async {
            uiFormResponse
        }

        val formDataJobsResult = awaitAll(
            formTemplateFetchJob,
            uiResponseFetchJob
        )
        if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE) {
            formFieldDataUseCase.createFormFromResult(formDataJobsResult,false, shouldFillUiResponse = shouldFillUiResponse, savedImageUniqueIdentifierToValueMap = savedImageUniqueIdentifierToValueMap)
        }else{
            Log.e(INSPECTION_FORM_DATA_RESPONSE,"Invalid result size while fetching form data ${formDataJobsResult.size}")
            Form()
        }
    }

    suspend fun getFreeForm(formId: Int, isMandatoryInspection:Boolean, shouldFillUiResponse: Boolean, uiFormResponse: UIFormResponse): Form = coroutineScope {

        val formTemplateFetchJob = async {
            eDVIRFormRepository.getFreeForm(formId, isMandatoryInspection)
        }
        val uiResponseFetchJob = async {
            uiFormResponse
        }
        val formDataJobsResult = awaitAll(
            formTemplateFetchJob,
            uiResponseFetchJob
        )
        if (formDataJobsResult.size == FORMRESULT_SIZE_INCLUDING_FORMDEF_FORMRESPONSE) {
            formFieldDataUseCase.createFormFromResult(formDataJobsResult,true, shouldFillUiResponse = shouldFillUiResponse)
        }else{
            Log.e(INSPECTION_FORM_DATA_RESPONSE,"Invalid result size while fetching form data ${formDataJobsResult.size}")
            Form()
        }

    }

    suspend fun saveEDVIRFormResponse(
        saveEDVIRFormResponseData: EDVIRFormResponseUsecasesData
    ) = eDVIRFormRepository.saveEDVIRFormResponse(
        EDVIRFormResponseRepoData(
            saveEDVIRFormResponseData.customerId,
            saveEDVIRFormResponseData.obcId,
            saveEDVIRFormResponseData.curTimeUtcFormattedStr,
            saveEDVIRFormResponseData.formResponse,
            saveEDVIRFormResponseData.driverName,
            saveEDVIRFormResponseData.formId,
            saveEDVIRFormResponseData.formClass,
            saveEDVIRFormResponseData.currentTimeInMillisInUTC,
            saveEDVIRFormResponseData.inspectionType,
            saveEDVIRFormResponseData.isSyncToQueue
        )
    )

    suspend fun getEDVIRInspectionSetting(
        customerId: String,
        obcId: String,
        inspectionDocumentId: String
    ) = eDVIRInspectionsRepo.getEDVIRInspectionSetting(customerId, obcId, inspectionDocumentId)

    suspend fun getEDVIREnabledSetting(customerId: String, dsn: String) =
        eDVIRInspectionsRepo.getEDVIREnabledSetting(customerId, dsn)

    suspend fun syncEdvirChanges() {
        supervisorScope {
            launch { syncEdvirSettings(EDVIR_ENABLED_SETTINGS_DOCUMENT_ID) }
            launch { syncEdvirSettings(EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID) }
            launch { syncEdvirSettings(EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID) }
            launch { syncEdvirSettings(EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID) }
            launch { syncEdvirSettings(EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID) }
            launch { syncEdvirSettings(EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID) }
        }
    }

    private suspend fun syncEdvirSettings(edvirMetadata: String) {
        getEDVIRInspectionSetting(
            appModuleCommunicator.doGetCid(),
            appModuleCommunicator.doGetObcId(), edvirMetadata
        ).also {
            if (it.asn > 0 && it.dsn > 0) {
                confirmMessageViewStatus(it, edvirMetadata)
            }
        }
    }

    private suspend fun confirmMessageViewStatus(
        eDVIRPayload: EDVIRPayload,
        inspectionTypeForLogging: String
    ) {
        val messageConfirmation =
            MessageConfirmation(
                eDVIRPayload.asn,
                eDVIRPayload.dsn,
                DateUtil.getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
            )
        messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(
            "$EDVIR_MSG_CONFIRMATION_COLLECTION/${appModuleCommunicator.doGetCid()}/${appModuleCommunicator.doGetObcId()}/$MOBILE_ORIGINATED_DOCUMENT_ID/$EDVIR_MOBILE_ORIGINATED_MSG_COLLECTION/${eDVIRPayload.asn}",
            messageConfirmation, inspectionTypeForLogging
        )
    }

    suspend fun isEDVIREnabled(customerId: String, dsn: String) =
        eDVIRInspectionsRepo.isEDVIRSettingsExist(customerId, dsn)

    suspend fun getEDVIRFormDataResponse(customerId: String, obcId: String, createdAt: String) =
        eDVIRFormRepository.getEDVIRFormDataResponse(customerId, obcId, createdAt)

    suspend fun getDeviceToken() =
        appModuleCommunicator.getDeviceToken()

    suspend fun getFireBaseToken(deviceToken: String) =
        firebaseAuthRepo.getFireBaseToken(deviceToken) ?: ""

    suspend fun authenticateFirestore(firebaseToken: String) =
        firebaseAuthRepo.authenticateFirestore(firebaseToken)

    fun setDockMode(bundle: Bundle) =
        appModuleCommunicator.setDockMode(bundle, MANDATORY_EDVIR_INTENT_ACTION)

    fun resetDockMode() = appModuleCommunicator.resetDockMode()

    suspend fun getCustomerId(): String = appModuleCommunicator.doGetCid()

    suspend fun getVehicleId(): String = appModuleCommunicator.doGetTruckNumber()

    suspend fun getOBCId(): String = appModuleCommunicator.doGetObcId()

    suspend fun setCrashReportIdentifier() = appModuleCommunicator.setCrashReportIdentifier()

    fun startForegroundService() = appModuleCommunicator.startForegroundService()

    fun getCurrentDrivers() = appModuleCommunicator.getCurrentDrivers()

}