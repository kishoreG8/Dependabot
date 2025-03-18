package com.trimble.ttm.formlibrary.model

data class EDVIRInspection(
    val driverName: String = "",
    val formId: Int = 0,
    val formClass: Int = -1,
    val inspectionType: String = ""
) {
    var createdAt: String = ""
}