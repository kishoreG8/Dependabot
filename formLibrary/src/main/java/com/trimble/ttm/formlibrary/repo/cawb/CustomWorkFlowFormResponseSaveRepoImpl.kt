package com.trimble.ttm.formlibrary.repo.cawb

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.EXPIRE_AT
import com.trimble.ttm.commons.utils.FORM_DATA_KEY
import com.trimble.ttm.formlibrary.model.CustomWorkFlowFormResponse
import com.trimble.ttm.formlibrary.utils.toSafeLong

const val COLLECTION_CUSTOM_WORKFLOW_FORM_RESPONSES = "CustomWorkFlowFormResponses"
private const val TAG = "CustomWorkFlowFormResponseSaveRepoImpl"

class CustomWorkFlowFormResponseSaveRepoImpl(private val appModuleCommunicator: AppModuleCommunicator) : CustomWorkFlowFormResponseSaveRepo {
    override suspend fun addFormResponse(
        customWorkFlowFormResponse: CustomWorkFlowFormResponse
    ) {
        HashMap<String, Any>().apply {
            includeTruckInfoInsideResponse(customWorkFlowFormResponse)
            this[FORM_DATA_KEY] = customWorkFlowFormResponse
            this[EXPIRE_AT] =
                Timestamp(DateUtil.getExpireAtDateTimeForTTLInUTC()) //TTL timestamp is to auto delete doc after 30 days of time.
            FirebaseFirestore.getInstance().collection(COLLECTION_CUSTOM_WORKFLOW_FORM_RESPONSES).add(this)
                .addOnSuccessListener {
                    Log.d(TAG, "CWFFormResponseWrittenWithID ${it.id}")
                }.addOnFailureListener {
                    Log.e(TAG, "CWFormResponseWriteError e${it.stackTraceToString()}")
                }
        }
    }

    private suspend fun includeTruckInfoInsideResponse(customWorkFlowFormResponse: CustomWorkFlowFormResponse) {
        customWorkFlowFormResponse.cid = appModuleCommunicator.doGetCid()
        customWorkFlowFormResponse.dsn = appModuleCommunicator.doGetObcId().toSafeLong()
        customWorkFlowFormResponse.truckNumber = appModuleCommunicator.doGetTruckNumber()
    }
}