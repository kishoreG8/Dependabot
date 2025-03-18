package com.trimble.ttm.commons.model

import android.content.Intent
import com.trimble.ttm.commons.utils.CAN_SHOW_CANCEL
import com.trimble.ttm.commons.utils.DISPATCH_FORM_PATH_SAVED
import com.trimble.ttm.commons.utils.DRIVER_FORM_ID
import com.trimble.ttm.commons.utils.FORM_DATA_KEY
import com.trimble.ttm.commons.utils.FORM_RESPONSE_PATH
import com.trimble.ttm.commons.utils.IMESSAGE_REPLY_FORM_DEF
import com.trimble.ttm.commons.utils.IS_ACTION_RESPONSE_SENT_TO_SERVER
import com.trimble.ttm.commons.utils.IS_FROM_DRAFT
import com.trimble.ttm.commons.utils.IS_FROM_TRIP_PANEL
import com.trimble.ttm.commons.utils.IS_SECOND_FORM_KEY
import com.trimble.ttm.commons.utils.composeFormActivityIntentAction
import com.trimble.ttm.commons.utils.ext.fromIntent
import com.trimble.ttm.commons.utils.formActivityIntentAction

data class FormActivityIntentActionData(
    val isComposeEnabled: Boolean,
    val containsStopData: Boolean,
    val customerId: Int?,
    val formId: Int?,
    val formResponse: FormResponse,
    val driverFormPath: DispatchFormPath,
    val isSecondForm: Boolean = false,
    val dispatchFormSavePath: String,
    val isFormFromTripPanel: Boolean = false,
    val isFormResponseSentToServer: Boolean = false,
    val isFromDraft: Boolean = false
) {
    private val destinationActivity =
        if (!isComposeEnabled) formActivityIntentAction else composeFormActivityIntentAction

    fun buildIntent(flags: Int=Intent.FLAG_ACTIVITY_CLEAR_TOP, intent: Intent?=null): Intent {
        return with(intent.fromIntent(destinationActivity)) {
            if (customerId != null && formId != null) {
                putExtra(IMESSAGE_REPLY_FORM_DEF, FormDef(cid = customerId, formid = formId))
            }
            if (containsStopData) {
                putExtra(FORM_DATA_KEY, formResponse)
                putExtra(IS_FROM_DRAFT, isFromDraft)
                putExtra(DRIVER_FORM_ID, driverFormPath)
                putExtra(IS_SECOND_FORM_KEY, isSecondForm)
                addFlags(flags)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            putExtra(FORM_RESPONSE_PATH, dispatchFormSavePath)
            putExtra(DISPATCH_FORM_PATH_SAVED, driverFormPath)
            putExtra(IS_FROM_TRIP_PANEL, isFormFromTripPanel)
            putExtra(CAN_SHOW_CANCEL, containsStopData.not())
            putExtra(IS_ACTION_RESPONSE_SENT_TO_SERVER, isFormResponseSentToServer)
            this
        }
    }
}