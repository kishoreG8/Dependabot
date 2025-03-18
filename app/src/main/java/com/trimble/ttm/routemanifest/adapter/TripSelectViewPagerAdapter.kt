package com.trimble.ttm.routemanifest.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trimble.ttm.routemanifest.ui.fragments.TripListFragment
import com.trimble.ttm.routemanifest.utils.TRIP_SELECT_INDEX

class TripSelectViewPagerAdapter(
    fragmentManager: FragmentManager,
    private val tabsDescriptions: List<String>,
    lifecycle: Lifecycle
) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = tabsDescriptions.size

    override fun createFragment(position: Int) = when (position) {
        TRIP_SELECT_INDEX -> TripListFragment()
        else -> Fragment()
    }
}
