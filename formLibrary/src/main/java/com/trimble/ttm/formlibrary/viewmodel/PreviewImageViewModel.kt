package com.trimble.ttm.formlibrary.viewmodel

import androidx.lifecycle.ViewModel
import com.trimble.ttm.commons.usecase.EncodedImageRefUseCase

class PreviewImageViewModel(private val encodedImageRefUseCase: EncodedImageRefUseCase) :
    ViewModel() {

    suspend fun fetchPreviewImage(cid: String, vehicleId: String, uniqueIdentifier: String, caller: String) =
        encodedImageRefUseCase.fetchPreviewImageBitmap(cid, vehicleId, uniqueIdentifier, caller)

    suspend fun fetchThumbnailImage(cid: String, vehicleId: String, uniqueIdentifier: String, caller: String) =
        encodedImageRefUseCase.fetchEncodedStringForReadOnlyThumbnailDisplay(cid, vehicleId, uniqueIdentifier, caller)
}