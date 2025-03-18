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
 *   Abstract: Contains data for the saved form response from backend.
 * *
 */
package com.trimble.ttm.commons.model

import android.os.Parcelable
import com.trimble.ttm.commons.utils.ASN
import com.trimble.ttm.commons.utils.DAMN
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import java.util.Date

@Parcelize
data class FormResponse(
    var dsn: Long = 0L,
    var baseSerialNumber: Long = 0L,
    var baseSerialNumberType: String = ASN,
    val creationDateTime: String = getUTCFormattedDate(Date()),
    val driverCanSendUrgent: Boolean = false,
    val driverMustSend: Boolean = false,
    val driverSentUrgent: Boolean = false,
    var msgSerialNumber: Long = 0L,
    var msgSerialNumberType: String = DAMN,
    var mailbox: String = "",
    val noDelete: Boolean = false,
    val priority: Boolean = false,
    val readFromPda: Boolean = false,
    val sendToPda: Boolean = false,
    val timeOffset: Int = 0,
    var uniqueTemplateHash: Long = 0L,
    var uniqueTemplateTag: Long = 0L,
    var recipients: MutableList<Recipients> = mutableListOf(),
    var fieldData: @RawValue ArrayList<Any> = ArrayList()
): Parcelable