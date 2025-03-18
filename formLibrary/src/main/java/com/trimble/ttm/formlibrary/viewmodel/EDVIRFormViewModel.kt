package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.perf.metrics.AddTrace
import com.trimble.ttm.backbone.api.data.eld.CurrentUser
import com.trimble.ttm.backbone.api.data.user.UserName
import com.trimble.ttm.commons.logger.INSPECTION_FLOW
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.model.isFreeForm
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.DateUtil.getCurrentTimeInMillisInUTC
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_MANDATORY_INSPECTION
import com.trimble.ttm.formlibrary.model.EDVIRFormData
import com.trimble.ttm.formlibrary.model.EDVIRFormResponseUsecasesData
import com.trimble.ttm.formlibrary.model.InspectionType
import com.trimble.ttm.formlibrary.usecases.EDVIRFormUseCase
import com.trimble.ttm.formlibrary.usecases.MessageFormUseCase
import com.trimble.ttm.formlibrary.usecases.UpdateInspectionInformationUseCase
import com.trimble.ttm.formlibrary.utils.DRIVER_ACTION_LOGIN
import com.trimble.ttm.formlibrary.utils.DRIVER_ACTION_LOGOUT
import com.trimble.ttm.formlibrary.utils.EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.FETCH_FORM_TO_BE_RENDERED_EDVIRFORMVIEWMODEL
import com.trimble.ttm.formlibrary.utils.GET_EDVIR_FORM_RESPONSE_EDVIRFORMVIEWMODEL
import com.trimble.ttm.formlibrary.utils.GET_FORM_ID_FOR_INSPECTION_EDVIRFORMVIEWMODEL
import com.trimble.ttm.formlibrary.utils.SAVE_EDVIRFORM_DATA_EDVIRFORMVIEWMODEL
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import java.util.Date

