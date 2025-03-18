package com.trimble.ttm.formlibrary.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trimble.ttm.formlibrary.ui.fragments.DraftFragment
import com.trimble.ttm.formlibrary.ui.fragments.InboxFragment
import com.trimble.ttm.formlibrary.ui.fragments.SentFragment
import com.trimble.ttm.formlibrary.ui.fragments.TrashFragment
import com.trimble.ttm.formlibrary.utils.DRAFT_INDEX
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.SENT_INDEX
import com.trimble.ttm.formlibrary.utils.TRASH_INDEX

class MessageFragmentStateAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            INBOX_INDEX -> InboxFragment()
            SENT_INDEX  -> SentFragment()
            DRAFT_INDEX -> DraftFragment()
            TRASH_INDEX -> TrashFragment()
            else -> InboxFragment()
        }
    }
}