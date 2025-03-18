package com.trimble.ttm.formlibrary.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.trimble.ttm.formlibrary.utils.ADDR_BOOK_FIELD
import com.trimble.ttm.formlibrary.utils.UID_FIELD_KEY
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class User(
    @SerializedName(value ="UID", alternate = [UID_FIELD_KEY]) val uID: Long = -1L,
    @SerializedName("Username") val username: String = "",
    @SerializedName("Email") val email: String = "",
    @SerializedName("Active") val active: Long = 0L) : Parcelable, Serializable {
    @IgnoredOnParcel
    var isSelected: Boolean = false

    @IgnoredOnParcel
    @SerializedName(ADDR_BOOK_FIELD) val addressBook: Boolean = false
}

data class Favourite(val formId: String, val formName: String, val cid: String, val formClass: String)