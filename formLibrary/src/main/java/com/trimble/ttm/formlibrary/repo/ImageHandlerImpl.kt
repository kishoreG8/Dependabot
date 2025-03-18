package com.trimble.ttm.formlibrary.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.repo.EncodedImageRefRepoImpl.Companion.rotateImageIfRequired
import com.trimble.ttm.commons.utils.COMPRESSION_QUALITY_100
import com.trimble.ttm.commons.utils.COMPRESSION_QUALITY_95
import com.trimble.ttm.commons.utils.DOT_JPG
import com.trimble.ttm.commons.utils.IMAGE_HANDLER
import com.trimble.ttm.commons.utils.THUMBNAIL_SIZE
import com.trimble.ttm.commons.utils.UNDERSCORE_THUMBNAIL
import com.trimble.ttm.commons.utils.getOrCreateThumbnailFolder
import com.trimble.ttm.commons.utils.getOrCreateToBeUploadedFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class ImageHandlerImpl(
    private val context: Context,
    private val storageReference: StorageReference = FirebaseStorage.getInstance().reference
) : ImageHandler {

    override suspend fun saveImageLocallyFromByteArray(coroutineDispatcher: CoroutineDispatcher, uniqueId: String, imageData: ByteArray): Boolean {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap == null) {
                Log.e(IMAGE_HANDLER, "Failed to decode byte array to bitmap")
                return false
            }
            withContext(coroutineDispatcher) {
                val permFolder = context.getOrCreateToBeUploadedFolder()
                val originalFile = File(permFolder, uniqueId)
                FileOutputStream(originalFile).use { outputStream ->
                    val success = bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        COMPRESSION_QUALITY_100,
                        outputStream
                    ) // Use PNG format for lossless compression
                    if (!success) {
                        Log.e(IMAGE_HANDLER, "Failed to compress and save bitmap")
                        return@withContext false
                    }
                }
            }
            Log.d(IMAGE_HANDLER, "Image is locally saved successfully for custom workflow", throwable = null , "File uniqueId" to uniqueId)
            true
        } catch (e: Exception) {
            // Handle exceptions, e.g., log the error or return false
            Log.e(IMAGE_HANDLER, "Error saving image for custom workflow: ${e.message}")
            false
        }
    }

    override suspend fun deleteImage(coroutineDispatcher: CoroutineDispatcher, path: String, uniqueId: String): Boolean {
        return withContext(coroutineDispatcher) {
            try {
                val imageRef = storageReference.child("$path/$uniqueId")
                val result = suspendCoroutine<Boolean> { continuation ->
                    imageRef.delete().addOnSuccessListener {
                        continuation.resume(true)
                    }.addOnFailureListener { exception ->
                        Log.e("$IMAGE_HANDLER Delete", "Error deleting file: ${exception.message}")
                        continuation.resume(false)
                    }
                }
                result
            } catch (e: Exception) {
                Log.i(
                    IMAGE_HANDLER,
                    "Error deleting image in path $path/$uniqueId$DOT_JPG : with message: ${e.message}"
                )
                false
            }
        }
    }

    override suspend fun saveImageLocallyFromURI(
        coroutineDispatcher: CoroutineDispatcher,
        imageUri: Uri,
        uniqueId: String
    ): Boolean {
        return withContext(coroutineDispatcher) {
            Log.d(IMAGE_HANDLER, "saveImageLocallyFromURI called URI:$imageUri UniqueId:$uniqueId")
            val retryCount = 3
            var isSaved = false

            repeat(retryCount) { attempt ->
                try {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    val toBeUploadedFolder = context.getOrCreateToBeUploadedFolder()
                    val thumbnailFolder = context.getOrCreateThumbnailFolder()
                    val originalFile = File(toBeUploadedFolder, "$uniqueId$DOT_JPG")
                    val thumbnailFile = File(thumbnailFolder, "${uniqueId}$UNDERSCORE_THUMBNAIL$DOT_JPG")

                    inputStream?.use { input ->
                        // Save original image
                        FileOutputStream(originalFile).use { output ->
                            input.copyTo(output)
                        }

                        // Generate and save thumbnail with orientation handling
                        val bitmap = BitmapFactory.decodeStream(
                            context.contentResolver.openInputStream(imageUri)
                        )
                        val rotatedBitmap =
                            rotateImageIfRequired(bitmap, imageUri, context) ?: bitmap
                        val thumbnail = ThumbnailUtils.extractThumbnail(rotatedBitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                        FileOutputStream(thumbnailFile).use { thumbnailOutputStream ->
                            thumbnail.compress(
                                Bitmap.CompressFormat.JPEG,
                                COMPRESSION_QUALITY_95,
                                thumbnailOutputStream
                            )
                        }
                    }

                    isSaved = true
                    Log.d(IMAGE_HANDLER, "saved successfully URI:$imageUri UniqueId:$uniqueId")
                    return@repeat // Exit the repeat loop if successful
                } catch (e: Exception) {
                    Log.d(IMAGE_HANDLER, "Error saving image (attempt $attempt): ${e.message}", e)
                    delay(1000) // Delay before retrying
                }
            }

            isSaved // Return true if saved successfully, false otherwise
        }
    }

    override suspend fun uploadImage(coroutineDispatcher: CoroutineDispatcher, file: File, path: String, uniqueId: String): Boolean {
        return withContext(coroutineDispatcher) {
            try {
                val imageRef = storageReference.child("$path/$uniqueId$DOT_JPG")
                val uploadTask = imageRef.putFile(Uri.fromFile(file))
                    .await() // Use await() for coroutine integration
                uploadTask.storage.downloadUrl.await() // Get download URL using await()
                Log.d(IMAGE_HANDLER, "Uploaded image in path $path/$uniqueId.jpg")
                true // Return true if upload and download URL retrieval are successful
            } catch (e: Exception) {
                // Handle upload or download URL retrieval failure
                Log.i(
                    IMAGE_HANDLER,
                    "Error uploading image in path $path/$uniqueId.jpg : with message: ${e.message}"
                )
                false // Return false if an error occurs
            }
        }
    }
}