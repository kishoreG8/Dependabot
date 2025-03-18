package com.trimble.ttm.commons.repo

import androidx.datastore.preferences.core.Preferences
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.Utils.fromJsonString

class LocalDataSourceRepoImpl(
    private val dataStoreManager: DataStoreManager,
    private val formDataStoreManager: FormDataStoreManager,
    private val appModuleCommunicator: AppModuleCommunicator
) : LocalDataSourceRepo {

    override suspend fun hasActiveDispatch(): Boolean = isKeyAvailableInAppModuleDataStore(
        DataStoreManager.ACTIVE_DISPATCH_KEY)

    //If returned string is empty from datastore. Then, fromJsonString will return null. Thus, Stop is nullable here
    override suspend fun getCurrentStop(): Stop? =
        fromJsonString<Stop>(getFromAppModuleDataStore(DataStoreManager.CURRENT_STOP_KEY, EMPTY_STRING))

    override suspend fun setLastSentTripPanelMessageId(value: Int) = setToAppModuleDataStore(
        DataStoreManager.LAST_SENT_TRIP_PANEL_MESSAGE_ID, value)

    override suspend fun getLastSentTripPanelMessageId(): Int = getFromAppModuleDataStore(
        DataStoreManager.LAST_SENT_TRIP_PANEL_MESSAGE_ID, -1)

    override suspend fun getActiveDispatchId(caller: String) =
        getFromAppModuleDataStore(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)

    override suspend fun getSelectedDispatchId(caller: String): String {
        return getFromAppModuleDataStore(DataStoreManager.SELECTED_DISPATCH_KEY, EMPTY_STRING)
    }

    override suspend fun removeSelectedDispatchIdFromLocalCache() {
        dataStoreManager.removeItem(DataStoreManager.SELECTED_DISPATCH_KEY)
    }

    override suspend fun setActiveDispatchId(dispatchId: String) {
        setToAppModuleDataStore(DataStoreManager.ACTIVE_DISPATCH_KEY, dispatchId)
    }

    override suspend fun setCurrentWorkFlowDispatchName(dispatchName: String) {
        setToAppModuleDataStore(DataStoreManager.CURRENT_DISPATCH_NAME_KEY, dispatchName)
    }

    override suspend fun setIsDyaShownForStop(isDyaShown: Boolean) {
        setToAppModuleDataStore(DataStoreManager.IS_DYA_ALERT_ACTIVE, isDyaShown)
    }

    override suspend fun isFCMTokenSavedInFirestore(): Boolean =
        getFromFormLibModuleDataStore(
            FormDataStoreManager.IS_FCM_DEVICE_TOKEN_STORED_IN_FIRESTORE,
            false
        )

    override suspend fun setFCMTokenFirestoreStatusInDataStore(isTokenStoredInFirestore: Boolean) =
        setToFormLibModuleDataStore(FormDataStoreManager.IS_FCM_DEVICE_TOKEN_STORED_IN_FIRESTORE, isTokenStoredInFirestore)


    override suspend fun <T> setToAppModuleDataStore(key: Preferences.Key<T>, value: T) {
        dataStoreManager.setValue(key, value)
    }

    override suspend fun <T> setToFormLibModuleDataStore(key: Preferences.Key<T>, value: T) {
        formDataStoreManager.setValue(key, value)
    }

    override suspend fun <T> isKeyAvailableInAppModuleDataStore(key: Preferences.Key<T>): Boolean =
        dataStoreManager.containsKey(key)

    override suspend fun <T> getFromAppModuleDataStore(key: Preferences.Key<T>, defaultValueIfNull: T): T {
        return dataStoreManager.getValue(key, defaultValueIfNull)
    }

    override suspend fun getStopAddressFromAppModuleDataStore(stopId: Int): String =
        dataStoreManager.getStopAddress(stopId)

    override suspend fun <T> getFromFormLibModuleDataStore(
        key: Preferences.Key<T>,
        defaultValueIfNull: T
    ): T {
        return formDataStoreManager.getValue(key, defaultValueIfNull)
    }

    override suspend fun removeAllKeysOfAppModuleDataStore() {
        dataStoreManager.removeAllKeys()
    }

    override suspend fun <T> removeItemFromAppModuleDataStore(key: Preferences.Key<T>) {
        dataStoreManager.removeItem(key)
    }

    override fun getAppModuleCommunicator(): AppModuleCommunicator = appModuleCommunicator

}