class EDVIRFormViewModel(
    private val eDVIRFormUseCase: EDVIRFormUseCase,
    authenticateUseCase: AuthenticateUseCase,
    private val updateInspectionInformationUseCase: UpdateInspectionInformationUseCase,
    private val messageFormUseCase: MessageFormUseCase,
    private val formDataStoreManager: FormDataStoreManager,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    application: Application
) : AuthenticationViewModel(
    eDVIRFormUseCase,
    authenticateUseCase,
    formDataStoreManager,
    application
), KoinComponent {

    private val tag = "EDVIRFormVM"
    private val emptyIdLogKey = "Customer id or OBC id is empty"
    private var formResponse: FormResponse = FormResponse()

    private val _shouldShowDialog = MutableLiveData<Boolean>()
    val shouldShowDialog: LiveData<Boolean> = _shouldShowDialog

    init {
        if (formResponse.fieldData.size == 0) {
            formResponse = createFormResponse()
        }
    }

    @AddTrace(name = FETCH_FORM_TO_BE_RENDERED_EDVIRFORMVIEWMODEL, enabled = true)
    suspend fun fetchFormToBeRendered(
        customerId: String,
        formDef: FormDef,
        isMandatoryInspection:Boolean,
        uiFormResponse: UIFormResponse,
        shouldFillUiResponse: Boolean,
        savedImageUniqueIdentifierToValueMap : HashMap<String, Pair<String,Boolean>> = hashMapOf()
    ): Form = if (formDef.isFreeForm()) eDVIRFormUseCase.getFreeForm(formDef.formid,isMandatoryInspection,shouldFillUiResponse,uiFormResponse)
    else eDVIRFormUseCase.getForm(customerId, formDef.formid,isMandatoryInspection,uiFormResponse,shouldFillUiResponse, savedImageUniqueIdentifierToValueMap = savedImageUniqueIdentifierToValueMap)

    fun setMandatoryInspection(isMandatoryInspection: Boolean) {
        viewModelScope.launch(ioDispatcher + CoroutineName(tag)) {
            Log.d(tag, "isInMandatoryEDVIR $isMandatoryInspection")
            formDataStoreManager.setValue(IS_MANDATORY_INSPECTION, isMandatoryInspection)
        }
    }

    suspend fun hasValidBackboneData(onValidBackboneData: (Boolean, String) -> Unit) =
        if (appModuleCommunicator.doGetCid().isNotEmpty()) onValidBackboneData(
            true,
            appModuleCommunicator.doGetCid()
        )
        else onValidBackboneData(false, "")

    @AddTrace(name = SAVE_EDVIRFORM_DATA_EDVIRFORMVIEWMODEL, enabled = true)
    fun saveEDVIRFormData(
        saveEDVIRFormData: EDVIRFormData,
        date: Date
    ) {
        if (saveEDVIRFormData.customerId.isEmpty() || saveEDVIRFormData.obcId.isEmpty()) {
            Log.e(tag, emptyIdLogKey)
            return
        }
        val curTimeUtcFormattedStr = getUTCFormattedDate(date)
        if (curTimeUtcFormattedStr.isEmpty()) {
            Log.e(tag, "Current time UTC formatted string is empty")
            return
        }
        formResponse = createFormResponse()
        formResponse.let { response ->
            appModuleCommunicator.getAppModuleApplicationScope()
                .launch(CoroutineName(tag) + ioDispatcher) {
                    messageFormUseCase.addFieldDataInFormResponse(saveEDVIRFormData.formTemplate,
                        response,
                        appModuleCommunicator.doGetObcId()).collect { formResponse ->
                        eDVIRFormUseCase.saveEDVIRFormResponse(
                            EDVIRFormResponseUsecasesData(
                                saveEDVIRFormData.customerId,
                                saveEDVIRFormData.obcId,
                                curTimeUtcFormattedStr,
                                formResponse,
                                saveEDVIRFormData.driverName,
                                saveEDVIRFormData.formId,
                                saveEDVIRFormData.formClass,
                                getCurrentTimeInMillisInUTC(),
                                saveEDVIRFormData.inspectionType,
                                saveEDVIRFormData.isSyncToQueue)
                        ).also { isFormSaved ->
                            Log.i(tag, "EDVIR form response: $formResponse. isFormSaved: $isFormSaved")
                            this.coroutineContext.job.cancel()
                        }
                    }
                }
        }
    }

    private fun createFormResponse() = FormResponse()

    @AddTrace(name = GET_FORM_ID_FOR_INSPECTION_EDVIRFORMVIEWMODEL, enabled = true)
    suspend fun getFormIdForInspection(
        customerId: String,
        obcId: String,
        inspectionType: String,
        uiFormResponse: UIFormResponse
    ): FormDef {
        if (customerId.isEmpty() || obcId.isEmpty()) {
            Log.e(tag, emptyIdLogKey)
            return FormDef(formid = -1, formClass = -1)
        }
        if (inspectionType.isEmpty()) {
            Log.e(tag, "Inspection type is empty")
            return FormDef(formid = -1, formClass = -1)
        }
        return try {
            viewModelScope.async(ioDispatcher + CoroutineName(tag)) {
                val inspectionDocumentId = when (inspectionType) {
                    InspectionType.PreInspection.name -> {
                        EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID
                    }
                    InspectionType.PostInspection.name -> {
                        EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID
                    }
                    InspectionType.InterInspection.name -> {
                        EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID
                    }
                    else -> {
                        EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID
                    }
                }
                //if edvir is enabled from customer manager in pfm. Display the form for the respective inspection type else show cff
                return@async if (eDVIRFormUseCase.getEDVIREnabledSetting(customerId, obcId).intValue == 1) {
                    with(eDVIRFormUseCase.getEDVIRInspectionSetting(
                        customerId,
                        obcId,
                        inspectionDocumentId
                    )) {
                        FormDef(formid = this.intValue, formClass = this.formClass)
                    }
                } else {
                    with(messageFormUseCase.getFreeForm(uiFormResponse).formTemplate.formDef) {
                        FormDef(formid = this.formid, formClass = this.formClass)
                    }
                }
            }.await()
        } catch (e: Exception) {
            Log.e(tag, "Exception in getFormIdForInspection ${e.message}", e)
            FormDef(formid = -1, formClass = -1)
        }
    }

    @AddTrace(name = GET_EDVIR_FORM_RESPONSE_EDVIRFORMVIEWMODEL, enabled = true)
    suspend fun getEDVIRFormDataResponse(
        customerId: String,
        obcId: String,
        createdAt: String
    ): UIFormResponse {
        if (customerId.isEmpty() || obcId.isEmpty()) {
            Log.e(tag, emptyIdLogKey)
            return UIFormResponse()
        }
        return try {
            viewModelScope.async(ioDispatcher + CoroutineName(tag)) {
                return@async eDVIRFormUseCase.getEDVIRFormDataResponse(customerId, obcId, createdAt)
            }.await()
        } catch (e: Exception) {
            Log.e(tag, "Exception in getEDVIRFormDataResponse ${e.message}", e)
            UIFormResponse()
        }
    }

    fun setDockMode(bundle: Bundle) = eDVIRFormUseCase.setDockMode(bundle)

    fun releaseDockMode() = eDVIRFormUseCase.resetDockMode()

    suspend fun updateInspectionInformation(inspectionType: String) {
        when (inspectionType) {
            // App launcher sends login and logout strings for PreInspection and PostInspection respectively
            InspectionType.PreInspection.name -> updateInspectionInformationUseCase.updatePreTripInspectionRequire(false)
            InspectionType.PostInspection.name -> updateInspectionInformationUseCase.updatePostTripInspectionRequire(false)
        }
    }

    suspend fun updatePreviousAnnotationInformation(inspectionType: String, annotation: String) {
        when (inspectionType) {
            InspectionType.PreInspection.name -> {
                updateInspectionInformationUseCase.updatePreviousPreTripAnnotation(annotation)
            }
            InspectionType.PostInspection.name -> {
                updateInspectionInformationUseCase.updatePreviousPostTripAnnotation(annotation)
            }
        }
    }

    suspend fun isInspectionRequired(inspectionType: String): Boolean = when (inspectionType) {
        InspectionType.PreInspection.name ->
            with(updateInspectionInformationUseCase) {
                setLastSignedInDriversCount(getCurrentDrivers().size)
                //adding the checking in the annotations, avoids repeat unnecessary inspections
                isPreTripInspectionRequired() && getPreviousAnnotation(inspectionType).isEmpty()
            }
        InspectionType.PostInspection.name ->
            with(updateInspectionInformationUseCase) {
                setLastSignedInDriversCount(getCurrentDrivers().size)
                //adding the checking in the annotations, avoids repeat unnecessary inspections
                isPostTripInspectionRequired() && getPreviousAnnotation(inspectionType).isEmpty()
            }
        else -> true
    }

    suspend fun inspectionCompleted(inspectionType: String, isFormMandatory: Boolean) {
        Log.i(tag, "inspectionType: $inspectionType, isFormMandatory: $isFormMandatory")
        if (inspectionType == InspectionType.PostInspection.name) {
            with(updateInspectionInformationUseCase) {
                updateInspectionRequire(true)
                if (getLastSignedInDriversCount().isLessThanAndEqualTo(1) && isFormMandatory && getPreviousAnnotation(
                        inspectionType
                    ).isNotEmpty()
                ) {
                    clearPreviousAnnotations()
                    Log.d(
                        INSPECTION_FLOW,
                        "Logging out last driver so clearing previous annotations"
                    )
                    /*
                     we remove the clearPreviousAnnotations() because if we clear the annotation
                     every time the inspection is completed, we don't have any way to know if we
                     did that inspection, and exist a possibility to repeat it, for more information
                     check the task https://jira.trimble.tools/browse/MAPP-4505
                    */
                }
            }
        }
    }

    suspend fun getPreviousAnnotation(inspectionType: String): String = when (inspectionType) {
            InspectionType.PreInspection.name -> updateInspectionInformationUseCase.getPreviousPreTripAnnotation()
            InspectionType.PostInspection.name -> updateInspectionInformationUseCase.getPreviousPostTripAnnotation()
            else -> EMPTY_STRING
        }

    fun getCurrentDrivers() = eDVIRFormUseCase.getCurrentDrivers()

    fun getProperTypeByDriverAction(inspectionType:String) :String {
        return when (inspectionType) {
            // App launcher sends login and logout strings for PreInspection
            // and PostInspection respectively
            DRIVER_ACTION_LOGIN ->
                InspectionType.PreInspection.name
            DRIVER_ACTION_LOGOUT ->
                InspectionType.PostInspection.name
            else -> {
                inspectionType
            }
        }
    }

    fun getCurrentDriverId() : String {
        return try{
            appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()[CurrentUser]!!.data.toString()
        }catch (e:Exception){
            ""
        }
    }

    fun getCurrentDriverName(driverId:String) : String {
        return try{
            val nameEntry = appModuleCommunicator.getCurrentUserAndUserNameFromBackbone()[UserName]!!.data
            return "${nameEntry?.get(driverId)?.firstName} ${nameEntry?.get(driverId)?.lastName}"
        }catch (e:Exception){
            ""
        }
    }

    fun observeInspectionAlertDialog(){
        // This will properly displays the dialog when we have a persistent notification on the inspection screen
        viewModelScope.safeLaunch (ioDispatcher + CoroutineName(tag)) {
            formDataStoreManager.fieldObserver(FormDataStoreManager.SHOW_INSPECTION_ALERT_DIALOG)
                .onEach {
                    if (it == true) {
                        _shouldShowDialog.postValue(true)
                        formDataStoreManager.setValue(
                            FormDataStoreManager.SHOW_INSPECTION_ALERT_DIALOG,
                            false
                        )
                    }
                }.launchIn(this)
        }
    }

    fun syncEdvirChanges(){
        viewModelScope.launch(CoroutineName(tag) + ioDispatcher) {
            eDVIRFormUseCase.syncEdvirChanges()
        }
    }
}