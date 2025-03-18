package com.trimble.ttm.commons.preferenceManager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.trimble.launchercommunicationlib.commons.DOCK_MODE_ACK_ID
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.ArrivedGeoFenceTriggerData
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreManager(var context: Context) {

    private val tag = "DataStoreManager"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "route_manifest_data_store")
    suspend fun <T> containsKey(key: Preferences.Key<T>): Boolean {
        try {
            return context.dataStore.data.first().contains(key)
        } catch (e: Exception) {
            Log.e(
                tag,
                "Error preferencesDataStore containsKey ${e.stackTraceToString()}"
            )
            throw e
        }
    }

    suspend fun <T> removeItem(key: Preferences.Key<T>) = context.dataStore.edit {
        try {
            it.remove(key)
        } catch (e: Exception) {
            Log.e(
                tag,
                "Error removing preferencesDataStore ${e.stackTraceToString()}",
                throwable = null,
                "key" to key
            )
            throw e
        }
    }

    suspend fun <T> getValue(key: Preferences.Key<T>, defaultValueIfNull: T): T {
        return try {
            context.dataStore.data
                .map { preferences ->
                    preferences[key]
                }.first() ?: defaultValueIfNull
        } catch (e: Exception) {
            Log.d(tag, "Error getting preferencesDataStore values ${e.stackTraceToString()}", throwable = null, "key" to key)
            defaultValueIfNull
        }
    }

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        try {
            context.dataStore.edit { preferences ->
                preferences[key] = value
            }
        } catch (e: Exception) {
            Log.d(tag, "Error setting preferencesDataStore values ${e.stackTraceToString()}", throwable = null, "key" to key)
            throw e
        }
    }

    fun <T> fieldObserver(key: Preferences.Key<T>): Flow<T?> {
        try {
            return context.dataStore.data.distinctUntilChanged()
                .map { preferences ->
                    preferences[key]
                }
        } catch (e: Exception) {
            Log.d(tag, "Error fieldObserver preferencesDataStore ${e.stackTraceToString()}", throwable = null, "key" to key)
            throw e
        }
    }

    suspend fun hasActiveDispatch(caller: String, logError: Boolean): Boolean {
        return if (this.containsKey(ACTIVE_DISPATCH_KEY) && this.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING).isNotEmpty()) {
            true
        } else {
            if (logError) {
                Log.e(caller, "Active dispatch is empty")
            }
            false
        }
    }

    suspend fun putStopAddress(stopId: Int, address: String) =
        this.setValue(stringPreferencesKey("stop_address$stopId"), address)

    suspend fun getStopAddress(stopId: Int) =
        this.getValue(stringPreferencesKey("stop_address$stopId"), EMPTY_STRING)

    suspend fun removeAllKeys() {
        try {
            val keysToBeRemoved = context.dataStore.data.first().asMap().keys.filter {
                it != VID_KEY && it != IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED
            }
            keysToBeRemoved.forEach {
                Log.d(tag, "Removing datastore keys", throwable = null, "key" to it)
                removeItem(it)
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Error removing all preferencesDataStore keys ${e.stackTraceToString()}"
            )
            throw e
        }
    }

    companion object {
        val emptyArrivedGeoFenceTriggerList: String =
            Gson().toJson(ArrayList<ArrivedGeoFenceTriggerData>())
        val ARE_STOPS_SEQUENCED_KEY = booleanPreferencesKey(name = "are_stops_sequenced")
        val CURRENT_STOP_KEY = stringPreferencesKey(name = "current_stop")
        val NAVIGATION_ELIGIBLE_STOP_LIST_KEY =
            stringSetPreferencesKey(name = "navigation_stop_list")
        val TOTAL_DISTANCE_KEY = floatPreferencesKey(name = "total_distance")
        val TOTAL_HOURS_KEY = floatPreferencesKey(name = "total_hours")
        val TOTAL_STOPS_KEY = intPreferencesKey(name = "total_stops")
        val TRIP_START_TIME_IN_MILLIS_KEY = longPreferencesKey(name = "trip_start_time_in_millis")
        val ROUTE_DATA_KEY = stringPreferencesKey(name = "route_data")
        val TRAILER_IDS_KEY = stringPreferencesKey(name = "trailer_ids")
        val SHIPMENTS_IDS_KEY = stringPreferencesKey(name = "shipment_ids")
        val DISPATCH_NAME_KEY = stringPreferencesKey(name = "dispatch_name")
        val CURRENT_DISPATCH_NAME_KEY = stringPreferencesKey(name = "current_dispatch_name")
        val VID_KEY = longPreferencesKey(name = "vid")
        val IS_TRIP_AUTO_STARTED_KEY = booleanPreferencesKey(name = "is_trip_auto_started")
        val SELECTED_DISPATCH_KEY = stringPreferencesKey(name = "selected_dispatch")
        val STOPS_SERVICE_REFERENCE_KEY = stringPreferencesKey(name = "stops")
        val COMPLETED_STOP_ID_SET_KEY = stringSetPreferencesKey(name = "completed_stop_ids")
        val ARRIVED_TRIGGERS_KEY = stringPreferencesKey(name = "geofence_trigger")
        val DOCK_MODE_ACK_ID_KEY = intPreferencesKey(name = DOCK_MODE_ACK_ID)
        val ACTIVE_DISPATCH_KEY = stringPreferencesKey(name = "activeDispatch")
        val DISPATCHES_QUANTITY = intPreferencesKey(name = "dispatchesQuantity")
        val RECEIVED_DISPATCH_SET_KEY = stringSetPreferencesKey(name = "received_dispatch_set")
        val LAST_SENT_TRIP_PANEL_MESSAGE_ID = intPreferencesKey("last_sent_trip_panel_message_id")
        val NOTIFICATION_LIST = stringPreferencesKey(name = "notificationList")
        val IS_APP_LAUNCHER_WITH_PERFORMANCE_FIX_INSTALLED = booleanPreferencesKey("isALWithPerformanceFixInstalled")
        val IS_ACTIVE_DISPATCH_STOP_MANIPULATED = booleanPreferencesKey("isActiveDispatchStopManipulated")
        val UNCOMPLETED_DISPATCH_FORMS_STACK_KEY = stringPreferencesKey(name = "form_stack")
        val ARRIVAL_TIME = stringPreferencesKey(name = "arrivalTime")
        val DEPARTED_TIME = stringPreferencesKey(name = "departedTime")
        // This key is used to store whether the driver starts the trip from the first stop or not
        val IS_DRIVER_STARTS_FROM_FIRST_STOP = booleanPreferencesKey(name = "isDriverStartsFromFirstStop")
        // This key is set to true when DYA alert is shown, to prevent overwriting when trigger is ignored with DYA_IGNORED_BY_DRIVER
        var IS_DYA_ALERT_ACTIVE = booleanPreferencesKey(name = "isDYAAlertActive")
    }
}
