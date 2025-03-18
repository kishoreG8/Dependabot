package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.utils.DateUtil.getUTCFormattedDate
import com.trimble.ttm.commons.utils.EMPTY_STRING
import java.util.Date

/**
 * Class to hold custom workflow responses need to be sent to firestore/PFM
 */
data class CustomWorkFlowFormResponse(
    var dsn: Long ?= 0L,
    val creationDateTime: String?=getUTCFormattedDate(Date()),
    val uniqueTemplateTag: Long ?= 0L,
    val recipients: MutableList<Recipients>?= mutableListOf(),
    val fieldData: ArrayList<Any>?= arrayListOf(),
) {
    //Adding truck details since Custom workflow apps don't give them. it can be helpful in queries
    var cid: String = EMPTY_STRING
    var truckNumber: String = EMPTY_STRING
}

