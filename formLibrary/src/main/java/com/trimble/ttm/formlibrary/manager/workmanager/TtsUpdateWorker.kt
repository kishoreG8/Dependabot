package com.trimble.ttm.formlibrary.manager.workmanager

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.widget.DriverTtsWidget
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TtsUpdateWorker(
    private var appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams),
    KoinComponent {
    private val tag = "TtsUpdateWorker"
    private val appModuleCommunicator: AppModuleCommunicator by inject()

    override fun doWork(): Result {
        appModuleCommunicator.getAppModuleApplicationScope().launch(
            Dispatchers.Default + CoroutineName(tag)
        ) {
            appContext.sendBroadcast(
                Intent(
                    appContext,
                    DriverTtsWidget::class.java
                ).apply {
                    action = DriverTtsWidget.DELETE_ACTION
                }
            )
        }
        return Result.success()
    }

}