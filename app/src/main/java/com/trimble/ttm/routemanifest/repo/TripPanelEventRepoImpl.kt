package com.trimble.ttm.routemanifest.repo

import android.os.Bundle
import com.trimble.launchercommunicationlib.client.wrapper.AppLauncherCommunicator
import com.trimble.launchercommunicationlib.client.wrapper.CommunicationProviderCallBack
import com.trimble.launchercommunicationlib.client.wrapper.DockModeCallBack
import com.trimble.launchercommunicationlib.commons.MESSAGE_RESPONSE_ACTION_KEY
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.launchercommunicationlib.commons.model.PanelDetails
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_PANEL
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.routemanifest.utils.DEFAULT_ROUTE_CALC_REQ_DEBOUNCE_THRESHOLD
import com.trimble.ttm.routemanifest.utils.FILL_FORMS_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.NEXT_STOP_MESSAGE_ID
import com.trimble.ttm.routemanifest.utils.SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

class TripPanelEventRepoImpl : TripPanelEventRepo {

    private val serviceConnectionChannel = Channel<HostAppState>()
    private val retryConnectionChannel = Channel<Boolean>()
    private var communicationProviderCallBack: CommunicationProviderCallBack? = null
    private var calculateRouteJob: Job? = null
    private var coroutineScope = CoroutineScope(Dispatchers.IO)
    private val tag = "TripPanelEventRepoImpl"

    override fun sendEvent(uniqueMessageId: Int, panelInfo: PanelDetails) {
        Log.d(tag,
            "-----TripPanelEventRepoImpl sendEvent uniqueMessageId : $uniqueMessageId panelInfo: ${panelInfo.message}")
        if (uniqueMessageId != SELECT_STOP_TO_NAVIGATE_TO_MESSAGE_ID && uniqueMessageId != FILL_FORMS_MESSAGE_ID && uniqueMessageId != NEXT_STOP_MESSAGE_ID)
            Log.n(TRIP_PANEL, "sending Did you arrive message to launcher",throwable = null, "uniqueMessageId" to uniqueMessageId, "panelInfoMessage" to  "${panelInfo.message}", "autoDismissTime" to "${panelInfo.autoDismissTime}")
        AppLauncherCommunicator.sendPanelMessage(
            messageId = uniqueMessageId,
            panelDetails = panelInfo
        )
    }

    override fun dismissEvent(uniqueMessageId: Int) {
        Log.d(tag, "-----Trip Panel dismissEvent called : uniqueMessageId: ${uniqueMessageId}")
        AppLauncherCommunicator.dismissPanelMessage(messageId = uniqueMessageId)
    }


    override fun registerCallbacks(
        communicationProviderCallBack: CommunicationProviderCallBack
    ) {
        this.communicationProviderCallBack = communicationProviderCallBack
    }

    override fun unregisterCallbacks() {
        communicationProviderCallBack = null
    }

    override fun monitorServiceConnection(): Channel<HostAppState> = serviceConnectionChannel

    override fun sendServiceConnectionStatus(value: HostAppState) {
        CoroutineScope(Dispatchers.Default).safeLaunch(CoroutineName("$tag Send service connection status")) {
            serviceConnectionChannel.send(value)
        }
    }

    override fun retryConnection() {
        CoroutineScope(Dispatchers.Default).safeLaunch(CoroutineName("$tag Retry connection")) {
            retryConnectionChannel.send(true)
        }
    }

    override fun observeRetryStatus(): Channel<Boolean> = retryConnectionChannel

    override fun calculateRoute(data: Bundle) {
        calculateRouteJob?.cancel()
        calculateRouteJob = coroutineScope.safeLaunch(CoroutineName(tag)) {
            Log.d(tag, "CALCULATE_ROUTE - calculateRoute request received..")
            delay(DEFAULT_ROUTE_CALC_REQ_DEBOUNCE_THRESHOLD)
            communicationProviderCallBack?.apply {
                AppLauncherCommunicator.sendMessage(
                    messageId = MESSAGE_RESPONSE_ACTION_KEY,
                    data = data,
                    communicationProviderCallBack = this@apply
                )
            }
            Log.d(tag, "CALCULATE_ROUTE - Route calc req sent")
        }
    }

    override fun setDockMode(data: Bundle, callback: DockModeCallBack) =
        AppLauncherCommunicator.setDockMode(data, callback)

    override fun resetDockMode(ackId: Int, callback: DockModeCallBack) =
        AppLauncherCommunicator.resetDockMode(ackId, callback)
}