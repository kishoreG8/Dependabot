package com.trimble.ttm.routemanifest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.repo.isAppLauncherWithMapsPerformanceFixInstalled
import com.trimble.ttm.routemanifest.usecases.SendDispatchDataUseCase
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.utils.KEY_EVENT_MESSAGE
import com.trimble.ttm.routemanifest.utils.KEY_MAP_SERVICE_STATUS_EVENT
import com.trimble.ttm.routemanifest.utils.MAP_SERVICE_BOUND
import com.trimble.ttm.routemanifest.utils.Utils.getAppLauncherVersionAndSaveInMemory
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppLauncherMapServiceBoundStatusReceiver : BroadcastReceiver(), KoinComponent {

    private const val tag = "MapServiceBoundStatusBR"

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.extras?.let { bundle ->
            bundle.getString(KEY_MAP_SERVICE_STATUS_EVENT)?.let { mapServiceStatusEvent ->
                Log.i(tag, "From AppLauncher - ${bundle.getString(KEY_EVENT_MESSAGE)}")
                when (mapServiceStatusEvent) {
                    MAP_SERVICE_BOUND -> {
                        // On app update or device restart in the middle of the trip
                        // broadcast the stop data to draw route and geofence
                        CoroutineScope(Dispatchers.IO).launch(CoroutineName("$tag $MAP_SERVICE_BOUND")) {
                            val sendDispatchDataUseCase: SendDispatchDataUseCase by inject()
                            val dataStoreManager: DataStoreManager by inject()
                            val tripPanelUseCase: TripPanelUseCase by inject()
                            Log.d(tag, "AppLauncherMapBoundReceiver invoked.")
                            getAppLauncherVersionAndSaveInMemory(dataStoreManager)
                            Log.d(
                                tag,
                                "isAppLauncherWithMapsPerformanceFixInstalled - $isAppLauncherWithMapsPerformanceFixInstalled."
                            )
                            sendDispatchDataUseCase.sendCurrentDispatchDataToMaps(shouldRedrawCopilotRoute = false, caller = "Device Restarted / App Updated")
                            tripPanelUseCase.sendMessageToLocationPanelBasedOnCurrentStop()
                        }
                    }
                    else -> {
                        //Ignore
                    }
                }
            }
        }
    }
}