package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.ui.BaseToolbarInteractionActivity
import com.trimble.ttm.commons.utils.ext.isNotNull
import com.trimble.ttm.formlibrary.adapter.HamburgerMenuAdapter
import com.trimble.ttm.formlibrary.model.HamburgerMenuItem
import com.trimble.ttm.formlibrary.ui.activities.EDVIRInspectionsActivity
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.IS_FOR_NEW_TRIP
import com.trimble.ttm.formlibrary.utils.MESSAGES_MENU_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.NOTIFICATION_DISPATCH_DATA
import com.trimble.ttm.formlibrary.utils.SCREEN
import com.trimble.ttm.formlibrary.utils.Screen
import com.trimble.ttm.formlibrary.utils.Utils.goToFormLibraryActivity
import com.trimble.ttm.formlibrary.utils.getHamburgerMenu
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.adapter.TripSelectViewPagerAdapter
import com.trimble.ttm.routemanifest.application.WorkflowApplication.Companion.isDispatchListActivityVisible
import com.trimble.ttm.routemanifest.application.WorkflowApplication.Companion.setDispatchListActivityPaused
import com.trimble.ttm.routemanifest.application.WorkflowApplication.Companion.setDispatchListActivityResumed
import com.trimble.ttm.routemanifest.databinding.ActivityDispatchListBinding
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.utils.TRIP_SELECT_INDEX
import com.trimble.ttm.routemanifest.viewmodel.DispatchListViewModel
import com.trimble.ttm.toolbar.ui.IconType
import com.trimble.ttm.toolbar.ui.OnFragmentInteractionListener
import com.trimble.ttm.toolbar.ui.compose.InstinctAppBar
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class DispatchListActivity : BaseToolbarInteractionActivity(), OnFragmentInteractionListener {
    private val tag = "DispatchListActivity"
    private val viewModel: DispatchListViewModel by viewModel()
    private var pagerAdapter: TripSelectViewPagerAdapter? = null
    private lateinit var hamburgerMenuAdapter: HamburgerMenuAdapter
    private lateinit var activityDispatchListBinding: ActivityDispatchListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        activityDispatchListBinding = ActivityDispatchListBinding.inflate(layoutInflater)
        setContentView(activityDispatchListBinding.root)
        activityDispatchListBinding.toolbar.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                InstinctAppBar(toolbarViewModel)
            }
        }
        removeSelectedDispatchIdFromLocalCache()
        intent.extras?.let {
            if (it.getBoolean(IS_FOR_NEW_TRIP)) {
                viewModel.processDispatchNotificationData(
                    dispatchData = getDispatch(),
                    intentAction = intent.action
                )
            } else {
                Log.w(tag, "No data found in intent extras : $it")
            }
        }
        setupViewpager()
        setupHamburgerMenu()
        updateHotKeysMenu()
        toolbarViewModel.title.postValue(getString(R.string.app_name).uppercase())
        toolbarViewModel.iconType.postValue(IconType.HAMBURGER)
        hamburgerMenuAdapter.showInspectionInHamburgerMenu(lifecycleScope)
        viewModel.performStateAndUiUpdateUponCreatedLifecycle(
            updateStopMenuItemVisibility = { hasActiveDispatch ->
                lifecycleScope.launch {
                    showOrHideStopItemMenu(hasActiveDispatch)
                }
            },
            isActiveDispatchAndHasOnlyOneDispatch = { isActiveDispatchAndHasOnlyOneDispatch ->
                navigateToStopDetailScreenIfSingleDispatch(isActiveDispatchAndHasOnlyOneDispatch, caller = "isActiveDispatchAndHasOnlyOneDispatch")
            }
        )
        lifecycleScope.launch {
            viewModel.hasAnActiveDispatch().firstOrNull()?.let { hasActiveDispatch ->
                showOrHideStopItemMenu(hasActiveDispatch)
            }
        }
    }

    private fun removeSelectedDispatchIdFromLocalCache() = viewModel.removeSelectedDispatchIdFromLocalCache()

    override fun onResume() {
        super.onResume()
        setDispatchListActivityResumed()
        onBackPressedDispatcher.addCallback(this, onBackButtonPressCallback)
    }

    override fun onPause() {
        super.onPause()
        setDispatchListActivityPaused()
        onBackButtonPressCallback.remove()
    }

    private fun navigateToStopDetailScreenIfSingleDispatch(isActiveDispatchAndHasOnlyOneDispatch: Boolean,caller: String) {
        if (isActiveDispatchAndHasOnlyOneDispatch &&  isDispatchListActivityVisible()) goToDispatchDetail()
        else setupViewpager()
    }

    private fun updateHotKeysMenu() {
        viewModel.isHotKeysAvailable.observe(this) {
            hamburgerMenuAdapter.showHotKeysHamburgerMenu(it)
        }
        viewModel.canShowHotKeysMenu()
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

    private fun setupHamburgerMenu() {
        activityDispatchListBinding.hamburgerMenuLayout.tvHeaderTitle.setText(R.string.app_name)
        activityDispatchListBinding.hamburgerMenuLayout.hamburgerMenuList.let { hamburgerMenuList ->
            hamburgerMenuAdapter = HamburgerMenuAdapter(this)
            hamburgerMenuAdapter.hamburgerMenuList = getHamburgerMenu()
            hamburgerMenuList.setOnGroupClickListener { _, _, groupPosition, _ ->
                (hamburgerMenuAdapter.getGroup(groupPosition) as? HamburgerMenuItem)?.let { group ->
                    Log.logUiInteractionInInfoLevel(
                        tag,
                        "$tag ${this.resources.getString(group.menuItemStringRes)}} menu item clicked from hamburger menu"
                    )
                    when (group.menuItemStringRes) {
                        R.string.menu_messaging -> {
                            Intent(this, MessagingActivity::class.java).apply {
                                putExtra(MESSAGES_MENU_TAB_INDEX, INBOX_INDEX)
                                putExtra(SCREEN, Screen.DISPATCH_LIST.ordinal)
                            }.also { startActivity(it) }
                        }

                        R.string.menu_form_library, R.string.menu_hot_keys -> goToFormLibraryActivity(context = this, selectedMenuGroupIndex = group.menuItemStringRes, hotKeysMenuGroupIndex = R.string.menu_hot_keys)

                        R.string.menu_inspections -> Intent(
                            this, EDVIRInspectionsActivity::class.java
                        ).also {
                            startActivity(it)
                        }

                        R.string.menu_stop_list -> goToDispatchDetail()
                        else -> {
                            Log.e(tag, "Unknown menu item clicked. ${group.menuItemStringRes}")
                        }
                    }
                }
                activityDispatchListBinding.drawerLayout.closeDrawer(GravityCompat.START)
                false
            }
            hamburgerMenuList.setAdapter(hamburgerMenuAdapter)
        }
    }

    private fun showOrHideStopItemMenu(hasActiveDispatch: Boolean) {
        if (hasActiveDispatch) hamburgerMenuAdapter.addGroupItem(R.string.menu_stop_list, 2)
        else hamburgerMenuAdapter.removeGroupItem(R.string.menu_stop_list)
    }

    override fun onToolbarNavigationIconClicked(iconType: IconType) {
        with(activityDispatchListBinding) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                Log.logUiInteractionInInfoLevel(tag, "$tag Hamburger icon closed")
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                Log.logUiInteractionInInfoLevel(tag, "$tag Hamburger icon opened")
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private val onBackButtonPressCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Log.logUiInteractionInInfoLevel(tag, "$tag onBackPressed")
            when {
                activityDispatchListBinding.drawerLayout.isDrawerOpen(GravityCompat.START) -> activityDispatchListBinding.drawerLayout.closeDrawer(
                    GravityCompat.START
                )
                else -> finish()
            }
        }
    }

    override suspend fun onAllPermissionsGranted() {
        viewModel.doAfterAllPermissionsGranted()
    }

    private fun setupViewpager() {
        if (pagerAdapter.isNotNull()) return
        pagerAdapter = TripSelectViewPagerAdapter(
            this.supportFragmentManager, listOf(getString(R.string.tripSelect)), this.lifecycle
        )
        with(activityDispatchListBinding) {
            viewPager.adapter = pagerAdapter
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                when (position) {
                    TRIP_SELECT_INDEX -> tab.text = getString(R.string.tripSelect)
                }
            }.attach()
            viewPager.currentItem = TRIP_SELECT_INDEX
        }
    }

    private fun goToDispatchDetail() {
        viewModel.restoreSelectedDispatch {
            startActivity(Intent(this, DispatchDetailActivity::class.java))
        }
    }

    private fun getDispatch(): Dispatch? {
        val dispatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NOTIFICATION_DISPATCH_DATA, Dispatch::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NOTIFICATION_DISPATCH_DATA)
        }
        return dispatch
    }
}
