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
 *   Abstract: Contains all types of fields for form rendering
 * *
 */
package com.trimble.ttm.commons.model

import com.trimble.ttm.commons.utils.BARCODE_KEY
import com.trimble.ttm.commons.utils.FREETEXT_KEY
import com.trimble.ttm.commons.utils.IMAGE_REFERENCE_KEY
import com.trimble.ttm.commons.utils.LATLNG_KEY
import com.trimble.ttm.commons.utils.LOCATION_KEY
import com.trimble.ttm.commons.utils.MULTIPLECHOICE_KEY
import com.trimble.ttm.commons.utils.ODOMETER_KEY
import com.trimble.ttm.commons.utils.SIGNATURE_KEY

// https://confluence.trimble.tools/display/PNETTECH/Forms
//https://confluence.trimble.tools/pages/viewpage.action?spaceKey=PNETTECH&title=Forms+Based+Messaging+Overview
enum class FormFieldType(val serializedName: String) {
    NUMERIC("number"),  //0
    TEXT(FREETEXT_KEY),    //1
    MULTIPLE_CHOICE(MULTIPLECHOICE_KEY), //2
    PASSWORD("password"), //3
    TIME("time"),//4
    DATE_TIME( "dateTime"),//5
    AUTO_DRIVER_NAME("driverName"),//6
    AUTO_VEHICLE_LOCATION(LOCATION_KEY),//7
    AUTO_VEHICLE_LATLONG(LATLNG_KEY),//8
    AUTO_VEHICLE_ODOMETER(ODOMETER_KEY),//9
    SIGNATURE_CAPTURE(SIGNATURE_KEY),//10
    BARCODE_SCAN(BARCODE_KEY),//11
    DISPLAY_TEXT("displayText"),//12
    AUTO_VEHICLE_FUEL("autoVehicleFuel"),//13
    NUMERIC_ENHANCED("numericEnhanced"),//14
    AUTO_DATE_TIME("autoDateTime"),//15
    DATE("date"),//16
    BRANCH_TO("branchTo"),//17
    BRANCH_TARGET("branchTarget"), //18 /*NOTE: do not remove unused members as it will affect original position in form field */
    LOOP_START("loopStart"),//19
    LOOP_END("loopEnd"),//20
    IMAGE_REFERENCE(IMAGE_REFERENCE_KEY)//21
}