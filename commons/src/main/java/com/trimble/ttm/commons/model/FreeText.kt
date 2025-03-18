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
 *   Abstract: Contains necessary information to render a simple text field with user data.
 * *
 */
package com.trimble.ttm.commons.model

data class FreeText(val uniqueTag: Long, val initiallyEmpty: Boolean, val text: String, var qtype: Int? = null)