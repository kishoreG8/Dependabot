package com.trimble.ttm.routemanifest.viewmodel

import androidx.lifecycle.ViewModel
import com.trimble.ttm.routemanifest.usecases.TripPanelActionHandleUseCase
import kotlinx.coroutines.flow.flow

class TripPanelPositiveActionTransitionScreenViewModel(private val tripPanelActionHandleUseCase: TripPanelActionHandleUseCase): ViewModel() {

    fun handlePositiveAction(messageId: Int, isAutoDismissed: Boolean, messagePriority: Int) = flow {
        tripPanelActionHandleUseCase.handleTripPanelPositiveAction(messageId, isAutoDismissed, messagePriority)
        emit(Unit)
    }
}