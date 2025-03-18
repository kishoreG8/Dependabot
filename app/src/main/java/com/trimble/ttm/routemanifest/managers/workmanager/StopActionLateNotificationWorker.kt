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
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_STOP_ACTION_LATE_NOTIFICATION
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.routemanifest.model.isEtaMissed
import com.trimble.ttm.routemanifest.usecases.LateNotificationUseCase
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_WORKER_KEY
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class StopActionLateNotificationWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {
    private val lateNotificationUseCase: LateNotificationUseCase by inject()
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()

    companion object {
        fun enqueueStopActionLateNotificationCheckWork(
            workManager: WorkManager,
            workUniqueName: String,
            dispatchId: String,
            delay: Long
        ) {
            val workRequest = OneTimeWorkRequestBuilder<StopActionLateNotificationWorker>()
                .setInputData(workDataOf(DISPATCH_ID_WORKER_KEY to dispatchId))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .addTag(dispatchId)
                .build()
            workManager.enqueueUniqueWork(workUniqueName, ExistingWorkPolicy.KEEP, workRequest)
            Log.i(TRIP_STOP_ACTION_LATE_NOTIFICATION, "Work scheduled for dispatch: $dispatchId. workName: $workUniqueName")
        }

        fun cancelAllLateNotificationCheckWorkByTag(workManager: WorkManager, workTag: String) {
            workManager.cancelAllWorkByTag(workTag)
            Log.i(TRIP_STOP_ACTION_LATE_NOTIFICATION, "Late notification Work cancelled for dispatch. Trip ended: $workTag")
        }

    }

    override suspend fun doWork(): Result = withContext(coroutineDispatcherProvider.io()) {
        inputData.getString(DISPATCH_ID_WORKER_KEY)?.let { dispatchId ->
            Log.i(TRIP_STOP_ACTION_LATE_NOTIFICATION, "Checking late notification for dispatch: $dispatchId in StopActionLateNotificationWorker")
            try {
                val inCompleteActionsOfActiveStops = lateNotificationUseCase.fetchInCompleteStopActions(TRIP_STOP_ACTION_LATE_NOTIFICATION, dispatchId)
                inCompleteActionsOfActiveStops.forEach { action ->
                    if (action.isEtaMissed(TRIP_STOP_ACTION_LATE_NOTIFICATION + " D: ${action.dispid} S: ${action.stopid} A: ${action.actionid}")) {
                        lateNotificationUseCase.processForLateActionNotification(coroutineDispatcherProvider = coroutineDispatcherProvider, action = action)
                    }
                }
                Result.success()
            } catch (e: Exception) {
                Log.w(TRIP_STOP_ACTION_LATE_NOTIFICATION, "ExceptionInLateNotificationWorker. retrying. ${e.stackTraceToString()}")
                Result.retry()
            }
        } ?: kotlin.run {
            Log.e(TRIP_STOP_ACTION_LATE_NOTIFICATION, "Dispatch id is null")
            Result.failure()
        }
    }

}