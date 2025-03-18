package com.trimble.ttm.formlibrary.model

import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import java.io.Serializable

data class FormDataToSave(
    val formTemplate: FormTemplate,
    val path: String,
    val formId: String,
    val typeOfResponse: String,
    val formName: String,
    val formClass: Int,
    val cid: String,
    val hasPredefinedRecipients: Boolean = false,
    val obcId: String,
    val unCompletedDispatchFormPath: DispatchFormPath = DispatchFormPath(),
    val dispatchFormSavePath: String = EMPTY_STRING
) : Serializable