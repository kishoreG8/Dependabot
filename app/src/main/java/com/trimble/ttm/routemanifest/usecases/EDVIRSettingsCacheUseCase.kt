package com.trimble.ttm.routemanifest.usecases

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.model.MessageConfirmation
import com.trimble.ttm.formlibrary.usecases.MessageConfirmationUseCase
import com.trimble.ttm.formlibrary.utils.EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_ENABLED_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_MOBILE_ORIGINATED_MSG_COLLECTION
import com.trimble.ttm.formlibrary.utils.EDVIR_MSG_CONFIRMATION_COLLECTION
import com.trimble.ttm.formlibrary.utils.EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.MOBILE_ORIGINATED_DOCUMENT_ID
import com.trimble.ttm.routemanifest.repo.FireStoreCacheRepository
import com.trimble.ttm.routemanifest.utils.Utils.setCrashReportIdentifierAfterBackboneDataCache
import com.trimble.ttm.routemanifest.utils.Utils.toSafeInt
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Calendar
import java.util.Locale

class EDVIRSettingsCacheUseCase(
    private val fireStoreCacheRepository: FireStoreCacheRepository,
    private val messageConfirmationUseCase: MessageConfirmationUseCase,
    private val appModuleCommunicator: AppModuleCommunicator,
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) {

    private val tag = "EDVIRSettingsCacheUC"

    suspend fun listenToEDVIRSettingsLiveUpdates() {
        supervisorScope {
            try {
                setCrashReportIdentifierAfterBackboneDataCache(
                    appModuleCommunicator
                )
            } catch (e: Exception) {
                Log.e(tag, "Error listening to EDVIR settings ${e.message}", e)
            }
            if (appModuleCommunicator.doGetCid().isEmpty() || appModuleCommunicator.doGetObcId()
                    .isEmpty()
            ) {
                Log.e(
                    tag,
                    "Error listening to EDVIR settings. Customer id ${appModuleCommunicator.doGetCid()}, OBC id ${appModuleCommunicator.doGetObcId()}"
                )
                return@supervisorScope
            }

            listenToEdvirMetadata(this,EDVIR_ENABLED_SETTINGS_DOCUMENT_ID)
            listenToEdvirMetadata(this,EDVIR_MANDATORY_SETTINGS_DOCUMENT_ID)
            listenToEdvirSettings(this,EDVIR_POST_TRIP_SETTINGS_DOCUMENT_ID)
            listenToEdvirSettings(this,EDVIR_PRE_TRIP_SETTINGS_DOCUMENT_ID)
            listenToEdvirSettings(this,EDVIR_INTER_TRIP_SETTINGS_DOCUMENT_ID)
            listenToEdvirSettings(this,EDVIR_DOT_TRIP_SETTINGS_DOCUMENT_ID)
        }
    }

    private fun listenToEdvirMetadata(scope: CoroutineScope, edvirMetadata: String) {
        scope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
            fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId(),
                edvirMetadata
            )?.also { listenerFlow ->
                listenerFlow.catch { e ->
                    Log.d(
                        tag,
                        "Exception at getting edvir payload.so edvir acknowledging failed $edvirMetadata exception ${e.message}."
                    )
                }.onEach { eDVIRPayload ->
                    if (eDVIRPayload.dsn <= 0) {
                        Log.w(
                            tag,
                            "Invalid edvir data in listenToEdvirMetadata ${eDVIRPayload.intValue} dsn ${eDVIRPayload.dsn}"
                        )
                        return@onEach
                    }
                    confirmMessageViewStatus(eDVIRPayload, edvirMetadata)
                }.launchIn(this) //Do not change the coroutine scope to maintain structured concurrency.
            } ?: Log.e(
                tag,
                "Error listening to EDVIR metadata The flow is null. Customer id ${appModuleCommunicator.doGetCid()}, OBC id ${appModuleCommunicator.doGetObcId()}"
            )
        }
    }

    private suspend fun listenToEdvirSettings(scope: CoroutineScope, edvirSetting: String) {
        scope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
            fireStoreCacheRepository.addSnapshotListenerForEDVIRSetting(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId(),
                edvirSetting
            )?.also { listenerFlow ->
                listenerFlow.catch { e ->
                    Log.d(
                        tag,
                        "Exception while syncing $edvirSetting settings Cid ${appModuleCommunicator.doGetCid()} ObcId ${appModuleCommunicator.doGetObcId()} exception ${e.message}.",
                    )
                }.onEach { eDVIRPayload ->
                    if (eDVIRPayload.dsn <= 0) {
                        Log.w(
                            tag,
                            "Invalid edvir data in listenToEdvirSettings ${eDVIRPayload.intValue} dsn ${eDVIRPayload.dsn}"
                        )
                        return@onEach
                    }
                    syncEDVIRInspectionForm(
                        this,
                        eDVIRPayload, edvirSetting
                    )
                }
                    .launchIn(this) //Do not change the coroutine scope to maintain structured concurrency.
            } ?: Log.e(
                tag,
                "Error listening to EDVIR settings The flow is null. Customer id ${appModuleCommunicator.doGetCid()}, OBC id ${appModuleCommunicator.doGetObcId()}"
            )
        }

    }

    private fun syncEDVIRInspectionForm(
        coroutineScope: CoroutineScope,
        eDVIRPayload: EDVIRPayload, inspectionTypeForLogging: String
    ) {
        coroutineScope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
            try {
                confirmMessageViewStatus(eDVIRPayload, inspectionTypeForLogging)
                processInspectionFormSync(eDVIRPayload, coroutineScope)
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Error when trying to sync form for $inspectionTypeForLogging Cid ${appModuleCommunicator.doGetCid()} ObcId ${appModuleCommunicator.doGetObcId()} exception ${e.message}"
                )
            }
        }
    }

    suspend fun processInspectionFormSync(
        eDVIRPayload: EDVIRPayload,
        coroutineScope: CoroutineScope
    ) {
        val formId = eDVIRPayload.intValue
        val formClass = eDVIRPayload.formClass
        if (formId > 0 && formClass > -1) {
            val formSyncExceptionHandler =
                CoroutineExceptionHandler { _, exception ->
                    coroutineScope.launch(coroutineDispatcherProvider.io() + CoroutineName(tag)) {
                        Log.e(
                            tag,
                            "Error while syncing forms ${exception.message}",
                            exception,
                            "form id" to formId,
                            "form class" to formClass,
                            "customer id" to appModuleCommunicator.doGetCid(),
                            "obc id" to appModuleCommunicator.doGetObcId()
                        )
                    }
                }
            coroutineScope.launch(coroutineDispatcherProvider.io() + formSyncExceptionHandler) {
                Log.i(
                    tag,
                    "${eDVIRPayload.keyName} form ${eDVIRPayload.intValue} sync started."
                )
                fireStoreCacheRepository.syncFormData(
                    cid = appModuleCommunicator.doGetCid(),
                    formId = formId.toString(),
                    formClass = formClass
                )
            }
        } else {
            if (eDVIRPayload.pendIntValue>0 &&  eDVIRPayload.asn == 0 )  //Edvir payload int value can be 0 before acknowledgement
                Log.e(tag, "Invalid edvir data form $formId dsn ${eDVIRPayload.dsn}")
        }
    }

    /**
     * Send message viewed by driver ack back to PFM
     */
    private suspend fun confirmMessageViewStatus(
        eDVIRPayload: EDVIRPayload,
        inspectionTypeForLogging: String
    ) {
        if (eDVIRPayload.asn > 0) {
            val messageConfirmation =
                MessageConfirmation(
                    eDVIRPayload.asn,
                    eDVIRPayload.dsn.toSafeInt(),
                    getUTCFormattedDate(Calendar.getInstance(Locale.getDefault()).time)
                )
            messageConfirmationUseCase.sendEdvirMessageViewedConfirmation(
                "$EDVIR_MSG_CONFIRMATION_COLLECTION/${appModuleCommunicator.doGetCid()}/${appModuleCommunicator.doGetObcId()}/$MOBILE_ORIGINATED_DOCUMENT_ID/$EDVIR_MOBILE_ORIGINATED_MSG_COLLECTION/${eDVIRPayload.asn}",
                messageConfirmation, inspectionTypeForLogging
            )
        }
    }
}