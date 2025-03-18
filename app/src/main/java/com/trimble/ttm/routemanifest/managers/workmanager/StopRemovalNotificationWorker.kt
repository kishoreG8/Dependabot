package com.trimble.ttm.routemanifest.managers.workmanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trimble.ttm.commons.logger.AUTO_TRIP_END
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_COMPLETE
import com.trimble.ttm.commons.logger.TRIP_STOP_REMOVAL_WORKER
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.routemanifest.model.PFMEventsInfo
import com.trimble.ttm.routemanifest.model.StopActionReasonTypes
import com.trimble.ttm.routemanifest.repo.DispatchFirestoreRepo
import com.trimble.ttm.routemanifest.usecases.ICancelNotificationHelper
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.WorkflowAppNotificationUseCase
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_WORKER_KEY
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class StopRemovalNotificationWorker(
    private val context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {
    private val workflowAppNotificationUseCase: WorkflowAppNotificationUseCase by inject()
    private val dispatchFirestoreRepo : DispatchFirestoreRepo by inject()
    private val tripCompletionUseCase: TripCompletionUseCase by inject()
    private val iCancelNotificationHelper : ICancelNotificationHelper by inject()
    private val appModuleCommunicator : AppModuleCommunicator by inject()
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()

    companion object {
        fun enqueueStopRemovalNotificationWork(
            workManager: WorkManager,
            dispatchId: String,
            delay: Long = 5,
            units: TimeUnit = TimeUnit.SECONDS
        ) {
            workManager.enqueueUniqueWork(  //Will not trigger another work if the incoming dispatch id is same as existing work
                dispatchId,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<StopRemovalNotificationWorker>()
                    .setInputData(workDataOf(DISPATCH_ID_WORKER_KEY to dispatchId))
                    .setInitialDelay(delay, units)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .build()
            )
        }
    }

    override suspend fun doWork(): Result = withContext(coroutineDispatcherProvider.io()) {
        inputData.getString(DISPATCH_ID_WORKER_KEY)?.let { dispatchId ->
            Log.i(
                TRIP_STOP_REMOVAL_WORKER,
                "Scheduling stop removal notification in WorkManager",
                throwable = null,
                "dispatchId" to dispatchId
            )
            try {
                val cid = appModuleCommunicator.doGetCid()
                val truckNumber = appModuleCommunicator.doGetTruckNumber()
                if (cid.isNotEmpty() && truckNumber.isNotEmpty()) {
                    checkDispatchCompleteStatusAndStopCount(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        dispatchId
                    )
                }
                workflowAppNotificationUseCase.prepareNotificationDataBasedOnPriority(
                    workflowAppNotificationUseCase.stopRemovalNotifications,
                    dispatchId
                )
                Result.success()
            } catch (e: Exception) {
                Log.w(
                    TRIP_STOP_REMOVAL_WORKER,
                    "SchedulingStopRemovalNotification in WorkManager failed due to ${e.message}",
                    throwable = null,
                    "dispatchId" to dispatchId
                )
                Result.retry()
            }
        } ?: Result.failure()
    }

    private suspend fun checkDispatchCompleteStatusAndStopCount(cid : String, truckNumber : String, dispatchId : String){
        if (cid.isEmpty() || truckNumber.isEmpty() || dispatchId.isEmpty()) {
            Log.e(
                TRIP_STOP_REMOVAL_WORKER+ TRIP_COMPLETE,
                "Error getting stop count.Empty trip values",
                throwable = null,
                "cid" to cid,
                "truckNumber" to truckNumber,
                "trip id" to dispatchId
            )
            return //No need to proceed if any of the values are empty
        }

        val areAllStopsDeleted =
            tripCompletionUseCase.areAllStopsDeleted(cid, truckNumber, dispatchId)
        if (areAllStopsDeleted.not()) return

        Log.logTripRelatedEvents(TRIP_STOP_REMOVAL_WORKER + AUTO_TRIP_END,"All stops are removed D:$dispatchId")
        iCancelNotificationHelper.cancelEditOrCreationTripNotification()
        val pfmEventsInfo = PFMEventsInfo.TripEvents(
            reasonType = StopActionReasonTypes.AUTO.name,
            negativeGuf = false
        )
       tripCompletionUseCase. runOnTripEnd(
            dispatchId = dispatchId,
            caller = "StopRemovalNotificationWorker",
            workManager = WorkManager.getInstance(context),
            pfmEventsInfo = pfmEventsInfo
        )
    }

}