package com.trimble.ttm.routemanifest.managers.workmanager

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trimble.ttm.commons.logger.DEVICE_FCM
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.NOTIFICATION
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.usecase.AuthenticateUseCase
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.FCM_TOKENS_COLLECTION
import com.trimble.ttm.commons.utils.FCM_TOKEN_RECHECK
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.CHECK_WITH_FIRESTORE_FCM_WORKER_KEY
import com.trimble.ttm.routemanifest.utils.FCM_TOKEN_RECHECK_INTERVAL
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class RefreshFCMTokenWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {

    private val authenticateUseCase: AuthenticateUseCase by inject()
    private val localDataSourceRepo : LocalDataSourceRepo by inject()
    private val appModuleCommunicator : AppModuleCommunicator by inject()
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()

    companion object {
        /* Will run every 30-minute interval, along with an internet check for work to be executed.
           This will read the FCM token from Firestore and then check for emptiness and device-Firestore token is in sync and updates datastore key when there is an update*/
        fun enqueueCheckFcmTokenPeriodicWork(
            workManager: WorkManager,
            delay: Long
        ) {
            workManager.cancelAllWorkByTag("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK")
            val networkConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val fcmTokenRefreshWorkPeriodicRequest =
                PeriodicWorkRequestBuilder<RefreshFCMTokenWorker>(FCM_TOKEN_RECHECK_INTERVAL, TimeUnit.MINUTES)
                    .setConstraints(networkConstraint)
                    .setInputData(
                        workDataOf(
                            Pair(CHECK_WITH_FIRESTORE_FCM_WORKER_KEY, true),
                        )
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .addTag("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK")
                    .setInitialDelay(delay, TimeUnit.SECONDS)
                    .build()
            workManager.enqueue(fcmTokenRefreshWorkPeriodicRequest)
        }

        /* Will be enqueued every time as an OneTimeWorkRequest.
           This will validate the datastore value of VID and Token. Update if these values are changed */
        fun enqueueCheckFcmTokenWork(
            workManager: WorkManager,
            workUniqueName: String
        ) {
            val isAndroidAboveS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val fcmTokenRefreshWorkRequestBuilder =
                OneTimeWorkRequestBuilder<RefreshFCMTokenWorker>()
                    .setInputData(
                        workDataOf(
                            Pair(CHECK_WITH_FIRESTORE_FCM_WORKER_KEY, false)
                        )
                    )
            if (isAndroidAboveS) {
                fcmTokenRefreshWorkRequestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            workManager.enqueueUniqueWork(
                workUniqueName,
                ExistingWorkPolicy.KEEP,
                fcmTokenRefreshWorkRequestBuilder.build()
            )
        }
    }

    override suspend fun doWork(): Result = withContext(coroutineDispatcherProvider.io()) {
        val customerId = appModuleCommunicator.doGetCid()
        val truckNumber = appModuleCommunicator.doGetTruckNumber()
        if(customerId.isEmpty() || truckNumber.isEmpty()){
            Log.e("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
                "Ignoring CheckFcmTokenWork since isCidEmpty : ${customerId.isEmpty()} , isTruckNumberEmpty : ${truckNumber.isEmpty()}")
            return@withContext Result.failure()
        }
        if(appModuleCommunicator.isFirebaseAuthenticated().not()){
            return@withContext Result.failure()
        }
        val deviceFcmToken = authenticateUseCase.fetchFCMDeviceToken()
        val oldTruckNumber = localDataSourceRepo.getFromFormLibModuleDataStore(
            FormDataStoreManager.TRUCK_NUMBER,
            EMPTY_STRING)
        if(oldTruckNumber.isNotEmpty() && oldTruckNumber != truckNumber){
            Log.n("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
                "Deleting old FCM token for vehicleID : $oldTruckNumber, currentVehicleID : $truckNumber")
            authenticateUseCase.registerDeviceSpecificTokenToFireStore(
                customerId,
                truckNumber,
                deviceFcmToken,
                oldTruckNumber,
                "$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK"
            )
            return@withContext Result.success()
        }

        if (inputData.getBoolean(CHECK_WITH_FIRESTORE_FCM_WORKER_KEY, false)) {
            if(authenticateUseCase.checkFcmInSyncWithFireStore(customerId, truckNumber, deviceFcmToken)) {
                // adding this to check whether its been updated in offline since addOnSuccessListener is not called in offline MAPP - 12536
                // will remove later
                val fcmTokenFromFirestore = authenticateUseCase.fetchFCMTokenFromFireStore("$FCM_TOKENS_COLLECTION/$customerId/$truckNumber")
                Log.d("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
                    "FCM recheck worker job is success when checkFcmInSyncWithFireStore oldTruckNumber : $oldTruckNumber, currentVehicleID : $truckNumber",
                null,
                    "deviceFCMToken" to deviceFcmToken,
                    "fcmTokenFromFireStore" to fcmTokenFromFirestore
                )
                return@withContext Result.success()
            }
            Log.e("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
                "FCM recheck worker failed when checkFcmInSyncWithFireStore oldTruckNumber : $oldTruckNumber, currentVehicleID : $truckNumber")
            return@withContext Result.failure()
        }
        if(authenticateUseCase.checkFcmInSyncWithCache(customerId, truckNumber, deviceFcmToken)){
            return@withContext Result.success()
        }
        Log.e("$NOTIFICATION$DEVICE_FCM$FCM_TOKEN_RECHECK",
            "FCM recheck worker failed when checkFcmInSyncWithCache, oldTruckNumber : $oldTruckNumber, currentVehicleID : $truckNumber")
        return@withContext Result.failure()
    }
}