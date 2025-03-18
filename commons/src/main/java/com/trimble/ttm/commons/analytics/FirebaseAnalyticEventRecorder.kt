package com.trimble.ttm.commons.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ParametersBuilder
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.CID
import com.trimble.ttm.commons.utils.CID_VEHICLE_ID
import com.trimble.ttm.commons.utils.DURATION
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.OBC_ID
import com.trimble.ttm.commons.utils.VEHICLE_ID
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class FirebaseAnalyticEventRecorder(
    private val firebaseAnalytics: FirebaseAnalytics,
    private val dispatcherProvider: DispatcherProvider,
    private val appModuleCommunicator: AppModuleCommunicator
) : KoinComponent {

    private var cid: String = EMPTY_STRING
    private var obcId: String = EMPTY_STRING
    private var vehicleId: String = EMPTY_STRING


    private fun fetchBackboneValues() {
        appModuleCommunicator.getAppModuleApplicationScope().launch(dispatcherProvider.io()) {
            cid = appModuleCommunicator.doGetCid()
            vehicleId = appModuleCommunicator.doGetTruckNumber()
            obcId = appModuleCommunicator.doGetObcId()
        }
    }

    fun logNewCustomEventWithDefaultCustomParameters(
        eventName: String
    ) {
        fetchBackboneValues()
        firebaseAnalytics.logEvent(eventName, defaultCustomParameters().bundle)
    }

    fun logScreenViewEventWithDefaultAndCustomParameters(
        screenName: String
    ) {
        fetchBackboneValues()
        val parametersBundle = defaultCustomParameters().apply {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        }.bundle
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, parametersBundle)
    }

    fun logCustomEventWithCustomAndTimeDurationParameters(
        eventName: String,
        duration: String
    ) {
        fetchBackboneValues()
        val parametersBundle = defaultCustomParameters().apply {
            param(DURATION, duration)
        }.bundle
        firebaseAnalytics.logEvent(eventName, parametersBundle)
    }

    private fun defaultCustomParameters(): ParametersBuilder {
        val defaultParameter = ParametersBuilder()
        defaultParameter.param(CID, cid)
        defaultParameter.param(OBC_ID, obcId)
        defaultParameter.param(VEHICLE_ID, vehicleId)
        defaultParameter.param(CID_VEHICLE_ID, "$cid $vehicleId")
        return defaultParameter
    }


}