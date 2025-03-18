package com.trimble.ttm.routemanifest.repo

import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.model.FormTemplate
import com.trimble.ttm.commons.model.Recipients
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.routemanifest.model.Action
import kotlinx.coroutines.flow.Flow

interface FormsRepository {

    suspend fun formsSync(customerId: String, formDefList: ArrayList<FormDef>)
    suspend fun getForm(
        customerId: String,
        formID: Int
    ): FormTemplate

    suspend fun getLatestFormRecipients(
        customerId: Int,
        formID: Int
    ): ArrayList<Recipients>

    fun getFormsTemplateListFlow(): Flow<ArrayList<FormTemplate>>

    suspend fun getActionForStop(
        vehicleId: String,
        cid: String,
        dispatchId: String,
        stopId: String,
        actionId: String
    ): Action

    suspend fun getFreeForm(
        formId: Int
    ): FormTemplate

    suspend fun getSavedFormResponseFromDraftOrSent(queryPath: String,
                                                    shouldFetchFromServer: Boolean,
                                                    caller: String): Pair<UIFormResponse, Boolean>

    suspend fun getFromFormResponses(path: String,
                                     actionId: String,
                                     shouldFetchFromServer: Boolean,
                                     caller: String): Pair<UIFormResponse, Boolean>
}