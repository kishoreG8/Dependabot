package com.trimble.ttm.formlibrary.manager.workmanager


import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.formlibrary.utils.FORM_LIB_CACHE_FROM_AUTH
import com.trimble.ttm.formlibrary.utils.FORM_LIB_CACHE_WORKER_TAG
import com.trimble.ttm.formlibrary.utils.USER_IDS
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class FormLibraryCacheWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), KoinComponent {
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val coroutineDispatcherProvider: DispatcherProvider by inject()
    private val cacheGroupsUseCase: CacheGroupsUseCase by inject()

    companion object {
        fun enqueueFormLibraryCacheFromAuthWorkRequest(
            workManager: WorkManager,
            uniqueWorkName: String
        ) {
            val isAndroidAboveS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val networkConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val foreGroundServiceFormLibraryCacheWork =
                OneTimeWorkRequestBuilder<FormLibraryCacheWorker>()
                    .setConstraints(networkConstraint)
                    .setInputData(workDataOf(FORM_LIB_CACHE_FROM_AUTH to true))
                    .setInputData(workDataOf(USER_IDS to uniqueWorkName))
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
            if (isAndroidAboveS) {
                foreGroundServiceFormLibraryCacheWork.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                foreGroundServiceFormLibraryCacheWork.build()
            )
            Log.i(FORM_LIB_CACHE_WORKER_TAG, "Unique work enqueued for $uniqueWorkName")
        }
    }

    override suspend fun doWork(): Result {
        val userCredential = inputData.getString(USER_IDS)
        if (inputData.getBoolean(FORM_LIB_CACHE_FROM_AUTH, true)) {
            try {
                withContext(
                    SupervisorJob() + coroutineDispatcherProvider.io() + CoroutineName(
                        FORM_LIB_CACHE_WORKER_TAG
                    )
                ) {
                    if (appModuleCommunicator.doGetCid()
                            .isEmpty() or appModuleCommunicator.doGetObcId().isEmpty()
                    ) return@withContext Result.failure()
                    val status = cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetObcId(),
                        this,
                        FORM_LIB_CACHE_WORKER_TAG
                    )
                    Log.i(
                        FORM_LIB_CACHE_WORKER_TAG,
                        "Groups server cache status for $userCredential : $status"
                    )
                }
                return Result.success()
            } catch (e: Exception) {
                Log.w(
                    FORM_LIB_CACHE_WORKER_TAG,
                    "Retrying FormLibraryCaching work for $userCredential failed due to exception ${e.message}"
                )
                return Result.retry()
            }
        } else {
            Log.w(
                FORM_LIB_CACHE_WORKER_TAG,
                "FormLibraryCaching work for $userCredential failed because $FORM_LIB_CACHE_FROM_AUTH is false"
            )
            return Result.failure()
        }
    }
}