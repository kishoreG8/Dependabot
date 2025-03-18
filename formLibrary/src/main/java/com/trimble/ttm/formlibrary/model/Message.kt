package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.formlibrary.utils.LINE_FEED
import java.io.Serializable

data class Message(
    val userName: String = "",
    val subject: String = "",
    var summary: String = "",
    var date: String = "",
    var dateTime: String = "",
    val formId: String = "",
    val formClass: String = "",
    val formName: String = "",
    val replyFormId: String = "",
    val replyFormClass: String = "",
    val replyFormName: String = "",
    var formFieldList: ArrayList<MessageFormField> = arrayListOf(),
    var recipients: Map<String, Any> = mutableMapOf(),
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val asn: String = "",
    val uid: Long = 0L,
    val replyActionType: String = ""
) : Serializable {

    var summaryText = this.summary.getWithDateRemovedAndFormatted()
    var timestamp: Long = 0L
    var rowDate: Long = 0L

    companion object {

        fun String.getWithDateRemovedAndFormatted(): String {
            return this.getFormattedUiString().removeFirstDateOccurrence()
        }

        fun String.getFormattedUiString(): String {
            return this.replace(LINE_FEED, System.getProperty("line.separator")!!)
        }

        fun String.removeFirstDateOccurrence(): String {
            val pattern =
                Regex("^(\\d)*\\/\\d*\\s\\d*\\:\\d*((\\sGMT(\\+|\\-)\\d*\\:\\d*)|(\\sCST))\\n*")
            return pattern.replace(
                this,
                ""
            ).trim()
        }

    }
}