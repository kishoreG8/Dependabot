package com.trimble.ttm.routemanifest.repo

import android.os.Bundle
import com.trimble.launchercommunicationlib.client.wrapper.CommunicationProviderCallBack
import com.trimble.launchercommunicationlib.client.wrapper.DockModeCallBack
import com.trimble.launchercommunicationlib.commons.model.HostAppState
import com.trimble.launchercommunicationlib.commons.model.PanelDetails
import kotlinx.coroutines.channels.Channel

interface TripPanelEventRepo {
    fun sendEvent(uniqueMessageId: Int, panelInfo: PanelDetails)

    fun dismissEvent(uniqueMessageId: Int)

    fun registerCallbacks(
        communicationProviderCallBack: CommunicationProviderCallBack
    )

    fun unregisterCallbacks()

    fun monitorServiceConnection(): Channel<HostAppState>

    fun sendServiceConnectionStatus(value: HostAppState)

    fun retryConnection()

    fun observeRetryStatus(): Channel<Boolean>

    fun calculateRoute(data: Bundle)

    fun setDockMode(data: Bundle, callback: DockModeCallBack)

    fun resetDockMode(ackId: Int, callback: DockModeCallBack)
}