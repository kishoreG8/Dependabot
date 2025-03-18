package com.trimble.ttm.formlibrary.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.formlibrary.BR
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.FormListRowItemBinding
import com.trimble.ttm.formlibrary.listeners.IFormClickListener
import com.trimble.ttm.formlibrary.utils.isNull
import java.util.*

@Suppress("UNCHECKED_CAST")
class FormLibraryListAdapter(private val onFormSelected: (FormDef) -> Unit) :
    RecyclerView.Adapter<FormLibraryListAdapter.FormLibraryListViewHolder>(),
    IFormClickListener, Filterable {

    private var formListFiltered = mutableListOf<FormDef>()
    var formSet = mutableSetOf<FormDef>()
        set(value) {
            if (field.containsAll(value).not()) {
                field.clear()
                field.addAll(value)
                formListFiltered.clear()
                formListFiltered.addAll(value)
            }
        }
    private var filter: FormDefFilter? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormLibraryListViewHolder {
        val itemRowBinding =
            DataBindingUtil.inflate<FormListRowItemBinding>(
                LayoutInflater.from(parent.context),
                R.layout.form_list_row_item,
                parent,
                false
            )
        return FormLibraryListViewHolder(itemRowBinding)
    }

    override fun getItemCount(): Int = formListFiltered.size

    override fun onBindViewHolder(holder: FormLibraryListViewHolder, position: Int) {
        holder.bind(formListFiltered[position])
        holder.itemRowBinding.let {
            it.formItemClickListener = this
            it.tvFormName.text = formListFiltered[position].name
        }
    }

    class FormLibraryListViewHolder(val itemRowBinding: FormListRowItemBinding) :
        RecyclerView.ViewHolder(itemRowBinding.root) {
        fun bind(form: FormDef) {
            itemRowBinding.setVariable(BR.formDef, form)
            itemRowBinding.executePendingBindings()
        }
    }

    override fun onFormClicked(formDef: FormDef) = onFormSelected(formDef)

    override fun getFilter(): Filter {
        if (filter.isNull())
            filter = FormDefFilter(formSet) {
                formListFiltered.clear()
                formListFiltered.addAll(it)
                notifyDataSetChanged()
            }
        return filter as FormDefFilter
    }

    class FormDefFilter(
        var data: Set<FormDef>,
        private val resultCallback: (Set<FormDef>) -> Unit
    ) : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredData: MutableSet<FormDef> = mutableSetOf()
            if (constraint == null || constraint.isEmpty()) {
                filteredData.addAll(data)
            } else {
                val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                data.filter { item ->
                    item.name.lowercase(Locale.getDefault())
                        .contains(filterPattern.lowercase(Locale.getDefault()))
                }.let { filteredItems ->
                    filteredData.addAll(filteredItems)
                }
            }
            val results = FilterResults()
            results.values = filteredData
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            results?.let { filterResults ->
                (filterResults.values as? Set<FormDef>)?.let { textFilterResults ->
                    resultCallback.invoke(textFilterResults)
                } ?: resultCallback.invoke(emptySet())
            } ?: resultCallback.invoke(emptySet())
        }
    }
}