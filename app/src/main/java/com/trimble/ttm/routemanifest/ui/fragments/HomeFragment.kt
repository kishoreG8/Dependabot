package com.trimble.ttm.routemanifest.ui.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.trimble.ttm.commons.logger.AUTO_TRIP_START
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MANUAL_TRIP_START
import com.trimble.ttm.commons.logger.TRIP_LOAD_VIEW
import com.trimble.ttm.commons.logger.TRIP_PREVIEWING
import com.trimble.ttm.commons.utils.DateUtil.getCalendar
import com.trimble.ttm.commons.utils.Utils.isNull
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.adapter.ViewPagerAdapter
import com.trimble.ttm.routemanifest.databinding.ActivityDispatchDetailBinding
import com.trimble.ttm.routemanifest.databinding.FragmentHomeBinding
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_TRIP_AUTO_STARTED_KEY
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.logAction
import com.trimble.ttm.routemanifest.usecases.TripStartCaller
import com.trimble.ttm.routemanifest.utils.STOP_LIST_INDEX
import com.trimble.ttm.routemanifest.utils.TIMELINE_INDEX
import com.trimble.ttm.routemanifest.utils.TRIP_START_EVENT_REASON_TYPE
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.CopyOnWriteArrayList

private const val LIST_FRAGMENT_POSITION = 0

class HomeFragment : Fragment() {

