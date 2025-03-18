package com.trimble.ttm.routemanifest.utils.ext

import com.trimble.launchercommunicationlib.commons.model.DisplayPriority
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY

fun Int.getPanelMessageDisplayPriority(): Int {
    return when (this) {
        TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY -> DisplayPriority.HOME_SCREEN_ONLY.value
        else -> DisplayPriority.HOME_SCREEN_ONLY.value or DisplayPriority.VEHICLE_NOT_IN_MOTION.value
    }
}