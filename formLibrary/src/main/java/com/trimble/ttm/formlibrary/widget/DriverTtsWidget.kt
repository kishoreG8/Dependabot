package com.trimble.ttm.formlibrary.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.trimble.ttm.commons.logger.INBOX
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.WIDGET
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.manager.IMessageManagerCallback
import com.trimble.ttm.formlibrary.manager.ITtsPlayerManager
import com.trimble.ttm.formlibrary.manager.MessagesManagerImpl
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class DriverTtsWidget : AppWidgetProvider(),
    IMessageManagerCallback,
    ITrackStateCallback,
    KoinComponent {

    private val ttsPlayerManager: ITtsPlayerManager by inject()
    private val messagesManager: MessagesManagerImpl by inject()

    companion object {
        const val PLAY_BUTTON_ACTION = "TTS_WIDGET_PLAY_BUTTON_PRESSED"
        const val STOP_BUTTON_ACTION = "TTS_WIDGET_STOP_BUTTON_PRESSED"
        const val PAUSE_BUTTON_ACTION = "TTS_WIDGET_PAUSE_BUTTON_PRESSED"
        const val PREVIOUS_BUTTON_ACTION = "TTS_WIDGET_PREVIOUS_BUTTON_PRESSED"
        const val NEXT_BUTTON_ACTION = "TTS_WIDGET_NEXT_BUTTON_PRESSED"
        const val REPLAY_BUTTON_ACTION = "TTS_WIDGET_REPLAY_BUTTON_PRESSED"
        const val OFF_BUTTON_ACTION = "TTS_WIDGET_OFF_BUTTON_PRESSED"
        const val DELETE_ACTION = "TTS_WIDGET_DELETE_ACTION"
    }

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.i("$INBOX$WIDGET","onUpdate")
        ttsPlayerManager.setTrackStateCallback(this)
        messagesManager.setCallback(this)
        messagesManager.getMessages()
        context?.let {
            updateWidget<DriverTtsWidget>(it,messagesManager)
        }
    }

    override fun onReceive(context: Context?, intent: Intent) {
        super.onReceive(context, intent)
        Log.i("$INBOX$WIDGET","action: ${intent.action}")
        context?.let {
            when (intent.action) {
                PLAY_BUTTON_ACTION -> {
                    turnOnStopButton(it)
                    ttsPlayerManager.playMessage(messagesManager.getCurrentMessage())
                }

                STOP_BUTTON_ACTION -> {
                    updateButtonVisibility(it, R.id.playerWidgetPlayButton, View.VISIBLE)
                    ttsPlayerManager.stopMessage()
                }

                PAUSE_BUTTON_ACTION -> {
                    updateButtonVisibility(it, R.id.playerWidgetPlayButton, View.VISIBLE)
                    ttsPlayerManager.pauseMessage()
                }

                PREVIOUS_BUTTON_ACTION -> {
                    ttsPlayerManager.stopMessage()
                    messagesManager.navigatePrevious()
                }

                NEXT_BUTTON_ACTION -> {
                    ttsPlayerManager.stopMessage()
                    messagesManager.navigateNext()
                }

                REPLAY_BUTTON_ACTION -> {
                    updateButtonVisibility(it, R.id.playerWidgetReplayButton, View.GONE)
                    ttsPlayerManager.playMessage(messagesManager.getCurrentMessage())
                }
                DELETE_ACTION -> {
                    messagesManager.clearMessages()
                }
            }
            if(
                intent.action!= PLAY_BUTTON_ACTION &&
                intent.action!= REPLAY_BUTTON_ACTION&&
                intent.action!= OFF_BUTTON_ACTION
            ){
                ttsPlayerManager.setTrackStateCallback(this)
                messagesManager.setCallback(this)
                messagesManager.getMessages()
                updateWidget<DriverTtsWidget>(it,messagesManager)
            }
        }
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
    }

    private fun turnOnStopButton(context: Context){
        val views = RemoteViews(
            context.packageName,
            R.layout.driver_tts_widget
        )
        views.setViewVisibility(R.id.playerWidgetReplayButton, View.GONE)
        views.setViewVisibility(R.id.playerWidgetPlayButton, View.GONE)
        val appWidget = ComponentName(context, DriverTtsWidget::class.java)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetManager.updateAppWidget(appWidget, views)
    }

    private fun updateButtonVisibility(context: Context, button: Int, visibility: Int) {
        val views = RemoteViews(
            context.packageName,
            R.layout.driver_tts_widget
        )

        views.setViewVisibility(button, visibility)

        val appWidget = ComponentName(context, DriverTtsWidget::class.java)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetManager.updateAppWidget(appWidget, views)
    }

    override fun onMessagesUpdated() {
        updateWidget<DriverTtsWidget>(
            context = messagesManager.getContext(),
            messagesManager = messagesManager
        )
    }

    override fun onError(msg: String) {
        Log.w("$INBOX$WIDGET", "onError: $msg")
    }

    override fun onTrackFinished() {
        updateButtonVisibility(messagesManager.getContext(), R.id.playerWidgetReplayButton, View.VISIBLE)
    }

    override fun onTrackPaused() {
        Log.d("$INBOX$WIDGET", "TrackPaused")
    }

    override fun onTrackError(error: String) {
        Log.w("$INBOX$WIDGET", "onTrackError: $error")
    }

}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    messagesManager: MessagesManagerImpl
) {
    val views = RemoteViews(
        context.packageName,
        R.layout.driver_tts_widget
    )

    views.setTextViewText(
        R.id.playerWidgetMessageText,
        messagesManager.getMessageCountTitle()
    )
    views.setViewVisibility(
        R.id.playerWidgetPlayButtonOff,
        messagesManager.hideOrShowPlay()
    )
    views.setViewVisibility(
        R.id.playerWidgetPlayButton,
        View.VISIBLE
    )
    views.setViewVisibility(
        R.id.playerWidgetPreviousButtonOff,
        messagesManager.hideOrShowPrevious()
    )
    views.setViewVisibility(
        R.id.playerWidgetNextButtonOff,
        messagesManager.hideOrShowNext()
    )

    views.setViewVisibility(
        R.id.playerWidgetReplayButton,
        View.GONE
    )

    createButton(DriverTtsWidget.PLAY_BUTTON_ACTION, R.id.playerWidgetPlayButton, views, context)
    createButton(DriverTtsWidget.STOP_BUTTON_ACTION, R.id.playerWidgetStopButton, views, context)
    createButton(DriverTtsWidget.PAUSE_BUTTON_ACTION, R.id.playerWidgetPauseButton, views, context)
    createButton(DriverTtsWidget.PREVIOUS_BUTTON_ACTION, R.id.playerWidgetPreviousButton, views, context)
    createButton(DriverTtsWidget.NEXT_BUTTON_ACTION, R.id.playerWidgetNextButton, views, context)
    createButton(DriverTtsWidget.REPLAY_BUTTON_ACTION, R.id.playerWidgetReplayButton, views, context)
    createButton(DriverTtsWidget.OFF_BUTTON_ACTION, R.id.playerWidgetPreviousButtonOff, views, context)
    createButton(DriverTtsWidget.OFF_BUTTON_ACTION, R.id.playerWidgetNextButtonOff, views, context)
    createButton(DriverTtsWidget.OFF_BUTTON_ACTION, R.id.playerWidgetPlayButtonOff, views, context)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

fun createButton(
    action: String,
    buttonId: Int,
    views: RemoteViews,
    context: Context
) {
    val intent = Intent(context, DriverTtsWidget::class.java).also {
        it.action = action
    }
    val playPendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    views.setOnClickPendingIntent(buttonId, playPendingIntent)
}

internal inline fun <reified T> updateWidget(
    context: Context,
    messagesManager: MessagesManagerImpl
) {
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
        updateAppWidget(
            context,
            appWidgetManager,
            appWidgetId,
            messagesManager
        )
    }
}