package com.trimble.ttm.formlibrary.model
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
 *   Abstract: Contains different types of inspections available.
 * *
 */

enum class InspectionType {
    Unknown,
    PreInspection,
    PostInspection,
    InterInspection,
    DotInspection
}