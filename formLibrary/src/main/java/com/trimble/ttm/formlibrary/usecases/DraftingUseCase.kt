package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.logger.DISPATCH_FORM_DRAFT
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.model.MessageFormData
import com.trimble.ttm.formlibrary.repo.DraftingRepo
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class DraftingUseCase(
    private val messageFormUseCase: MessageFormUseCase,
    private val draftingRepo: DraftingRepo,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val dispatcherProvider: DispatcherProvider,
    private val dataStoreManager: DataStoreManager
) {

    private val tag = "DraftingUseCase"

    val draftProcessFinished: SharedFlow<Boolean> = draftingRepo.draftProcessFinished

    val initDraftProcessing: SharedFlow<Boolean> = draftingRepo.initDraftProcessing

    fun makeDraft(
        formDataToSave: FormDataToSave, caller: String, needToSendImages : Boolean
    ) {
        appModuleCommunicator.getAppModuleApplicationScope()
            .launch(CoroutineName(tag) + dispatcherProvider.io()) {
                messageFormUseCase.mapImageUniqueIdentifier(formDataToSave.formTemplate)
                checkAndAddDispatchName(formDataToSave.unCompletedDispatchFormPath, formDataToSave.dispatchFormSavePath)
                messageFormUseCase.addFieldDataInFormResponse(
                    formDataToSave.formTemplate,
                    FormResponse(),
                    formDataToSave.obcId
                ).collect { formResponse ->
                    messageFormUseCase.saveFormData(
                        MessageFormData(
                            formDataToSave.path,
                            formResponse,
                            formDataToSave.formId,
                            formDataToSave.typeOfResponse,
                            formDataToSave.formName,
                            formDataToSave.formClass,
                            formDataToSave.cid.toSafeLong(),
                            formDataToSave.hasPredefinedRecipients,
                            formDataToSave.unCompletedDispatchFormPath,
                            formDataToSave.dispatchFormSavePath
                        )
                    ).also { isFormSaved ->
                        Log.i(DISPATCH_FORM_DRAFT, "isDraftSaved: $isFormSaved")
                        //here updated flow to indicates that draft process is finished
                        setDraftProcessAsFinished()
                        this.coroutineContext.job.cancel()
                    }
                }
            }
    }

    suspend fun setDraftProcessAsFinished() = draftingRepo.setDraftProcessFinished(true)

    suspend fun restoreDraftProcessFinished(){
        draftingRepo.restoreDraftProcessFinished()
    }

    suspend fun restoreInitDraftProcessing(){
        draftingRepo.restoreInitDraftProcessing()
    }

    private suspend fun checkAndAddDispatchName(dispatchFormPath: DispatchFormPath, path: String) {
        if (dispatchFormPath.dispatchName.isEmpty() && getDispatchIDFromPath(path) == dataStoreManager.getValue(
                DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)) {
            dispatchFormPath.dispatchName =
                dataStoreManager.getValue(DataStoreManager.CURRENT_DISPATCH_NAME_KEY, EMPTY_STRING)
            Log.d(tag, "Dispatch name added to dispatchFormPath ${dispatchFormPath.dispatchName}")
        }
    }

    private fun getDispatchIDFromPath(path: String): String {
        return path.split("/").getOrNull(3) ?: return EMPTY_STRING
    }

}