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
 *   Abstract: Contains information to render a form with choices.
 * *
 */
package com.trimble.ttm.commons.model

import com.google.gson.annotations.SerializedName

data class FormChoice(
    @SerializedName("qnum")
    val qnum: Int,

    @SerializedName("choicenum")
    val choicenum: Int,

    @SerializedName("value")
    val value: String,

    @SerializedName("formid")
    val formid: Int,

    @SerializedName("branchTargetId")
    val branchTargetId: Int? = null
)  {
    var viewId = 0
}
