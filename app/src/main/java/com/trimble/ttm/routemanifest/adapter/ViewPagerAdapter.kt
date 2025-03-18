package com.trimble.ttm.routemanifest.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trimble.ttm.routemanifest.ui.fragments.ListFragment
import com.trimble.ttm.routemanifest.ui.fragments.TimelineFragment
import com.trimble.ttm.routemanifest.utils.STOP_LIST_INDEX
import com.trimble.ttm.routemanifest.utils.TIMELINE_INDEX

class ViewPagerAdapter(
    fragmentManager: FragmentManager,
    private val tabsDescriptions: List<String>,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount() = tabsDescriptions.size

    override fun createFragment(position: Int) = when (position) {
        STOP_LIST_INDEX -> ListFragment()
        TIMELINE_INDEX -> TimelineFragment()
        else -> Fragment()
    }
}