package com.trimble.ttm.commons.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.trimble.ttm.commons.logger.FORM_DEF_VALUES
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.ACTIONS
import com.trimble.ttm.commons.utils.DISPATCHER_FORM_VALUES
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.STOPS
import com.trimble.ttm.commons.utils.ext.getFromCache
import com.trimble.ttm.commons.utils.ext.getFromServer
import com.trimble.ttm.commons.utils.ext.isCacheEmpty
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class DispatcherFormValuesRepoImpl(private val fireStore: FirebaseFirestore = FirebaseFirestore.getInstance()) : DispatcherFormValuesRepo {
    @Suppress("UNCHECKED_CAST")
    override suspend fun getDispatcherFormValues(
        customerId: String,
        vehicleId: String,
        dispatchId: String,
        stopId: String,
        actionId: String
    ): HashMap<String, ArrayList<String>> {
        try {
            val documentReference =
                fireStore.collection(DISPATCH_COLLECTION)
                    .document(customerId).collection(vehicleId)
                    .document(dispatchId).collection(STOPS)
                    .document(stopId).collection(ACTIONS)
                    .document(actionId)
            val actionDoc = if (documentReference.isCacheEmpty()) {
                Log.d(
                    FORM_DEF_VALUES,
                    "data not found in cache getFormDefaultValues(path: ${documentReference.path})"
                )
                    documentReference.getFromServer().await()
                }else {
                    documentReference.getFromCache().await()
                }
                actionDoc.data?.let {
                    return try {
                        Log.d(FORM_DEF_VALUES, "data from server getFormDefaultValues $actionDoc")
                            if (it.containsKey(DISPATCHER_FORM_VALUES)){
                                it[DISPATCHER_FORM_VALUES] as HashMap<String, ArrayList<String>>
                            }else{
                                HashMap()
                            }
                    } catch (e: TypeCastException) {
                        Log.e(
                            FORM_DEF_VALUES,
                            "TypeCastException in getFormDefaultValues(path: ${documentReference.path})"
                        )
                        HashMap()
                    }
                } ?: kotlin.run {
                    Log.e(
                        FORM_DEF_VALUES,
                        "data not found in server getFormDefaultValues(path: ${documentReference.path})"
                    )
                    return HashMap()
                }
        }catch (firestoreException: FirebaseFirestoreException) {
            Log.e(FORM_DEF_VALUES, "firestoreException in getFormDefaultValues", firestoreException)
        }
        catch (e: CancellationException) {
            //Ignored
        } catch (e: Exception) {
            Log.e(FORM_DEF_VALUES, "exception in getFormDefaultValues", e)
        }
        return HashMap()
    }

}