package com.trimble.ttm.routemanifest.managers.workmanager

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.trimble.ttm.commons.logger.AUTO_TRIP_START_BACKGROUND
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.application.WorkflowApplication.Companion.isDispatchListActivityVisible
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.usecases.TripCompletionUseCase
import com.trimble.ttm.routemanifest.usecases.TripStartCaller
import com.trimble.ttm.routemanifest.usecases.TripStartUseCase
import com.trimble.ttm.routemanifest.utils.AUTO_START_CALLER_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.AUTO_TRIP_START_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider
import com.trimble.ttm.routemanifest.utils.ApplicationContextProvider.getApplicationContext
import com.trimble.ttm.routemanifest.utils.CUSTOMER_ID_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.DISPATCH_NAME_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.TRIP_START_EVENT_REASON_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.VEHICLE_ID_WORKER_KEY
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class AutoTripStartWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
    private val tripStartUseCase: TripStartUseCase by inject()
    private val tripCompletionUseCase: TripCompletionUseCase by inject()
    private val localDataSourceRepo : LocalDataSourceRepo by inject()
    private val appModuleCommunicator : AppModuleCommunicator by inject()
    companion object {
        fun enqueueAutoTripStartWork(
            dispatchId: String,
            dispatchName: String,
            cid: String,
            vehicleId: String,
            tripStartEventReason: String,
            delay: Long,
            caller: String
        ) {
            val inputData = Data.Builder().apply {
                putString(DISPATCH_ID_WORKER_KEY, dispatchId)
                putString(DISPATCH_NAME_WORKER_KEY, dispatchName)
                putString(CUSTOMER_ID_WORKER_KEY, cid)
                putString(VEHICLE_ID_WORKER_KEY, vehicleId)
                putString(TRIP_START_EVENT_REASON_WORKER_KEY, tripStartEventReason)
                putString(AUTO_START_CALLER_WORKER_KEY, caller)
            }.build()
            val autoStartTripWorkRequest =
                OneTimeWorkRequestBuilder<AutoTripStartWorker>()
                .setInputData(inputData)
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .addTag("$cid$vehicleId$dispatchId")
            if (delay > 0L) {
                autoStartTripWorkRequest.setInitialDelay(delay, TimeUnit.MILLISECONDS)
            }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && delay == 0L) {
                autoStartTripWorkRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(AUTO_TRIP_START_WORKER_KEY, ExistingWorkPolicy.REPLACE, autoStartTripWorkRequest.build())
            Log.i(caller, "Work scheduled for dispatch: $dispatchId name $dispatchName delay $delay caller $caller")
        }

    }
    override suspend fun doWork(): Result = withContext(coroutineDispatcherProvider.io()) {
        inputData.getString(DISPATCH_ID_WORKER_KEY)?.let { dispatchId ->
            val dispatchName = (inputData.getString(DISPATCH_NAME_WORKER_KEY))?.trim()
            val cid = (inputData.getString(CUSTOMER_ID_WORKER_KEY))?.trim()
            val vehicleNumber = (inputData.getString(VEHICLE_ID_WORKER_KEY))?.trim()
            val tripStartEventReason = (inputData.getString(TRIP_START_EVENT_REASON_WORKER_KEY))?.trim()
            val caller = inputData.getString(AUTO_START_CALLER_WORKER_KEY) ?: AUTO_TRIP_START_BACKGROUND
            try {
                //start the trip
                Log.i(caller, "Worker STARTED for dispatch: $dispatchId")

            if (cid.isNullOrEmpty() || vehicleNumber.isNullOrEmpty()) {
                Log.e(caller, "Worker failed cid $cid or vehicleNumber $vehicleNumber is empty")
                return@withContext Result.failure()
            }
            if(vehicleNumber != appModuleCommunicator.doGetTruckNumber()){
                Log.i(caller, "Worker failed vehicleNumber $vehicleNumber is not matched with current vehicle number ${appModuleCommunicator.doGetTruckNumber()}")
                return@withContext Result.failure()
            }
            if (localDataSourceRepo.hasActiveDispatch()){
                Log.i(caller, "Worker failed Active dispatch is already present dispatchId $dispatchId activeDispatchId ${localDataSourceRepo.getActiveDispatchId(AUTO_TRIP_START_BACKGROUND)}")
                return@withContext Result.failure()
            }
                tripCompletionUseCase.sendDispatchCompleteEventToCPIK(AUTO_TRIP_START_BACKGROUND)
                tripStartUseCase.updateDispatchInfoInDataStore(
                    dispatchId, dispatchName ?: EMPTY_STRING, caller)
                tripStartUseCase.getDispatchStopsAndActionsAndStartTrip(cid, vehicleNumber, dispatchId,
                    tripStartEventReason ?: EMPTY_STRING, caller, TripStartCaller.AUTO_TRIP_START_BACKGROUND)
                //To show toast only when app is in foreground and dispatch list activity is not visible
                if (WorkflowApplication.isAppBackground.not() && isDispatchListActivityVisible().not()) {
                    withContext(coroutineDispatcherProvider.main()) {
                        Toast.makeText(ApplicationContextProvider.getApplicationContext(), "Trip started for $dispatchName", Toast.LENGTH_SHORT).show()
                    }
                }
                Log.i(caller, "Worker COMPLETED for dispatch: $dispatchId")
                Result.success()
            } catch (exception: FirebaseFirestoreException) {
                Log.e(caller, "Worker retrying reason - FirebaseFirestoreException ${exception.localizedMessage}")
                Result.retry()
            }
        } ?: kotlin.run {
            Log.e(AUTO_TRIP_START_BACKGROUND, "Dispatch id is null")
            Result.failure()
        }
    }
}