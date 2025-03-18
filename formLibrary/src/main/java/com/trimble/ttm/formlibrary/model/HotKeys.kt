package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING

data class HotKeys(
    @SerializedName("formClass") val formClass: Int = -1,
    @SerializedName("formId") val formId: Int = -1,
    @SerializedName("formName") val formName: String = EMPTY_STRING,
    @SerializedName("hkId") val hkId: Int = -1,
    val hotKeysDescription : HotKeysDescription = HotKeysDescription()
)