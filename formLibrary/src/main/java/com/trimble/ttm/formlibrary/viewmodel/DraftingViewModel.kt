package com.trimble.ttm.formlibrary.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.formlibrary.model.FormDataToSave
import com.trimble.ttm.formlibrary.usecases.DraftingUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_DRAFT_RESPONSE_COLLECTION
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_TYPE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class DraftingViewModel(
    private val appModuleCommunicator: AppModuleCommunicator,
    private val draftingUseCase: DraftingUseCase,
    private val featureFlagGateKeeper: FeatureGatekeeper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    var showDraftMessage: Boolean = false

    val draftProcessFinished: SharedFlow<Boolean> = draftingUseCase.draftProcessFinished

    val initDraftProcessing: SharedFlow<Boolean> = draftingUseCase.initDraftProcessing

    private var saveToDraftsFeatureFlag = false

    internal var unCompletedDispatchFormPath = DispatchFormPath()
    internal var dispatchFormSavePath = EMPTY_STRING

    private val tag = "DraftingViewModel"

    fun makeDraft(
        scope: CoroutineScope,
        formTemplateData: FormTemplate,
        isOpenedForDraft: Boolean,
        formResponseType: String,
        replyFormName: String,
        hasPredefinedRecipients: Boolean,
        formClass:Int
    ) {
        scope.launch(
            ioDispatcher +
                CoroutineName("$tag make Draft")
        ) {
            makeDraft(
                formDataToSave = getFormDataToDraftAsync(
                    scope,
                    formTemplateData,
                    isOpenedForDraft,
                    formResponseType,
                    replyFormName,
                    hasPredefinedRecipients,
                    formClass
                ).await(), caller = tag
            )
        }
    }

    fun makeDraft(
        formDataToSave: FormDataToSave, caller: String
    ) = draftingUseCase.makeDraft(formDataToSave, caller, needToSendImages = true)

    fun setDraftProcessAsFinished(scope: CoroutineScope) {
        scope.launch(
            ioDispatcher + CoroutineName("$tag Set draft process as finished")
        ) {
            draftingUseCase.setDraftProcessAsFinished()
        }
    }

    fun restoreDraftProcessFinished(scope: CoroutineScope) {
           scope.launch(
                ioDispatcher + CoroutineName("$tag Restore draft process finished")
            ) {
                draftingUseCase.restoreDraftProcessFinished()
            }
    }

    fun restoreInitDraftProcessing(scope: CoroutineScope) {
        scope.launch(
                ioDispatcher + CoroutineName("$tag Restore init draft processing")
            ) {
                draftingUseCase.restoreInitDraftProcessing()
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getFormDataToDraftAsync(
        scope: CoroutineScope,
        formTemplateData: FormTemplate,
        isOpenedForDraft: Boolean,
        formResponseType: String,
        replyFormName: String,
        hasPredefinedRecipients: Boolean,
        formClass: Int
    ): Deferred<FormDataToSave> =
        scope.async(
            ioDispatcher + CoroutineName(DraftingViewModel::getFormDataToDraftAsync.name)
        ) {
            val cid = appModuleCommunicator.doGetCid()
            val vehicleNumber = appModuleCommunicator.doGetTruckNumber()
            val obcId = appModuleCommunicator.doGetObcId()

            return@async FormDataToSave(
                formTemplate = formTemplateData,
                path = "$INBOX_FORM_DRAFT_RESPONSE_COLLECTION/$cid/$vehicleNumber",
                formId = formTemplateData.formDef.formid.toString(),
                typeOfResponse = if (isOpenedForDraft) {
                    formResponseType
                } else {
                    INBOX_FORM_RESPONSE_TYPE
                },
                formName = replyFormName,
                formClass = formClass,
                cid = cid,
                hasPredefinedRecipients = hasPredefinedRecipients,
                obcId = obcId,
                unCompletedDispatchFormPath = unCompletedDispatchFormPath,
                dispatchFormSavePath = dispatchFormSavePath
            )
        }

    fun lookupSaveToDraftsFeatureFlag(scope: CoroutineScope) {
        scope.launch(
            ioDispatcher + CoroutineName(tag)
        ) {
            saveToDraftsFeatureFlag = featureFlagGateKeeper.isFeatureTurnedOn(
                FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG,
                appModuleCommunicator.getFeatureFlags(),
                appModuleCommunicator.doGetCid()
            )
        }
    }
    fun getSaveToDraftsFeatureFlag() = saveToDraftsFeatureFlag
}
