package com.trimble.ttm.routemanifest.ui.activities

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.composable.commonComposables.LoadingScreen
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_PANEL
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_AUTO_DISMISS_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MESSAGE_ID_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_PANEL_MESSAGE_PRIORITY_KEY
import com.trimble.ttm.routemanifest.viewmodel.TripPanelPositiveActionTransitionScreenViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class TripPanelPositiveActionTransitionScreenActivity : ComponentActivity() {

    private val tripPanelPositiveActionTransitionScreenViewModel: TripPanelPositiveActionTransitionScreenViewModel by viewModel()
    private var tripPanelPositiveActionCollectJob : Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lockOrientation()
        setContent {
            Column(modifier = Modifier.background(color = colorResource(id = R.color.colorPrimary))) {
                LoadingScreen(
                    progressText = stringResource(id = R.string.loading_text),
                    show = true
                )
            }
        }
        tripPanelPositiveActionCollectJob?.cancel()
        tripPanelPositiveActionCollectJob = lifecycleScope.launch {
            val messageId = intent.getStringExtra(TRIP_PANEL_MESSAGE_ID_KEY) ?: "-1"
            val isAutoDismissed = intent.getBooleanExtra(TRIP_PANEL_AUTO_DISMISS_KEY, false)
            val messagePriority = intent.getStringExtra(TRIP_PANEL_MESSAGE_PRIORITY_KEY)?.toInt() ?: -1
            Log.i(
                TRIP_PANEL,
                "TripPanelPositiveActionTransitionScreenActivity: messageId: $messageId, isAutoDismissed: $isAutoDismissed"
            )
            tripPanelPositiveActionTransitionScreenViewModel.handlePositiveAction(
                messageId.toInt(),
                isAutoDismissed,
                messagePriority
            ).collect {
                cancelTripPanelPositiveActionCollectJob()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTripPanelPositiveActionCollectJob()
    }

    private fun cancelTripPanelPositiveActionCollectJob() {
        if(tripPanelPositiveActionCollectJob?.isActive == true) {
            tripPanelPositiveActionCollectJob?.cancel()
        }
    }

    private fun lockOrientation() {
        val currentOrientation = resources.configuration.orientation
        requestedOrientation =
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

}