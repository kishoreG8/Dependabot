package com.trimble.ttm.formlibrary.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.EDVIRInspection
import com.trimble.ttm.formlibrary.model.EDVIRPayload
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.EDVIR_COLLECTION_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_ENABLED_SETTINGS_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.EDVIR_INVALID_INT_VALUE
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSES
import com.trimble.ttm.formlibrary.utils.INVALID_FORM_CLASS
import com.trimble.ttm.formlibrary.utils.MOBILE_ORIGINATED_DOCUMENT_ID
import com.trimble.ttm.formlibrary.utils.PAYLOAD
import com.trimble.ttm.formlibrary.utils.ext.dataSource
import com.trimble.ttm.formlibrary.utils.ext.toFlow
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class EDVIRInspectionsRepoImpl : EDVIRInspectionsRepo {
    private val tag = "InspectionsRepo"

    private val inspectionListFlowPair = getCallbackFlow<ArrayList<EDVIRInspection>>()

    override suspend fun getEDVIREnabledSetting(
        customerId: String,
        dsn: String
    ): EDVIRPayload {
        val documentReference = FirebaseFirestore.getInstance()
            .collection(EDVIR_COLLECTION_ID)
            .document(customerId)
            .collection(dsn)
            .document(EDVIR_ENABLED_SETTINGS_DOCUMENT_ID)
        return try {
            val documentSnapshot = documentReference.get().await()
            if (documentSnapshot.exists()) {
                Log.i(
                    tag,
                    "Enabled settings document fetched from ${documentSnapshot?.dataSource()}",
                    throwable = null,
                    "path" to documentReference.path
                )
                Gson().fromJson(
                    Gson().toJson(documentSnapshot[PAYLOAD]),
                    EDVIRPayload::class.java
                )
            } else {
                // If no document available in firestore, assume the eDVIR settings is always on
                Log.d(
                    tag, "Enabled settings document not available", null,
                    "path" to documentReference.path
                )
                EDVIRPayload(intValue = 1)
            }
        } catch (e: Exception) {
            Log.e(
                tag, "Error fetching Enabled settings document", e,
                "path" to documentReference.path
            )
            EDVIRPayload(intValue = EDVIR_INVALID_INT_VALUE)
        }
    }

    override suspend fun getEDVIRInspectionSetting(
        customerId: String,
        dsn: String,
        inspectionDocumentId: String
    ): EDVIRPayload {
        val documentReference = FirebaseFirestore.getInstance()
            .collection(EDVIR_COLLECTION_ID)
            .document(customerId)
            .collection(dsn)
            .document(inspectionDocumentId)
        return try {
            val documentSnapshot = documentReference.get().await()
            if (documentSnapshot.exists()) {
                Log.i(
                    tag,
                    "$inspectionDocumentId settings document fetched from ${documentSnapshot?.dataSource()}",
                    throwable = null,
                    "path" to documentReference.path
                )
                Gson().fromJson(
                    Gson().toJson(documentSnapshot[PAYLOAD]),
                    EDVIRPayload::class.java
                )
            } else {
                Log.d(
                    tag, "$inspectionDocumentId settings document not available", null,
                    "path" to documentReference.path
                )
                EDVIRPayload(
                    intValue = EDVIR_INVALID_INT_VALUE,
                    formClass = INVALID_FORM_CLASS
                )
            }
        } catch (e: Exception) {
            Log.e(
                tag, "Error retrieving $inspectionDocumentId settings document", e,
                "path" to documentReference.path
            )
            EDVIRPayload(
                intValue = EDVIR_INVALID_INT_VALUE,
                formClass = INVALID_FORM_CLASS
            )
        }
    }

    override suspend fun listenToInspectionHistory(
        customerId: String,
        dsn: String,
        thresholdTimeInMillis: Long
    ) {
        FirebaseFirestore.getInstance().collection(EDVIR_COLLECTION_ID)
            .document(customerId).collection(dsn)
            .document(MOBILE_ORIGINATED_DOCUMENT_ID)
            .collection(FORM_RESPONSES)
            .whereGreaterThanOrEqualTo(CREATED_AT, thresholdTimeInMillis)
            .toFlow()
            .safeCollect("$tag Inspection history") {
                when (it) {
                    is QuerySnapshot -> {
                        val eDVIRInspectionList = ArrayList<EDVIRInspection>()
                        it.documents.forEach { doc ->
                            val eDVIRInspection = Gson().fromJson(
                                Gson().toJson(doc.data),
                                EDVIRInspection::class.java
                            )
                            eDVIRInspection?.let { it ->
                                it.createdAt = doc.id
                                eDVIRInspectionList.add(it)
                            }
                        }
                        inspectionListFlowPair.first.notify(eDVIRInspectionList)
                    }
                    else -> {
                        Log.d(tag, (it as java.lang.Exception).message)
                    }
                }
            }
    }

    override fun getInspectionHistoryAsFlow(): Flow<List<EDVIRInspection>> =
        inspectionListFlowPair.second

    override suspend fun listenToEDVIRSetting(
        customerId: String,
        dsn: String
    ): Flow<EDVIRPayload> {
        return callbackFlow {
            val document = FirebaseFirestore.getInstance().collection(EDVIR_COLLECTION_ID)
                .document(customerId).collection(dsn)
                .document(EDVIR_ENABLED_SETTINGS_DOCUMENT_ID)
            val subscription = document.addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(tag, "Error retrieving Enabled settings document", e)
                    this.trySend(EDVIRPayload(intValue = EDVIR_INVALID_INT_VALUE)).isSuccess
                    return@addSnapshotListener
                }

                try {
                    if (snapshot != null && snapshot.exists()) {
                        Log.i(
                            tag,
                            "Enabled settings document fetched from ${snapshot.dataSource()}"
                        )
                        this.trySend(
                            Gson().fromJson(
                                Gson().toJson(snapshot[PAYLOAD]),
                                EDVIRPayload::class.java
                            )
                        ).isSuccess
                    } else {
                        Log.d(tag, "Enabled settings document not available")
                        this.trySend(EDVIRPayload(intValue = 1)).isSuccess
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error processing Enabled settings document", e)
                    this.trySend(EDVIRPayload(intValue = EDVIR_INVALID_INT_VALUE)).isSuccess
                }
            }

            awaitClose {
                subscription.remove()
            }
        }
    }

    override suspend fun isEDVIRSettingsExist(
        customerId: String, dsn: String
    ): Boolean {
        return getEDVIREnabledSetting(customerId, dsn).dsn > 0
    }

}