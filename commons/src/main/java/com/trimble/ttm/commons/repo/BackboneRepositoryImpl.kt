package com.trimble.ttm.commons.repo

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.trimble.ttm.backbone.api.*
import com.trimble.ttm.backbone.api.data.*
import com.trimble.ttm.backbone.api.data.eld.*
import com.trimble.ttm.backbone.api.data.ttc.TTCAccountId
import com.trimble.ttm.backbone.api.data.ttc.TTCUser
import com.trimble.ttm.backbone.api.data.user.UserLogInStatus
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.*
import com.trimble.ttm.commons.utils.ext.asStateFlow
import com.trimble.ttm.mep.log.api.TrimbLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

@OptIn(DelicateCoroutinesApi::class)
class BackboneRepositoryImpl(
    val context: Context,
    private val dispatchProvider: DispatcherProvider) : BackboneRepository {

    private val backbone = BackboneFactory.backbone(context)
    private val publisher = BackboneFactory.publisher(context)
    private var trailerQueryStoppable: Stoppable? = null
    private var shipmentQueryStoppable: Stoppable? = null
    private var customerIdQueryStoppable: Stoppable? = null
    private var vehicleIdQueryStoppable: Stoppable? = null
    private var obcIdQueryStoppable: Stoppable? = null

    private val tag = "BackboneRepositoryImpl"

    private val backgroundHandler = Handler(
        with(HandlerThread("BackgroundHandler")) {
            start()
            looper
        }
    )

    override suspend fun getCustomerId(): String? =
        withContext(dispatchProvider.io()) {
            try {
                backbone.retrieveDataFor(CustomerId).fetch()?.data?.value
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting cid",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
                EMPTY_STRING
            }
        }


    override suspend fun getVehicleId(): String =
        withContext(dispatchProvider.io()) {
            try {
                backbone.retrieveDataFor(VehicleId).fetch()?.data?.value ?: EMPTY_STRING
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting truck number",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
                EMPTY_STRING
            }
        }

    override suspend fun getOBCId(): String? =
        withContext(dispatchProvider.io()) {
            try {
                backbone.retrieveDataFor(ObcId).fetch()?.data?.unitId
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting dsn",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
                EMPTY_STRING
            }
        }

    override fun getMultipleData(
        retriever: Backbone.Retriever<*>,
        vararg retrievers: Backbone.Retriever<*>
    ): MultipleEntryQuery.Result = backbone.retrieveDataFor(retriever, *retrievers).fetch()

    override suspend fun getUserEldStatus(): Map<String, UserEldStatus>? =
        withContext(dispatchProvider.io()) {
            try {
                backbone.retrieveDataFor(UserEldStatus).fetch()?.data
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting user eld status data",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
                hashMapOf()
            }
        }

    override fun monitorTrailersData(): Flow<List<String>> {
        return callbackFlow {
            trailerQueryStoppable = backbone.monitorChangesInDataFor(Trailers).handle(
                onEach = { result ->
                    result.data?.let {
                        if (this.isClosedForSend.not())
                            this.trySend(it).isSuccess
                    }
                }, onError = {
                    Log.e(
                        tag,
                        "Failed to get trailer data from the backbone ${it.message.toString()}", it
                    )
                    if (this.isClosedForSend.not()) {
                        val emptyStringList = listOf<String>()
                        this.trySend(emptyStringList).isSuccess
                    }
                })
            awaitClose {
                cancel()
                trailerQueryStoppable?.stop()
                trailerQueryStoppable = null
            }
        }
    }

    override fun monitorShipmentsData(): Flow<List<String>> {
        return callbackFlow {
            shipmentQueryStoppable =
                backbone.monitorChangesInDataFor(Shipments).handle(onEach = { result ->
                    result.data?.let {
                        if (this.isClosedForSend.not())
                            this.trySend(it).isSuccess
                    }
                }, onError = {
                    Log.e(
                        tag,
                        "Failed to get shipment data from the backbone ${it.message.toString()}", it
                    )
                    if (this.isClosedForSend.not()) {
                        val emptyStringList = listOf<String>()
                        this.trySend(emptyStringList).isSuccess
                    }
                })
            awaitClose {
                cancel()
                shipmentQueryStoppable?.stop()
                shipmentQueryStoppable = null
            }
        }
    }

    override fun monitorCustomerId(): Flow<String> {
        return callbackFlow {
            customerIdQueryStoppable?.stop()
            customerIdQueryStoppable =
                backbone.monitorChangesInDataFor(CustomerId).handle(onEach = { result ->
                    result.data?.let {
                        if (this.isClosedForSend.not())
                            this.trySend(it.value).isSuccess
                    }
                }, onError = {
                    Log.e(
                        tag,
                        "Failed to monitor customer id from the backbone ${it.message.toString()}",
                        it
                    )
                    if (this.isClosedForSend.not()) {
                        this.trySend(EMPTY_STRING).isSuccess
                    }
                })
            awaitClose {
                cancel()
                customerIdQueryStoppable?.stop()
                customerIdQueryStoppable = null
            }
        }
    }

    override fun monitorVehicleId(): Flow<String> {
        return callbackFlow {
            vehicleIdQueryStoppable?.stop()
            vehicleIdQueryStoppable =
                backbone.monitorChangesInDataFor(VehicleId).handle(onEach = { result ->
                    result.data?.let {
                        if (this.isClosedForSend.not())
                            this.trySend(it.value).isSuccess
                    }
                }, onError = {
                    Log.e(
                        tag,
                        "Failed to monitor vehicle id from the backbone ${it.message.toString()}",
                        it
                    )
                    if (this.isClosedForSend.not()) {
                        this.trySend(EMPTY_STRING).isSuccess
                    }
                })
            awaitClose {
                cancel()
                vehicleIdQueryStoppable?.stop()
                vehicleIdQueryStoppable = null
            }
        }
    }

    override fun monitorMotion(): StateFlow<Boolean?> = backbone.monitorChangesInDataFor(Motion).asStateFlow(Motion.key)

    private fun <T : Any> SingleEntryQuery<T>.asFlow(retrieverKey: String) = callbackFlow {
        val listener = this@asFlow.handle(
            onEach = { result ->
                this.trySend(result.data).isSuccess
            },
            onError = {
                TrimbLog.error(tag, "Exception while querying backbone", "key" to retrieverKey, "exception" to it.message.toString())
            },
            handler = backgroundHandler
        )
        awaitClose {
            TrimbLog.debug(tag, "Stopping listener", "key" to retrieverKey)
            listener.stop()
            cancel()
        }
    }

    private fun <T : Any> SingleEntryQuery<T>.asStateFlow(retrieverKey: String) = this.asFlow(retrieverKey).asStateFlow()

    override fun monitorOBCId(): Flow<String> {
        return callbackFlow {
            obcIdQueryStoppable?.stop()
            obcIdQueryStoppable =
                backbone.monitorChangesInDataFor(ObcId).handle(onEach = { result ->
                    result.data?.let {
                        if (this.isClosedForSend.not())
                            this.trySend(it.unitId).isSuccess
                    }
                }, onError = {
                    Log.e(
                        tag,
                        "Failed to monitor obc id from the backbone ${it.message.toString()}", it
                    )
                    if (this.isClosedForSend.not()) {
                        this.trySend(EMPTY_STRING).isSuccess
                    }
                })
            awaitClose {
                cancel()
                obcIdQueryStoppable?.stop()
                obcIdQueryStoppable = null
            }
        }
    }

    override suspend fun fetchEngineMotion(caller: String): Boolean? =
        withContext(dispatchProvider.io()) {
            try {
                val motion = backbone.retrieveDataFor(Motion).fetch()?.data
                motion
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting motion data. ${e.stackTraceToString()}"
                )
                null
            }
        }

    override fun getDrivers(): Set<String> {
        val driverSet = mutableSetOf<String>()
        backbone.retrieveDataFor(UserLogInStatus).fetch()?.data?.values?.onEach {
            if (it.loggedIn) driverSet.add(it.userId)
        }
        return driverSet
    }

    override suspend fun setWorkflowStartAction(dispatchId: Int): Unit =
        withContext(dispatchProvider.io()) {
            try {
                publisher.publish(WorkflowCurrentTrip(dispatchId, WorkflowTripAction.START))
                Log.i(
                    tag,
                    "Sending workflow start event",
                    throwable = null,
                    "Dispatch id" to backbone.retrieveDataFor(WorkflowCurrentTrip)
                        .fetch()?.data?.toString()
                )
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception setting WorkflowStartAction",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
            }
        }


    override suspend fun setWorkflowEndAction(dispatchId: Int): Unit = withContext(dispatchProvider.io()) {
        try {
            publisher.publish(WorkflowCurrentTrip(dispatchId, WorkflowTripAction.END))
            Log.i(
                tag,
                "Sending workflow trip end event",
                throwable = null,
                "Dispatch id" to backbone.retrieveDataFor(WorkflowCurrentTrip)
                    .fetch()?.data?.toString()
            )
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception setting WorkflowEndAction",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
        }

    }

    override suspend fun getCurrentWorkFlowId(): WorkflowCurrentTrip =
        withContext(dispatchProvider.io()) {
            try {
                backbone.retrieveDataFor(WorkflowCurrentTrip).fetch()?.data as WorkflowCurrentTrip
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting WorkflowCurrentTrip",
                    throwable = null,
                    "stack" to e.stackTraceToString()
                )
                WorkflowCurrentTrip(
                    0,
                    WorkflowTripAction.END
                ) //Deliberately setting incorrect value.
            }
        }

    override suspend fun getLoggedInUsersStatus(): List<UserLogInStatus> =
        withContext(dispatchProvider.io()) {
            try {
                val userLogInStatus = backbone.retrieveDataFor(UserLogInStatus).fetch()?.data
                if (userLogInStatus.isNullOrEmpty() || userLogInStatus.values.isEmpty()) {
                    Log.w(
                        tag,
                        "user login status data null"
                    )
                    return@withContext listOf()
                }
                userLogInStatus.values.filter { it.loggedIn }
            } catch (e: Exception) {
                Log.e(
                    tag,
                    "Exception getting user login status data ${e.stackTraceToString()}"
                )
                listOf()
            }
        }

    override suspend fun getCurrentUser(): String = withContext(dispatchProvider.io()) {
        try {
            val currentUserId = backbone.retrieveDataFor(CurrentUser).fetch()?.data
            if (currentUserId.isNullOrEmpty()) {
                Log.w(
                    tag,
                    "current user data null or empty"
                )
                return@withContext EMPTY_STRING
            }
            currentUserId
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception getting current user data ${e.stackTraceToString()}"
            )
            EMPTY_STRING
        }
    }
    override suspend fun getCurrentLocation(): Pair<Double, Double> =
        try {
            withContext(dispatchProvider.io()) {
                val data = backbone.retrieveDataFor(DisplayLocation).fetch()?.data
                return@withContext if (data != null) {
                    Pair(data.latitude, data.longitude)
                } else {
                    Log.e(tag, "null location from backbone,returning 0.0 lat and long")
                    Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                }
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Error fetching location from backbone,returning 0.0 lat and long",
                e
            )
            Pair(DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
        }

    override suspend fun getFuelLevel(): Int {
        val fuelToGallonConversion = 0.264172  //Litre to US Gallon conversion unit
        val pfmConversion =
            8 //PFM manipulates incoming gallon data by dividing it with 8, so here to cancel that output is multiplied by 8.
        return try {
            withContext(dispatchProvider.io()) {
                return@withContext backbone.retrieveDataFor(
                        TotalFuelConsumed
                    ).fetch()?.data?.liters?.let {
                        (it * fuelToGallonConversion * pfmConversion).toInt()
                    } ?: BACKBONE_ERROR_INT_VALUE
            }
        } catch (e: Exception) {
            BACKBONE_ERROR_INT_VALUE
        }
    }

    override suspend fun getOdometerReading(shouldFetchConfigOdometer: Boolean): Double =
        try {
            withContext(dispatchProvider.io()) {
                if (shouldFetchConfigOdometer) {
                    return@withContext backbone.retrieveDataFor(ConfigurableOdometerKm).fetch()?.data?.value
                        ?: BACKBONE_ERROR_VALUE
                } else {
                    return@withContext BackboneFactory.backbone(context)
                        .retrieveDataFor(EngineOdometerKm).fetch()?.data?.value
                        ?: BACKBONE_ERROR_VALUE
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error fetching Odometer from backbone,returning $BACKBONE_ERROR_VALUE", e)
            BACKBONE_ERROR_VALUE
        }

    override suspend fun getTTCAccountId(): String {
        return try {
            withContext(dispatchProvider.io()) {
                backbone.retrieveDataFor(TTCAccountId).fetch()?.data?.value ?: EMPTY_STRING
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception getting ttc account id",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
            EMPTY_STRING
        }
    }

    override suspend fun getTTCIdForCurrentUser(currentUser: String): String {
        return try {
            withContext(dispatchProvider.io()) {
                backbone.retrieveDataFor(TTCUser).fetch()?.data?.get(currentUser)?.ttcId ?: EMPTY_STRING
            }
        } catch (e: Exception) {
            Log.e(
                tag,
                "Exception getting ttc user id",
                throwable = null,
                "stack" to e.stackTraceToString()
            )
            EMPTY_STRING
        }
    }

}