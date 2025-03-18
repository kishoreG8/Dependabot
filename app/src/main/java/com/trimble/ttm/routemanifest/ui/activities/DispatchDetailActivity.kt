package com.trimble.ttm.routemanifest.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_PREVIEWING
import com.trimble.ttm.commons.utils.ext.isNotNull
import com.trimble.ttm.formlibrary.adapter.HamburgerMenuAdapter
import com.trimble.ttm.formlibrary.model.HamburgerMenuItem
import com.trimble.ttm.formlibrary.ui.activities.EDVIRInspectionsActivity
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.MESSAGES_MENU_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.SCREEN
import com.trimble.ttm.formlibrary.utils.Screen
import com.trimble.ttm.formlibrary.utils.Utils.goToFormLibraryActivity
import com.trimble.ttm.formlibrary.utils.getHamburgerMenu
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.databinding.ActivityDispatchDetailBinding
import com.trimble.ttm.routemanifest.model.DispatchActiveState
import com.trimble.ttm.routemanifest.service.RouteManifestForegroundService
import com.trimble.ttm.routemanifest.ui.fragments.HomeFragment
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_TO_RENDER
import com.trimble.ttm.routemanifest.utils.INCOMING_ARRIVED_TRIGGER
import com.trimble.ttm.routemanifest.utils.TRIP_START_EVENT_REASON_TYPE
import com.trimble.ttm.routemanifest.utils.checkAppUpdate
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import com.trimble.ttm.toolbar.ui.IconType
import com.trimble.ttm.toolbar.ui.OnFragmentInteractionListener
import com.trimble.ttm.toolbar.ui.compose.InstinctAppBar
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import me.drakeet.support.toast.ToastCompat

private const val HOME_FRAGMENT_TAG = "HomeFragment"

const val DISPATCH_DETAIL_ACTIVITY = "DispatchDetailActivity"

