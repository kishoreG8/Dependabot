package com.trimble.ttm.formlibrary.service

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.CUSTOM_WORKFLOW_FOREGROUND_SERVICE_CHANNEL_ID
import com.trimble.ttm.commons.utils.CUSTOM_WORK_FLOW_FOREGROUND_SERVICE_ID
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.STORAGE
import com.trimble.ttm.commons.utils.createNotification
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.manager.workmanager.scheduleOneTimeImageUpload
import com.trimble.ttm.formlibrary.usecases.CustomWorkflowFormHandleUseCase
import com.trimble.ttm.formlibrary.usecases.ImageHandlerUseCase
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.seconds

private const val tag = "CustomWorkFlowForegroundService"
private const val EXTRA_FORM_DATA = "MessageData"
private const val EXTRA_IMAGE_DATA = "ImageData"
private const val EXTRA_IMAGE_NAME = "ImageName"


class CustomWorkFlowEventForegroundService : LifecycleService() {
    private val customWorkflowFormHandleUseCase: CustomWorkflowFormHandleUseCase by inject()
    private val imageHandlerUseCase: ImageHandlerUseCase by inject()
    private lateinit var serviceIdleTimer: Job
    private val coroutineDispatcherProvider: DispatcherProvider by inject()

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "CWFServiceOnCreateCall")
        startNotificationForForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(tag, "CWFServiceOnStartCommandCall")

        scheduleStopService()
        if (intent == null || intent.action == null) {
            Log.e(tag, "CWFIntentOrActionNull $intent")
        }

        if (intent != null) {
            intent.getStringExtra(EXTRA_FORM_DATA)?.let {
                Log.d(tag, "CWFIntentFormData $it")
                lifecycleScope.launch(coroutineDispatcherProvider.default()) {
                    customWorkflowFormHandleUseCase.deserializeFormXMLToDataClass(it)
                        ?.let { deserializedFormData ->
                            customWorkflowFormHandleUseCase.sendCustomWorkflowResponseToPFM(
                                deserializedFormData
                            )
                        }
                }
            }

            var imageName = intent.getStringExtra(EXTRA_IMAGE_NAME)
            val imageData = intent.getByteArrayExtra(EXTRA_IMAGE_DATA)
            if (imageName.isNullOrEmpty().not() && imageData?.isNotEmpty() == true) {
                imageName = STORAGE+imageName
                Log.d(tag, "CWFIntentImageData $imageName") //not logging the image data as it can be too large

                val imageSavedChannel = Channel<Boolean>(capacity = 1) // Create a channel

                lifecycleScope.launch(coroutineDispatcherProvider.io()) {
                    val retryCount = 3
                    var isImageSaved = false

                    repeat(retryCount) { attempt ->
                        try {
                            with(imageHandlerUseCase.saveImageLocally(Dispatchers.IO,
                                imageName?: EMPTY_STRING,
                                imageData
                            )) {
                                imageName = first
                                isImageSaved = second
                            }
                            if (isImageSaved) return@repeat
                        } catch (e: Exception) {
                            Log.e(tag, "Error saving image (attempt $attempt): ${e.message}", e)
                            delay(1000)
                        }
                    }
                    imageSavedChannel.send(isImageSaved)
                }

                lifecycleScope.launch {
                    val isImageSaved = imageSavedChannel.receive()
                    Log.d(tag, "Preparing to upload , is image saved - $isImageSaved for the image name $imageName")
                    if (isImageSaved) {
                        val retryCount = 3
                        var isUploadScheduled = false

                        repeat(retryCount) { attempt ->
                            try {
                                this@CustomWorkFlowEventForegroundService.scheduleOneTimeImageUpload(
                                    listOf(imageName ?: EMPTY_STRING), isDraft = false
                                )
                                isUploadScheduled = true
                                return@repeat
                            } catch (e: Exception) {
                                Log.e(tag, "Error scheduling upload (attempt $attempt): ${e.message}", e)
                                delay(1000)
                            }
                        }

                        if (!isUploadScheduled) {
                            Log.n(tag, "Upload scheduling failed after $retryCount attempts, for $imageName")
                            // Handle upload scheduling failure, e.g., store the image for later upload
                        }
                    } else {
                        Log.d(tag, "Image saving failed, skipping upload")
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleStopService() {
        Log.n(tag, "Scheduling stop service")
        if (::serviceIdleTimer.isInitialized && serviceIdleTimer.isActive) {
            serviceIdleTimer.cancel()
        }
        serviceIdleTimer = lifecycleScope.launch(coroutineDispatcherProvider.default()) {
            delay(20.seconds)
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.n(tag, "Stopping service $tag")
            stopSelf()
        }
    }

    private fun startNotificationForForeground() {
        startForeground(
            CUSTOM_WORK_FLOW_FOREGROUND_SERVICE_ID, this.createNotification(
                CUSTOM_WORKFLOW_FOREGROUND_SERVICE_CHANNEL_ID,
                getString(R.string.custom_workflow_service_channel_name),
                getString(R.string.app_name),
                getString(R.string.notification_content_text),
                R.mipmap.ic_forms_shortcut
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.n(tag, "Destroy $tag")
        if (::serviceIdleTimer.isInitialized && serviceIdleTimer.isActive) {
            serviceIdleTimer.cancel()
        }
    }
}