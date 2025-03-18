package com.trimble.ttm.formlibrary.manager.workmanager

import android.content.Context
import androidx.work.*
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DOT_JPG
import com.trimble.ttm.commons.utils.ENCODED_IMAGE_COLLECTION
import com.trimble.ttm.commons.utils.IMAGES
import com.trimble.ttm.commons.utils.IMAGE_DELETE_ONE_TIME_WORKER
import com.trimble.ttm.commons.utils.IMAGE_NAMES_KEY
import com.trimble.ttm.commons.utils.IMAGE_UPLOAD_ONE_TIME_WORKER
import com.trimble.ttm.commons.utils.IMAGE_UPLOAD_PERIODIC_WORKER
import com.trimble.ttm.commons.utils.IS_DRAFT_KEY
import com.trimble.ttm.commons.utils.SHOULD_DELETE_FROM_STORAGE_KEY
import com.trimble.ttm.commons.utils.UNDERSCORE_THUMBNAIL
import com.trimble.ttm.commons.utils.UNIQUE_WORK_NAME_KEY
import com.trimble.ttm.commons.utils.getOrCreateDraftImagesFolder
import com.trimble.ttm.commons.utils.getOrCreateThumbnailFolder
import com.trimble.ttm.commons.utils.getOrCreateToBeUploadedFolder
import com.trimble.ttm.formlibrary.usecases.ImageHandlerUseCase
import kotlinx.coroutines.Dispatchers
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.concurrent.TimeUnit

class ImageHandlingWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val imageHandlerUseCase: ImageHandlerUseCase by inject()

    override suspend fun doWork(): Result {
        val uniqueWorkName = inputData.getString(UNIQUE_WORK_NAME_KEY)
            ?: return Result.failure()
        try {
            val folderWithFilesToUpload = context.getOrCreateToBeUploadedFolder()
            val folderWithDraftImages = context.getOrCreateDraftImagesFolder()
            val files = when (uniqueWorkName) {
                IMAGE_UPLOAD_ONE_TIME_WORKER, IMAGE_UPLOAD_PERIODIC_WORKER -> {
                    val imageNames = inputData.getStringArray(IMAGE_NAMES_KEY)
                    val imagesList = imageNames?.toList()
                    Log.d(uniqueWorkName, "Worker $uniqueWorkName started - Images to upload $imagesList")
                    if (imagesList != null) {
                        getImagesWithSpecificNames(folderWithFilesToUpload, imagesList)
                    } else {
                        folderWithFilesToUpload.listFiles()?.toList() ?: emptyList()
                    }
                }

                IMAGE_DELETE_ONE_TIME_WORKER -> {
                    val imageNames = inputData.getStringArray(IMAGE_NAMES_KEY)
                    val imagesList = imageNames?.toList()
                    Log.d(uniqueWorkName, "Worker $uniqueWorkName started - Images to be deleted $imagesList")
                    if (imagesList != null) {
                        getImagesWithSpecificNames(folderWithFilesToUpload, imagesList) +
                                getImagesWithSpecificNames(
                                    folderWithDraftImages,
                                    imagesList
                                )
                    } else emptyList()
                }

                else -> {
                    Log.d(uniqueWorkName, "Unexpected worker name: $uniqueWorkName")
                    emptyList()
                }
            }

            val path = (ENCODED_IMAGE_COLLECTION
                    + "/" + appModuleCommunicator.doGetCid()
                    + "/" + appModuleCommunicator.doGetTruckNumber() + "/" + IMAGES)

            Log.d(uniqueWorkName, "Images ready to be uploaded for ${files.size} file/files")

            files.forEach { file ->
                Log.d(uniqueWorkName, "Image ready to be uploaded for the file $file.name")

                val successful = when (uniqueWorkName) {
                    IMAGE_UPLOAD_ONE_TIME_WORKER, IMAGE_UPLOAD_PERIODIC_WORKER -> {
                        handleImageUpload(file, path, uniqueWorkName, folderWithDraftImages)
                    }

                    IMAGE_DELETE_ONE_TIME_WORKER -> {
                        handleImageDelete(file, path, uniqueWorkName)
                    }

                    else -> true // Handle unexpected cases
                }

                if (successful.not()) {
                    Log.e(uniqueWorkName, "Worker $uniqueWorkName failed, so retrying it for $file")
                    return Result.retry() // Retry the entire work request if any operation fails
                }
            }

            return Result.success() // Indicate success after processing all files
        } catch (e: Exception) {
            Log.e(uniqueWorkName, "Error processing images: ${e.message}", e)
            return Result.retry() // Retry the entire work request
        }
    }

    private suspend fun handleImageUpload(
        file: File,
        path: String,
        uniqueWorkName: String,
        folderWithDraftImages: File
    ): Boolean {
        return try {
            val isUploaded = imageHandlerUseCase.uploadImage(Dispatchers.IO, file, path, file.nameWithoutExtension)
            if (!isUploaded) {
                Log.e(
                    uniqueWorkName,
                    "Error uploading image: download URL is blank with path:$path"
                )
                return false
            }
            Log.d(uniqueWorkName, "Image uploaded successfully: ${file.name}")

            if (uniqueWorkName == IMAGE_UPLOAD_ONE_TIME_WORKER) {
                val isDraft = inputData.getBoolean(IS_DRAFT_KEY, false)
                if (isDraft) {
                    file.renameTo(File(folderWithDraftImages, file.name))
                } else {
                    file.delete()
                }
            }
            true // Return true if all operations succeed
        } catch (e: Exception) {
            Log.e(uniqueWorkName, "Error uploading image: ${e.message}", e)
            false // Return false if any exception occurs
        }
    }

    private suspend fun handleImageDelete(
        file: File,
        path: String,
        uniqueWorkName: String
    ): Boolean {
        return try {
            val shouldDeleteFromStorage =
                inputData.getBoolean(SHOULD_DELETE_FROM_STORAGE_KEY, false)
            // Delete the original file
            if (file.exists()) file.delete()
            //Delete the thumbnail
            val thumbnailFolder = context.getOrCreateThumbnailFolder()
            val thumbnailFile = File(thumbnailFolder, "${file.nameWithoutExtension}$UNDERSCORE_THUMBNAIL$DOT_JPG")
            if (thumbnailFile.exists()) thumbnailFile.delete()
            if (shouldDeleteFromStorage) {
                val isDeleted =
                    imageHandlerUseCase.deleteImageFromStorage(Dispatchers.IO, path, file.name)
                if (!isDeleted) {
                    Log.e(uniqueWorkName, "Error deleting image at storage path:$path")
                    return false
                }
            }
            Log.d(uniqueWorkName, "Image deleted successfully for File ${file.name}")
            true // Return true if all operations succeed
        } catch (e: Exception) {
            Log.e(uniqueWorkName, "Error deleting image: ${e.message}", e)
            false // Return false if any exception occurs
        }
    }

    private fun getImagesWithSpecificNames(folder: File, imageNames: List<String>): List<File> =
        folder.listFiles()?.filter { it.nameWithoutExtension in imageNames || it.name in imageNames } ?: emptyList()

}

