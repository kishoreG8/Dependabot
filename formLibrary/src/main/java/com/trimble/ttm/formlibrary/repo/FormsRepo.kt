package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.FormTemplate

interface FormsRepo {
    suspend fun getForm(customerId: String, formId: Int,isMandatoryInspection:Boolean): FormTemplate
    suspend fun getFreeForm(formId: Int,isMandatoryInspection:Boolean): FormTemplate
}