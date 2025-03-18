package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.utils.EMPTY_STRING

data class MessageFormData(val path: String,
                           val formResponse: FormResponse,
                           val formId: String,
                           val typeOfResponse: String,
                           val formName: String,
                           val formClass: Int,
                           val cid: Long,
                           val hasPredefinedRecipients: Boolean,
                           val unCompletedDispatchFormPath: DispatchFormPath = DispatchFormPath(),
                           val dispatchFormSavePath: String = EMPTY_STRING
)
