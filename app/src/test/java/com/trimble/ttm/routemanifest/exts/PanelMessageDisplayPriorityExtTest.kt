package com.trimble.ttm.routemanifest.exts

import com.trimble.launchercommunicationlib.commons.model.DisplayPriority
import com.trimble.ttm.routemanifest.utils.*
import com.trimble.ttm.routemanifest.utils.ext.getPanelMessageDisplayPriority
import org.junit.Assert
import org.junit.Test

class PanelMessageDisplayPriorityExtTest {
    @Test
    fun `verify return values of display priority from message priority`() {    //NOSONAR
        Assert.assertEquals(
            DisplayPriority.HOME_SCREEN_ONLY.value,
            TRIP_PANEL_NEXT_STOP_ADDRESS_MSG_PRIORITY.getPanelMessageDisplayPriority()
        )
        Assert.assertEquals(
            DisplayPriority.HOME_SCREEN_ONLY.value or DisplayPriority.VEHICLE_NOT_IN_MOTION.value,
            TRIP_PANEL_SELECT_STOP_MSG_PRIORITY.getPanelMessageDisplayPriority()
        )
        Assert.assertEquals(
            DisplayPriority.HOME_SCREEN_ONLY.value or DisplayPriority.VEHICLE_NOT_IN_MOTION.value,
            TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY.getPanelMessageDisplayPriority()
        )
        Assert.assertEquals(
            DisplayPriority.HOME_SCREEN_ONLY.value or DisplayPriority.VEHICLE_NOT_IN_MOTION.value,
            TRIP_PANEL_DID_YOU_ARRIVE_MSG_PRIORITY_FOR_CURRENT_STOP.getPanelMessageDisplayPriority()
        )
        Assert.assertEquals(
            DisplayPriority.HOME_SCREEN_ONLY.value or DisplayPriority.VEHICLE_NOT_IN_MOTION.value,
            TRIP_PANEL_COMPLETE_FORM_MSG_PRIORITY.getPanelMessageDisplayPriority()
        )
    }
}