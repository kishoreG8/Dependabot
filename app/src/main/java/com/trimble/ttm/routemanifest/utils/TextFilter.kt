package com.trimble.ttm.routemanifest.utils

import android.widget.Filter
import com.trimble.ttm.formlibrary.utils.isNull
import java.util.*

interface ITextFilter {
    fun getFilterableText(): String
}

@Suppress("UNCHECKED_CAST")
class TextFilter<T : ITextFilter>(
    var data: List<T>,
    private val resultCallback: (List<T>) -> Unit
) : Filter() {
    override fun performFiltering(constraint: CharSequence?): FilterResults {
        val filteredData: MutableList<T> = ArrayList()
        if (constraint.isNull() || constraint?.isEmpty()!!) {
            filteredData.addAll(data)
        } else {
            val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
            data.filter { item ->
                item.getFilterableText().lowercase(Locale.getDefault())
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
            (filterResults.values as? List<T>)?.let { textFilterResults ->
                resultCallback.invoke(textFilterResults)
            } ?: resultCallback.invoke(emptyList())
        } ?: resultCallback.invoke(emptyList())
    }
}