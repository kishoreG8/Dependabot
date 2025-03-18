package com.trimble.ttm.formlibrary.repo.cawb

import com.trimble.ttm.formlibrary.model.CustomWorkFlowFormResponse

interface CustomWorkFlowFormResponseSaveRepo {
   suspend fun addFormResponse(customWorkFlowFormResponse: CustomWorkFlowFormResponse)
}