package com.trimble.ttm.commons.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.trimble.ttm.commons.logger.IMAGE
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.COMPRESSION_QUALITY_100
import com.trimble.ttm.commons.utils.COMPRESSION_QUALITY_95
import com.trimble.ttm.commons.utils.DOT_JPG
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.ENCODED_IMAGE_COLLECTION
import com.trimble.ttm.commons.utils.ENCODED_IMAGE_REF_REPO
import com.trimble.ttm.commons.utils.IMAGES
import com.trimble.ttm.commons.utils.IMAGE_HANDLER
import com.trimble.ttm.commons.utils.IMAGE_REFERENCE_KEY
import com.trimble.ttm.commons.utils.JPG
import com.trimble.ttm.commons.utils.STORAGE
import com.trimble.ttm.commons.utils.TEMP_IMAGE
import com.trimble.ttm.commons.utils.THUMBNAIL_SIZE
import com.trimble.ttm.commons.utils.UNDERSCORE_THUMBNAIL
import com.trimble.ttm.commons.utils.ext.getFromCache
import com.trimble.ttm.commons.utils.ext.getFromServer
import com.trimble.ttm.commons.utils.ext.isCacheEmpty
import com.trimble.ttm.commons.utils.ext.isNull
import com.trimble.ttm.commons.utils.getOrCreateDraftImagesFolder
import com.trimble.ttm.commons.utils.getOrCreateThumbnailFolder
import com.trimble.ttm.commons.utils.getOrCreateToBeUploadedFolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume

