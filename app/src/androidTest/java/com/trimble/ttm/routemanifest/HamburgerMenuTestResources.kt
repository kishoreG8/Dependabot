package com.trimble.ttm.routemanifest

import com.trimble.ttm.formlibrary.R

class HamburgerMenuTestResources : MockableResource {
    override fun getString(id: Int): String {
        return when (id) {
            R.string.menu_messaging -> "Messaging"
            R.string.menu_inbox -> "Inbox"
            R.string.menu_trash -> "Trash"
            R.string.menu_form_library -> "Forms"
            R.string.menu_inspections -> "Inspections"
            R.string.menu_end_trip -> "End Trip"
            R.string.menu_stop_list -> "Stop List"
            R.string.menu_trip_list -> "Trip List"
            else -> throw Exception("No mock found for the given resource id")
        }
    }
}