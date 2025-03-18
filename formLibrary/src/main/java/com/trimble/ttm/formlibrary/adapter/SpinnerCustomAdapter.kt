package com.trimble.ttm.formlibrary.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.utils.isNull


class SpinnerCustomAdapter(
    context: Context,
    private val layoutResource : Int,
    private var dropDownItems : ArrayList<String>
) : ArrayAdapter<String>(
    context,layoutResource,dropDownItems) {

    override fun getCount(): Int {
        return dropDownItems.size
    }

    override fun getItem(position: Int): String = dropDownItems[position]

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        try {
            if (convertView.isNull()) {
                view = LayoutInflater.from(context).inflate(layoutResource,null)
            }
            val name: TextView = view?.findViewById(R.id.tv_spinner)!!
            val bottomLine: View = view.findViewById(R.id.spinner_line)
            name.text = dropDownItems[position]
            if (position == dropDownItems.size -1){
                bottomLine.visibility = View.GONE
            } else {
                bottomLine.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return view ?: LayoutInflater.from(context).inflate(layoutResource,null)
    }

}