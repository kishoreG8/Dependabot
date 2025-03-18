package com.trimble.ttm.routemanifest.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.commons.utils.isFeatureTurnedOn
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.databinding.FragmentTimelineStopDetailBinding
import com.trimble.ttm.routemanifest.eventbus.WorkflowEventBus
import com.trimble.ttm.routemanifest.ui.activities.StopDetailActivity
import com.trimble.ttm.routemanifest.utils.CURRENT_STOP_INDEX
import com.trimble.ttm.routemanifest.utils.SELECTED_STOP_ID
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class TimelineStopDetailFragment : Fragment() {

    private val viewModel: DispatchDetailViewModel by sharedViewModel()
    private val logTag = "TimelineStopDetailFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        viewModel.selectStop(0)
        val parentLayout =
            DataBindingUtil.inflate<FragmentTimelineStopDetailBinding>(
                inflater,
                R.layout.fragment_timeline_stop_detail,
                container,
                false
            )
        parentLayout.lifecycleOwner = this
        parentLayout.dispatchDetailViewModel = viewModel

        //Increasing the touch area of the buttons
        Utils.increaseTouchPortionOfView(parentLayout.nextStop)
        Utils.increaseTouchPortionOfView(parentLayout.previousStop)

        // Add click listener to bottomCard CardView to
        // navigate to stop detail page
        parentLayout.bottomCard.setOnClickListener {
            lifecycleScope.safeLaunch {
                WorkflowEventBus.stopListEvents.firstOrNull()?.let { stopDetailList ->
                    viewModel.currentStop.value?.let { stopDetail ->
                        activity?.let { activity ->
                            Log.logUiInteractionInInfoLevel(logTag, "$logTag Clicked on stop detail card. Redirecting to stop detail screen")
                            Intent(activity, StopDetailActivity::class.java)
                                .apply {
                                    putExtra(
                                        CURRENT_STOP_INDEX,
                                        stopDetailList.indexOf(stopDetailList.find { it.stopid == stopDetail.stopid })
                                    )
                                    putExtra(SELECTED_STOP_ID, stopDetail.stopid)
                                    this.flags =
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    startActivity(this)
                                }
                        }
                    }
                }
            }
        }
        setNameStopObserver(parentLayout)
        return parentLayout.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
    }

    private fun setNameStopObserver(
        parentLayout: FragmentTimelineStopDetailBinding,
        dispatcherMain:CoroutineDispatcher = Dispatchers.Main
    ) {
        viewModel.currentStop.observe(
            viewLifecycleOwner
        ) {
            it?.let {
                lifecycleScope.safeLaunch(dispatcherMain) {
                    viewModel.obtainFormattedAddress(
                        it.stopid,
                        true, context?.let { context ->
                            Utils.getDeviceLocale(context)
                        } != null
                    ).safeCollect("$tag Get formatted address") {
                        parentLayout.stopAddress1.text = it
                    }
                    if(viewModel.appModuleCommunicator.getFeatureFlags().isFeatureTurnedOn(FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS)){
                        parentLayout.stopAddress1.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}