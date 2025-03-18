package com.trimble.ttm.commons.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import com.trimble.ttm.commons.utils.FREE_FORM_FORM_CLASS
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class FormDef(
    @SerializedName("cid")
    val cid: Int = -1,

    @SerializedName("formid")
    val formid: Int = -1,

    @SerializedName("name")
    var name: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("formHash")
    val formHash: Long = 0L,

    @SerializedName("formClass")
    val formClass: Int = -1,

    @SerializedName("driverOriginate")
    val driverOriginate: Int = 0,

    @SerializedName("driverEditable")
    val driverEditable: Int = 0,

    @SerializedName("recipients")
    var recipients: @RawValue Map<String, Any> = mutableMapOf(),

    @SerializedName("userFdlScript")
    val userFdlScript: String? = null,
): Parcelable

fun FormDef.isValidForm(): Boolean = this.formid >= 0 && this.formClass > -1

fun FormDef.isFreeForm(): Boolean = this.isValidForm() && this.formClass == FREE_FORM_FORM_CLASS