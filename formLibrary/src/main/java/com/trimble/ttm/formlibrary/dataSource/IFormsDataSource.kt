package com.trimble.ttm.formlibrary.dataSource

import com.trimble.ttm.commons.model.FormTemplate

interface IFormsDataSource {

    suspend fun getForm(customerId: String, formId: Int): FormTemplate

    suspend fun getFreeForm(formId: Int): FormTemplate
}