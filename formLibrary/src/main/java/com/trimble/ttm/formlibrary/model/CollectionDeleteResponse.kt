package com.trimble.ttm.formlibrary.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class CollectionDeleteResponse (
    @SerializedName("isDeleteSuccess") val isDeleteSuccess: Boolean,
    @SerializedName("error") val error: String
    ): Serializable