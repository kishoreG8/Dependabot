package com.trimble.ttm.routemanifest.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MigrationResponse(@SerializedName("isMigrationSuccess") val isDeleteSuccess: Boolean,
                             @SerializedName("error") val error: String): Serializable