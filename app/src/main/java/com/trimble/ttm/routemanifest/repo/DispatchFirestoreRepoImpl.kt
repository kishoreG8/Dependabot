package com.trimble.ttm.routemanifest.repo


import androidx.annotation.VisibleForTesting
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.trimble.ttm.commons.logger.DISPATCH_BLOB
import com.trimble.ttm.commons.logger.LISTEN_ALL_DISPATCHES
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_CACHING
import com.trimble.ttm.commons.logger.TRIP_IS_READY
import com.trimble.ttm.commons.logger.TRIP_LIST
import com.trimble.ttm.commons.logger.TRIP_PREVIEWING
import com.trimble.ttm.commons.logger.TRIP_START_CALL
import com.trimble.ttm.commons.logger.TRIP_STOP_COUNT
import com.trimble.ttm.commons.model.DispatchBlob
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.CID_JSON_KEY
import com.trimble.ttm.commons.utils.CREATE_DATE_JSON_KEY
import com.trimble.ttm.commons.utils.DISPATCH_COLLECTION
import com.trimble.ttm.commons.utils.DateUtil
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.VEHICLE_NUMBER_JSON_KEY
import com.trimble.ttm.commons.utils.VID_JSON_KEY
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.utils.ExternalNotifier
import com.trimble.ttm.formlibrary.utils.ext.getFromCache
import com.trimble.ttm.formlibrary.utils.ext.getFromServer
import com.trimble.ttm.formlibrary.utils.ext.isCacheEmpty
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import com.trimble.ttm.routemanifest.application.WorkflowApplication
import com.trimble.ttm.routemanifest.model.Action
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.utils.APPID_JSON_KEY
import com.trimble.ttm.routemanifest.utils.BLOB_JSON_KEY
import com.trimble.ttm.routemanifest.utils.DISPATCH_BLOB_COLLECTION
import com.trimble.ttm.routemanifest.utils.DISPATCH_QUERY_LIMIT
import com.trimble.ttm.routemanifest.utils.HOSTID_JSON_KEY
import com.trimble.ttm.routemanifest.utils.ISCOMPLETED
import com.trimble.ttm.routemanifest.utils.ISREADY
import com.trimble.ttm.routemanifest.utils.IS_ACTIVE_DISPATCH
import com.trimble.ttm.routemanifest.utils.IS_DELETED_STOP
import com.trimble.ttm.routemanifest.utils.IS_DISPATCH_DELETED
import com.trimble.ttm.routemanifest.utils.TRIP_CREATION_DATE
import com.trimble.ttm.routemanifest.utils.Utils.getTheFourteenDaysBeforeDate
import com.trimble.ttm.routemanifest.utils.VEHICLES_COLLECTION
import com.trimble.ttm.routemanifest.utils.ext.toDispatch
import com.trimble.ttm.routemanifest.viewmodel.ACTIONS
import com.trimble.ttm.routemanifest.viewmodel.PAYLOAD
import com.trimble.ttm.routemanifest.viewmodel.STOPS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TreeMap
import java.util.TreeSet

const val COMPLETED_TIME_KEY = "CompletedTime"
const val DEPARTED_TIME_KEY = "DepartedTime"
const val IS_MANUAL_ARRIVAL = "isManualArrival"
const val ARRIVAL_LATITUDE = "arrivalLatitude"
const val ARRIVAL_LONGITUDE = "arrivalLongitude"
const val STOP_DELETED_IDENTIFIER = 0L

