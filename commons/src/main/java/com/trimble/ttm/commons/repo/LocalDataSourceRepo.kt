package com.trimble.ttm.commons.repo

import androidx.datastore.preferences.core.Preferences
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import org.koin.core.component.KoinComponent

interface LocalDataSourceRepo : KoinComponent {
    suspend fun hasActiveDispatch(): Boolean
    suspend fun getActiveDispatchId(caller:String):String
    suspend fun getSelectedDispatchId(caller: String) : String
    suspend fun removeSelectedDispatchIdFromLocalCache()
    suspend fun setActiveDispatchId(dispatchId: String)
    suspend fun setCurrentWorkFlowDispatchName(dispatchName: String)
    suspend fun setIsDyaShownForStop(isDyaShown: Boolean)

    //If returned string is empty from datastore. Then, fromJsonString will return null. Thus, Stop is nullable here
    suspend fun getCurrentStop(): Stop?
    suspend fun setLastSentTripPanelMessageId(value: Int)
    suspend fun getLastSentTripPanelMessageId(): Int
    suspend fun isFCMTokenSavedInFirestore(): Boolean
    suspend fun setFCMTokenFirestoreStatusInDataStore(isTokenStoredInFirestore: Boolean)
    suspend fun <T> setToAppModuleDataStore(key: Preferences.Key<T>, value: T)
    suspend fun <T> setToFormLibModuleDataStore(key: Preferences.Key<T>, value: T)
    suspend fun <T> isKeyAvailableInAppModuleDataStore(key: Preferences.Key<T>): Boolean
    suspend fun <T> getFromAppModuleDataStore(key: Preferences.Key<T>, defaultValueIfNull: T): T
    suspend fun getStopAddressFromAppModuleDataStore(stopId: Int): String
    suspend fun <T> getFromFormLibModuleDataStore(key: Preferences.Key<T>, defaultValueIfNull: T): T
    suspend fun removeAllKeysOfAppModuleDataStore()
    suspend fun <T> removeItemFromAppModuleDataStore(key: Preferences.Key<T>)
    fun getAppModuleCommunicator(): AppModuleCommunicator


}