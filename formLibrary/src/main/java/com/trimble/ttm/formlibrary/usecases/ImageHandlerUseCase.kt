package com.trimble.ttm.formlibrary.usecases

import android.net.Uri
import com.trimble.ttm.commons.utils.DOT_JPG
import com.trimble.ttm.formlibrary.repo.ImageHandler
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

class ImageHandlerUseCase(private val imageHandler: ImageHandler) {
    suspend fun uploadImage(coroutineDispatcher: CoroutineDispatcher, file: File, path: String, uniqueId: String): Boolean =
        imageHandler.uploadImage(coroutineDispatcher, file, path, uniqueId)

    suspend fun saveImageLocally(
        coroutineDispatcher: CoroutineDispatcher,
        imageUri: Uri, uniqueId: String
    ): Boolean = imageHandler.saveImageLocallyFromURI(coroutineDispatcher, imageUri, uniqueId)

    private fun checkFilenameForJpgExtension(filename: String): String {
        return if (filename.endsWith(DOT_JPG, ignoreCase = true)) {
            filename
        } else {
            "$filename$DOT_JPG"
        }
    }

    suspend fun saveImageLocally(coroutineDispatcher: CoroutineDispatcher, imageName: String, imageData: ByteArray): Pair<String, Boolean> {
        if(imageName.isEmpty()) return Pair(imageName, false)
        val uniqueId = checkFilenameForJpgExtension(imageName)
        return Pair(uniqueId, imageHandler.saveImageLocallyFromByteArray(coroutineDispatcher, uniqueId, imageData))
    }

    suspend fun deleteImageFromStorage(
        coroutineDispatcher: CoroutineDispatcher,
        path: String,
        uniqueId: String
    ): Boolean =
        imageHandler.deleteImage(coroutineDispatcher, path, uniqueId)
}