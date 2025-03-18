package com.trimble.ttm.routemanifest.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.trimble.ttm.commons.composable.commonComposables.ErrorWithRetry
import com.trimble.ttm.commons.composable.commonComposables.LoadingScreen
import com.trimble.ttm.commons.composable.commonComposables.ScreenContentState
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_PREVIEWING
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.commons.utils.traceBeginSection
import com.trimble.ttm.commons.utils.traceEndSection
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.databinding.ActivityStopDetailBinding
import com.trimble.ttm.routemanifest.utils.CURRENT_STOP_INDEX
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_TO_RENDER
import com.trimble.ttm.routemanifest.utils.INCOMING_ARRIVED_TRIGGER
import com.trimble.ttm.routemanifest.utils.SELECTED_STOP_ID
import com.trimble.ttm.routemanifest.utils.STOP_DETAIL_SCREEN_TIME
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.startDispatchFormActivity
import com.trimble.ttm.toolbar.ui.IconType
import com.trimble.ttm.toolbar.ui.OnFragmentInteractionListener
import com.trimble.ttm.toolbar.ui.compose.InstinctAppBar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat

const val STOP_DETAIL_ACTIVITY = "StopDetailActivity"

class StopDetailActivity : DispatchBaseActivity(STOP_DETAIL_ACTIVITY),
    OnFragmentInteractionListener {

    private val tag = "StopDetailActivity"
    private lateinit var activityStopDetailBinding: ActivityStopDetailBinding
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private fun setupArrivedClickListener() {
        activityStopDetailBinding.arrived.setOnClickListener {
            Log.logUiInteractionInNoticeLevel(tag, "$tag arrive button clicked", null,
                DISPATCHID to stopDetailViewModel.currentStop.value?.dispid,
                STOPID to stopDetailViewModel.currentStop.value?.stopid,
                KEY to DISPATCH_LIFECYCLE
            )
            stopDetailViewModel.processManualApproachOrArrival(
                disableButtonsToPreventDoubleClick = {
                    disableButtonsToPreventDoubleClick()
                },
                startFormActivity = { path, dispatchFormPath, isActionResponseSent ->
                    startFormActivity(path, dispatchFormPath, isActionResponseSent)
                },
                checkForTripCompletion = {
                    checkForTripCompletion()
                }
            )
            //manual arrival, adjust departure geofence if arrived outside arrival geofence.
            stopDetailViewModel.manipulateDepartureGeofenceOnManualArrival()
        }
    }

    private fun setupDepartClickListener() {
        activityStopDetailBinding.departed.setOnClickListener {
            Log.logUiInteractionInNoticeLevel(tag, "$tag depart button clicked", null,
                DISPATCHID to stopDetailViewModel.currentStop.value?.dispid,
                STOPID to stopDetailViewModel.currentStop.value?.stopid,
                KEY to DISPATCH_LIFECYCLE
            )
            stopDetailViewModel.processManualDeparture(
                disableButtonsToPreventDoubleClick = {
                    disableButtonsToPreventDoubleClick()
                },
                startFormActivity = { path, dispatchFormPath, isActionResponseSent ->
                    // There is no form for depart action. In future, if there is one for depart action, then this method will be invoked.
                    startFormActivity(path, dispatchFormPath, isActionResponseSent)
                },
                checkForTripCompletion = {
                    checkForTripCompletion()
                },
                showDepartureTime = {
                    showDepartureTime()
                }
            )
        }
    }

    private fun showDepartureTime() {
        activityStopDetailBinding.depatureLayout.visibility = View.VISIBLE
        activityStopDetailBinding.sepLine9.visibility = View.VISIBLE
    }

    private fun observeCurrentStop() {
        stopDetailViewModel.currentStop.observe(this) { stopDetail ->
            stopDetail?.let { stop ->
                stopDetailViewModel.obtainFormattedAddress(
                    stop.stopid,
                    false,
                    Utils.getDeviceLocale(applicationContext) != null
                ).onEach { address ->
                    activityStopDetailBinding.address1.text = address
                }.launchIn(lifecycleScope)
                toolbarViewModel.title.value = stop.name.uppercase()
                stopDetailViewModel.updateUIElements()
            } ?: Log.e(tag, "StopDetail is null when setting stop name in toolbar")
        }
    }

    private fun listenForStopAdditionAndRemoval() {
        with(stopDetailViewModel) {
            shouldFinishActivityLiveData.observe(this@StopDetailActivity) {
                finish()
            }
            listenForStopAdditionAndRemoval()
        }
    }

    @Composable
    private fun ProgressView(screenContentState: ScreenContentState) {
        when (screenContentState) {
            is ScreenContentState.Loading -> {
                activityStopDetailBinding.stopDetailContent.visibility = GONE
                LoadingScreen(
                    progressText = stringResource(id = R.string.loading_text),
                    show = true
                )
            }
            is ScreenContentState.Success -> {
                LoadingScreen(progressText = "", show = false)
                activityStopDetailBinding.stopDetailContent.visibility = View.VISIBLE
                traceEndSection(STOP_DETAIL_ACTIVITY)
            }
            is ScreenContentState.Error -> {
                LoadingScreen(progressText = EMPTY_STRING, show = false)
                activityStopDetailBinding.stopDetailContent.visibility = GONE
                ErrorWithRetry(
                    screenContentState.errorMessage
                ) {
                    Log.logUiInteractionInNoticeLevel(tag, "$tag Retry button clicked")
                    with(stopDetailViewModel) {
                        getStopListData()
                        registerDataStoreListener()
                        updateStopDetailScreenWithActions()
                    }
                }
            }
        }
    }

    private fun observeCurrentStopActionsForOffline() {
        stopDetailViewModel.isCurrentStopActionsEmptyOffline.observe(this) {
            if (it) ToastCompat.makeText(this, getString(
                        R.string.no_internet_connection_with_placeholder,
                        getString(R.string.stop_actions_could_not_be_loaded)
                    ), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        traceBeginSection(STOP_DETAIL_ACTIVITY)
        activityStopDetailBinding = DataBindingUtil.setContentView<ActivityStopDetailBinding>(
            this,
            R.layout.activity_stop_detail
        ).apply {
            viewModel = stopDetailViewModel
            lifecycleOwner = this@StopDetailActivity
        }
        val screenContentView = findViewById<ComposeView>(R.id.progressErrorView)
        screenContentView.setContent {
            ProgressView(stopDetailViewModel.screenContentState.collectAsState().value)
        }
        activityStopDetailBinding.toolbar.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                InstinctAppBar(toolbarViewModel)
            }
        }
        observeOnBackPress()
        initialize()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.logLifecycle(tag, "$tag onNewIntent")
        this.intent = intent
        lifecycleScope.launch {
            val dispatchIdToRender = intent.getStringExtra(DISPATCH_ID_TO_RENDER) ?: EMPTY_STRING
            val currentRenderedDispatchId =
                stopDetailViewModel.getSelectedDispatchId("$tag-OnNewIntent()")
            Log.d(
                tag+ TRIP_PREVIEWING,
                "dispatchIdToRender: $dispatchIdToRender, currentRenderedDispatchId: $currentRenderedDispatchId"
            )
            //We need to reload the dispatch data if we get a new DispatchId in onNewIntent()
            //We get into this during coming back to StopDetail from FormActivity after submitting the form if opened from draft.
            stopDetailViewModel.reloadUIIfRequired(dispatchIdToRender = dispatchIdToRender, currentRenderedDispatchId = currentRenderedDispatchId){
                reInitializeData()
            }
        }
    }

    private fun initialize(){
        determineAndUpdateAddressVisibility()
        toolbarViewModel.iconType.postValue(IconType.BACK)
        processAndUpdateCurrentStopInformation()
        setupArrivedClickListener()
        setupDepartClickListener()
        observeCurrentStop()
        listenForStopAdditionAndRemoval()
        observeCurrentStopActionsForOffline()
    }

    private fun reInitializeData(){
        stopDetailViewModel.restoreSelectedDispatchIdOnReInitialize { initialize() }
    }

    private fun determineAndUpdateAddressVisibility() {
        stopDetailViewModel.determineAndUpdateAddressVisibility()
    }

    private fun processAndUpdateCurrentStopInformation() {
        val currentStopIndex = intent.getIntExtra(CURRENT_STOP_INDEX,  stopDetailViewModel.getCurrentStopIndex())
        stopDetailViewModel.setCurrentStopIndexAndSelectedStopId(
            currentStopIndex = currentStopIndex,
            selectedStopId = intent.getIntExtra(SELECTED_STOP_ID, 0)
        )
    }

    private fun startFormActivity(
        path: String,
        dispatchFormPath: DispatchFormPath,
        isActionResponseSent: Boolean
    ) {
        lifecycleScope.launch {
            this@StopDetailActivity.startDispatchFormActivity(
                isComposeEnabled = stopDetailViewModel.isComposeFormFeatureFlagEnabled(),
                path = path,
                dispatchFormPath = dispatchFormPath,
                isManualArrival = true,
                isFormResponseSentToServer = isActionResponseSent
            )
        }
    }

    private val arrivedEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == INCOMING_ARRIVED_TRIGGER) checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        with(stopDetailViewModel) {
            logScreenViewEvent(screenName = STOP_DETAIL_SCREEN_TIME)
            resetIsDraftView()
            registerDataStoreListener()
            updateArrivedButtonText()
        }
        WorkflowApplication.setDispatchActivityResumed()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            arrivedEventReceiver,
            IntentFilter(INCOMING_ARRIVED_TRIGGER)
        )
        enableButtonsWhichWereDisabledToPreventDoubleClick()
    }

    private fun disableButtonsToPreventDoubleClick() {
        with(activityStopDetailBinding) {
            arrived.isEnabled = false
            nextArrow.isEnabled = false
            previousArrow.isEnabled = false
        }
    }

    private fun enableButtonsWhichWereDisabledToPreventDoubleClick() {
        with(activityStopDetailBinding) {
            arrived.isEnabled = true
            nextArrow.isEnabled = true
            previousArrow.isEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        Log.logLifecycle(tag, "$tag onPause")
        stopDetailViewModel.unRegisterDataStoreListener()
        WorkflowApplication.setDispatchActivityPaused()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(arrivedEventReceiver)
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

    override fun onToolbarNavigationIconClicked(iconType: IconType) {
        super.onToolbarNavigationIconClicked(iconType)
        Log.logUiInteractionInNoticeLevel(tag, "$tag soft back button clicked")
        softOrHardBackButtonPress()
    }

    private fun observeOnBackPress() {
        onBackPressedCallback = object : OnBackPressedCallback(true /* enabled by default */) {
            override fun handleOnBackPressed() {
                softOrHardBackButtonPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun softOrHardBackButtonPress() {
        val presentDispatchId = stopDetailViewModel.currentStop.value?.dispid
        Intent(this@StopDetailActivity, DispatchDetailActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(DISPATCH_ID_TO_RENDER, presentDispatchId)
            startActivity(this)
        }
        finish()
    }
}