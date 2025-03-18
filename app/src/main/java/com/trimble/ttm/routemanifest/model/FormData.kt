package com.trimble.ttm.routemanifest.model

import com.trimble.ttm.commons.utils.EMPTY_STRING

data class FormData(val customerId: String = EMPTY_STRING,
                    val vehicleId: String= EMPTY_STRING,
                    val dispatchId: String= EMPTY_STRING,
                    val formTemplatePath: String,
                    val stopId: String,
                    val actionId: String,
                    val formId: String,
                    val isFreeForm: Boolean)
