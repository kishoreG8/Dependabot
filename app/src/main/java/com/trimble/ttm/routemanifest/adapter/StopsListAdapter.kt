package com.trimble.ttm.routemanifest.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_STOP_LIST
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import com.trimble.ttm.commons.utils.isFeatureTurnedOn
import com.trimble.ttm.routemanifest.BR
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.databinding.ItemRowBinding
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.isArrived
import com.trimble.ttm.routemanifest.model.isDeparted
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.ui.activities.StopDetailActivity
import com.trimble.ttm.routemanifest.utils.SELECTED_STOP_ID
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class StopsListAdapter(
    private val featureFlags: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>,
    val lifecycleScope: CoroutineScope,
) : RecyclerView.Adapter<StopsListAdapter.StopDetailViewHolder>(), CustomClickListener {
    private val tag = "StopListAdapter"
    var stopList = listOf<StopDetail>()
    var isActiveDispatch: Boolean = false
    private lateinit var context: Context

    override fun cardClicked(stopDetail: StopDetail) {
        lifecycleScope.launch(CoroutineName("StopItemClicked") + Dispatchers.Main.immediate) {
            Log.logUiInteractionInInfoLevel(TRIP_STOP_LIST + "ListAdapter", "StopItemClicked. DispatchId: ${stopDetail.dispid} StopId: ${stopDetail.stopid}")
            Intent(context, StopDetailActivity::class.java).apply {
                putExtra(SELECTED_STOP_ID, stopDetail.stopid)
                this.flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                context.startActivity(this)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopDetailViewHolder {

        val itemRowBinding =
            DataBindingUtil.inflate<ItemRowBinding>(
                LayoutInflater.from(parent.context),
                R.layout.item_row,
                parent,
                false
            )

        return StopDetailViewHolder(itemRowBinding)
    }

    override fun getItemCount() = stopList.size

    override fun onBindViewHolder(holder: StopDetailViewHolder, position: Int) {
        context = holder.itemView.context
        try {
            val currentStop = stopList[position]
            holder.bind(currentStop)
            val viewModel = (context as DispatchDetailActivity).dispatchDetailViewModel
            holder.itemRowBinding.let {
                it.itemClickListener = this
                it.setVariable(BR.tripViewModel, viewModel)
                clearViewsToAvoidViewReuseIssues(it)
                lifecycleScope.launch( CoroutineName("StopListOnBindViewHolder") + Dispatchers.Main.immediate) {
                    it.setVariable(
                        BR.displayNavigate,
                        viewModel.canShowNavigate(
                            currentStop
                        )
                    )
                    setStopAddressVisibilityBasedOnFeatureFlag(
                        it,
                        featureFlags.isFeatureTurnedOn(
                            FeatureGatekeeper.KnownFeatureFlags.SHOULD_DISPLAY_ADDRESS
                        )
                    )
                    setStopAddressVisibility(it, currentStop)
                    handleUIIfStopHasArrived(it, currentStop)
                    handleUIIfHasDeparted(it, currentStop)
                    launch {
                        getFormatedAddressOrCompletedTime(
                            currentStop,
                            viewModel
                        ).collect { text ->
                            it.stopAdress.text = text
                        }
                    }
                }
                it.executePendingBindings()
            }
        } catch (e: Exception) {
            Log.e(tag, "exception in stopListAdapter ${e.message}")
        }
    }

    private fun clearViewsToAvoidViewReuseIssues(it: ItemRowBinding) {
        it.stopAddressTitle.visibility = View.GONE
        it.stopAdress.visibility = View.GONE
        it.stopDepartedOn.visibility = View.GONE
        it.stopDepartedOnText.visibility = View.GONE
        it.stopArrivedOn.visibility = View.GONE
        it.stopArrivedOnText.visibility = View.GONE
    }

    private fun setStopAddressVisibilityBasedOnFeatureFlag(
        it: ItemRowBinding,
        shouldDisplayAddress: Boolean
    ) {
        if (shouldDisplayAddress.not()) return
        it.stopAddressTitle.visibility = View.VISIBLE
        it.stopAdress.visibility = View.VISIBLE
    }

    private fun setStopAddressVisibility(it: ItemRowBinding, currentStop: StopDetail) {
        if (currentStop.isArrived() || currentStop.isDeparted()) {
            it.stopAddressTitle.visibility = View.GONE
            it.stopAdress.visibility = View.GONE
        }
    }

    private fun handleUIIfHasDeparted(it: ItemRowBinding, currentStop: StopDetail) {
        if (!currentStop.isDeparted()) return
        it.stopDepartedOn.visibility = View.VISIBLE
        it.stopDepartedOnText.visibility = View.VISIBLE
        it.stopDepartedOnText.text =
            if (currentStop.departedTime.isNotEmpty()) Utils.getSystemLocalDateTimeFromUTCDateTime(
                currentStop.departedTime
            ) else ""
    }

    private fun handleUIIfStopHasArrived(it: ItemRowBinding, currentStop: StopDetail) {
        if (!currentStop.isArrived()) return
        if (!currentStop.isArrivalAvailable()) return
        it.stopArrivedOn.visibility = View.VISIBLE
        it.stopArrivedOnText.visibility = View.VISIBLE
        it.stopArrivedOnText.text =
            if (currentStop.completedTime.isNotEmpty()) Utils.getSystemLocalDateTimeFromUTCDateTime(
                currentStop.completedTime
            ) else ""
    }

    private fun getFormatedAddressOrCompletedTime(
        currentStop: StopDetail,
        viewModel: DispatchDetailViewModel
    ): Flow<String> = flow {
        if (currentStop.completedTime.isNotEmpty()) {
            emit(
                viewModel.getCompletedTime(currentStop.completedTime)
            )
        } else {
            viewModel.obtainFormattedAddress(
                currentStop.stopid,
                true,
                Utils.getDeviceLocale(context) != null
            ).collect {
                emit(it)
            }
        }
    }

    class StopDetailViewHolder(val itemRowBinding: ItemRowBinding) :
        RecyclerView.ViewHolder(itemRowBinding.root) {
        fun bind(stopDetail: StopDetail) {
            itemRowBinding.setVariable(BR.stopDetail, stopDetail)
            itemRowBinding.executePendingBindings()
        }
    }

}