package com.trimble.ttm.routemanifest.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRIP_WIDGET
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.TRIP_INFO_WIDGET
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.ACTIVE_DISPATCH_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_DISTANCE_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_HOURS_KEY
import com.trimble.ttm.commons.preferenceManager.DataStoreManager.Companion.TOTAL_STOPS_KEY
import com.trimble.ttm.routemanifest.ui.activities.DispatchDetailActivity
import com.trimble.ttm.routemanifest.ui.activities.DispatchListActivity
import com.trimble.ttm.routemanifest.utils.Utils
import com.trimble.ttm.routemanifest.utils.ext.toMilesText
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Implementation of route info Widget functionality.
 */
class TripInfoWidget : AppWidgetProvider(), KoinComponent {
    private val dataStoreManager: DataStoreManager by inject()

    private val coroutineScope = CoroutineScope(DefaultDispatcherProvider().io())

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)

        context?.let {
            //Shouldn't be in main thread. Otherwise blocks ui
            coroutineScope.launch(CoroutineName(TRIP_WIDGET)) {
                updateWidget<TripInfoWidget>(context, dataStoreManager)
            }
        }
    }
}

private suspend fun getType(dataStoreManager: DataStoreManager): Class<*> =
    if (dataStoreManager.hasActiveDispatch(TRIP_WIDGET,false)) DispatchDetailActivity::class.java else DispatchListActivity::class.java


internal suspend fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    dataStoreManager: DataStoreManager
) {
    val widgetIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, getType(dataStoreManager))
    widgetIntent.apply {
        putExtra(Intent.EXTRA_REFERRER_NAME, TRIP_INFO_WIDGET)
    }
    val pendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            widgetIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    var totalDistance: Float = dataStoreManager.getValue(TOTAL_DISTANCE_KEY, -1f)
    var totalHours: Float = dataStoreManager.getValue(TOTAL_HOURS_KEY, -1f)
    val totalStops: Int = dataStoreManager.getValue(TOTAL_STOPS_KEY, ZERO)
    val activeDispatch: String = dataStoreManager.getValue(ACTIVE_DISPATCH_KEY, EMPTY_STRING)
    if(totalDistance == -1f){
        totalDistance = 0f
    }
    if(totalHours == -1f){
        totalHours = 0f
    }

    Log.i(TRIP_WIDGET, "D$activeDispatch Dist$totalDistance Hours$totalHours Stops$totalStops")
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.trip_info_widget).apply {
        setOnClickPendingIntent(R.id.appWidgetButton, pendingIntent)

        setTextViewText(
            R.id.tv_total_miles,
            """${totalDistance.toMilesText()} ${context.getString(R.string.miles_widget_place_holder)}"""
        )

        setTextViewText(
            R.id.widgetHoursText,
            """${Utils.getHoursFromMinutes(totalHours)} ${context.getString(R.string.widget_hours_place_holder)}"""
        )

        setTextViewText(
            R.id.widgetNoOfStopsText, """$totalStops ${context.getString(R.string.stops)}"""
        )
    }

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

internal suspend inline fun <reified T> updateWidget(context: Context, dataStoreManager: DataStoreManager) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val appWidgetIds = appWidgetManager
        .getAppWidgetIds(
            ComponentName(
                context,
                T::class.java
            )
        )
    // There may be multiple widgets active, so update all of them
    for (appWidgetId in appWidgetIds) {
        updateAppWidget(context, appWidgetManager, appWidgetId, dataStoreManager)
    }
}