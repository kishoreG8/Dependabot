package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.FormTemplate

data class EDVIRFormData(val formTemplate: FormTemplate,
                         val customerId: String,
                         val obcId: String,
                         val driverName: String,
                         val formId: Int,
                         val formClass: Int,
                         val inspectionType: String,
                         val isSyncToQueue: Boolean)
