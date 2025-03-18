package com.trimble.ttm.routemanifest.datasource

import com.trimble.launchercommunicationlib.client.wrapper.DockModeCallBack
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DOCK_MODE_ACK_ID_KEY
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object LauncherDockModeCallback : DockModeCallBack, KoinComponent {

    private const val tag = "LauncherDockModeCallback"
    private val dataStore: DataStoreManager by inject()

    override fun onDockModeAcquired(ackId: Int) {
        CoroutineScope(Dispatchers.IO).launch(CoroutineName("$tag Dock Mode Acquire")) {
            dataStore.setValue(DOCK_MODE_ACK_ID_KEY, ackId)
        }
        Log.i(tag, "Dock mode acquired $ackId")
    }

    override fun onDockModeReleased() {
        CoroutineScope(Dispatchers.IO).launch(CoroutineName("$tag Dock Mode Release")) {
            Log.i(tag, "Dock mode released ${dataStore.getValue(DOCK_MODE_ACK_ID_KEY, -1)}")
        }
    }

    override fun onError(message: String) {
        Log.e(tag, "Dock mode error - $message")
    }
}