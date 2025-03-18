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
 *   Abstract: Contains information to render a form with various fields dynamically
 * *
 */
package com.trimble.ttm.commons.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class FormTemplate(
    @SerializedName("FormDef")
    var formDef: FormDef = FormDef(),

    @SerializedName("FormFields")
    var formFieldsList: ArrayList<FormField> = ArrayList()
): Serializable {
    var error: String = ""
    var asn:Long = 0L
}
