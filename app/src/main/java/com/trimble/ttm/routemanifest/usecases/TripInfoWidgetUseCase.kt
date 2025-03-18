package com.trimble.ttm.routemanifest.usecases

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.logger.TRIP_WIDGET
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ZERO
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ROUTE_DATA_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_DISTANCE_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_HOURS_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_STOPS_KEY
import com.trimble.ttm.routemanifest.model.RouteCalculationResult
import com.trimble.ttm.routemanifest.model.StopDetail
import com.trimble.ttm.commons.repo.LocalDataSourceRepo
import com.trimble.ttm.routemanifest.utils.Utils.createRouteDataMapFromRouteCalculationResult
import com.trimble.ttm.routemanifest.utils.Utils.getGsonInstanceWithIsoDateFormatter
import com.trimble.ttm.routemanifest.utils.Utils.toJsonString
import com.trimble.ttm.routemanifest.widget.TripInfoWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android context is passed to this class to update the widget.
 * There shouldn't be any context leak here.
 * This class is responsible for updating the widget with the latest data.
 * This class is also responsible for resetting the widget data.
 */
class TripInfoWidgetUseCase(
    private val context: Context,
    private val localDataSourceRepo: LocalDataSourceRepo,
    private val sendBroadCastUseCase: SendBroadCastUseCase,
    private val coroutineScope: CoroutineScope,
    private val coroutineDispatcherProvider: DispatcherProvider
) {

    fun resetTripInfoWidget(caller: String) {
        coroutineScope.launch(coroutineDispatcherProvider.io()) {
            with(localDataSourceRepo) {
                setToAppModuleDataStore(TOTAL_DISTANCE_KEY, ZERO.toFloat())
                setToAppModuleDataStore(TOTAL_HOURS_KEY, ZERO.toFloat())
                setToAppModuleDataStore(TOTAL_STOPS_KEY, ZERO)
            }
            updateTripInfoWidget(caller)
        }
    }

    fun updateTripInfoWidget(distance: Float, hour: Float, stopCount: Int, caller: String, routeCalculationResult: RouteCalculationResult, stopList: List<StopDetail>) {
        coroutineScope.launch(coroutineDispatcherProvider.io()) {
            val routeData = toJsonString(createRouteDataMapFromRouteCalculationResult(stopList = stopList, routeCalculatedStopList = routeCalculationResult.stopDetailList), getGsonInstanceWithIsoDateFormatter()) ?: EMPTY_STRING
            with(localDataSourceRepo) {
                setToAppModuleDataStore(TOTAL_DISTANCE_KEY, distance)
                setToAppModuleDataStore(TOTAL_HOURS_KEY, hour)
                setToAppModuleDataStore(TOTAL_STOPS_KEY, stopCount)
                setToAppModuleDataStore(ROUTE_DATA_KEY, routeData)
            }
            updateTripInfoWidget(caller)
        }
    }

    private fun updateTripInfoWidget(caller:String) {
        sendBroadCastUseCase.sendBroadCast(
            Intent(
                context,
                TripInfoWidget::class.java
            ).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }, "$TRIP_WIDGET UpdateWidgetCallFrom $caller"
        )
    }

}