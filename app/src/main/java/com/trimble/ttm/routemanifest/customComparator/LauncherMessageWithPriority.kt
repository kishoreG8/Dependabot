package com.trimble.ttm.routemanifest.customComparator

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_PANEL
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.isNotNull
import com.trimble.ttm.routemanifest.utils.INVALID_TRIP_PANEL_MESSAGE_ID
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class LauncherMessageWithPriority(
    val message: String = "",
    val messagePriority: Int = -1,
    val messageID: Int = INVALID_TRIP_PANEL_MESSAGE_ID,
    val location: Pair<Double, Double> = Pair(0.0, 0.0),
    val currentLocation: Pair<Double, Double> = Pair(0.0, 0.0),
    vararg var stopId: Int
)
/**
 * The head of this priority queue is the least element with respect to the specified ordering.
 * If multiple elements are tied for least value, the head is one of those elements — ties are broken arbitrarily.
 * So,if two elements share the same priority, priority queue does not maintain the order of insertion.
 * So, made logic if two elements having same priority then,
 * 1.comparing them by distance from the current location,
 * 2.if they share same distance comparing with the stop id ascending order
 */
class LauncherMessagePriorityComparator {
    companion object : Comparator<LauncherMessageWithPriority> {
        // all the unit test related with this class are already passing. Do we need make tests?
        override fun compare(messageA: LauncherMessageWithPriority, messageB: LauncherMessageWithPriority): Int = runBlocking {
            try{
                Log.d(TRIP_PANEL,"LauncherMessageWithPriority compare - messageA: $messageA messageB:$messageB")
                if(messageA.isNotNull() && messageB.isNotNull()){
                    val compareA = async {
                        getDistanceInFeet(
                            messageA.currentLocation,
                            messageA.location.first,
                            messageA.location.second
                        )
                    }
                    val compareB = async {
                        getDistanceInFeet(
                            messageB.currentLocation,
                            messageB.location.first,
                            messageB.location.second
                        )
                    }
                    when {
                        messageA.messagePriority != messageB.messagePriority -> messageA.messagePriority - messageB.messagePriority
                        compareA.await() != (compareB.await()) -> {
                            compareA.await().compareTo(compareB.await())
                        }
                        compareA.await() == (compareB.await()) -> {
                            messageA.messageID - messageB.messageID
                        }
                        else -> 0
                    }
                }else{
                     0
                }
            }catch (e: Exception){
                Log.e(TRIP_PANEL,"----Exception in LauncherMessageWithPriority compare method. stopIds: ${messageA.stopId} <-> ${messageB.stopId}. MessagePriorities: ${messageA.messagePriority} <-> ${messageB.messagePriority}. Messages: ${messageA.message} <-> ${messageB.message}",throwable = null,"error" to e.stackTraceToString() )
                 0
            }
        }

        private fun getDistanceInFeet(
            currentLocation: Pair<Double, Double>,
            latitude: Double,
            longitude: Double
        ): Double {
            return FormUtils.getMilesToFeet(
                FormUtils.getDistanceBetweenLatLongs(
                    currentLocation,
                    Pair(latitude, longitude)
                )
            )
        }
    }
}


