/*
 * *
 *  * Copyright Trimble Inc., 2019 - 2020 All rights reserved.
 *  *
 *  * Licensed Software Confidential and Proprietary Information of Trimble Inc.,
 *   made available under Non-Disclosure Agreement OR License as applicable.
 *
 *   Product Name: TTM - Route Manifest
 *
 *   Author: Vignesh Elangovan
 *
 *   Created On: 12-08-2020
 *
 *   Abstract: Contains information to render any kind of form dynamically.
 * *
 */
package com.trimble.ttm.commons.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.math.BigDecimal

const val EDITABLE = 1

@Parcelize
data class FormField(
    @SerializedName("qnum")
    val qnum: Int = 0, // Field position in the form
    @SerializedName("qtext")
    val qtext: String = "", // Text to be displayed in the field
    @SerializedName("qtype")
    val qtype: Int = 0, // Type of the field like text or num
    @SerializedName("formid")
    val formid: Int = 0, // Form Id created by pfm while creating
    @SerializedName("description")
    val description: String = "", // Currently used for barcode to show what are we scanning
    @SerializedName("fieldId")
    val fieldId: Long = 0L,
    @SerializedName("required")
    var required: Int = 0, // Used to specify the field must be filled or not
    @SerializedName("dispatchEditable")
    var dispatchEditable: Int = 0, // Used along with driverEditable to determine the form should be displayed for driver
    @SerializedName("driverEditable")
    var driverEditable: Int = 0, // Used to determine the field is editable or readonly
    @SerializedName("numspPre")
    val numspPre: String? = null, //currencySymbol
    @SerializedName("numspTsep")
    val numspTsep: Int? = null,  //thousandSeparator "###,###.##"
    @SerializedName("numspMax")
    val numspMax: BigDecimal? = null,//maxValue -- Used to specify the maxLines for the field
    @SerializedName("numspMin")
    val numspMin: BigDecimal? = null,//minValue --  Used to specify the minLines for the field
    @SerializedName("numspLeftjust")
    val numspLeftjust: Int? = null,//leftJustified
    @SerializedName("numspDec")
    val numspDec: Int? = null,//decimalCount after zero
    @SerializedName("displayText")
    val displayText: String? = "",
    @SerializedName("bcAllowDuplicate")
    val bcAllowDuplicate: Int? = null, //Allow Duplicate Bar Codes
    @SerializedName("bcAllowMultiple")
    val bcAllowMultiple: Int? = null,//Allow Multiple Bar Codes
    @SerializedName("bcBarCodeType")
    val bcBarCodeType: Int = 0, //Bar Code Type
    @SerializedName("bcLimitMultiples")
    val bcLimitMultiples: Int = 0, //Max. number of Bar Codes
    @SerializedName("bcMinLength")
    val bcMinLength: Int = 0, //Min Length (0=any length)
    @SerializedName("bcMaxLength")
    val bcMaxLength: Int = 0, //Max Length (0=any length)
    @SerializedName("loopcount")
    var loopcount: Int? = null,
    @SerializedName("ffmLength")
    var ffmLength: Int = 2000, // Default free form length
    @SerializedName("branchTargetId")
    val branchTargetId: Int? = null,

    @SerializedName("formChoices")
    val formChoices: @RawValue ArrayList<FormChoice>? = ArrayList()
) : Parcelable {
    @IgnoredOnParcel
    var formChoiceList: ArrayList<FormChoice>? = ArrayList()

    @IgnoredOnParcel
    var uiData = ""

    @IgnoredOnParcel
    var errorMessage: String = ""

    @IgnoredOnParcel
    var viewId = -1

    @IgnoredOnParcel
    var signViewHeight = 0

    @IgnoredOnParcel
    var signViewWidth = 0

    @IgnoredOnParcel
    var uniqueIdentifier = ""

    @IgnoredOnParcel
    var needToSyncImage = false

    @IgnoredOnParcel
    var isInDriverForm = false

    //There are scenarios where we need to convert/format the default values before setting the value to the edittext.
    // For driver not editable fields if we set the converted/formatted value, our logic in text change listener in FormEditText prevents it from setting the value in edittext.
    //Added this variable and modified the logic in the text change listener in FormEdittext.
    @IgnoredOnParcel
    var isSystemFormattable = false

    @IgnoredOnParcel
    var actualLoopCount: Int? = null
}

fun FormField.isDriverEditable() = this.driverEditable == EDITABLE

fun FormField.isDispatcherEditable() = this.dispatchEditable == EDITABLE

fun FormField.checkForDriverNonEditableFieldInDriverForm() =
    (isDriverEditable().not() && isInDriverForm)

fun FormField.isNotMultipleChoiceOrMultipleChoiceWithNoTarget(): Boolean {
    return this.qtype != FormFieldType.MULTIPLE_CHOICE.ordinal || this.multipleChoiceDriverInputNeeded()
        .not()
}

fun FormField.multipleChoiceDriverInputNeeded(): Boolean {
    return this.formChoiceList?.any { it.branchTargetId != null } ?: false
}

fun ArrayList<FormField>.getImageNames(): List<String>? {
    val imageNames =  this.filter { it.qtype == FormFieldType.IMAGE_REFERENCE.ordinal }
        .map { it.uniqueIdentifier }
    return imageNames.takeIf { it.isNotEmpty() }
}