class DispatchFirestoreRepoImpl(private val appModuleCommunicator: AppModuleCommunicator) :
    DispatchFirestoreRepo {
    private val tag = "DispatchFirestoreRepo"
    private val dispatchListenerListFlowPair =
        getCallbackFlow<Any>()
    private val scheduledDispatchSet = TreeSet<String>()
    private var dispatchListenerRegistration: ListenerRegistration? = null
    private var stopListenerRegistration: ListenerRegistration? = null
    private var actionListenerRegistration: HashMap<String, ListenerRegistration?> = hashMapOf()


    private fun Query.dispatchSnapshotFlow(): Flow<Any> =
        callbackFlow {
            dispatchListenerRegistration = addSnapshotListener { querySnapShot, error ->
                if (error != null) {
                    Log.e(
                        tag,
                        "error at adding dispatch live listener",
                        throwable = null,
                        "stack" to error.stackTraceToString()
                    )
                    trySend(error)
                    close()
                    return@addSnapshotListener
                }
                if (querySnapShot != null)
                    trySend(querySnapShot)
            }
            awaitClose {
                dispatchListenerRegistration?.remove()
            }
        }

    private fun CollectionReference.stopSnapshotFlow(): Flow<QuerySnapshot> =
        callbackFlow {
            val newStopListenerRegistration = addSnapshotListener() { querySnapShot, error ->
                if (error != null) {
                    Log.e(
                        tag,
                        "error at adding stop live listener",
                        throwable = null,
                        "stack" to error.stackTraceToString()
                    )
                    close()
                    return@addSnapshotListener
                }
                if (querySnapShot != null)
                    trySend(querySnapShot)
            }
            stopListenerRegistration = newStopListenerRegistration
            awaitClose {
                stopListenerRegistration?.remove()
                newStopListenerRegistration.remove()
            }
        }

    private fun CollectionReference.actionSnapshotFlow(
        dispatchId: String,
        stopId: String
    ): Flow<QuerySnapshot> =
        callbackFlow {
            actionListenerRegistration[dispatchId + stopId] =
                addSnapshotListener { querySnapShot, error ->
                    if (error != null) {
                        Log.e(
                            tag,
                            "error at adding action live listener",
                            throwable = null,
                            "stack" to error.stackTraceToString()
                        )
                        close()
                        return@addSnapshotListener
                    }
                    if (querySnapShot != null)
                        trySend(querySnapShot)
                }
            awaitClose {
                actionListenerRegistration[dispatchId + stopId]?.remove()
            }
        }

    override fun listenToStopActions(
        vehicleId: String,
        cid: String,
        dispatchId: String,
        stopId: String
    ): Flow<Set<Action>> = flow {
        val actions = TreeMap<Int, Action>()
        val actionCollectionReference =
            FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION).document(cid)
                .collection(vehicleId).document(dispatchId).collection(STOPS)
                .document(stopId).collection(ACTIONS)
        if (actionListenerRegistration.containsKey(dispatchId + stopId)) {
            actionListenerRegistration[dispatchId + stopId]?.remove()
        }
        actionListenerRegistration.remove(dispatchId + stopId)
        actionCollectionReference.actionSnapshotFlow(dispatchId, stopId)
            .catch { e ->
                Log.e(
                    tag,
                    "exception at listenToStopActions",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
            }
            .safeCollect("$tag Listen to stop actions") {
                it.documents.forEach { documentChange ->
                    documentChange.data?.run {
                        with(processAction(this)) {
                            if (isIncomingDataIsOfActiveDispatch(this.dispid).not()) {
                                return@safeCollect
                            }
                            actions[actionid] = this

                        }
                    }
                }
                emit(actions.values.toSet())
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun processAction(docData: MutableMap<String, Any>): Action =
        Gson().fromJson(
            Gson().toJson(docData[PAYLOAD]),
            Action::class.java
        ) ?: Action()

    // In Listener implementation, on immediate trip completion new trip's stop actions are overridden by old trip's stop actions
    // To avoid invalid caching of stop data checking incoming data is active dispatch and returning
    suspend fun isIncomingDataIsOfActiveDispatch(dispatchId: String): Boolean {
        val activeDispatchId = appModuleCommunicator.getCurrentWorkFlowId("compareActiveDispatchId")
        return !(activeDispatchId.isNotEmpty() && activeDispatchId != dispatchId)
    }

    override suspend fun isDispatchCompleted(dispatchId: String, cid: String, vehicleId: String): Boolean {
        val dispatchDocumentReference =
            getDispatchCollectionPath(cid, vehicleId).document(dispatchId)
        val dispatchDocumentSnapshot = if (dispatchDocumentReference.isCacheEmpty()) {
            dispatchDocumentReference.getFromServer().await()
        } else {
            dispatchDocumentReference.getFromCache().await()
        }
        return dispatchDocumentSnapshot.getBoolean(ISCOMPLETED) ?: false
    }


    override suspend fun getDispatchPayload(
        caller: String,
        cid: String,
        vehicleId: String,
        dispatchId: String,
        isForceFetchedFromServer: Boolean
    ): Dispatch {
        val documentReference = getDispatchCollectionPath(cid, vehicleId).document(dispatchId)
        return try {
            val dispatchDocument: DocumentSnapshot
            val dispatchData: Dispatch
            if (WorkflowApplication.getLastValueOfInternetCheck() && isForceFetchedFromServer) {
                Log.d(
                    caller,
                    "data fetch from server getDispatchPayload - ${documentReference.path}"
                )
                dispatchDocument = documentReference.getFromServer().await()
                dispatchData = dispatchDocument.toDispatch() ?: Dispatch()
            } else {
                Log.d(
                    caller,
                    "data fetch from cache getDispatchPayload - ${documentReference.path}"
                )
                dispatchDocument = documentReference.getFromCache().await()
                dispatchData = dispatchDocument.toDispatch() ?: Dispatch()
            }
            Log.d(
                caller,
                "fetched dispatch document dispatchId: ${dispatchData.dispid} disable End Trip: ${dispatchData.tripStartDisableManual}"
            )
            return dispatchData
        } catch (cancellationException: CancellationException) {
            Dispatch()
        } catch (e: Exception) {
            Log.e(
                caller,
                "exception in getDispatchPayload in ${documentReference.path}",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
            Dispatch()
        }
    }

    override fun getDispatchCollectionPath(
        cid: String,
        vehicleId: String
    ): CollectionReference {
        return FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION)
            .document(cid).collection(vehicleId)
    }

    override suspend fun getStopCountOfDispatch(cid: String,truckNum: String,dispatchId: String): Int? {
        Log.d(TRIP_STOP_COUNT,"getStopCountCallId $dispatchId")
        var stopCount = 0
        val collectionReference =
            FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION).document(cid)
                .collection(truckNum).document(dispatchId).collection(STOPS)
        try {
            val querySnapshot =
                if (collectionReference.isCacheEmpty()) collectionReference.getFromServer().await()
                else collectionReference.getFromCache().await()
            querySnapshot.documents.forEach {
                if (it.get(IS_DELETED_STOP) == STOP_DELETED_IDENTIFIER) {
                    ++stopCount
                }
            }
            return stopCount
        } catch (e: CancellationException) {
            Log.e(
                TRIP_STOP_COUNT,
                "cancel exception in getStopsCount in ${collectionReference.path}",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
            return null
        } catch (e: Exception) {
            Log.e(
                TRIP_STOP_COUNT,
                "exception in getStopsCount in ${collectionReference.path}",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
            return null
        }
    }


    override suspend fun listenDispatchesForTruck(
        vehicleId: String,
        cid: String
    ) {
        val dispatches = TreeMap<String, Dispatch>()
        getDispatchListQuery(cid,vehicleId).dispatchSnapshotFlow()
            .catch { e ->
                Log.e(
                    "$TRIP_LIST $LISTEN_ALL_DISPATCHES",
                    "exception at getDispatchesForTruck",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
            }.collect {
                processAndFilterDispatches(it, dispatches, dispatchListenerListFlowPair, "$TRIP_LIST $LISTEN_ALL_DISPATCHES")
            }
    }

    private fun filterIncompleteAndReadyTripsToDisplay(
        it: QuerySnapshot,
        tempDispatchMap: TreeMap<String, Dispatch>
    ) {
        it.filter { documentSnapshot ->
            ((documentSnapshot.getBoolean(ISCOMPLETED) == null || documentSnapshot.getBoolean(
                ISCOMPLETED
            ) == false) and (documentSnapshot.getBoolean(ISREADY) == true)) and checkForDispatchDeletion(
                documentSnapshot
            )
        }.forEach { doc ->
            doc.data.run {
                val dispatch = Gson().fromJson(
                    Gson().toJson(this[PAYLOAD]),
                    Dispatch::class.java
                )
                // This flag is used to display active dispatch chip in dispatch list screen
                dispatch.isActive = this[IS_ACTIVE_DISPATCH] as? Boolean ?: false
                tempDispatchMap[dispatch.dispid] = dispatch
            }
        }
    }

    private fun checkForDispatchDeletion(querySnapshot: QueryDocumentSnapshot): Boolean {
        return if (querySnapshot.contains(IS_DISPATCH_DELETED)) {
            querySnapshot.getBoolean(IS_DISPATCH_DELETED) == false
        } else {
            true
        }
    }

    override fun listenDispatchListFlow() = dispatchListenerListFlowPair.second


    override fun getStopsForDispatch(
        vehicleId: String,
        cid: String,
        dispatchId: String
    ): Flow<Set<StopDetail>> = flow {
        val stops = TreeMap<Int, StopDetail>()
        val stopCollectionReference =
            FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION).document(cid)
                .collection(
                    vehicleId
                ).document(dispatchId).collection(STOPS)
        stopCollectionReference.stopSnapshotFlow()
            .safeCollect("$tag Get stops for dispatch") { querySnapshot ->
                val tempStops = TreeMap<Int, StopDetail>()
                querySnapshot.documentChanges.forEach { documentChange ->
                    when (documentChange.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED ->
                            with(processStopDetail(documentChange.document.data)) {
                                tempStops[stopid] = this
                            }
                        else -> { /*Ignored*/
                        }
                    }
                }
                stops.clear()
                stops.putAll(tempStops)
                tempStops.clear()
                emit(stops.values.toSet())
            }
    }

    override suspend fun getAllStopsForDispatchIncludingDeletedStops(
        vehicleId: String,
        cid: String,
        dispatchId: String
    ): List<StopDetail> {
        val stops = TreeMap<Int, StopDetail>()
        try {
            val stopCollectionReference =
                FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION).document(cid)
                    .collection(
                        vehicleId
                    ).document(dispatchId).collection(STOPS)
            val stopQuerySnapshot = stopCollectionReference.get().await()
            val tempStops = TreeMap<Int, StopDetail>()
            stopQuerySnapshot.documents.forEach { document ->
                document.data?.toMutableMap()?.also { stopMutableMap ->
                    with(processStopDetail(stopMutableMap)) {
                        if (isIncomingDataIsOfActiveDispatch(this.dispid).not()) {
                            return@also
                        }
                        tempStops[stopid] = this
                    }
                }
            }
            stops.clear()
            stops.putAll(tempStops)
            tempStops.clear()
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("Invalid document reference. Document references must have an even number", ignoreCase = true) == true) {
                Log.i(tag, "exception in getAllStopsForDispatchIncludingDeletedStops. ${e.stackTraceToString()}")
                return emptyList()
            }
        } catch (e: CancellationException) {
            //Ignored
        } catch (e: Exception) {
            Log.e(
                tag,
                "exception in getAllStopsForDispatchIncludingDeletedStops ${e.stackTraceToString()}",
            )
        }
        return stops.values.toList()
    }


    override suspend fun getStopsFromFirestore(
        caller:String,
        vehicleId: String,
        cid: String,
        dispatchId: String,
        isForceFetchedFromServer: Boolean
    ): List<StopDetail> {
        val stops = TreeMap<Int, StopDetail>()
        try {
            val stopCollectionReference = getDispatchCollectionPath(cid, vehicleId).document(dispatchId).collection(STOPS)
            var stopsQuerySnapshot: QuerySnapshot?
            if(isForceFetchedFromServer) {
                stopsQuerySnapshot = stopCollectionReference.getFromServer().await()
            } else {
                stopsQuerySnapshot = stopCollectionReference.getFromCache().await()
                if (stopsQuerySnapshot.isEmpty){
                    stopsQuerySnapshot = stopCollectionReference.getFromServer().await()
                }
            }
            val tempStops = TreeMap<Int, StopDetail>()
            stopsQuerySnapshot.documents.forEach { document ->
                document.data?.toMutableMap()?.also { stopMutableMap ->
                    with(processStopDetail(stopMutableMap)) {
                        this.Actions.clear()
                        this.Actions.addAll(getActionsOfStop(dispatchId,this.stopid.toString(),caller))
                        tempStops[stopid] = this
                    }
                }
            }
            stops.clear()
            stops.putAll(tempStops)

            tempStops.clear()
            return stops.values.toList()
        } catch (cacheException: CancellationException) {
            //Ignore
            return listOf()
        } catch (e: Exception) {
            Log.e(caller, "getStopListFromCache${e.stackTraceToString()}")
            return listOf()
        }
    }

    override suspend fun getActionsOfStop(activeDispatchId: String, stopId: String, caller: String): List<Action> {
        val actions = TreeMap<Int, Action>()
        if (activeDispatchId.isEmpty()) {
            return actions.values.toList()
        }
        val tempActions = TreeMap<Int, Action>()
        val actionCollectionReference =
            FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION)
                .document(appModuleCommunicator.doGetCid())
                .collection(appModuleCommunicator.doGetTruckNumber())
                .document(activeDispatchId)
                .collection(STOPS).document(stopId).collection(ACTIONS)
        try {
            var actionQuerySnapshot = actionCollectionReference.getFromCache().await()
            if (actionQuerySnapshot.isEmpty) {
                actionQuerySnapshot = actionCollectionReference.getFromServer().await()
                if (actionQuerySnapshot.isEmpty) {
                    Log.w(tag, "ActionNAInCacheAndServer: D${activeDispatchId} S${stopId}",throwable = null,"caller" to caller)
                    return listOf()
                }
            }
            return processActionQuerySnapshots(
                actions,
                actionQuerySnapshot,
                tempActions,
                caller
            )
        } catch (cacheException: FirebaseFirestoreException) {
            try {
                val actionQuerySnapshot = actionCollectionReference.getFromServer().await()
                if (actionQuerySnapshot.isEmpty) {
                    Log.w(
                        tag,
                        "ActionNAInServer: D${activeDispatchId} S${stopId}} CE${cacheException.message}",throwable = null,"caller" to caller
                    )
                    return listOf()
                }
                return processActionQuerySnapshots(
                    actions,
                    actionQuerySnapshot,
                    tempActions,
                    caller
                )
            } catch (serverException: FirebaseFirestoreException) {
                Log.w(
                    tag,
                    "ActionNAInServer: D${activeDispatchId} S${stopId} SE${serverException.message}",throwable = null,"caller" to caller
                )
                return listOf()
            }
        }
    }

    private suspend fun processActionQuerySnapshots(
        actions: TreeMap<Int, Action>,
        actionQuerySnapshot: QuerySnapshot,
        tempActions: TreeMap<Int, Action>,
        caller: String
    ): List<Action> {
        actionQuerySnapshot.documents.forEach { document ->
            document.data?.toMutableMap()?.also { actionMutableMap ->
                with(processAction(actionMutableMap)) {
                    // if the caller is only from TripCacheUseCase, need to cache inactive trip's action data in tempActions
                    // so blocking the if statement as it is returning without caching
                    if ((caller != TRIP_CACHING && caller != TRIP_PREVIEWING)  && isIncomingDataIsOfActiveDispatch(dispid).not()) {
                        Log.i(tag, "ActionFromDifferentDispatch D${dispid}")
                        return@also
                    }
                    tempActions[actionid] = this
                }
            }
        }
        actions.clear()
        actions.putAll(tempActions)
        tempActions.clear()
        return actions.values.toList()
    }

    override suspend fun getStop(
        cid: String,
        truckNum: String,
        dispatchId: String,
        stopId: String
    ): StopDetail {
        val stopDocumentReference =
            FirebaseFirestore.getInstance().collection(DISPATCH_COLLECTION).document(cid)
                .collection(
                    truckNum
                ).document(dispatchId).collection(STOPS).document(stopId)
        try {
            var stopDocumentSnapshot = stopDocumentReference.getFromCache().await()
            if (stopDocumentSnapshot.exists().not()) {
                stopDocumentSnapshot = stopDocumentReference.getFromServer().await()
                if (stopDocumentSnapshot.exists().not()) {
                    Log.w(tag, "GeofenceEventStopNAInServer: D${dispatchId} S${stopId}")
                    return StopDetail()
                }
            }
            getFirestoreDocumentSource(stopDocumentSnapshot, dispatchId, stopId)
            stopDocumentSnapshot.data?.toMutableMap()?.let {
                return processStopDetail(it)
            }
        } catch (cacheException: FirebaseFirestoreException) {
            try {
                val stopDocumentSnapshot = stopDocumentReference.getFromServer().await()
                if (stopDocumentSnapshot.exists().not()) {
                    Log.w(
                        tag,
                        "GeofenceEventStopNAInServer: D${dispatchId} S${stopId} CE${cacheException.message}"
                    )
                    return StopDetail()
                }
                getFirestoreDocumentSource(stopDocumentSnapshot, dispatchId, stopId)
                stopDocumentSnapshot.data?.toMutableMap()?.let {
                    return processStopDetail(it)
                }
            } catch (serverException: FirebaseFirestoreException) {
                Log.w(
                    tag,
                    "GeofenceEventStopNull: D${dispatchId} S${stopId} SE${serverException.message}"
                )
                return StopDetail()
            }
        }
        Log.w(tag, "GeofenceEventStopNA: D$dispatchId S$stopId")
        return StopDetail()
    }

    private fun getFirestoreDocumentSource(
        documentSnapshot: DocumentSnapshot,
        dispatchId: String,
        stopId: String
    ) {
        val source =
            if (documentSnapshot.metadata.isFromCache) "cache" else "server"
        Log.i(tag, "GeofenceEventStopFrom: $source D${dispatchId} S${stopId}")
    }

    internal fun processStopDetail(docData: MutableMap<String, Any>): StopDetail =
        Gson().fromJson(
            Gson().toJson(docData[PAYLOAD]),
            StopDetail::class.java
        )?.let { stopDetail ->
            if (docData.containsKey(COMPLETED_TIME_KEY) && docData[COMPLETED_TIME_KEY].toString()
                    .isNotEmpty()
            ) stopDetail.completedTime = docData[COMPLETED_TIME_KEY].toString()
            if (docData.containsKey(DEPARTED_TIME_KEY) && docData[DEPARTED_TIME_KEY].toString()
                    .isNotEmpty()
            ) stopDetail.departedTime = docData[DEPARTED_TIME_KEY].toString()
            if(docData.containsKey(IS_MANUAL_ARRIVAL) && docData[IS_MANUAL_ARRIVAL] as Boolean){
                stopDetail.isManualArrival = true
            }
            if(docData.containsKey(ARRIVAL_LATITUDE) && docData[ARRIVAL_LATITUDE] as Double != 0.0){
                stopDetail.arrivalLatitude = docData[ARRIVAL_LATITUDE] as Double
            }
            if(docData.containsKey(ARRIVAL_LONGITUDE) && docData[ARRIVAL_LONGITUDE] as Double != 0.0){
                stopDetail.arrivalLongitude = docData[ARRIVAL_LONGITUDE] as Double
            }
            stopDetail
        } ?: StopDetail()

    override suspend fun scheduleDispatchToDisplay(
        dispatchId: String,
        cid: String,
        vehicleNumber: String,
        created: String
    ) {
        coroutineScope {
            HashMap<String, Any>().apply {
                this[ISREADY] = true
                if (dispatchId.isNotEmpty() && cid.isNotEmpty() && vehicleNumber.isNotEmpty()) {
                    val path= "$DISPATCH_COLLECTION/${cid.trim()}/${vehicleNumber.trim()}/${dispatchId.trim()}"
                    FirebaseFirestore.getInstance().document(path)
                        .set(this, SetOptions.merge())
                        .addOnSuccessListener {
                            scheduledDispatchSet.remove(dispatchId)
                            val isReadyTime =
                                DateUtil.getUTCFormattedDate(Calendar.getInstance().time)
                            Log.n(
                                TRIP_IS_READY,
                                "Trip is visible now",
                                throwable = null,
                                "CreatedTime" to created,
                                "TripReadyTime" to isReadyTime,
                                "TripReady" to dispatchId,
                                "TimeDifferenceInSeconds" to DateUtil.getDifferenceBetweenTwoDatesInSeconds(
                                    created,
                                    isReadyTime
                                )
                            )
                            Log.i(TRIP_IS_READY, "SuccessWritingIsReady $path")
                        }
                        .addOnFailureListener { e ->
                            Log.e(
                                TRIP_IS_READY,
                                "ErrorUpdatingIsReady $path",
                                throwable = null,
                                "stack" to e.stackTraceToString()
                            )
                        }
                }
            }
        }

    }

    override fun unRegisterFirestoreLiveListeners() {
        try {
            dispatchListenerRegistration?.remove()
            stopListenerRegistration?.remove()
            val mapIterator = actionListenerRegistration.iterator()
            while (mapIterator.hasNext()) {
                mapIterator.next().value?.let { listenerRegistration ->
                    listenerRegistration.remove()
                    mapIterator.remove()
                }
            }
            actionListenerRegistration.clear()
        } catch (e: Exception) {
            Log.w(tag, "exception at unRegisterFirestoreLiveListeners. ${e.stackTraceToString()}")
        }
    }

    override fun getAppModuleCommunicator(): AppModuleCommunicator = appModuleCommunicator

    override suspend fun getCurrentWorkFlowId(caller: String) = appModuleCommunicator.getCurrentWorkFlowId(caller)

    override suspend fun setCurrentWorkFlowId(currentWorkFlowId: String) = appModuleCommunicator.setCurrentWorkFlowId(currentWorkFlowId)

    override suspend fun setCurrentWorkFlowDispatchName(dispatchName: String) = appModuleCommunicator.setCurrentWorkFlowDispatchName(dispatchName)

    override suspend fun getDispatchBlobDataByBlobId(
        cid: String,
        vehicleId: String,
        blobId: String
    ): DispatchBlob {
        val blobDocumentReference =
            getDispatchBlobCollection().document(cid)
                .collection(VEHICLES_COLLECTION).document(blobId)
        try {
            var blobDocumentSnapshot = blobDocumentReference.getFromCache().await()
            if (blobDocumentSnapshot.exists().not()) {
                blobDocumentSnapshot = blobDocumentReference.getFromServer().await()
                if (blobDocumentSnapshot.exists().not()) {
                    Log.w(tag+DISPATCH_BLOB, "DispatchBlobNAInServer: CID:$cid Vehicle:$vehicleId BlobOd:$blobId")
                    return DispatchBlob()
                }
            }
            blobDocumentSnapshot.data?.toMutableMap()?.let {
                return processDispatchBlob(it, blobDocumentSnapshot.id)
            }
        } catch (cacheException: FirebaseFirestoreException) {
            try {
                val blobDocumentSnapshot = blobDocumentReference.getFromServer().await()
                if (blobDocumentSnapshot.exists().not()) {
                    Log.w(
                        tag+DISPATCH_BLOB,
                        "DispatchBlobNAInServer: CID:$cid Vehicle:$vehicleId BlobId:$blobId exception:${cacheException.message}"
                    )
                    return DispatchBlob()
                }
                blobDocumentSnapshot.data?.toMutableMap()?.let {
                    return processDispatchBlob(it, blobDocumentSnapshot.id)
                }
            } catch (serverException: FirebaseFirestoreException) {
                Log.w(
                    tag+DISPATCH_BLOB,
                    "DispatchBlobNAInServer: CID:$cid Vehicle:$vehicleId BlobId:$blobId exception:${serverException.message}"
                )
                return DispatchBlob()
            }
        }catch (e:Exception){
            Log.e(tag+DISPATCH_BLOB, "exception in getDispatchBlobData ${e.stackTraceToString()}")
        }
        Log.w(tag+DISPATCH_BLOB, "DispatchBlobNA: CID:$cid Vehicle:$vehicleId BlobId:$blobId")
        return DispatchBlob()
    }

    override suspend fun getAllDispatchBlobDataForVehicle(
        cid: String,
        vehicleId: String
    ): ArrayList<DispatchBlob> {
        val dispatchBlobList = ArrayList<DispatchBlob>()
        val blobCollectionReference =
            getDispatchBlobCollection().document(cid)
                .collection(VEHICLES_COLLECTION)
        val query = blobCollectionReference.whereEqualTo("vehicleNumber", vehicleId)
        try {
            val blobQuerySnapshot = query.getFromServer().await()
            blobQuerySnapshot.documents.forEach { document ->
                document.data?.toMutableMap()?.let {
                    dispatchBlobList.add(processDispatchBlob(it, document.id))
                }
            }
        }catch (e:Exception){
            Log.e(tag+DISPATCH_BLOB, "exception in getAllDispatchBlobData ${e.stackTraceToString()}")
        }
        return dispatchBlobList
    }

    override suspend fun deleteDispatchBlobByBlobId(
        cid: String,
        vehicleId: String,
        blobId: String
    ) {
        val blobDocumentReference =
            getDispatchBlobCollection().document(cid)
                .collection(VEHICLES_COLLECTION).document(blobId)
        try {
            blobDocumentReference.delete().await().let {
                Log.i(tag+DISPATCH_BLOB, "deleted DispatchBlobDocument: $cid $vehicleId $blobId")
            }
        }catch (e:Exception) {
            Log.e(tag+DISPATCH_BLOB, "exception in deleteDispatchBlobDocument ${e.stackTraceToString()}")
        }
    }

    override suspend fun deleteAllDispatchBlobDataForVehicle(
        cid: String,
        vehicleId: String,
        dispatchBlobIdList: List<String>
    ) {
        try {
            val batch = FirebaseFirestore.getInstance().batch()
            dispatchBlobIdList.forEach { blobId ->
                batch.delete(getDispatchBlobCollection().document(cid)
                    .collection(VEHICLES_COLLECTION).document(blobId))
            }
            Log.d(tag+DISPATCH_BLOB, "deleting all DispatchBlobDocuments for vehicle $vehicleId dispatchBlobIdList size: ${dispatchBlobIdList.size}")
            batch.commit().await()
        }catch (e:Exception) {
            Log.e(tag+DISPATCH_BLOB, "exception in deleteDispatchBlobDocuments ${e.stackTraceToString()}")
        }
    }

    internal fun processDispatchBlob(
        docData: MutableMap<String, Any>,
        documentId: String
    ): DispatchBlob {
        return try {
            val cidValue = docData[CID_JSON_KEY] ?: 0L
            val cid = if(cidValue is Long){
                cidValue
            }else{
                (docData[CID_JSON_KEY] as Int).toLong()
            }
            val vehicleNumber = docData[VEHICLE_NUMBER_JSON_KEY] ?: EMPTY_STRING
            val vidValue = docData[VID_JSON_KEY] ?: 0L
            val vid = if(vidValue is Long){
                vidValue
            }else{
                (docData[VID_JSON_KEY] as Int).toLong()
            }
            val blobMessage = docData[BLOB_JSON_KEY] ?: EMPTY_STRING
            val hostIdValue = docData[HOSTID_JSON_KEY] ?: 0L
            val hostId = if(hostIdValue is Long){
                hostIdValue
            }else{
                (docData[HOSTID_JSON_KEY] as Int).toLong()
            }
            val appIdValue = docData[APPID_JSON_KEY] ?: 0L
            val appId  = if(appIdValue is Long){
                appIdValue
            }else{
                (docData[APPID_JSON_KEY] as Int).toLong()
            }
            val createTimeStamp = docData[CREATE_DATE_JSON_KEY] ?: Timestamp.now()
            DispatchBlob(
                cid = cid,
                vehicleNumber = vehicleNumber as String,
                vid = vid,
                blobMessage = blobMessage as String,
                appId = appId,
                hostId = hostId,
                createDate = (createTimeStamp as Timestamp).toDate().toInstant()
            ).also {
                it.id = documentId
            }
        } catch (e: Exception) {
            Log.e(tag + DISPATCH_BLOB, "exception in processDispatchBlob ${e.stackTraceToString()}")
            return DispatchBlob()
        }
    }

    private fun getDispatchBlobCollection() = FirebaseFirestore.getInstance().collection(DISPATCH_BLOB_COLLECTION)

    override suspend fun setActiveDispatchFlagInFirestore(
        cid: String,
        truckNumber: String,
        activeDispatchId: String
    ) {
        HashMap<String, Any>().apply {
            this[IS_ACTIVE_DISPATCH] = true
            val path =
                "$DISPATCH_COLLECTION/${cid}/${truckNumber}/${activeDispatchId}"
            FirebaseFirestore.getInstance().document(path)
                .set(this, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TRIP_START_CALL, "IsActiveDispatch flag set to true for dispId: $activeDispatchId")
                }
                .addOnFailureListener { e ->
                    Log.e(
                        TRIP_START_CALL,
                        "ErrorUpdating IsActiveDispatch for dispId: $activeDispatchId",
                        throwable = null,
                        "stack" to e.stackTraceToString()
                    )
                }
        }
    }

    /**
     * Retrieves the list of dispatches for the specified vehicle ID and company ID using the provided Firestore query.
     * Filters and processes the retrieved dispatches, then updates the latest dispatch list.
     *
     * @param vehicleId The vehicle ID to filter the dispatches.
     * @param cid The company ID to filter the dispatches.
     */
    override suspend fun getDispatchesList(vehicleId: String, cid: String, caller: String) : Set<Dispatch> {
        val dispatches = TreeMap<String, Dispatch>()
        try {
            var dispatchQuerySnapshot = getDispatchListQuery(cid, vehicleId).getFromServer().await()
            if (dispatchQuerySnapshot.isEmpty) {
                dispatchQuerySnapshot = getDispatchListQuery(cid, vehicleId).getFromCache().await()
            }
            processAndFilterDispatches(dispatchQuerySnapshot = dispatchQuerySnapshot, dispatches = dispatches, caller =caller)
            }catch (exception:FirebaseFirestoreException) {
           Log.e(caller, "ExceptionGettingTrips exception ${exception.localizedMessage}")
        }
        return dispatches.values.toSet()
    }

    /**
     * Constructs a Firestore query to retrieve the latest dispatches based on the given company ID and vehicle ID.
     * The query filters dispatches from the last fourteen days and orders them by creation date in descending order.
     *
     * @param cid The company ID to filter the dispatches.
     * @param vehicleId The vehicle ID to filter the dispatches.
     * @return A Firestore query to retrieve the latest dispatches.
     */
    private fun getDispatchListQuery(cid: String, vehicleId: String) : Query =
        getDispatchCollectionPath(cid, vehicleId)
            .whereGreaterThanOrEqualTo(TRIP_CREATION_DATE, getTheFourteenDaysBeforeDate())
            .orderBy(TRIP_CREATION_DATE, Query.Direction.DESCENDING).limit(DISPATCH_QUERY_LIMIT)

    /**
     * Processes the dispatches by scheduling for delayed visibility if a feature flag is off,
     * and filters incomplete and ready-to-start trips to display.
     *
     * @param dispatchQuerySnapshot The snapshot of the query results.
     * @param dispatches The mutable map of dispatches to be updated.
     */
    private suspend fun processAndFilterDispatches(dispatchQuerySnapshot: Any, dispatches: TreeMap<String, Dispatch>,
                                           dispatchFlowPair: Pair<ExternalNotifier<Any>, Flow<Any>>? = null, caller :String ) {
        when (dispatchQuerySnapshot) {
            is QuerySnapshot -> {
                Log.i(caller, "QueriedTripsCount  ${dispatchQuerySnapshot.size()}")

                //Sends incomplete and ready to start trips to dispatchList screen
                filterIncompleteAndReadyTripsToDisplay(dispatchQuerySnapshot, dispatches)
            }
            else -> {
                Log.e(caller, "ExceptionGettingTrips$dispatchQuerySnapshot")
                dispatchFlowPair?.first?.notify(dispatchQuerySnapshot)
            }
        }
        Log.i(caller, "FilteredTripsCount ${dispatches.size}")
        dispatchFlowPair?.first?.notify(dispatches.values.toSet())
    }
}
