package com.trimble.ttm.routemanifest.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.ARRIVAL_REASON
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ARRIVAL_REASON_COLLECTION
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.routemanifest.model.ArrivalReason
import com.trimble.ttm.routemanifest.utils.VEHICLES_COLLECTION
import com.trimble.ttm.routemanifest.viewmodel.STOPS
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class ArrivalReasonEventRepoImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val appModuleCommunicator: AppModuleCommunicator
) : ArrivalReasonEventRepo {

    override suspend fun getCurrentStopArrivalReason(documentPath: String): ArrivalReason {
        val docRef = firestore.collection(ARRIVAL_REASON_COLLECTION).document(documentPath)
        try {
            val stopDocumentSnapshot = docRef.get().await()
            if (stopDocumentSnapshot.exists().not()) {
                Log.w(ARRIVAL_REASON, "No data available for path: $documentPath")
                return ArrivalReason()
            }
            stopDocumentSnapshot.data?.toMutableMap()?.let {
                return processArrivalReason(it)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(ARRIVAL_REASON, "Unexpected error while retrieving arrivalReason for path $documentPath", e, "ExceptionDetails" to e)
        }
        return ArrivalReason()
    }

    private fun processArrivalReason(data: MutableMap<String, Any>): ArrivalReason {
        val dataProcessed = Gson().fromJson(
            Gson().toJson(data),
            ArrivalReason::class.java
        )
        return dataProcessed
    }

    override suspend fun getArrivalReasonCollectionPath(stopId: Int): String {
        return "${
            appModuleCommunicator.doGetCid()
        }/$VEHICLES_COLLECTION/${
            appModuleCommunicator.doGetTruckNumber()
        }/$DISPATCH_COLLECTION/${
            appModuleCommunicator.getCurrentWorkFlowId("getArrivalReasonCollectionPath")
        }/$STOPS/$stopId"
    }

    override fun setArrivalReasonforStop(
        path: String,
        valueMap: HashMap<String, Any>
    ) {
        try {
            firestore.collection(ARRIVAL_REASON_COLLECTION).document(path)
                .set(valueMap, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(ARRIVAL_REASON, "Success: firestore write in path $path for new arrival $valueMap")
                }
                .addOnFailureListener {
                    Log.e(
                        ARRIVAL_REASON,
                        "Error: firestore write for new arrival in path $path",
                        throwable = null
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                ARRIVAL_REASON, "Exception on updating NEW stopDetail $valueMap at: $path",
                throwable = null,
                "stack" to e
            )
        }
    }

    override fun updateArrivalReasonforStop(
        path: String,
        valueMap: HashMap<String, Any>
    ) {
        try {
            firestore.collection(ARRIVAL_REASON_COLLECTION).document(path)
                .update(valueMap)
                .addOnSuccessListener {
                    Log.d(
                        ARRIVAL_REASON,
                        "Success: firestore update in path $path  for existing arrival $valueMap"
                    )
                }
                .addOnFailureListener {
                    Log.d(
                        ARRIVAL_REASON,
                        "Error: firestore update in path $path  for existing arrival $valueMap"
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(
                ARRIVAL_REASON, "Exception on updating EXISTING stopDetail $valueMap at: $path",
                throwable = null,
                "stack" to e
            )
        }
    }
}