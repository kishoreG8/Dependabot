package com.trimble.ttm.formlibrary.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.SPACE
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.InspectionsListAdapter
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.ActivityEdvirInspectionsBinding
import com.trimble.ttm.formlibrary.databinding.CustomActionBarCenterAlignBinding
import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.utils.DRIVER_ACTION
import com.trimble.ttm.formlibrary.utils.DRIVER_ID
import com.trimble.ttm.formlibrary.utils.DRIVER_NAME
import com.trimble.ttm.formlibrary.utils.INSPECTION_CREATED_AT_KEY
import com.trimble.ttm.formlibrary.utils.INSPECTION_FORM_CLASS_KEY
import com.trimble.ttm.formlibrary.utils.INSPECTION_FORM_ID_KEY
import com.trimble.ttm.formlibrary.utils.IS_INSPECTION_FORM_VIEW_ONLY_KEY
import com.trimble.ttm.formlibrary.utils.SOURCE_ACTIVITY_KEY
import com.trimble.ttm.formlibrary.utils.VEHICLE_DSN
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.viewmodel.EDVIRFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.EDVIRInspectionsViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EDVIRInspectionsActivity : AppCompatActivity(), KoinComponent {

    private val tag = "EDVIRInspectionsActivity"
    private val inspectionViewModel: EDVIRInspectionsViewModel by viewModel()
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val eDVIRFormViewModel : EDVIRFormViewModel by viewModel()
    private lateinit var inspectionsListAdapter: InspectionsListAdapter
    private var vehicleDSN = ""
    private var multipleInvocationLock: Boolean = false
    private var internetAlertDialog: AlertDialog? = null
    private lateinit var activityEdvirInspectionsBinding: ActivityEdvirInspectionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        activityEdvirInspectionsBinding = ActivityEdvirInspectionsBinding.inflate(layoutInflater)
        setContentView(activityEdvirInspectionsBinding.root)
        lifecycleScope.safeLaunch(CoroutineName("$tag On create")) {
            vehicleDSN = appModuleCommunicator.doGetObcId()
            activityEdvirInspectionsBinding.progressErrViewInsp.setProgressState(getString(R.string.loading_text))
            setupToolbar()
            setupInspectionsList()
            inspectionViewModel.errorData.observe(this@EDVIRInspectionsActivity) {
                if (inspectionViewModel.inspectionsList.value.isNullOrEmpty() && !multipleInvocationLock)
                    activityEdvirInspectionsBinding.progressErrViewInsp.setErrorState(getString(R.string.no_internet_in_inspections_history))
                else
                    activityEdvirInspectionsBinding.progressErrViewInsp.setErrorState(it)
            }
            inspectionViewModel.errorDataForToast.observe(this@EDVIRInspectionsActivity) {
                if (it.isEmpty().not()) showToast(it)
            }
            activityEdvirInspectionsBinding.inspectionMenuView.visibility = View.GONE
            inspectionViewModel.canShowInspectionMenu.observe(this@EDVIRInspectionsActivity) { canShow ->
                if (canShow) {
                    activityEdvirInspectionsBinding.inspectionMenuView.visibility = View.VISIBLE
                    activityEdvirInspectionsBinding.inspectionMenuView.setMenuItemSelectedListener { inspectionType ->
                        startEDVIRFormActivity(inspectionType.name)
                    }
                } else {
                    activityEdvirInspectionsBinding.inspectionMenuView.visibility = View.GONE
                }
            }
            inspectionViewModel.inspectionsList.observe(this@EDVIRInspectionsActivity) { inspectionList ->
                if (inspectionList.isNotEmpty()) {
                    inspectionsListAdapter.inspectionsList = listOf()
                    inspectionsListAdapter.inspectionsList = inspectionList
                    inspectionsListAdapter.notifyDataSetChanged()
                }
                activityEdvirInspectionsBinding.progressErrViewInsp.setNoState()
            }
            retrieveCurrentUserInfo()
            inspectionViewModel.getInspectionsHistory()
            lifecycleScope.safeLaunch(CoroutineName("$tag on create")) {
                observeForNetworkConnectivityChange()
            }
            eDVIRFormViewModel.syncEdvirChanges()
        }
    }

    private suspend fun observeForNetworkConnectivityChange() {
        inspectionViewModel.listenToNetworkConnectivityChange()
            .collectLatest { isAvailable ->
                if (isAvailable) {
                    internetAlertDialog?.dismiss()
                    internetAlertDialog = null
                    if (multipleInvocationLock.not()) {
                        lifecycleScope.safeLaunch(
                            CoroutineName("$tag observe network connectivity change")
                        ) {
                            multipleInvocationLock = true
                        }
                    }
                } else {
                    multipleInvocationLock = false
                }
            }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
        if (activityEdvirInspectionsBinding.inspectionMenuView.isMenuOpened)
            activityEdvirInspectionsBinding.inspectionMenuView.closeMenu()
    }

    private fun setupInspectionsList() {
        inspectionsListAdapter = InspectionsListAdapter {
            onInspectionClicked(it)
        }
        val itemDecorator = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
            .also { itemDecoration ->
                ContextCompat.getDrawable(applicationContext, R.drawable.divider)?.let { drawable ->
                    itemDecoration.setDrawable(drawable)
                }
            }

        with(activityEdvirInspectionsBinding) {
            inspectionsRecylerView.addItemDecoration(itemDecorator)
            inspectionsRecylerView.layoutManager = LinearLayoutManager(applicationContext)
            inspectionsRecylerView.adapter = inspectionsListAdapter
        }

    }

    private fun onInspectionClicked(eDVIRInspection: EDVIRInspection) {
        Log.logUiInteractionInInfoLevel(tag, "$tag inspection form selected from the inspection list. Inspection form id: ${eDVIRInspection.formId}")
        Intent(this, ManualInspectionFormActivity::class.java).apply {
            putExtras(Bundle().apply {
                putString(INSPECTION_FORM_ID_KEY, eDVIRInspection.formId.toString())
                putString(INSPECTION_FORM_CLASS_KEY, eDVIRInspection.formClass.toString())
                putString(INSPECTION_CREATED_AT_KEY, eDVIRInspection.createdAt)
                putBoolean(IS_INSPECTION_FORM_VIEW_ONLY_KEY, true)
                putString(DRIVER_ACTION, eDVIRInspection.inspectionType)
                putString(SOURCE_ACTIVITY_KEY, EDVIRInspectionsActivity::class.java.simpleName)
            })
        }.also {
            startActivity(it)
        }
    }

    private fun setupToolbar() {
        supportActionBar?.let {
            it.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            it.setCustomView(R.layout.custom_action_bar_center_align)
            with(CustomActionBarCenterAlignBinding.bind(it.customView)) {
                toolbarTitle.setText(R.string.title_inspections)
            }
            it.setDisplayHomeAsUpEnabled(true)
        }
    }

    private suspend fun retrieveCurrentUserInfo() {
        inspectionViewModel.retrieveCurrentUserInfo()
    }

    override fun onRestart() {
        super.onRestart()
        Log.logLifecycle(tag, "$tag onRestart")
        lifecycleScope.safeLaunch(CoroutineName("$tag ON restart")) {
            retrieveCurrentUserInfo()
        }
    }

    private fun startEDVIRFormActivity(driverAction: String) {
        Log.logUiInteractionInNoticeLevel(tag, "$tag inspection form selected from the fab. Driver action: $driverAction")
        if (inspectionViewModel.isCurrentUserInitialised() && inspectionViewModel.currentUser.userId.isNotEmpty()) {
            startActivity(Intent(this, ManualInspectionFormActivity::class.java).apply {
                putExtra(DRIVER_ID, inspectionViewModel.currentUser.userId)
                putExtra(DRIVER_NAME, inspectionViewModel.currentUser.firstName.plus(SPACE).plus(inspectionViewModel.currentUser.lastName))
                putExtra(VEHICLE_DSN, vehicleDSN)
                putExtra(DRIVER_ACTION, driverAction)
                putExtra(SOURCE_ACTIVITY_KEY, EDVIRInspectionsActivity::class.java.simpleName)
            })
        } else showToast(R.string.err_current_driver_not_available)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.logUiInteractionInNoticeLevel(tag, "$tag soft back button pressed")
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (activityEdvirInspectionsBinding.inspectionMenuView.isMenuOpened)
            activityEdvirInspectionsBinding.inspectionMenuView.closeMenu()
        else
            super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

}