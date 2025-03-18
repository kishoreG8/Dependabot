package com.trimble.ttm.formlibrary.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.CAN_SHOW_EDVIR_IN_HAMBURGER_MENU
import com.trimble.ttm.formlibrary.model.HamburgerMenu
import com.trimble.ttm.formlibrary.model.HamburgerMenuItem
import com.trimble.ttm.formlibrary.utils.HOT_KEYS_MENU_INDEX
import com.trimble.ttm.formlibrary.utils.isNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HamburgerMenuAdapter(private val context: Context) : BaseExpandableListAdapter(), KoinComponent {
    private val _hamburgerMenuList = mutableListOf<HamburgerMenu>()
    var hamburgerMenuList = listOf<HamburgerMenu>()
        set(value) {
            _hamburgerMenuList.clear()
            _hamburgerMenuList.addAll(value)
        }
    private val inspection =
        HamburgerMenu(HamburgerMenuItem(R.string.menu_inspections), ArrayList())
    private val formDataStoreManager: FormDataStoreManager by inject()

    override fun getGroupCount(): Int = _hamburgerMenuList.size

    override fun getChildrenCount(groupPosition: Int): Int {
        return _hamburgerMenuList[groupPosition].child.size
    }

    override fun getGroup(groupPosition: Int): Any {
        return _hamburgerMenuList[groupPosition].group
    }

    override fun getChild(groupPosition: Int, childPosition: Int): Any {
        return _hamburgerMenuList[groupPosition].child[childPosition]
    }

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()

    override fun hasStableIds(): Boolean = true

    override fun getGroupView(
        groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?
    ): View? {
        var mutableConvertView = convertView
        val hamburgerMenuItem = getGroup(groupPosition) as HamburgerMenuItem
        if (convertView.isNull()) {
            val inflater: LayoutInflater? =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as? LayoutInflater
            mutableConvertView =
                inflater?.inflate(R.layout.hamburger_menu_group_item, parent, false)
        }
        mutableConvertView?.let { rootView ->
            val tvHamburgerMenu: TextView = rootView.findViewById(R.id.tvHamburgerMenu)
            val ivGroupIndicator: ImageView = rootView.findViewById(R.id.ivGroupIndicator)
            val ivIcon: ImageView = rootView.findViewById(R.id.ivIcon)
            ivGroupIndicator.visibility =
                if (getChildrenCount(groupPosition) == 0) View.INVISIBLE else View.VISIBLE
            if (isExpanded.not()) ivGroupIndicator.setImageResource(R.drawable.ic_hamburger_menu_collapsed_arrow) else ivGroupIndicator.setImageResource(
                R.drawable.ic_hamburger_menu_expanded_arrow
            )
            tvHamburgerMenu.text = context.getString(hamburgerMenuItem.menuItemStringRes)
            when (groupPosition) {
                0 -> ivIcon.setImageResource(R.drawable.ic_inbox_navigation_menu)
                1 -> ivIcon.setImageResource(R.drawable.ic_forms_navigation_menu)
                else -> when (tvHamburgerMenu.text) {
                    context.getString(R.string.menu_hot_keys) -> ivIcon.setImageResource(R.drawable.ic_hot_keys_navigation_menu)
                    context.getString(R.string.menu_inspections) -> ivIcon.setImageResource(R.drawable.ic_inspection_navigation_menu)
                    context.getString(R.string.menu_trip_list) -> ivIcon.setImageResource(R.drawable.ic_trip_list_navigation_menu)
                    context.getString(R.string.menu_stop_list) -> ivIcon.setImageResource(R.drawable.ic_stop_list_navigation_menu)
                    context.getString(R.string.menu_end_trip) -> ivIcon.setImageResource(R.drawable.ic_end_trip_navigation_menu)
                }
            }
        }
        return mutableConvertView
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        var mutableConvertView = convertView
        val navMenu = getChild(groupPosition, childPosition) as HamburgerMenuItem
        if (convertView.isNull()) {
            val inflater: LayoutInflater? =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as? LayoutInflater
            mutableConvertView =
                inflater?.inflate(R.layout.hamburger_menu_child_item, parent, false)
        }
        val tvHamburgerMenu: TextView = mutableConvertView?.findViewById(R.id.tvHamburgerMenu)!!
        tvHamburgerMenu.text = context.getString(navMenu.menuItemStringRes)
        return mutableConvertView
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

    fun removeGroupItem(menuItemStrRes: Int) {
        _hamburgerMenuList.find { it.group.menuItemStringRes == menuItemStrRes }?.let {
            _hamburgerMenuList.remove(it)
            notifyDataSetChanged()
        }
    }

    fun addGroupItem(menuItemStrRes: Int, index: Int) {
        if (!contains(menuItemStrRes)) {
            _hamburgerMenuList.add(
                index, HamburgerMenu(HamburgerMenuItem(menuItemStrRes), emptyList())
            )
            notifyDataSetChanged()
        }
    }

    fun contains(menuItemStrRes: Int) =
        _hamburgerMenuList.contains(HamburgerMenu(HamburgerMenuItem(menuItemStrRes), ArrayList()))

    fun showInspectionInHamburgerMenu(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            formDataStoreManager.getValue(CAN_SHOW_EDVIR_IN_HAMBURGER_MENU, false)
                .also { canShowInspectionOption ->
                    if (canShowInspectionOption) {
                        if (_hamburgerMenuList.contains(inspection).not()) {
                            _hamburgerMenuList.add(inspection)
                            notifyDataSetChanged()
                        }
                    } else {
                        if (_hamburgerMenuList.contains(inspection)) {
                            _hamburgerMenuList.remove(inspection)
                            notifyDataSetChanged()
                        }
                    }
                }
        }
    }

    fun showHotKeysHamburgerMenu(isHotKeysAvailable : Boolean) {
        if (isHotKeysAvailable) {
            addGroupItem(R.string.menu_hot_keys, HOT_KEYS_MENU_INDEX)
        } else {
            removeGroupItem(R.string.menu_hot_keys)
        }
    }

}