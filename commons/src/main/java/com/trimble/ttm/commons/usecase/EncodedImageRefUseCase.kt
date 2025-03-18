package com.trimble.ttm.commons.usecase

import android.graphics.Bitmap
import com.trimble.ttm.commons.model.FormFieldType
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.repo.EncodedImageRefRepo
import kotlinx.coroutines.Dispatchers

class EncodedImageRefUseCase(
    private val encodedImageRefRepo: EncodedImageRefRepo
) {

    suspend fun fetchEncodedStringForReadOnlyThumbnailDisplay(cid: String, truckNum: String, imageID: String, caller: String): String =
        encodedImageRefRepo.fetchEncodedStringForReadOnlyThumbnailDisplay(cid, truckNum, imageID, caller, Dispatchers.IO)

    suspend fun fetchPreviewImageBitmap(cid: String, truckNum: String, imageID: String, caller: String): Bitmap? =
        encodedImageRefRepo.fetchPreviewImageBitmap(cid, truckNum, imageID, caller, Dispatchers.IO)

    fun mapImageUniqueIdentifier(
        formTemplateData: FormTemplate,
    ) {
        formTemplateData.formFieldsList.filter { formField ->
            formField.qtype == FormFieldType.IMAGE_REFERENCE.ordinal && formField.uiData.isNotEmpty() && formField.uniqueIdentifier.isEmpty()
                    && formField.needToSyncImage
        }.let { imgFields ->
            imgFields.forEach { imgField ->
                imgField.uniqueIdentifier = encodedImageRefRepo.generateUUID()
            }
        }
    }

}