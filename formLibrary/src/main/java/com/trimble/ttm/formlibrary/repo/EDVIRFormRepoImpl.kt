package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SnapshotListenOptions
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormResponse
import com.trimble.ttm.commons.model.UIFormResponse
import com.trimble.ttm.commons.utils.FORM_DATA_KEY
import com.trimble.ttm.commons.utils.IS_SYNC_DATA_TO_QUEUE_KEY
import com.trimble.ttm.commons.utils.ext.doContinuation
import com.trimble.ttm.formlibrary.dataSource.IFormsDataSource
import com.trimble.ttm.formlibrary.model.EDVIRFormResponseRepoData
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.DRIVER_NAME_KEY
import com.trimble.ttm.formlibrary.utils.EDVIR_COLLECTION_ID
import com.trimble.ttm.formlibrary.utils.FORM_CLASS_KEY
import com.trimble.ttm.formlibrary.utils.FORM_ID_KEY
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSES
import com.trimble.ttm.formlibrary.utils.INSPECTION_TYPE
import com.trimble.ttm.formlibrary.utils.MOBILE_ORIGINATED_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.isNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

class EDVIRFormRepoImpl(fireStoreFormsDataSource : IFormsDataSource, cloudFUnctionFormsDataSource: IFormsDataSource) : FormsRepoImpl(fireStoreFormsDataSource, cloudFUnctionFormsDataSource), EDVIRFormRepo {
    private val tag = "EDVIRFormRepoImpl"
    private val gson = Gson()

    override suspend fun saveEDVIRFormResponse(
        saveEDVIRFormResponseData: EDVIRFormResponseRepoData
    ) = suspendCancellableCoroutine { continuation ->
        HashMap<String, Any>().apply {
            this[INSPECTION_TYPE] = saveEDVIRFormResponseData.inspectionType
            this[FORM_DATA_KEY] = saveEDVIRFormResponseData.formResponse
            this[FORM_ID_KEY] = saveEDVIRFormResponseData.formId
            this[FORM_CLASS_KEY] = saveEDVIRFormResponseData.formClass
            this[DRIVER_NAME_KEY] = saveEDVIRFormResponseData.driverName
            this[CREATED_AT] = saveEDVIRFormResponseData.currentTimeInMillisInUTC
            this[IS_SYNC_DATA_TO_QUEUE_KEY] = saveEDVIRFormResponseData.isSyncToQueue
        }.also {
            try {
                var listenerRegistration:ListenerRegistration?=null
                val options = SnapshotListenOptions.Builder()
                    .setMetadataChanges(MetadataChanges.INCLUDE)
                    .setSource(ListenSource.CACHE)
                    .build()
                val documentRef = FirebaseFirestore.getInstance()
                    .collection(EDVIR_COLLECTION_ID)
                    .document(saveEDVIRFormResponseData.customerId)
                    .collection(saveEDVIRFormResponseData.dsn)
                    .document(MOBILE_ORIGINATED_DOCUMENT_ID)
                    .collection(FORM_RESPONSES)
                    .document(saveEDVIRFormResponseData.curTimeUtcFormattedStr)
                documentRef.set(it)
                listenerRegistration = documentRef.addSnapshotListener(options){
                    snapshot, e ->
                    if (e != null) {
                        Log.e(
                            tag,
                            "Listen failed.",
                            e
                        )
                        doContinuation(continuation, false,listenerRegistration)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        Log.d(
                            tag,
                            "inspection saved ${snapshot.reference.path}"
                        )
                        doContinuation(continuation, true,listenerRegistration)
                    } else {
                        Log.e(
                            tag,
                            "inspection not saved ${documentRef.path}"
                        )
                       doContinuation(continuation, false,listenerRegistration)
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception while writing EDVIR form response document",
                    e
                )
                doContinuation(continuation, false)
            }
        }
    }


    override suspend fun getEDVIRFormDataResponse(
        customerId: String,
        dsn: String,
        createdAt: String
    ): UIFormResponse {
        try {
            val documentReference = FirebaseFirestore.getInstance()
                .collection(EDVIR_COLLECTION_ID)
                .document(customerId)
                .collection(dsn)
                .document(MOBILE_ORIGINATED_DOCUMENT_ID)
                .collection(FORM_RESPONSES)
                .document(createdAt)
            return if (documentReference.isCacheEmpty()) {
                Log.d(
                    tag,
                    "data not found in cache getEDVIRFormDataResponse(path: ${documentReference.path})"
                )
                val formDataDocument = documentReference.getFromServer().await()
                getEDVIRUiFormResponse(formDataDocument, true)
            } else {
                val formDataDocument = documentReference.getFromCache().await()
                getEDVIRUiFormResponse(formDataDocument, false)
            }
        } catch (e: Exception) {
            Log.e(tag, "exception in getEDVIRFormDataResponse:", e)
        }
        return UIFormResponse()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getEDVIRUiFormResponse(
        formDataDocument: DocumentSnapshot,
        isCacheEmpty: Boolean
    ): UIFormResponse {
        if (isCacheEmpty) {
            if (formDataDocument.data.isNull()) {
                Log.e(
                    tag,
                    "data not found in server getEDVIRFormDataResponse(path: ${formDataDocument.reference.path})"
                )
                return UIFormResponse()
            }

        } else {
            if (formDataDocument.data.isNull()) {
                Log.e(
                    tag,
                    "data not found in cache getEDVIRFormDataResponse(path: ${formDataDocument.reference.path})"
                )
                return UIFormResponse()
            }
            Log.d(
                tag,
                "data from cache getEDVIRFormDataResponse \nForm Data: ${formDataDocument.data}"
            )
        }
        formDataDocument.data?.let {
            return if (it.isNotEmpty()) {
                val isSyncDataToQueue: Boolean = it["isSyncDataToQueue"] as Boolean
                val formDataMap: HashMap<String, Any> =
                    it["formData"] as HashMap<String, Any>
                val formResponseFromServer: FormResponse =
                    gson.fromJson(gson.toJson(formDataMap), FormResponse::class.java)
                UIFormResponse(
                    isSyncDataToQueue,
                    formResponseFromServer
                )
            } else {
                UIFormResponse()
            }
        }
        return UIFormResponse()
    }

}