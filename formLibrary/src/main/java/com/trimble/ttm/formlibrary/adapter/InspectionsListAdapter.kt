package com.trimble.ttm.formlibrary.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.library.baseAdapters.BR
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.InspectionRowItemBinding
import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.getInspectionTypeUIText

class InspectionsListAdapter(private val onInspectionSelected: (EDVIRInspection) -> Unit) :
    RecyclerView.Adapter<InspectionsListAdapter.InspectionsListViewHolder>(),
    IInspectionClickListener {

    var inspectionsList = listOf<EDVIRInspection>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InspectionsListViewHolder {
        val itemRowBinding =
            DataBindingUtil.inflate<InspectionRowItemBinding>(
                LayoutInflater.from(parent.context),
                R.layout.inspection_row_item,
                parent,
                false
            )

        return InspectionsListViewHolder(itemRowBinding)
    }

    override fun getItemCount(): Int = inspectionsList.size

    override fun onBindViewHolder(holder: InspectionsListViewHolder, position: Int) {
        holder.bind(inspectionsList[position])
        holder.itemRowBinding.let {
            it.inspectionItemClickListener = this
            it.tvInspectionType.text =
                inspectionsList[position].inspectionType.getInspectionTypeUIText(holder.itemView.context)
            if (inspectionsList[position].createdAt.isNotEmpty()) {
                val localDateTime = Utils.getSystemLocalDateTimeFromUTCDateTime(
                    inspectionsList[position].createdAt,
                    holder.itemView.context
                )
                it.tvCreatedAt.text = localDateTime.ifEmpty { "" }
            } else it.tvCreatedAt.text = ""
        }
    }

    //region View Holder

    class InspectionsListViewHolder(val itemRowBinding: InspectionRowItemBinding) :
        RecyclerView.ViewHolder(itemRowBinding.root) {
        fun bind(inspection: EDVIRInspection) {
            itemRowBinding.setVariable(BR.eDVIRInspection, inspection)
            itemRowBinding.executePendingBindings()
        }
    }

    override fun onInspectionClicked(eDVIRInspection: EDVIRInspection) {
        onInspectionSelected(eDVIRInspection)
    }

    //endregion
}