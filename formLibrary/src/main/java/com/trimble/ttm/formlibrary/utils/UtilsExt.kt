package com.trimble.ttm.formlibrary.utils

import android.content.Context
import android.os.Bundle
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.InspectionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Int?.isGreaterThan(other: Int?) =
    this != null && other != null && this > other

fun Int?.isLessThan(other: Int?) =
    this != null && other != null && this < other

fun Int?.isGreaterThanAndEqualTo(other: Int?) =
    this != null && other != null && this >= other

fun Int?.isLessThanAndEqualTo(other: Int?) =
    this != null && other != null && this <= other

fun Int?.isEqualTo(other: Int?) =
    this != null && other != null && this == other

fun Int?.isNotEqualTo(other: Int?) =
    this != null && other != null && this != other

fun Long?.isGreaterThan(other: Long?) =
    this != null && other != null && this > other

fun Long?.isLessThan(other: Long?) =
    this != null && other != null && this < other

fun Long?.isGreaterThanAndEqualTo(other: Long?) =
    this != null && other != null && this >= other

fun Long?.isLessThanAndEqualTo(other: Long?) =
    this != null && other != null && this <= other

fun Long?.isEqualTo(other: Long?) =
    this != null && other != null && this == other

fun Long?.isNotEqualTo(other: Long?) =
    this != null && other != null && this != other

fun String.toSafeLong(): Long {
    return try {
        this.toLong()
    } catch (e: Exception) {
        0L
    }
}

fun String.toSafeDouble(): Double {
    return try {
        this.toDouble()
    } catch (e: Exception) {
        0.0
    }
}

fun Int.toSafeLong(): Long {
    return try {
        this.toLong()
    } catch (e: Exception) {
        0L
    }
}

fun String.toSafeInt(): Int {
    return try {
        this.toInt()
    } catch (e: Exception) {
        0
    }
}

fun Int.toSafeInt(): Int {
    return try {
        this
    } catch (e: Exception) {
        0
    }
}

fun Long.toSafeInt(): Int =
    try {
        toInt()
    } catch (e: Exception) {
        0
    }

fun String.getInspectionTypeUIText(context: Context): String {
    if (this == InspectionType.PreInspection.name) return context.getString(R.string.pre_trip_inspection)
    if (this == InspectionType.PostInspection.name) return context.getString(R.string.post_trip_inspection)
    if (this == InspectionType.InterInspection.name) return context.getString(R.string.inter_trip_inspection)
    return context.getString(R.string.dot_inspection)
}

fun Bundle.toInspectionFormDef(): FormDef {
    return FormDef(
        formid = if (this.getString(INSPECTION_FORM_ID_KEY).isNullOrEmpty()) -1
        else this.getString(INSPECTION_FORM_ID_KEY)!!.toInt(),
        formClass = if (this.getString(INSPECTION_FORM_CLASS_KEY).isNullOrEmpty()) -1
        else this.getString(INSPECTION_FORM_CLASS_KEY)!!.toInt()
    )
}

fun Any?.isNull() = this == null

fun Any?.isNotNull() = this != null

fun <T> T?.orDefault(default: T): T {
    return this ?: default
}

fun Long.getDateFormatted(format: String): String {
    val date = SimpleDateFormat(format, Locale.getDefault()).format(
        Date(this)
    )
    return date ?: EMPTY_STRING
}