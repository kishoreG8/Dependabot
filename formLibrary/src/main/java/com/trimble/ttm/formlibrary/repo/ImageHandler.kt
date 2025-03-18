package com.trimble.ttm.formlibrary.repo

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

interface ImageHandler {
    suspend fun uploadImage(coroutineDispatcher: CoroutineDispatcher, file: File, path: String, uniqueId: String): Boolean
    suspend fun saveImageLocallyFromURI(
        coroutineDispatcher: CoroutineDispatcher,
        imageUri: Uri, uniqueId: String
    ): Boolean

    suspend fun saveImageLocallyFromByteArray(coroutineDispatcher: CoroutineDispatcher, uniqueId: String, imageData: ByteArray): Boolean
    suspend fun deleteImage(
        coroutineDispatcher: CoroutineDispatcher,
        path: String,
        uniqueId: String
    ): Boolean
}