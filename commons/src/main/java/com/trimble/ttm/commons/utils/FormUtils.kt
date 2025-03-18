package com.trimble.ttm.commons.utils

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.utils.ext.removeDecimalPoints

object FormUtils {

    fun Any.getRecipient(): Recipients =
        try {
            if (this is Int || this is Long || this is Double) {
                Recipients(
                    this.toString().toDouble().toLong(),
                    null
                )
            } else {
                Recipients(
                    null,
                    this.toString()
                )
            }
        } catch (e: Exception) {
            Log.e(this@FormUtils.javaClass.name, "Error casting Recipient ${e.message}")
            Recipients(
                null,
                null
            )
        }

    /**
     * Convert the backbone odometer value to Miles, multiply this value in Miles with 10 and trim the decimal point values.
     * Multiply by 10 is required since pfm divides the incoming value by 10 and displays it.10ths of mile.
     * https://confluence.trimble.tools/pages/viewpage.action?spaceKey=PNETTECH&title=Forms
     *
     */
    fun convertOdometerKmValueToMilesAndRemoveDecimalPoints(odometerValueInKM: Double): Int {
        return (odometerValueInKM * 0.621371 * 10).removeDecimalPoints()
    }

    fun shouldDisplayDefaultValues(
        isDriverInMessageReplyForm: Boolean,
        isSyncDataToQueue: Boolean,
        isReplyWithSame: Boolean
    ): Boolean {

        val firstCondition = (isDriverInMessageReplyForm || isSyncDataToQueue) && isReplyWithSame

        val secondCondition = !isDriverInMessageReplyForm && !isSyncDataToQueue && !isReplyWithSame

        return firstCondition || secondCondition || isReplyWithSame
    }


}