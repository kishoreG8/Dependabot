package com.trimble.ttm.routemanifest.ui.fragments

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_STOP_LIST
import com.trimble.ttm.commons.logger.TRIP_STOP_LIST_ADAPTER
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.traceBeginSection
import com.trimble.ttm.commons.utils.traceEndSection
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.adapter.StopsListAdapter
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.databinding.FragmentListBinding
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.routemanifest.ui.activities.DispatchBaseActivity
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.utils.STOP_LIST_SCREEN_TIME
import com.trimble.ttm.routemanifest.utils.ext.getTimeStringFromMinutes
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import java.util.concurrent.CopyOnWriteArrayList

class ListFragment : Fragment() {
    private lateinit var noStopsPopupDialog: AlertDialog
    private lateinit var stopsListAdapter: StopsListAdapter
    private val viewModel: DispatchDetailViewModel by sharedViewModel()
    private val dataStoreManager: DataStoreManager by inject()

    private lateinit var fragmentListBinding: FragmentListBinding
    private var isActiveDispatch = false
    private val logTag = "ListFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        traceBeginSection("STOPLISTSCREENLOADINGTIME")
        // Inflate the layout for this fragment
        fragmentListBinding = FragmentListBinding.inflate(inflater, container, false)
        setAdapter()

        lifecycleScope.launch(viewModel.coroutineDispatcher.main()) {
            viewModel.dispatchActiveStateFlow.collect{ currentState ->
                Log.d(TRIP_STOP_LIST_ADAPTER, "ListFragment.onCreateView(): currentState=$currentState")

                val isActiveDispatch = currentState == DispatchActiveState.ACTIVE
                stopsListAdapter.isActiveDispatch = isActiveDispatch
                this@ListFragment.isActiveDispatch = isActiveDispatch
                if (isActiveDispatch) {
                    observeTotalDistance()
                    observeTotalHours()
                }
            }
        }
        return fragmentListBinding.root
    }

    private fun setAdapter() {
        lifecycleScope.launch(
            viewModel.coroutineDispatcher.main() + CoroutineName(logTag)
        ) {
            stopsListAdapter = StopsListAdapter(
                viewModel.appModuleCommunicator.getFeatureFlags(), lifecycleScope
            )
            setItemDecorator()
            observeForStops()
            observeTotalDistance()
            observeTotalHours()
            observeToShowStopsNotAvailablePopup()
            observeStopsActionsAllowedStatus()
            WorkflowEventBus.stopListEvents.firstOrNull()?.let {
                updateStopListUI(it)
                traceEndSection("STOPLISTSCREENLOADINGTIME")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        viewModel.logScreenViewEvent(
            screenName = STOP_LIST_SCREEN_TIME
        )
    }



    private fun setItemDecorator() {
        val itemDecorator = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            .also {
                it.setDrawable(
                    activity?.let { activity ->
                        ContextCompat.getDrawable(
                            activity.applicationContext,
                            R.drawable.divider
                        )
                    }!!
                )
            }
        with(fragmentListBinding) {
            tripInfoRecycler.addItemDecoration(itemDecorator)
            tripInfoRecycler.layoutManager =
                LinearLayoutManager(activity?.applicationContext)
            tripInfoRecycler.adapter = stopsListAdapter
            progressErrorView.setProgressState(getString(R.string.loading_text))
        }
    }

    private fun observeForStops() {
        lifecycleScope.launch(viewModel.coroutineDispatcher.io()) {
            WorkflowEventBus.stopListEvents.onEach {
                updateStopListUI(it)
            }.launchIn(this)
        }
    }

    /**
     * This method observes the stopActionsAllowed status  and notifies the adapter when it is true and when trip start button is clicked.
     * We need to update the UI to show navigation icon when trip is started.
     * After starting the trip, on going back and forth, this UI refresh is not required as it already has the required value.
     */
    private fun observeStopsActionsAllowedStatus(){
        viewModel.stopActionsAllowed.observe(viewLifecycleOwner){
            if(it && viewModel.tripStartAction.value == true){
                Log.d(TRIP_STOP_LIST_ADAPTER, "ListFragment.observeStopsActionsAllowedStatus(): stopActionsAllowed=true calling notifyDatasetChanged")
                stopsListAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun updateStopListUI(stopDetailList: CopyOnWriteArrayList<StopDetail>) {
        lifecycleScope.launch(CoroutineName(logTag)) {
            val areStopsManipulatedForTheActiveTrip = withContext(viewModel.coroutineDispatcher.io()) {
                viewModel.dispatchStopsUseCase.areStopsManipulatedForTheActiveTrip()
            }
            /**
             * removing all stops of an active trip residing in non driver workflow screen auto navigated driver to driver workflow.
             * "stopDetailList.isEmpty() and areStopsManipulatedForTheActiveTrip" will avoid that auto-navigation
             */
            if ((stopDetailList.isEmpty() and areStopsManipulatedForTheActiveTrip) or ::stopsListAdapter.isInitialized.not()){
                return@launch
            }
            if (stopDetailList.isEmpty()) {
                if (dataStoreManager.hasActiveDispatch("ToShowNoStops", false)) {
                    stopsListAdapter.stopList = stopDetailList
                    Log.i(TRIP_STOP_LIST,"EmptyStopList")
                    fragmentListBinding.progressErrorView.setErrorState(getString(R.string.no_stops_to_display))
                }
                (activity as? DispatchBaseActivity)?.checkForTripCompletion()
            } else {
                // Show header view once list is fetched
                fragmentListBinding.distanceDetailHolder.visibility = View.VISIBLE
                fragmentListBinding.vDivider.visibility = View.VISIBLE
                stopsListAdapter.stopList = stopDetailList.getSortedStops()
                stopsListAdapter.notifyDataSetChanged()
                calculateStopCountAndSetInTextView(stopDetailList)
                if (::noStopsPopupDialog.isInitialized) noStopsPopupDialog.dismiss()
                setNoState()
            }
        }
    }

    private fun observeTotalDistance() {
        viewModel.totalDistance.observe(
            viewLifecycleOwner
        ) {
            lifecycleScope.launch(
                CoroutineName(logTag) + viewModel.coroutineDispatcher.main()
            ) {

                if (this@ListFragment.isActiveDispatch || viewModel.appModuleCommunicator.hasActiveDispatch(
                        logTag,
                        false).not()
                ) {
                    fragmentListBinding.totalHours.visibility = View.VISIBLE
                    if (it >= ZERO) {
                        traceEndSection("ROUTECALCULATIONLOADINGTIME")
                        fragmentListBinding.totalMiles.text =
                            String.format("%.2f", it).plus(SPACE)
                                .plus(getString(R.string.miles))
                    } else {
                        traceBeginSection("ROUTECALCULATIONLOADINGTIME")
                        fragmentListBinding.totalMiles.text =
                            getString(R.string.calculating_distance)
                    }
                } else {
                    fragmentListBinding.totalMiles.text =
                        (getString(R.string.est_distance_for_active_trip_only))
                    fragmentListBinding.totalHours.visibility = View.GONE
                }
            }
        }
    }

    private fun observeTotalHours() {
        viewModel.totalHours.observe(
            viewLifecycleOwner
        ) {
            if (it >= ZERO) {
                fragmentListBinding.totalHours.text =
                    activity?.let { activity ->
                        it.getTimeStringFromMinutes(activity).plus(SPACE)
                            .plus(getString(R.string.driveTime))
                    }
            } else fragmentListBinding.totalHours.text = getString(R.string.calculating_eta)
        }
    }

    private fun observeToShowStopsNotAvailablePopup() {
        viewModel.canShowStopsNotAvailablePopup.observe(
            viewLifecycleOwner
        ) { canShowStopsNotAvailablePopup ->
            if (canShowStopsNotAvailablePopup) {
                fragmentListBinding.progressErrorView.setNoState()
                val messageToShow = getString(
                    R.string.no_internet_connection_with_placeholder,
                    getString(R.string.stop_list_could_not_be_loaded)
                )
                val positiveActionText = getString(R.string.ok_text)
                activity?.let { activity ->
                    noStopsPopupDialog = UiUtils.showAlertDialog(
                        AlertDialogData(
                            context = activity,
                            message = messageToShow,
                            positiveActionText = positiveActionText,
                            negativeActionText = "",
                            isCancelable = false,
                            positiveAction = { onNoStopsPopupDialogPositiveClick() },
                            negativeAction = { })
                    )
                }
            }
        }
    }

    private fun setNoState() = fragmentListBinding.progressErrorView.setNoState()

    private fun onNoStopsPopupDialogPositiveClick() {
        Log.logUiInteractionInInfoLevel(
            logTag,
            "$logTag NoStopsPopupDialogPositiveClicked"
        )
        (activity?.application as? WorkflowApplication)?.applicationScope?.launch(
            CoroutineName(logTag)
        ) {
            dataStoreManager.removeAllKeys()
        }
        activity?.let { activity ->
            Intent(activity, DispatchListActivity::class.java).also {
                startActivity(it)
                activity.finish()
            }
        }
    }

    private fun calculateStopCountAndSetInTextView(stopDetailList: List<StopDetail>) {
        lifecycleScope.launch(CoroutineName(logTag)) {
            val stopCountText = String.format("%d ", stopDetailList.filter {
                it.completedTime.isEmpty()
            }.size).plus(getString(R.string.stops))
            fragmentListBinding.totalStops.text = stopCountText
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fragmentListBinding.tripInfoRecycler.adapter = stopsListAdapter
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        if (::noStopsPopupDialog.isInitialized) noStopsPopupDialog.dismiss() // Prevent window leak

    }
}
