package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.ARRIVAL_PROMPT
import com.trimble.ttm.commons.logger.DISPATCH_LIFECYCLE
import com.trimble.ttm.commons.logger.KEY
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.ui.BaseToolbarInteractionActivity
import com.trimble.ttm.commons.utils.DISPATCHID
import com.trimble.ttm.commons.utils.STOPID
import com.trimble.ttm.routemanifest.model.ArrivalActionStatus
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.routemanifest.model.ArrivalType
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.utils.TOAST_DEBOUNCE_TIME
import com.trimble.ttm.routemanifest.utils.ext.startDispatchFormActivity
import com.trimble.ttm.routemanifest.viewmodel.DispatchBaseViewModel
import com.trimble.ttm.routemanifest.viewmodel.DispatchDetailViewModel
import com.trimble.ttm.routemanifest.viewmodel.StopDetailViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.androidx.viewmodel.ext.android.viewModel

open class DispatchBaseActivity(
    private val childActivity: String
) : BaseToolbarInteractionActivity() {

    private val tag = "DispatchBaseActivity"
    internal val dispatchDetailViewModel: DispatchDetailViewModel by viewModel()
    protected val stopDetailViewModel: StopDetailViewModel by viewModel()
    private lateinit var baseViewModel: DispatchBaseViewModel
    private var didYouArriveAlertDialog: AlertDialog? = null
    private var tripCompleteToastDebouncerJob: Job? = null
    private var showDidYouArriveAlertJob : Job? = null

    override fun onPause() {
        WorkflowApplication.setDispatchActivityPaused()
        super.onPause()
        if(showDidYouArriveAlertJob?.isActive == true) {
            showDidYouArriveAlertJob?.cancel()
        }
        baseViewModel.cancelDidYouArriveAlertTimerCollectJob()
        didYouArriveAlertDialog?.let {
            dismissDidYouArriveDialog()
            baseViewModel.updateTripPanelMessagePriority()
        }
        baseViewModel.updateTripPanelMessagePriorityIfThereIsNoMoreArrivalTrigger()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "DispatchBaseActivity oncreate called.")
        viewModelInitialisation()
        baseViewModel.onStopCompleted.observe(this) {
            checkForTripCompletion()
        }
        baseViewModel.onTripEnd.observe(this) {
            startActivity(Intent(this, TransitionScreenActivity::class.java))
            finishAffinity()
        }
    }

    private fun checkAndDisplayDidYouArriveIfTriggerEventAvailable() {
        baseViewModel.checkAndDisplayDidYouArriveIfTriggerEventAvailable(
            isDidYouArriveDialogNotNull = didYouArriveAlertDialog.isNotNull(),
            showDidYouArriveDialog = { stopData, activeDispatchId, arriveTriggerData ->
                if (didYouArriveAlertDialog != null) dismissDidYouArriveDialog()
                this@DispatchBaseActivity.runOnUiThread {
                    renderDidYouArriveDialog(stopData, activeDispatchId, arriveTriggerData)
                }
            }
        )
    }

    private fun viewModelInitialisation() {
        if (childActivity == DISPATCH_DETAIL_ACTIVITY) {
            baseViewModel = dispatchDetailViewModel
            Log.i(tag, "DispatchDetailViewModel initialised")
        } else if (childActivity == STOP_DETAIL_ACTIVITY) {
            baseViewModel = stopDetailViewModel
            Log.i(tag, "StopDetailViewModel initialised")
        }
    }

    internal fun checkForTripCompletion() {
        baseViewModel.checkForTripCompletion {
            lifecycleScope.launch {
                withContext(NonCancellable + baseViewModel.coroutineDispatcher.main()) {
                    tripCompleteToastDebouncerJob?.cancel()
                    tripCompleteToastDebouncerJob = launch {
                        delay(TOAST_DEBOUNCE_TIME)
                        ToastCompat.makeText(
                            this@DispatchBaseActivity,
                            R.string.trip_completed_toast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch() {
        if (baseViewModel.isNavigatingToFormActivity.not()) checkAndDisplayDidYouArriveIfTriggerEventAvailable()
    }

    override fun onResume() {
        super.onResume()
        checkTheDispatchIsComplete()
        showDidYouArriveAlertJob?.cancel()
        showDidYouArriveAlertJob = lifecycleScope.launch(CoroutineName(tag)) {
            WorkflowApplication.setDispatchActivityResumed()
            checkAndDisplayDidYouArriveIfTriggerEventAvailable()
            baseViewModel.restoreFormsWhenDraftFunctionalityIsTurnedOff(
                isDidYouArriveDialogNull = didYouArriveAlertDialog.isNull(),
                startDispatchFormActivity = { isComposeEnabled, path, dispatchFormPath, isManualArrival, isFormResponseSentToServer ->
                    this@DispatchBaseActivity.startDispatchFormActivity(
                        isComposeEnabled = isComposeEnabled,
                        path = path,
                        dispatchFormPath = dispatchFormPath,
                        isManualArrival = isManualArrival,
                        isFormResponseSentToServer = isFormResponseSentToServer
                    )
                }
            )
            checkForTripCompletion()
            baseViewModel.listenToCopilotEvents()
        }
    }

    private fun checkTheDispatchIsComplete(){
        lifecycleScope.launch(CoroutineName("checkTheDispatchIsComplete") + baseViewModel.coroutineDispatcher.io()) {
            val isDispatchComplete = baseViewModel.checkDispatchIsCompleted()
            if (isDispatchComplete) {
                withContext(baseViewModel.coroutineDispatcher.main()){
                    startActivity(Intent(this@DispatchBaseActivity, TransitionScreenActivity::class.java))
                    finishAffinity()
                }
            }
        }
    }

    private fun renderDidYouArriveDialog(
        stopData: StopDetail?,
        activeDispatchId: String,
        arriveTriggerData: LauncherMessageWithPriority
    ) {
        if (this.isFinishing) {
            Log.d(ARRIVAL_PROMPT, "DispatchBaseActivity is finishing. So, not showing the DYA dialog")
            return
        }
        val arrivalReasonToUpdate = HashMap<String,Any>()
        didYouArriveAlertDialog = AlertDialog.Builder(this@DispatchBaseActivity, R.style.formDialogTheme)
            .setTitle(R.string.alert)
            .setMessage(
                String.format(
                    application.getString(R.string.did_you_arrive_at),
                    stopData?.name
                )
            )
            .setPositiveButton(
                application.getString(R.string.yes)
            ) { _, _ ->
                Log.logUiInteractionInNoticeLevel(ARRIVAL_PROMPT, "App DYA Yes button clicked",
                    throwable = null,
                    DISPATCHID to activeDispatchId,
                    STOPID to arriveTriggerData.messageID,
                    KEY to DISPATCH_LIFECYCLE
                )
                lifecycleScope.launch(baseViewModel.coroutineDispatcher.io()) {
                    if (stopData != null) {
                        baseViewModel.updateArrivalReasonForCurrentStop(stopData, ArrivalType.DRIVER_CLICKED_YES.toString(), true)
                    }
                }
                baseViewModel.doOnDidYouArrivePositiveButtonPress(
                    doOnArrival = { pfmEventsInfo ->
                        doOnArrival(arriveTriggerData, pfmEventsInfo, stopData)
                    }
                )
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                Log.logUiInteractionInNoticeLevel(ARRIVAL_PROMPT, "App DYA No button clicked",
                    throwable = null,
                    DISPATCHID to activeDispatchId,
                    STOPID to arriveTriggerData.messageID,
                    KEY to DISPATCH_LIFECYCLE
                )
                lifecycleScope.launch(baseViewModel.coroutineDispatcher.io()) {
                    if (stopData != null) {
                        baseViewModel.setIsDyaShownKey(false)
                        baseViewModel.updateArrivalReasonForCurrentStop(stopData, ArrivalActionStatus.DRIVER_CLICKED_NO.toString(), false)
                    }
                }
                baseViewModel.doOnDidYouArriveNegativeButtonPress(
                    arriveTriggerData = arriveTriggerData,
                    dismissDidYouArriveAlert = {
                        dialog.dismiss()
                        dismissDidYouArriveDialog()
                    },
                    showDidYouArrive = {
                        checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch()
                    }
                )
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .create()

        didYouArriveAlertDialog?.setOnShowListener { dialog ->
            baseViewModel.setDidYouArriveDialogListener(
                arriveTriggerData = arriveTriggerData,
                stopData = stopData,
                activeDispatchId = activeDispatchId,
                updateDialogPositiveButtonText = { positiveButtonText ->
                    (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                        text = positiveButtonText
                    } ?: Log.w(tag, "Dialog is not an instance of AlertDialog")
                },
                dismissDialog = {
                    dialog.dismiss()
                },
                doOnArrival = { arriveTriggerData, pfmEventsInfo, stopDetail ->
                    doOnArrival(
                        arriveTriggerData,
                        pfmEventsInfo,
                        stopDetail
                    )
                }
            )
        }
        lifecycleScope.launch(baseViewModel.coroutineDispatcher.io()) {
            Log.d(ARRIVAL_PROMPT, "DYA dialog is shown", null, DISPATCHID to activeDispatchId, STOPID to stopData?.stopid, KEY to DISPATCH_LIFECYCLE)
            baseViewModel.setIsDyaShownKey(true)
        }
        didYouArriveAlertDialog?.show()
    }

    private fun doOnArrival(
        arriveTriggerData: LauncherMessageWithPriority,
        pfmEventsInfo: PFMEventsInfo.StopActionEvents,
        stopDetail: StopDetail? = null
    ) {
        baseViewModel.doOnArrival(
            arriveTriggerData = arriveTriggerData,
            pfmEventsInfo = pfmEventsInfo,
            stopDetail = stopDetail,
            checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch = {
                checkAndDisplayDidYouArriveIfTriggerEventAvailableIfIsTheActiveDispatch()
            },
            dismissDidYouArriveAlert = {
                dismissDidYouArriveDialog()
            },
            checkForTripCompletion = {
                checkForTripCompletion()
            }
        )
    }

    override fun onDestroy() {
        if (didYouArriveAlertDialog?.isNotNull() == true && (didYouArriveAlertDialog?.isShowing == true)) {
            dismissDidYouArriveDialog()
        }
        super.onDestroy()
    }

    private fun dismissDidYouArriveDialog() {
        didYouArriveAlertDialog?.dismiss()
        didYouArriveAlertDialog = null
    }

    override fun onStop() {
        super.onStop()
        baseViewModel.isNavigatingToFormActivity = false
    }

}
