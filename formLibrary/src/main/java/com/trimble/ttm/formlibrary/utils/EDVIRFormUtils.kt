package com.trimble.ttm.formlibrary.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object EDVIRFormUtils {
    /**
     * Takes an inspection type and returns a shortened string version with a space, for the annotation text.
     * e.g. InterInspection becomes "Inter insp."
     * */
    private fun getFormattedInspectionText(inspectionType: String) =
        inspectionType.replace("Inspection", " insp.")

    fun createAnnotationText(inspectionType: String, userId: String, vehicleDSN: String): String {
        // Formats inspection text as "Pre insp." or "Inter insp."
        val inspectionText = getFormattedInspectionText(inspectionType)
        // Formats dates as "02/05/19 17:33"
        val dateTimeFormatted = getCurrentUTCTime()
        var annotationText = "$inspectionText by $userId, $dateTimeFormatted, Veh $vehicleDSN"
        if (annotationText.length > MAX_CHAR) {
            //trim annotation text if length is greater than max limit
            val overage = annotationText.length - MAX_CHAR
            val userIdTextAbridged = userId.substring(0, userId.length - overage)
            annotationText =
                "$inspectionText by $userIdTextAbridged, $dateTimeFormatted, Veh $vehicleDSN"
        }
        return annotationText
    }

    fun isAnnotationValid(regex: String, value: String): Boolean {
        val sPattern = Pattern.compile(regex)
        return sPattern.matcher(value).matches().not() && value.isNotEmpty() && value.length <= MAX_CHAR
    }

    private fun getCurrentUTCTime(): String {
        val simpleDateFormat = SimpleDateFormat("MM/dd/yy H:mm", Locale.getDefault())
        simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return simpleDateFormat.format(Date())
    }
}