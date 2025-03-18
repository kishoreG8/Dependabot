package com.trimble.ttm.routemanifest.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.trimble.ttm.commons.logger.AUTO_TRIP_START
import com.trimble.ttm.commons.logger.FRAGMENT
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_LIST
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.CURRENT_DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.IS_TRIP_AUTO_STARTED_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.SELECTED_DISPATCH_KEY
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.eventbus.EventBus
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.adapter.DispatchListAdapter
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.databinding.FragmentTripListBinding
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.utils.NEGATIVE_GUF_TIMEOUT
import com.trimble.ttm.routemanifest.utils.TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS
import com.trimble.ttm.routemanifest.utils.TRIP_START_EVENT_REASON_TYPE
import com.trimble.ttm.routemanifest.viewmodel.DispatchListViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class TripListFragment : Fragment() {
    private val viewModel: DispatchListViewModel by sharedViewModel()
    private lateinit var dispatchListAdapter: DispatchListAdapter
    private var tripStartPrompt: AlertDialog? = null
    private var isPopupDisplayed: Boolean = false
    private var dispatchIdForWhichThePromptShown = ""
    private val dataStoreManager: DataStoreManager by inject()
    private var tripAutoStartTimer: Timer? = null
    private var timeRemaining: Int = 0
    private val logTag = "TripListFragment"
    private var tripStartEventReasons = EMPTY_STRING


    private lateinit var fragmentTripListBinding: FragmentTripListBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        // Inflate the layout for this fragment
        fragmentTripListBinding = FragmentTripListBinding.inflate(inflater, container, false)
        dispatchListAdapter = DispatchListAdapter(dataStoreManager, lifecycleScope) { onDispatchItemClick(it) }
        return fragmentTripListBinding.root
    }

    private fun onDispatchItemClick(clearTop: Boolean) {
        Intent(context, DispatchDetailActivity::class.java).apply {
            if (clearTop) this.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            this.putExtra(TRIP_START_EVENT_REASON_TYPE, tripStartEventReasons)
            startActivity(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        with(fragmentTripListBinding) {
            dispatchList.adapter = dispatchListAdapter
            dispatchList.layoutManager = LinearLayoutManager(context)
            progressErrorView.setProgressState(getString(R.string.loading_text))
        }
        observeDispatchList()
        observeErrorData()
        observeDispatchToShowTripStartPrompt()
        observeForDispatchRemovedFromList()
        observeIfLastDispatchReached()
    }

    private fun observeDispatchList() {
        viewModel.dispatchListFlow.asLiveData().observe(
            viewLifecycleOwner
        ) {
            with(fragmentTripListBinding) {
                Log.d("$TRIP_LIST$FRAGMENT", "ObserveTrips${it.map {it.dispid}}")
                if (it.isNotEmpty()) {
                    (activity as DispatchListActivity).findViewById<TabLayout>(R.id.tabLayout).let {
                        if(it.visibility == View.GONE)
                            it.visibility = View.VISIBLE
                    }
                    dispatchListAdapter.dispatchList = listOf()
                    dispatchListAdapter.dispatchList = it
                    dispatchListAdapter.notifyDataSetChanged()
                    progressErrorView.setNoState()
                    hidePaginationProgressBar()
                    dispatchList.visibility = View.VISIBLE
                } else {
                    (activity as DispatchListActivity).findViewById<TabLayout>(R.id.tabLayout).visibility = View.GONE
                    dispatchList.visibility = View.GONE
                    hidePaginationProgressBar()
                    progressErrorView.setErrorState(getString(R.string.no_trip))
                    tripStartPrompt?.dismiss()
                }
            }

        }
    }

    private fun FragmentTripListBinding.hidePaginationProgressBar() {
        if (paginationProgressBar.isVisible) {
            paginationProgressBar.visibility = View.GONE
        }
    }

    private fun observeErrorData() {
        viewModel.errorData.observe(viewLifecycleOwner) {
            it.let {
                fragmentTripListBinding.progressErrorView.setErrorState(it)
                fragmentTripListBinding.hidePaginationProgressBar()
            }
        }
    }

    private fun observeDispatchToShowTripStartPrompt() {
        viewModel.dispatchToShowTripStartPrompt.observe(
            viewLifecycleOwner
        ) { oldestDispatch ->
            lifecycleScope.launch(CoroutineName(TRIP_LIST + FRAGMENT)) {
                if (oldestDispatch.isNull() || dataStoreManager.hasActiveDispatch("autoStartTrip",false)) return@launch
                tripStartPrompt?.dismiss()
                tripStartPrompt = null
                if (oldestDispatch.tripStartTimed == 1) autoStartTrip(oldestDispatch, "Timed Start")
                else if (!isPopupDisplayed) {
                    dispatchIdForWhichThePromptShown = oldestDispatch.dispid
                    activity?.let { activity ->
                        AlertDialog.Builder(
                            activity, R.style.dialogTheme
                        ).let { alertDialogBuilder ->
                            val preText = getString(R.string.trip_start_prompt_pre_text)
                            val postText = getString(R.string.trip_start_prompt_post_text)
                            alertDialogBuilder.setMessage("$preText ${oldestDispatch.name} $postText")
                            alertDialogBuilder.setPositiveButton(R.string.yes) { _, _ ->
                                Log.logUiInteractionInNoticeLevel(TRIP_LIST + FRAGMENT, "$logTag Alert prompt positive action clicked")
                                tripStartEventReasons = viewModel.getTripStartEventReasons(oldestDispatch)
                                Log.d(TRIP_LIST + FRAGMENT, "tripStartEventReasons: $tripStartEventReasons")
                                autoStartTrip(oldestDispatch, "Alert prompt start")
                                cancelTripStartGufTimerAndDismissDialog()
                            }
                            alertDialogBuilder.setNegativeButton(R.string.no) { _, _ ->
                                lifecycleScope.launch(viewModel.coroutineDispatcherProvider.io() + CoroutineName(TRIP_LIST + FRAGMENT)) {
                                    dataStoreManager.setValue(SELECTED_DISPATCH_KEY, EMPTY_STRING)
                                    dataStoreManager.setValue(IS_TRIP_AUTO_STARTED_KEY, false)
                                    dataStoreManager.setValue(DISPATCH_NAME_KEY, EMPTY_STRING)
                                    dataStoreManager.setValue(CURRENT_DISPATCH_NAME_KEY, EMPTY_STRING)
                                    Log.logUiInteractionInNoticeLevel(TRIP_LIST + FRAGMENT, "$logTag Alert prompt negative action clicked. Dispatch id: ${oldestDispatch.dispid}")
                                }
                                isPopupDisplayed = true
                                cancelTripStartGufTimerAndDismissDialog()
                            }
                            alertDialogBuilder.create()
                        }.also { alertDialog ->
                            this@TripListFragment.tripStartPrompt = alertDialog
                            showTripToBeStartedDialogWithTimer(oldestDispatch, alertDialog)

                            alertDialog.setCancelable(false)
                            alertDialog.show()
                        }
                    }
                }
            }
        }
    }

    private fun cancelTripStartGufTimerAndDismissDialog() {
        tripAutoStartTimer?.cancel()
        tripAutoStartTimer?.purge()
        tripAutoStartTimer = null
        tripStartPrompt?.dismiss()
    }

    private fun showTripToBeStartedDialogWithTimer(
        oldestDispatch: Dispatch,
        alertDialog: AlertDialog
    ) {
        if (oldestDispatch.tripStartNegGuf == 1) {
            alertDialog.setOnShowListener {
                (it as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.let { positiveButton ->
                        tripAutoStartTimer?.cancel()
                        tripAutoStartTimer?.purge()
                        timeRemaining = TRIP_POSITIVE_ACTION_TIMEOUT_IN_SECONDS
                        tripAutoStartTimer =
                            fixedRateTimer("timer", false, 0, 1000) {
                                lifecycleScope.launch(CoroutineName(TRIP_LIST + FRAGMENT)) {
                                    if (timeRemaining > 0) {
                                        this@TripListFragment.context?.let { context ->
                                            positiveButton.text = context.getString(R.string.yes).plus("(").plus(timeRemaining)
                                                .plus(")")
                                        }
                                        timeRemaining = timeRemaining.minus(1)
                                    } else {
                                        Log.logUiInteractionInNoticeLevel(TRIP_LIST + FRAGMENT, "$logTag Alert prompt positive(~) action neg guf started")
                                        /** trip start = StopActionReasonTypes.TIMEOUT.name, negative guf timeout Event (sent from HomeFragment)
                                         * The trip has auto_start_driver_negative_guf tag and it is auto started when timer expires
                                         */
                                        tripStartEventReasons = NEGATIVE_GUF_TIMEOUT
                                        autoStartTrip(
                                            oldestDispatch, "Alert prompt expired start"
                                        )
                                        tripAutoStartTimer?.cancel()
                                        tripStartPrompt?.dismiss()
                                    }
                                }
                            }
                    }
            }
        }
    }

    private fun observeForDispatchRemovedFromList() {
        viewModel.dispatchRemovedFromList.observe(viewLifecycleOwner) { dispatchIdRemovedFromList ->
            if (dispatchIdRemovedFromList.isNullOrEmpty()) return@observe
            if (dispatchIdForWhichThePromptShown.isEmpty()) return@observe
            if (dispatchIdRemovedFromList == dispatchIdForWhichThePromptShown) tripStartPrompt?.let {
                it.dismiss()
                tripStartPrompt = null
            }
        }
    }

    private fun observeIfLastDispatchReached() {
        viewModel.isLastDispatchReached.observe(viewLifecycleOwner) {
            if (it && fragmentTripListBinding.paginationProgressBar.isVisible) {
                fragmentTripListBinding.paginationProgressBar.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        viewModel.dismissTripPanelMessageIfThereIsNoActiveTrip()
        viewModel.getDispatchList("onResume")
    }

    override fun onStop() {
        super.onStop()
        cancelTripStartGufTimerAndDismissDialog()
    }

    private fun autoStartTrip(dispatch: Dispatch, caller: String) {
        (activity?.application as? WorkflowApplication)?.applicationScope?.launch(
            CoroutineName(
                TRIP_LIST + FRAGMENT
            ) + viewModel.coroutineDispatcherProvider.io()
        ) {
            Log.logTripRelatedEvents(TRIP_LIST + FRAGMENT + AUTO_TRIP_START,"Auto Trip Started with caller $caller D:${dispatch.dispid}")
            viewModel.updateDispatchInfoToDataStore(dispatch.dispid,dispatch.name, caller)
            EventBus.resetRouteCalculationRetry()
            dataStoreManager.setValue(SELECTED_DISPATCH_KEY, dispatch.dispid)
            dataStoreManager.setValue(IS_TRIP_AUTO_STARTED_KEY, true)
            viewModel.hasOnlyOneDispatchOnList().collect {
                onDispatchItemClick(it)
                val currentJob = this.coroutineContext.job
                if (currentJob.isActive) currentJob.cancel()
            }
        }
    }

    override fun onDestroyView() {
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        tripStartPrompt?.dismiss()
        tripStartPrompt = null
        super.onDestroyView()
    }

}
