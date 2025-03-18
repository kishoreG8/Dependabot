package com.trimble.ttm.commons.moduleCommunicator

import android.os.Bundle
import com.trimble.ttm.backbone.api.MultipleEntryQuery
import com.trimble.ttm.backbone.api.data.eld.UserEldStatus
import com.trimble.ttm.commons.model.DeviceFcmToken
import com.trimble.ttm.commons.model.DriverDeviceInfo
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

interface AppModuleCommunicator {
    suspend fun getDeviceToken() : String?

    fun startForegroundService()

    fun isForegroundServiceRunning() : Boolean

    fun setDockMode(bundle: Bundle, intentAction: String)

    fun resetDockMode()

    fun getCurrentUserAndUserNameFromBackbone(): MultipleEntryQuery.Result

    suspend fun getUserEldStatus(): Map<String, UserEldStatus>?

    suspend fun setCrashReportIdentifier()

    fun getAppFlavor(): String

    fun getCurrentDrivers(): Set<String>

    fun getAppModuleApplicationScope(): CoroutineScope

    fun setIsInManualInspectionScreen(isInManualInspectionScreen: Boolean)

    fun getInternetEvents(): SharedFlow<Boolean>

    suspend fun hasActiveDispatch(caller: String,logError:Boolean): Boolean

    fun doSetCid(newCid: String)

    fun doSetTruckNumber(newTruckNumber: String)

    fun doSetObcId(newObcId: String)

    suspend fun doGetCid():String

    suspend fun doGetTruckNumber():String

    suspend fun doGetObcId():String

    suspend fun doGetVid(): Long

    suspend fun getCurrentWorkFlowId(caller:String):String

    suspend fun getSelectedDispatchId(caller: String): String

    suspend fun setCurrentWorkFlowId(currentWorkFlowId:String)

    suspend fun setCurrentWorkFlowDispatchName(dispatchName: String)

    suspend fun isFirebaseAuthenticated(): Boolean

    suspend fun getFCMTokenFirestoreStatus(): Boolean

    suspend fun setFCMTokenFirestoreStatusInDataStore(isTokenStoredInFirestore: Boolean)

    suspend fun setLastFetchedFcmTokenToDatastore(deviceFcmToken: DeviceFcmToken)

    suspend fun getLastFetchedFcmTokenFromDatastore() : DeviceFcmToken

    fun setFeatureFlags(map: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>)

    fun getFeatureFlags(): Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>

    suspend fun hasOnlyOneDispatchOnList(): Boolean

    suspend fun restoreSelectedDispatch()

    fun getConsumerKey() : String

    suspend fun getLatestDriverDeviceInfo() : DriverDeviceInfo?

    suspend fun getInternetConnectivityStatusByGooglePing(): Boolean

    fun listenForFeatureFlagDocumentUpdates()

    fun monitorDeviceChanges()

    suspend fun showEnqueuedNotificationsWhenTheUserMovesOutOfMandatoryInspection()

    suspend fun doGetCurrentUser(coroutineScope: CoroutineScope) : String
}