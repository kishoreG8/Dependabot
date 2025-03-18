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
 *   Abstract: Contains,
 * 1. form template to dynamically render forms in the application
 * 2. form default values which will be sent from pfm
 * 3. form user response, if anything saved already
 * *
 */
package com.trimble.ttm.commons.model

data class Form(
    val formTemplate: FormTemplate = FormTemplate(),
    val uiFormResponse: UIFormResponse = UIFormResponse(),
    val formFieldValuesMap : HashMap<String, ArrayList<String>> = HashMap()
)