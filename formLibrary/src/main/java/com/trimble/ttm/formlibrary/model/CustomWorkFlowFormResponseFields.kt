package com.trimble.ttm.formlibrary.model

/**
 * Class to hold custom workflow response form fields need to be sent to firestore
 */

data class CustomWorkFlowFreeText(val text: String, val fieldNumber: String)

data class CustomWorkFlowMultipleChoice(val choice: Int, val fieldNumber: String)

data class CustomWorkFlowOdometer(val j1708: Int, val gps: Int, val fieldNumber: String)

data class CustomWorkFlowLocation(
    val latitude: Double,
    val longitude: Double, val gpsQuality: Int, val fieldNumber: String
)

data class CustomWorkFlowImage(
    val dateTimeOfImage: String,
    val imageName: String,
    val mimeType: String,
    val fieldNumber: String,
    val uniqueIdentifier: String
)
