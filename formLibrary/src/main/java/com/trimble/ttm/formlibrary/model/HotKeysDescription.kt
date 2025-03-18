package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING

data class HotKeysDescription(@SerializedName("hkDesc") val description : String = EMPTY_STRING, @SerializedName("hkId") val hotKeysId : Int = -1)
