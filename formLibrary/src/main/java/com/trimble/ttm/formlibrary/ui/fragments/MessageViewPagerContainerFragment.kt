package com.trimble.ttm.formlibrary.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.MessageFragmentStateAdapter
import com.trimble.ttm.formlibrary.databinding.FragmentMessageViewPagerContainerBinding
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.DRAFT_INDEX
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.SENT_INDEX
import com.trimble.ttm.formlibrary.utils.TRASH_INDEX
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class MessageViewPagerContainerFragment : Fragment() {

    private var fragmentMessageViewPagerContainerBinding: FragmentMessageViewPagerContainerBinding? =
        null
    private val formManager: FormManager by lazy {
        FormManager()
    }
    private val messagingViewModel: MessagingViewModel by sharedViewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        activity?.findViewById<ComposeView>(R.id.toolbar)?.visibility = View.VISIBLE
        fragmentMessageViewPagerContainerBinding =
            FragmentMessageViewPagerContainerBinding.inflate(inflater, container, false)
        return fragmentMessageViewPagerContainerBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //disables swipe in viewpager
        fragmentMessageViewPagerContainerBinding?.run {
            messageViewPager.isUserInputEnabled = false

            messageViewPager.adapter =
                MessageFragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)

            TabLayoutMediator(messageTabLayout, messageViewPager) { tab, position ->
                when (position) {
                    INBOX_INDEX -> tab.text = getString(R.string.menu_inbox)
                    SENT_INDEX -> tab.text = getString(R.string.menu_sent)
                    DRAFT_INDEX -> tab.text = getString(R.string.menu_draft)
                    TRASH_INDEX -> tab.text = getString(R.string.menu_trash)
                }
            }.attach()

            setTabEnable(false)
            messagingViewModel.enabledTabs.observe(viewLifecycleOwner) { isEnabled ->
                if (isEnabled) setTabEnable(isEnabled)
            }

            (activity as? MessagingActivity)?.let { messagingActivity ->
                messageViewPager.currentItem = messagingActivity.viewPagerTabPositionToBeShownAfterScreenOpen
            }
            messagingViewModel.tabPosition.observe(viewLifecycleOwner) {
                (activity as? MessagingActivity)?.run {
                    messageViewPager.currentItem = viewPagerTabPositionToBeShownAfterScreenOpen
                }
            }
        }

    }

    private fun setTabEnable(isEnabled: Boolean) {
        for (index in 0..(fragmentMessageViewPagerContainerBinding?.messageTabLayout?.tabCount ?: 0)) {
            fragmentMessageViewPagerContainerBinding?.messageTabLayout?.getTabAt(index)?.view?.isEnabled =
                isEnabled
        }
    }

    override fun onDestroyView() {
        fragmentMessageViewPagerContainerBinding?.messageViewPager?.adapter = null
        fragmentMessageViewPagerContainerBinding = null
        formManager.removeKeyForImage()
        super.onDestroyView()
    }
}