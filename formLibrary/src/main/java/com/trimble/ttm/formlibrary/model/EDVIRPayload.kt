package com.trimble.ttm.formlibrary.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class EDVIRPayload(
    @SerializedName("Active") val active: Int = 0,
    @SerializedName("Asn") val asn: Int = 0,
    @SerializedName("DeviceDatastoreKeysID") val deviceDatastoreKeysID: String = "",
    @SerializedName("Dsn") val dsn: Int = 0,
    @SerializedName("IntValue") val intValue: Int = 0,
    @SerializedName("KeyName") val keyName: String = "",
    @SerializedName("LastupdateDt") val lastupdateDt: String = "",
    @SerializedName("LastupdatedUID") val lastupdatedUID: Int = 0,
    @SerializedName("PendIntValue") val pendIntValue: Int = 0,
    @SerializedName("PendTextValue") val pendTextValue: String = "",
    @SerializedName("PostingID") val postingID: Int = 0,
    @SerializedName("TextValue") val textValue: String = "",
    @SerializedName("FormClass") val formClass: Int = -1
) : Parcelable