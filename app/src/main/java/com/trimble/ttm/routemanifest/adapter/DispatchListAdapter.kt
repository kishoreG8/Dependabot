package com.trimble.ttm.routemanifest.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.routemanifest.BR
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.databinding.DispatchItemRowBinding
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.DISPATCH_NAME_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.SELECTED_DISPATCH_KEY
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.utils.DISPATCH_DESC_CHAR_LENGTH
import com.trimble.ttm.routemanifest.utils.END_INDEX
import com.trimble.ttm.routemanifest.utils.START_INDEX
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.Utils.decodeString
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class DispatchListAdapter(
    private val dataStoreManager: DataStoreManager,
    private val coroutineScope: CoroutineScope,
    private val coroutineDispatcherProvider: DefaultDispatcherProvider = DefaultDispatcherProvider(),
    private val onDispatchSelected: (Boolean) -> Unit
) : RecyclerView.Adapter<DispatchListAdapter.DispatchInfoViewHolder>(), IDispatchClickListener {
    private lateinit var context: Context
    var dispatchList = listOf<Dispatch>()

    override fun dispatchClicked(dispatch: Dispatch) {
        coroutineScope.launch(CoroutineName("DispatchClicked") + coroutineDispatcherProvider.io()) {
            dataStoreManager.setValue(SELECTED_DISPATCH_KEY, dispatch.dispid)
            dataStoreManager.setValue(DISPATCH_NAME_KEY, dispatch.name)
            onDispatchSelected(dispatchList.size == 1)
            Log.logUiInteractionInInfoLevel(TRIP + "ListAdapter", "DispatchItemClicked. DispatchId: ${dispatch.dispid}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DispatchInfoViewHolder {

        val itemRowBinding =
            DataBindingUtil.inflate<DispatchItemRowBinding>(
                LayoutInflater.from(parent.context),
                R.layout.dispatch_item_row,
                parent,
                false
            )

        return DispatchInfoViewHolder(itemRowBinding)
    }

    override fun getItemCount() = dispatchList.size

    override fun onBindViewHolder(holder: DispatchInfoViewHolder, position: Int) {
        context = holder.itemView.context
        holder.bind(dispatchList[position])
        holder.itemRowBinding.dispatchItemClickListener = this
        holder.itemRowBinding.userData.visibility =
            if (dispatchList[position].userdata1.isEmpty()
                && dispatchList[position].userdata2.isEmpty()
            )
                View.GONE
            else
                View.VISIBLE

        holder.itemRowBinding.userData.text =
            if (dispatchList[position].userdata1.isNotEmpty())
                decodeString(dispatchList[position].userdata1)
            else
                decodeString(dispatchList[position].userdata2)

        holder.itemRowBinding.date.text = Utils.systemDateFormat(dispatchList[position].created)
        if (holder.itemRowBinding.date.text.isEmpty()) {
            holder.itemRowBinding.tvDate.visibility = View.GONE
            holder.itemRowBinding.date.visibility = View.GONE
        } else {
            holder.itemRowBinding.tvDate.visibility = View.VISIBLE
            holder.itemRowBinding.date.visibility = View.VISIBLE
        }
        if (dispatchList[position].name.isEmpty())
            holder.itemRowBinding.textView.visibility = View.GONE
        else
            holder.itemRowBinding.textView.visibility = View.VISIBLE
        if (dispatchList[position].description.length > DISPATCH_DESC_CHAR_LENGTH) {
            holder.itemRowBinding.dispatchDesc.text =
                dispatchList[position].description.substring(START_INDEX, END_INDEX)
                    .plus(context.getString(R.string.dots))
        } else
            holder.itemRowBinding.dispatchDesc.text = dispatchList[position].description
    }

    class DispatchInfoViewHolder(val itemRowBinding: DispatchItemRowBinding) :
        RecyclerView.ViewHolder(itemRowBinding.root) {
        fun bind(dispatch: Dispatch) {
            itemRowBinding.setVariable(BR.dispatchInfo, dispatch)
            itemRowBinding.executePendingBindings()
        }
    }

}