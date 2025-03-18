package com.trimble.ttm.commons.preferenceManager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.EMPTY_STRING
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FormDataStoreManager(var context: Context) {

    private val tag = "FormDataStoreManager"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "route_manifest_form_data_store")

    suspend fun <T> containsKey(key: Preferences.Key<T>): Boolean {
        try {
            return context.dataStore.data.first().contains(key)
        } catch (e: Exception) {
            Log.d(tag,"Error preferencesDataStore containsKey ${e.stackTraceToString()}", throwable = null, "key" to key)
            throw e
        }
    }

    suspend fun <T> removeItem(key: Preferences.Key<T>) = context.dataStore.edit {
        try {
            it.remove(key)
        } catch (e: Exception) {
            Log.d(tag,"Error removing preferencesDataStore ${e.stackTraceToString()}", throwable = null, "key" to key)
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
            Log.d(tag,"Error getting preferencesDataStore values ${e.stackTraceToString()}", throwable = null, "key" to key)
            defaultValueIfNull
        }
    }

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        try {
            context.dataStore.edit { preferences ->
                preferences[key] = value
            }
        } catch (e: Exception) {
            Log.d(tag,"Error setting preferencesDataStore values ${e.stackTraceToString()}", throwable = null, "key" to key)
            throw e
        }
    }

    suspend fun getPreferenceKeys(): Set<Preferences.Key<*>> {
        try {
            return context.dataStore.data.first().asMap().keys
        } catch (e: Exception) {
            Log.d(tag,"Error getting preferencesDataStore values ${e.stackTraceToString()}")
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
            Log.d(tag,"Error fieldObserver preferencesDataStore ${e.stackTraceToString()}", throwable = null, "key" to key)
            throw e
        }
    }

    //Don't remove the string type from setEncodedImage and getEncodedImage
    suspend fun setEncodedImage(stopId: Int, viewId: Int, imageString: String) =
        setValue(stringPreferencesKey("form_image$stopId$viewId"), imageString)

    suspend fun getEncodedImage(stopId: Int, viewId: Int) =
        getValue(stringPreferencesKey("form_image$stopId$viewId"), EMPTY_STRING)

    companion object{
        val IS_PRE_TRIP_INSPECTION_REQUIRED = booleanPreferencesKey("IsPreTripInspectionRequired")
        val IS_POST_TRIP_INSPECTION_REQUIRED = booleanPreferencesKey("IsPostTripInspectionRequired")
        val PREVIOUS_PRE_TRIP_INSPECTION_ANNOTATION = stringPreferencesKey("PreviousPreTripAnnotation")
        val PREVIOUS_POST_TRIP_INSPECTION_ANNOTATION = stringPreferencesKey("PreviousPostTripAnnotation")
        val LAST_SIGNED_IN_DRIVERS_COUNT = intPreferencesKey("LastSignedInDriversCount")
        val IS_FCM_DEVICE_TOKEN_STORED_IN_FIRESTORE = booleanPreferencesKey(name = "is_fcm_device_token_saved_in_firestore")
        val IS_IN_FORM_KEY = booleanPreferencesKey(name = "isInForm")
        val CAN_SHOW_EDVIR_IN_HAMBURGER_MENU = booleanPreferencesKey("can_show_edvir_in_hamburger_menu")
        val ENCODED_IMAGE_SHARE_IN_FORM = stringPreferencesKey(name = "encodedImageShareInForm")
        val ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID = stringPreferencesKey(name = "encodedImageShareInFormViewId")
        val GROUP_UNITS_LAST_MODIFIED_TIME_KEY = longPreferencesKey(name = "groupUnitsLastModifiedTime")
        val GROUP_FORMS_LAST_MODIFIED_TIME_KEY = longPreferencesKey(name = "groupFormsLastModifiedTime")
        val GROUP_USERS_LAST_MODIFIED_TIME_KEY = longPreferencesKey(name = "groupUsersLastModifiedTime")
        val IS_CONTACTS_SNAPSHOT_EMPTY = booleanPreferencesKey(name = "isContactsSnapshotEmpty")
        val IS_FORM_LIBRARY_SNAPSHOT_EMPTY = booleanPreferencesKey(name = "isFormLibrarySnapshotEmpty")
        val IS_MANDATORY_INSPECTION = booleanPreferencesKey(name = "isMandatoryInspection")
        val SHOW_INSPECTION_ALERT_DIALOG = booleanPreferencesKey(name = "showInspectionAlertDialog")
        val CLOSE_FIRST = booleanPreferencesKey(name = "closeFirst")
        val IS_DRAFT_VIEW = booleanPreferencesKey(name = "isDraftView")
        //This is used to identify the old truck number in change vehicle scenario. Always use appModule communicator to get truckNumber data for consistency.
        val TRUCK_NUMBER = stringPreferencesKey(name = "truck_number")
        val LAST_FETCHED_FCM_TOKEN_DATA_KEY = stringPreferencesKey(name = "fcmTokenData")
        val IS_FIRST_TIME_OPEN = booleanPreferencesKey(name = "is_first_time_open")

    }

}