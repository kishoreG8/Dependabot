package com.trimble.ttm.commons.repo

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher

interface EncodedImageRefRepo {
    suspend fun fetchEncodedStringForReadOnlyThumbnailDisplay(
        cid: String,
        truckNum: String,
        imageID: String,
        caller: String,
        dispatcherIO: CoroutineDispatcher
    ): String

    fun generateUUID(): String

    suspend fun fetchPreviewImageBitmap(
        cid: String,
        truckNum: String,
        imageID: String,
        caller: String,
        dispatcherIO: CoroutineDispatcher
    ): Bitmap?
}