fun Context.schedulePeriodicImageUpload() {
    val inputData = Data.Builder()
        .putString(UNIQUE_WORK_NAME_KEY, IMAGE_UPLOAD_PERIODIC_WORKER)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val uploadWorkRequest = PeriodicWorkRequestBuilder<ImageHandlingWorker>(1, TimeUnit.DAYS)
        .setConstraints(constraints)
        .setInputData(inputData)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        IMAGE_UPLOAD_PERIODIC_WORKER,
        ExistingPeriodicWorkPolicy.KEEP,
        uploadWorkRequest
    )
}

fun Context.scheduleOneTimeImageUpload(imageNames: List<String>, isDraft: Boolean) {
    val inputData = Data.Builder()
        .putStringArray(IMAGE_NAMES_KEY, imageNames.filter { it.isNotEmpty() }.toTypedArray())
        .putBoolean(IS_DRAFT_KEY, isDraft)
        .putString(UNIQUE_WORK_NAME_KEY, IMAGE_UPLOAD_ONE_TIME_WORKER)
        .build()

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val uploadWorkRequest = OneTimeWorkRequestBuilder<ImageHandlingWorker>()
        .setConstraints(constraints)
        .setInputData(inputData)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()

    Log.d(tag = IMAGE_UPLOAD_ONE_TIME_WORKER, "Image upload work request created for $imageNames")
    WorkManager.getInstance(this).enqueueUniqueWork(
        IMAGE_UPLOAD_ONE_TIME_WORKER,
        ExistingWorkPolicy.APPEND,
        uploadWorkRequest
    )
}

fun Context.scheduleOneTimeImageDelete(imageNames: List<String>, shouldDeleteFromStorage: Boolean) {
    val chunkSize = 100
    val chunks = imageNames.chunked(chunkSize)

    chunks.forEach { chunk ->
        val inputData = Data.Builder()
            .putStringArray(IMAGE_NAMES_KEY, chunk.filter { it.isNotEmpty() }.toTypedArray())
            .putBoolean(SHOULD_DELETE_FROM_STORAGE_KEY, shouldDeleteFromStorage)
            .putString(UNIQUE_WORK_NAME_KEY, IMAGE_DELETE_ONE_TIME_WORKER)
            .build()

        val constraints = if (shouldDeleteFromStorage) {
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        } else {
            Constraints.NONE // Use default constraints if flag is false
        }

        val uploadWorkRequest = OneTimeWorkRequestBuilder<ImageHandlingWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            IMAGE_DELETE_ONE_TIME_WORKER,
            ExistingWorkPolicy.APPEND,
            uploadWorkRequest
        )
    }
}