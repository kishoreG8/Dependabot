package com.trimble.ttm.routemanifest.usecases

import android.os.Bundle
import com.trimble.launchercommunicationlib.commons.DOCK_MODE_INTENT_ACTION
import com.trimble.launchercommunicationlib.commons.DOCK_MODE_REQUESTER
import com.trimble.ttm.routemanifest.datasource.LauncherDockModeCallback
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DOCK_MODE_ACK_ID_KEY
import com.trimble.ttm.routemanifest.repo.TripPanelEventRepo
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class DockModeUseCase(
    private val eventRepo: TripPanelEventRepo,
    private val dataStoreManager: DataStoreManager
) : KoinComponent {

    private val tag = "DockModeUC"

    fun setDockMode(bundle: Bundle, appPackageName: String, intentAction: String) {
        bundle.also {
            it.putString(DOCK_MODE_REQUESTER, appPackageName)
            it.putString(DOCK_MODE_INTENT_ACTION, intentAction)
        }
        eventRepo.setDockMode(bundle, LauncherDockModeCallback)
    }

    fun resetDockMode() {
        CoroutineScope(Dispatchers.IO).launch(CoroutineName("$tag reset dock mode")) {
            dataStoreManager.getValue(DOCK_MODE_ACK_ID_KEY, -1).let {
                eventRepo.resetDockMode(it, LauncherDockModeCallback)
            }
        }
    }
}