class DispatchDetailActivity : DispatchBaseActivity(DISPATCH_DETAIL_ACTIVITY),
    OnFragmentInteractionListener {

    private val tag = "DispatchDetailActivity"
    private var endTripAlertDialog: AlertDialog? = null
    private lateinit var hamburgerMenuAdapter: HamburgerMenuAdapter
    private lateinit var activityDispatchDetailBinding:ActivityDispatchDetailBinding

    override fun onToolbarNavigationIconClicked(iconType: IconType) {
        with(activityDispatchDetailBinding) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                Log.logUiInteractionInInfoLevel(tag, "$tag Hamburger icon closed")
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                Log.logUiInteractionInInfoLevel(tag, "$tag Hamburger icon opened")
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        activityDispatchDetailBinding = ActivityDispatchDetailBinding.inflate(layoutInflater)
        setContentView(activityDispatchDetailBinding.root)
        activityDispatchDetailBinding.toolbar.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                InstinctAppBar(toolbarViewModel)
            }
        }
        initialize()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.logLifecycle(tag, "$tag onNewIntent")
        lifecycleScope.launch {
            val dispatchIdToRender = intent.getStringExtra(DISPATCH_ID_TO_RENDER) ?: EMPTY_STRING
            val currentRenderedDispatchId =
                dispatchDetailViewModel.getSelectedDispatchId("$tag-OnNewIntent()")
            Log.d(
                tag+TRIP_PREVIEWING,
                "dispatchIdToRender: $dispatchIdToRender, currentRenderedDispatchId: $currentRenderedDispatchId"
            )
            //We need to reload the dispatch data if we get a new DispatchId in onNewIntent()
            //We get into this during coming back to DispatchDetail from StopDetail if form is opened from draft and from trip panel DYA positive action click.
            dispatchDetailViewModel.reloadUIIfRequired(dispatchIdToRender = dispatchIdToRender, currentRenderedDispatchId = currentRenderedDispatchId){
                reInitializeData()
            }
        }
    }

    private fun initialize(isReloadData: Boolean = false){
        setupHamburgerMenu()
        updateHotKeysMenu()
        hamburgerMenuAdapter.showInspectionInHamburgerMenu(lifecycleScope)
        setHomeFragment(intent.getStringExtra(TRIP_START_EVENT_REASON_TYPE), isReloadData)
        setToolbarName()
        toolbarViewModel.iconType.postValue(IconType.HAMBURGER)
        startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
        registerObservers()
        checkAppUpdate(applicationContext, lifecycleScope)
        dispatchDetailViewModel.enableEndTripNavigationViewItem()
    }

    private fun updateHotKeysMenu() {
        dispatchDetailViewModel.isHotKeysAvailable.observe(this) {
            hamburgerMenuAdapter.showHotKeysHamburgerMenu(it)
        }
        dispatchDetailViewModel.canShowHotKeysMenu()
    }

    private fun reInitializeData(){
        dispatchDetailViewModel.restoreSelectedDispatchIdOnReInitialize {
            initialize(isReloadData = true)
        }
    }

    private fun setToolbarName() {
        lifecycleScope.launch(CoroutineName(DISPATCH_DETAIL_ACTIVITY) + dispatchDetailViewModel.coroutineDispatcher.main()) {
            dispatchDetailViewModel.getSelectedDispatchName { toolbarViewModel.title.postValue(it) }
        }
    }

    private fun registerObservers() {
        with(dispatchDetailViewModel) {
            listenForStopAdditionAndRemoval()
            // Observe for error in route calculation and display toast to the user
            dispatchDetailError.observe(this@DispatchDetailActivity) {
                ToastCompat.makeText(this@DispatchDetailActivity, getString(R.string.err_route_calculation), Toast.LENGTH_SHORT).show()
            }
            isEndTripEnabled.observe(this@DispatchDetailActivity) {
                setEndTripHamburgerMenuItemVisibility(
                    canVisible = it,
                    addIndex = hamburgerMenuAdapter.groupCount // Add End Trip menu as last item
                )
            }
        }
    }

    private fun setEndTripHamburgerMenuItemVisibility(
        canVisible: Boolean,
        addIndex: Int = -1
    ) {
        val endTripMenuItemStrRes = R.string.menu_end_trip
        if (canVisible && dispatchDetailViewModel.dispatchActiveStateFlow.value== DispatchActiveState.ACTIVE) {
            if (addIndex > -1 && hamburgerMenuAdapter.contains(endTripMenuItemStrRes).not())
                hamburgerMenuAdapter.addGroupItem(endTripMenuItemStrRes, addIndex)
        } else {
            hamburgerMenuAdapter.removeGroupItem(endTripMenuItemStrRes)
        }
    }

    private fun setupHamburgerMenu() {
        activityDispatchDetailBinding.hamburgerMenuLayout.tvHeaderTitle.setText(R.string.app_name)
        activityDispatchDetailBinding.hamburgerMenuLayout.hamburgerMenuList.let { hamburgerMenuList ->
            hamburgerMenuAdapter = HamburgerMenuAdapter(this)
            hamburgerMenuAdapter.hamburgerMenuList = getHamburgerMenu()
            hamburgerMenuList.setOnGroupClickListener { _, _, groupPosition, _ ->
                (hamburgerMenuAdapter.getGroup(groupPosition) as? HamburgerMenuItem)?.let { hamburgerMenuGroupItem ->
                    Log.logUiInteractionInInfoLevel(
                        tag, "$tag ${ this.resources.getString(hamburgerMenuGroupItem.menuItemStringRes) } menu item clicked from hamburger menu"
                    )
                    when (hamburgerMenuGroupItem.menuItemStringRes) {
                        R.string.menu_messaging -> {
                            Intent(this, MessagingActivity::class.java).apply {
                                putExtra(MESSAGES_MENU_TAB_INDEX, INBOX_INDEX)
                                putExtra(SCREEN, Screen.DISPATCH_DETAIL.ordinal)
                                startActivity(this)
                                finish()
                            }
                        }
                        R.string.menu_form_library, R.string.menu_hot_keys -> goToFormLibraryActivity(context = this, selectedMenuGroupIndex = hamburgerMenuGroupItem.menuItemStringRes, hotKeysMenuGroupIndex = R.string.menu_hot_keys)
                        R.string.menu_inspections -> startActivity(Intent(this, EDVIRInspectionsActivity::class.java))
                        R.string.menu_end_trip -> showEndTripDialog()
                        R.string.menu_trip_list -> finish()
                        else -> Log.w(tag, "invalid hamburger group item. HamburgerMenuGroupItem: $hamburgerMenuGroupItem")
                    }
                } ?: Log.w(tag, "invalid hamburger group item clicked. GroupPosition: $groupPosition")
                activityDispatchDetailBinding.drawerLayout.closeDrawer(GravityCompat.START)
                false
            }
            hamburgerMenuList.setAdapter(hamburgerMenuAdapter)
        }
        updateDropDownMenu()
    }

    private fun updateDropDownMenu() {
        checkIfHasOnlyOneDispatchAndIsActive { hasAOnlyOneDispatchAndIsActive ->
            if (hasAOnlyOneDispatchAndIsActive) hamburgerMenuAdapter.removeGroupItem(R.string.menu_trip_list)
            else hamburgerMenuAdapter.addGroupItem(R.string.menu_trip_list, hamburgerMenuAdapter.groupCount)
        }
    }

    private fun showEndTripDialog() {
        endTripAlertDialog = AlertDialog.Builder(this, R.style.formDialogTheme)
            .setTitle(getString(R.string.alert))
            .setMessage(getString(R.string.end_trip_dialog_message))
            .setCancelable(false)
            .setNegativeButton(R.string.no) { dialog, _ ->
                Log.logUiInteractionInNoticeLevel(tag, "$tag No button clicked in end trip dialog")
                dialog.dismiss()
            }
            .setPositiveButton(R.string.yes) { _, _ ->
                Log.logUiInteractionInNoticeLevel(tag, "$tag Yes button clicked in end trip dialog")
                dispatchDetailViewModel.processEndTrip()
            }.create()
        endTripAlertDialog?.show()
    }

    override fun onStart() {
        super.onStart()
        dispatchDetailViewModel.setMenuDataOptionsListener { updateDropDownMenu() }
    }

    private fun checkIfHasOnlyOneDispatchAndIsActive(
        hasOnlyOneActiveDispatch : (Boolean) -> Unit
    ) {
        dispatchDetailViewModel.checkIfHasOnlyOneDispatchAndIsActive {
            hasOnlyOneActiveDispatch(it)
        }
    }

    private val onBackButtonPressCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Log.logUiInteractionInInfoLevel(tag, "$tag onBackPressed")
            checkIfHasOnlyOneDispatchAndIsActive { hasAOnlyOneDispatchAndIsActive ->
                when {
                    activityDispatchDetailBinding.drawerLayout.isDrawerOpen(GravityCompat.START) -> activityDispatchDetailBinding.drawerLayout.closeDrawer(
                        GravityCompat.START
                    )
                    hasAOnlyOneDispatchAndIsActive -> finishAffinity()
                    else -> finish()
                }
            }
        }
    }

    private val arrivedEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == INCOMING_ARRIVED_TRIGGER) checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        onBackPressedDispatcher.addCallback(this, onBackButtonPressCallback)
        with(dispatchDetailViewModel) {
            resetIsDraftView()
            registerDataStoreListener()
        }
        WorkflowApplication.setDispatchActivityResumed()
        LocalBroadcastManager.getInstance(this@DispatchDetailActivity).registerReceiver(
            arrivedEventReceiver,
            IntentFilter(INCOMING_ARRIVED_TRIGGER)
        )
    }

    override fun onPause() {
        super.onPause()
        Log.logLifecycle(tag, "$tag onPause")
        dispatchDetailViewModel.unRegisterDataStoreListener()
        WorkflowApplication.setDispatchActivityPaused()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(arrivedEventReceiver)
        if (endTripAlertDialog != null) {
            endTripAlertDialog?.dismiss()
            endTripAlertDialog = null
        }
        onBackButtonPressCallback.remove()
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
    }

    private fun setHomeFragment(tripStartReasonType: String?, isReloadData: Boolean = false) {
        val fragment = supportFragmentManager.findFragmentByTag(HOME_FRAGMENT_TAG)
        if (isReloadData.not() && fragment.isNotNull()) return
        val bundle = Bundle()
        bundle.putString(TRIP_START_EVENT_REASON_TYPE, tripStartReasonType)
        val homeFragment = HomeFragment()
        homeFragment.arguments = bundle
        supportFragmentManager.beginTransaction().replace(R.id.homeFragmentContainer, homeFragment, HOME_FRAGMENT_TAG).commit()
    }

    override fun onDestroy() {
        Log.logLifecycle(tag, "$tag onDestroy")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(arrivedEventReceiver)
        dispatchDetailViewModel.handleMapClearForInActiveDispatchTripPreview()
        super.onDestroy()
    }

}