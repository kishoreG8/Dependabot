package com.trimble.ttm.commons.model

import com.trimble.ttm.commons.utils.EMPTY_STRING
import java.time.Instant

data class DispatchBlob(val cid : Long = 0,
                        val vehicleNumber : String = EMPTY_STRING,
                        val vid : Long = 0,
                        val blobMessage : String = EMPTY_STRING,
                        val createDate : Instant = Instant.now(),
                        val appId : Long = 0,
                        val hostId : Long = 0) {

    var id : String = EMPTY_STRING
}