    private val logTag = "HomeFragment"
    private val dataStoreManager: DataStoreManager by inject()
    private val dispatchDetailViewModel: DispatchDetailViewModel by sharedViewModel()
    private lateinit var  activityDispatchDetailBinding: ActivityDispatchDetailBinding
    private lateinit var homeFragment : FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        homeFragment = FragmentHomeBinding.inflate(inflater, container, false)
        activityDispatchDetailBinding = ActivityDispatchDetailBinding.inflate(inflater, container, false)
        homeFragment.root
            .apply {
                val tabDescriptions =
                    listOf(
                        getString(R.string.list),
                        getString(R.string.timeline)
                    )
                with(homeFragment){
                    viewPager.adapter = ViewPagerAdapter(childFragmentManager, tabDescriptions, lifecycle)
                    TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                        setTabText(tab, position)
                    }.attach()
                    viewPager.currentItem = STOP_LIST_INDEX
                }

            }
        lifecycleScope.launch(CoroutineName(logTag)) {
            observeForStops()
            dispatchDetailViewModel.dispatchActiveStateFlow.collect{ currentState ->
                saveTripName(currentState)
                setButtonAndLabelVisibilities(currentState)
            }
        }
        return homeFragment.root
    }

    private fun observeForStops(){
        lifecycleScope.launch(CoroutineName(logTag) + dispatchDetailViewModel.coroutineDispatcher.io()) {
            WorkflowEventBus.stopListEvents.onEach { stopDetails ->
                stopDetails.let { stopDetailList ->
                    if (stopDetailList.isEmpty()) return@let
                    if (dataStoreManager.getValue(IS_TRIP_AUTO_STARTED_KEY, false)) {
                        dataStoreManager.setValue(IS_TRIP_AUTO_STARTED_KEY, false)
                        val tripEventReasonTypeAndGuf =
                            dispatchDetailViewModel.getTripEventReasonTypeAndGuf(
                                this@HomeFragment.arguments?.getString(TRIP_START_EVENT_REASON_TYPE)
                            )
                        val pfmEventsInfo = PFMEventsInfo.TripEvents(
                            reasonType = tripEventReasonTypeAndGuf.first,
                            negativeGuf = tripEventReasonTypeAndGuf.second
                        )
                        if(this@HomeFragment.arguments.isNull()) {
                            Log.e(logTag + AUTO_TRIP_START, "Trip start event reason argument is null")
                        }
                        doOnTripStart(
                            stopDetailList,
                            pfmEventsInfo,
                            TripStartCaller.DISPATCH_DETAIL_SCREEN
                        )
                        val dispatchId = dispatchDetailViewModel.appModuleCommunicator.getCurrentWorkFlowId(logTag + AUTO_TRIP_START)
                        Log.logTripRelatedEvents(logTag + AUTO_TRIP_START,"Auto Trip Start D:$dispatchId Reason Type: ${tripEventReasonTypeAndGuf.first}")
                    }
                }
                Log.d(
                    logTag,
                    "Observing stop list",
                    throwable = null,
                    "size" to stopDetails.size
                )
                stopDetails.forEach { stopDetail ->
                    Log.d(
                        logTag,
                        "Dispatch ID: ${stopDetail.dispid}, Stop ID ${stopDetail.stopid} and Actions Size: ${stopDetail.Actions.size}",
                        null,
                        "Class" to "HomeFragment",
                        "Action" to "observeForStops"
                    )
                    stopDetail.Actions.forEach { action ->
                        Log.i(logTag, stopDetail.logAction(action))
                    }
                }
            }.launchIn(this)
        }
    }

    private fun setTabText(tab: TabLayout.Tab, position: Int) {
        when (position) {
            STOP_LIST_INDEX -> tab.text = getString(R.string.list)
            TIMELINE_INDEX -> tab.text = getString(R.string.timeline)
        }
    }

    private fun doOnTripStart(
        stopDetailList: CopyOnWriteArrayList<StopDetail>? = null,
        pfmEventsInfo: PFMEventsInfo.TripEvents,
        tripStartCaller: TripStartCaller
    ) {
        lifecycleScope.launch {
            if (dataStoreManager.hasActiveDispatch("HomeFragmentTripStart", logError = false)
                    .not()
            ) {
                with(dispatchDetailViewModel) {
                    scheduleLateNotificationCheckWorker("doOnTripStart", isFromTripStart = true)
                    startTrip(
                        timeInMillis = getCalendar().timeInMillis,
                        stopDetailList = stopDetailList,
                        pfmEventsInfo = pfmEventsInfo,
                        tripStartCaller = tripStartCaller
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        getView()?.also { fragmentView ->
            fragmentView.isFocusableInTouchMode = true
            fragmentView.requestFocus()
            fragmentView.setOnKeyListener(View.OnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                    setCurrentViewPagerTab()
                    doOnBackButtonPress()
                    return@OnKeyListener true
                }
                false
            })
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(logTag, "$logTag onDestroy")
    }

    private fun doOnBackButtonPress() {
        dispatchDetailViewModel.resetTripInfoWidgetIfThereIsNoActiveTrip()
    }

    private fun setCurrentViewPagerTab(){
        homeFragment.viewPager.let {
            // If current tab is not list tab then navigate to list tab
            if (it.currentItem > 0) {
                Log.i(
                    logTag,
                    "Since the current tab is not a list tab, navigate to list tab",
                    null,
                    "current tab" to it.currentItem,
                    "Class" to "HomeFragment",
                    "Action" to "setCurrentViewPagerTab"
                )

                activityDispatchDetailBinding.drawerLayout.let { drawer ->
                    if (drawer.isDrawerOpen(GravityCompat.START))
                        drawer.closeDrawer(GravityCompat.START)
                    else
                        homeFragment.viewPager.setCurrentItem(LIST_FRAGMENT_POSITION, true)
                }
            } else {
                    activity?.finish()
            }
        }
    }

    private fun saveTripName(currentState: DispatchActiveState) {
        Log.d(TRIP_LOAD_VIEW+ TRIP_PREVIEWING, "saveTripName(): currentState=$currentState")

        if (currentState == DispatchActiveState.ACTIVE) {
            dispatchDetailViewModel.saveSelectedTripName()
        }
    }

    /**
     * Sets up the UI based on the incoming dispatch, choosing whether to show the End Trip menu
     * button, as well as the START TRIP button or the PREVIEW ONLY label in the top-right corner.
     */
    private fun setButtonAndLabelVisibilities(currentState: DispatchActiveState) {
        Log.d(TRIP_LOAD_VIEW+ TRIP_PREVIEWING, "setButtonAndLabelVisibilities(): currentState=$currentState")
        if (currentState == DispatchActiveState.ACTIVE) {
            // Show/hide End Trip button based on whether it is allowed in the selected trip's XML
            dispatchDetailViewModel.enableEndTripNavigationViewItem()
        } else {
            // Hide End Trip button if the selected trip hasn't started
            dispatchDetailViewModel.showEndTripNavigationViewItem(false)
        }
        // Show/hide START TRIP button or PREVIEW ONLY label
        setStartButtonAndPreviewLabelVisibility(
            shouldShowStartButton = currentState == DispatchActiveState.NO_TRIP_ACTIVE,
            shouldShowPreviewLabel = currentState == DispatchActiveState.PREVIEWING
        )
        homeFragment.startTripButton.setOnClickListener {
            startButtonAction()
        }
    }

    private fun startButtonAction() {
        Log.logUiInteractionInNoticeLevel(logTag + MANUAL_TRIP_START, "$logTag Manual Trip Start")
        setStartButtonAndPreviewLabelVisibility(
            shouldShowStartButton = false,
            shouldShowPreviewLabel = false
        )
        /** trip start = StopActionReasonTypes.MANUAL.name, driver manual event
         * When the trip is started by driver manually by tapping on start button
         */
        val pfmEventsInfo = PFMEventsInfo.TripEvents(
            reasonType = StopActionReasonTypes.MANUAL.name,
            negativeGuf = false
        )
        doOnTripStart(pfmEventsInfo = pfmEventsInfo, tripStartCaller = TripStartCaller.START_TRIP_BUTTON_PRESS_FROM_DISPATCH_DETAIL_SCREEN)
        dispatchDetailViewModel.setTripStartActionLiveDataValue(true)
        lifecycleScope.launch(dispatchDetailViewModel.coroutineDispatcher.io()) {
            val dispatchId = dispatchDetailViewModel.appModuleCommunicator.getCurrentWorkFlowId(logTag + MANUAL_TRIP_START)
            Log.logTripRelatedEvents(logTag + MANUAL_TRIP_START,"Manual Trip Start D:$dispatchId")
        }
    }

    private fun setStartButtonAndPreviewLabelVisibility(
        shouldShowStartButton: Boolean,
        shouldShowPreviewLabel: Boolean
    ) {
        homeFragment.startTripButton.apply {
            isClickable = shouldShowStartButton
            Log.d(logTag, "setStartButtonAndPreviewLabelVisibility(): shouldShowStartButton=$shouldShowStartButton")
            visibility = if (shouldShowStartButton) View.VISIBLE else View.GONE
        }
        homeFragment.previewOnlyTextView.apply {
            visibility = if (shouldShowPreviewLabel) View.VISIBLE else View.GONE
        }
    }

}
