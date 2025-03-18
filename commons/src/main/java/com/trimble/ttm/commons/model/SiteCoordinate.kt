package com.trimble.ttm.commons.model

import com.google.gson.annotations.SerializedName

data class SiteCoordinate(
    @SerializedName("Latitude") var latitude: Double,
    @SerializedName("Longitude") var longitude: Double
)
