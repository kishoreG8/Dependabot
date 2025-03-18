package com.trimble.ttm.commons.model


data class UIFormResponse(
    var isSyncDataToQueue: Boolean = false,
    var formData: FormResponse = FormResponse()
)
