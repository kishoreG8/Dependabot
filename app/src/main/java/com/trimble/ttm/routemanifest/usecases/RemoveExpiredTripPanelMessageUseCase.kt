package com.trimble.ttm.routemanifest.usecases


import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.routemanifest.customComparator.LauncherMessagePriorityComparator
import com.trimble.ttm.routemanifest.customComparator.LauncherMessageWithPriority
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ARE_STOPS_SEQUENCED_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.COMPLETED_STOP_ID_SET_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.UNCOMPLETED_DISPATCH_FORMS_STACK_KEY
import com.trimble.ttm.commons.model.Stop
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.routemanifest.model.getSortedStops
import com.trimble.ttm.routemanifest.utils.TRUE
import com.trimble.ttm.routemanifest.utils.Utils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.PriorityBlockingQueue


class RemoveExpiredTripPanelMessageUseCase(
    private val coroutineScope: CoroutineScope,
    private val tripPanelUsecase: TripPanelUseCase,
    private val dispatchStopsUseCase: DispatchStopsUseCase,
    private val dispatcherProvider: DispatcherProvider
) : KoinComponent {
    private val tag = "RemoveExpiredTripPanelMessageUC"

    fun removeMessageFromTripPanelQueue(
        stopList: CopyOnWriteArrayList<StopDetail>,
        dataStoreManager: DataStoreManager
    ) {
        coroutineScope.launch(dispatcherProvider.io() + SupervisorJob() + CoroutineName(tag)) {
            try {
                // Constructing a new collection here to avoid concurrent modification exception.
                // The received stopList will be manipulated concurrently from different coroutines
                Log.d(tag,"-----Inside RemoveExpiredTripPanelMessageUseCase removeMessageFromTripPanelQueue listLauncherMessageWithPriority:${tripPanelUsecase.listLauncherMessageWithPriority}")
                val stopListData = ArrayList(stopList)
                if (stopListData.isEmpty()) {
                    tripPanelUsecase.listLauncherMessageWithPriority.clear()
                    tripPanelUsecase.dismissTripPanelMessage(tripPanelUsecase.lastSentTripPanelMessage.messageId)
                    dataStoreManager.setValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
                    dataStoreManager.removeItem(DataStoreManager.CURRENT_STOP_KEY)
                    dataStoreManager.setValue(
                        DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                        EMPTY_STRING
                    )
                    return@launch
                }
                dataStoreManager.setValue(
                    DataStoreManager.STOPS_SERVICE_REFERENCE_KEY,
                    GsonBuilder().setPrettyPrinting().create()
                        .toJson(stopListData.filter { st -> st.completedTime == "" }.getSortedStops())
                        ?: EMPTY_STRING
                )

                val currentStopString =
                    dataStoreManager.getValue(DataStoreManager.CURRENT_STOP_KEY, EMPTY_STRING)
                //if currentStopString is empty fromJsonString will return null. so, the Stop is nullable here
                val currentStop: Stop? = Utils.fromJsonString<Stop>(currentStopString)

                val stopListStopIds = stopListData.map { it.stopid }

                if (currentStop?.stopId !in stopListStopIds) {
                    dataStoreManager.removeItem(DataStoreManager.CURRENT_STOP_KEY)
                }

                if (dataStoreManager.containsKey(ARE_STOPS_SEQUENCED_KEY) && dataStoreManager.getValue(ARE_STOPS_SEQUENCED_KEY, TRUE)) {
                    val cachedCompletedStopSet =
                        dataStoreManager.getValue(COMPLETED_STOP_ID_SET_KEY, emptySet())
                    stopListData.filter { stop ->
                        stop.completedTime.isEmpty() && cachedCompletedStopSet.contains(
                            stop.stopid.toString()
                        ).not()
                    }.also { unFinishedStops ->
                        unFinishedStops.getOrNull(0)?.run {
                            //Set current stop
                            dispatchStopsUseCase.putStopIntoPreferenceAsCurrentStop(
                                this,
                                dataStoreManager
                            )
                        }
                    }
                }

                val (_, distinctStopIdList) = removeLauncherMessageOfRemovedStopsFromPriorityQueue(
                    stopListData
                )

                with(tripPanelUsecase) {
                    removeMessageFromPriorityQueueBasedOnStopId(listLauncherMessageWithPriority)
                }

                removeSentMessageIfTheStopRemoved(distinctStopIdList)

                updateFormStack(
                    dataStoreManager,
                    distinctStopIdList.toMutableList()
                )
            } catch (e: Exception) {
                Log.e(tag, "Exception in removeMessageFromTripPanelQueue ${e.message}", e)
            }
        }
    }

    suspend fun updateStopInformationInTripPanel(coroutineScope: CoroutineScope, stopList: List<StopDetail>) {
        tripPanelUsecase.updateStopInformationInTripPanel(coroutineScope, stopList)
    }

    fun removeLauncherMessageOfRemovedStopsFromPriorityQueue(stopList: List<StopDetail>): Pair<PriorityBlockingQueue<LauncherMessageWithPriority>, IntArray> {
        val stopIdList = stopList.map { it.stopid }

        val modifiedQueue =
            PriorityBlockingQueue(1, LauncherMessagePriorityComparator)

        if (tripPanelUsecase.listLauncherMessageWithPriority.isNotEmpty()) {
            tripPanelUsecase.listLauncherMessageWithPriority.find { launcherMessage ->
                launcherMessage.stopId.filter {
                    it in stopIdList
                }.let {
                    launcherMessage.stopId = it.toIntArray()
                }
                modifiedQueue.add(launcherMessage)
            }
            return Pair(modifiedQueue, stopIdList.toIntArray())
        }

        return Pair(modifiedQueue, stopIdList.toIntArray())
    }

    suspend fun removeSentMessageIfTheStopRemoved(
        distinctStopIdList: IntArray
    ) {
        tripPanelUsecase.lastSentTripPanelMessage.stopId.filterNot {
            it in distinctStopIdList
        }.let {
            if (it.isNotEmpty()) {
                tripPanelUsecase.dismissTripPanelMessage(tripPanelUsecase.lastSentTripPanelMessage.messageId)
            }
        }
    }

    suspend fun updateFormStack(
        dataStoreManager: DataStoreManager,
        modifiedLauncherMessageStopIdListBasedOnExistingStops: MutableList<Int>
    ): IntArray {
        dataStoreManager.getValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING).let { formStack ->
            if (formStack.isNotEmpty()) {
                val formIDList: ArrayList<DispatchFormPath> = Gson().fromJson(
                    formStack,
                    object : TypeToken<ArrayList<DispatchFormPath>>() {}.type
                )

                formIDList.filter { formIdList -> formIdList.stopId in modifiedLauncherMessageStopIdListBasedOnExistingStops }
                    .let { list ->
                        if (list.isNotEmpty()) {
                            Gson().toJson(list.distinctBy { Pair(it.stopId, it.actionId) })
                                .let { formListJson ->
                                    dataStoreManager.setValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, formListJson)
                                    Log.i(tag, "FORM_STACK_KEY Updated -> $formListJson")
                                }
                        } else {
                            dataStoreManager.setValue(UNCOMPLETED_DISPATCH_FORMS_STACK_KEY, EMPTY_STRING)
                        }
                        return list.map { it.stopId }.toIntArray()
                    }
            }
        }
        return IntArray(0)
    }
}