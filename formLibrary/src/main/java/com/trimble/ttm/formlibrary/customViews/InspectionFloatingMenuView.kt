package com.trimble.ttm.formlibrary.customViews

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.InspectionFloatingMenuViewLayoutBinding
import com.trimble.ttm.formlibrary.model.InspectionType

class InspectionFloatingMenuView(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {

    var isMenuOpened = false
    var listener: (InspectionType) -> Unit = {}
    private var inspectionFloatingMenuView: InspectionFloatingMenuViewLayoutBinding =
        InspectionFloatingMenuViewLayoutBinding.inflate(LayoutInflater.from(context),this,true)

    init {

        setupFabMenu()
    }

    private fun setupFabMenu() {

        with(inspectionFloatingMenuView){
            fabNew.setOnClickListener {
                doOnFabClick()
            }
            tvPreTrip.setOnClickListener {
                listener.invoke(InspectionType.PreInspection)
            }
            tvPostTrip.setOnClickListener {
                listener.invoke(InspectionType.PostInspection)
            }
            tvInterTrip.setOnClickListener {
                listener.invoke(InspectionType.InterInspection)
            }
            tvDot.setOnClickListener {
                listener.invoke(InspectionType.DotInspection)
            }
            transparentLayout.setOnClickListener {
                if (isMenuOpened) closeMenu()
            }
        }

    }

    private fun doOnFabClick() = if (isMenuOpened) closeMenu() else openMenu()

    fun closeMenu() {

        with(inspectionFloatingMenuView){
            transparentLayout.visibility = INVISIBLE
            tvDot.visibility = INVISIBLE
            tvInterTrip.visibility = INVISIBLE
            tvPostTrip.visibility = INVISIBLE
            tvPreTrip.visibility = INVISIBLE
            fabNew.setImageResource(R.drawable.ic_add_white_24dp)
        }

        isMenuOpened = false
    }

    private fun openMenu() {
        with(inspectionFloatingMenuView){
            transparentLayout.visibility = View.VISIBLE
            tvDot.visibility = View.VISIBLE
            tvInterTrip.visibility = View.VISIBLE
            tvPostTrip.visibility = View.VISIBLE
            tvPreTrip.visibility = View.VISIBLE
            fabNew.setImageResource(R.drawable.ic_arrow_down_white_24dp)
        }

        isMenuOpened = true
    }

    fun setMenuItemSelectedListener(listener: (InspectionType) -> Unit) {
        this.listener = listener
    }
}