class EncodedImageRefRepoImpl(private val context: Context,
                              private val fireStore: FirebaseFirestore = FirebaseFirestore.getInstance(),
                              private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : EncodedImageRefRepo {

    override suspend fun fetchPreviewImageBitmap(
        cid: String,
        truckNum: String,
        imageID: String,
        caller: String,
        dispatcherIO: CoroutineDispatcher
    ): Bitmap? {

        if (cid.isEmpty() || truckNum.isEmpty() || imageID.isEmpty()) {
            Log.d(
                "$IMAGE$caller",
                "companyId:$cid or vehicleId:$truckNum or imageID:$imageID is empty while fetching encoded image"
            )
            return null
        }

        val path = "$ENCODED_IMAGE_COLLECTION/$cid/$truckNum/$IMAGES/$imageID$DOT_JPG"
        val draftFolder = context.getOrCreateDraftImagesFolder()
        val previewFile = File(draftFolder, "${imageID}$DOT_JPG")
        val yetToBeUploadedPreviewFile = File(context.getOrCreateToBeUploadedFolder(), "${imageID}$DOT_JPG")

        return try {
            when {
                // If image was captured in draft mode and not uploaded due offline then its exist in to be uploaded folder and not in draft images folder
                yetToBeUploadedPreviewFile.exists() -> {
                    rotateImageIfRequired(BitmapFactory.decodeFile(yetToBeUploadedPreviewFile.absolutePath), Uri.fromFile(yetToBeUploadedPreviewFile), context)
                }

                // all uploaded images exist in draft images folder
                previewFile.exists() -> {
                    rotateImageIfRequired(BitmapFactory.decodeFile(previewFile.absolutePath), Uri.fromFile(previewFile), context)
                }

                imageID.startsWith(STORAGE) -> {
                    withContext(dispatcherIO) {
                        suspendCancellableCoroutine<Bitmap?> { continuation ->
                            val child = storage.reference.child(path)
                            val localTempFile = File.createTempFile(IMAGE, JPG) // local image

                            child.getFile(localTempFile)
                                .addOnSuccessListener {
                                    // Image downloaded successfully to localTempFile
                                    try {
                                        val rotatedBitmap = rotateImageIfRequired(BitmapFactory.decodeFile(localTempFile.absolutePath), Uri.fromFile(localTempFile), context)
                                        FileOutputStream(previewFile).use { outputStream ->
                                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY_100, outputStream)
                                        }
                                        localTempFile.delete()
                                        continuation.resume(rotatedBitmap)
                                    } catch (e: Exception) {
                                        Log.e(
                                            ENCODED_IMAGE_REF_REPO,
                                            "Caller $caller : Error saving image locally for path $path: with error : ${e.message}"
                                        )
                                        continuation.resume(null) // Resume with null if there is an exception
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    Log.e(
                                        ENCODED_IMAGE_REF_REPO,
                                        "Caller $caller : Error while downloading image for $path with error ${exception.message}"
                                    )
                                    continuation.resume(null)
                                }
                        }
                    }
                }

                else -> {
                    null // else is required though there is no logic here
                }
            }
        } catch (e: Exception) {
            Log.e(ENCODED_IMAGE_REF_REPO, " Error fetching preview image for $path")
            null
        }
    }

    override suspend fun fetchEncodedStringForReadOnlyThumbnailDisplay(
        cid: String,
        truckNum: String,
        imageID: String,
        caller: String,
        dispatcherIO: CoroutineDispatcher
    ): String {
        if (cid.isEmpty() || truckNum.isEmpty() || imageID.isEmpty()) {
            Log.d(
                "$IMAGE$caller",
                "companyId:$cid or vehicleId:$truckNum or imageID:$imageID is empty while fetching encoded image"
            )
            return EMPTY_STRING
        }

        val path = "$ENCODED_IMAGE_COLLECTION/$cid/$truckNum/$IMAGES/$imageID$DOT_JPG"
        val thumbnailFolder = context.getOrCreateThumbnailFolder()
        val thumbnailFile = File(thumbnailFolder, "${imageID}$UNDERSCORE_THUMBNAIL$DOT_JPG")

        return try {
            when {
                thumbnailFile.exists() -> {
                    // Refactored to avoid unnecessary ByteArrayOutputStream
                   thumbnailFile.readBytes().let { Base64.encodeToString(it, Base64.DEFAULT) }
                }

                imageID.startsWith(STORAGE) -> {
                    withContext(dispatcherIO) {
                        suspendCancellableCoroutine { continuation ->
                            val child = storage.reference.child(path)

                            val localTempFile = File.createTempFile(TEMP_IMAGE, JPG)

                            child.getFile(localTempFile)
                                .addOnSuccessListener {
                                    // Image downloaded successfully to localTempFile
                                    try {
                                        val bitmap =
                                           rotateImageIfRequired(BitmapFactory.decodeFile(localTempFile.absolutePath), Uri.fromFile(localTempFile), context)

                                        val thumbnail = ThumbnailUtils.extractThumbnail(bitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                                         FileOutputStream(thumbnailFile).use { thumbnailOutputStream ->
                                            thumbnail.compress(
                                                Bitmap.CompressFormat.JPEG,
                                                COMPRESSION_QUALITY_95,
                                                thumbnailOutputStream
                                            )
                                        }
                                        localTempFile.delete()
                                        continuation.resume(
                                            thumbnailFile.readBytes().let {
                                                Base64.encodeToString(
                                                    it,
                                                    Base64.DEFAULT
                                                )
                                            })
                                    } catch (e: Exception) {
                                        Log.e(
                                            ENCODED_IMAGE_REF_REPO,
                                            "Caller $caller : Error saving thumbnail image locally path $path: with error: ${e.message}"
                                        )
                                        continuation.resume(EMPTY_STRING) // Resume with null if there is an exception
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    Log.e(
                                        ENCODED_IMAGE_REF_REPO,
                                        "Caller $caller : Error while downloading image for $path with error ${exception.message}"
                                    )
                                    continuation.resume(EMPTY_STRING)
                                }
                        }
                    }
                }

                else -> {
                    val documentReference = fireStore.collection(ENCODED_IMAGE_COLLECTION)
                        .document(cid).collection(truckNum).document(imageID)
                    val docSnapShot = if (documentReference.isCacheEmpty()) {
                        documentReference.getFromServer().await()
                    } else {
                        documentReference.getFromCache().await()
                    }
                    if (docSnapShot.data.isNull()) {
                        Log.e("$ENCODED_IMAGE_COLLECTION$caller", "encoded image doc is null for $imageID")
                        EMPTY_STRING
                    } else {
                        docSnapShot[IMAGE_REFERENCE_KEY].toString()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(
                "$IMAGE$caller",
                "exception in getting encoded image",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
            EMPTY_STRING
        }
    }

    override fun generateUUID(): String = fireStore.collection(ENCODED_IMAGE_COLLECTION).document().id

    companion object {
        fun rotateImageIfRequired(img: Bitmap, selectedImage: Uri, context: Context): Bitmap {
            return try {
                context.contentResolver.openInputStream(selectedImage)?.use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    val orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotateImageBasedOnOrientation(img, orientation)
                } ?: img // Return original image if ExifInterface creation fails
            } catch (e: IOException) {
                Log.i(IMAGE_HANDLER, "Error rotating image: ${e.message}")
                img // Return original image on error
            }
        }

        private fun rotateImageBasedOnOrientation(img: Bitmap, orientation: Int): Bitmap {
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
                else -> img
            }
        }

        private fun rotateImage(img: Bitmap, degree: Int): Bitmap {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(degree.toFloat())
            val rotatedImg =
                Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
            img.recycle()
            return rotatedImg
        }
    }

}

