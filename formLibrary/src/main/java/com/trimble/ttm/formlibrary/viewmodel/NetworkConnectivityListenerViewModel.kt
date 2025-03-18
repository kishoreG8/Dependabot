package com.trimble.ttm.formlibrary.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeLaunch
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent

abstract class NetworkConnectivityListenerViewModel(
    private val appModuleCommunicator: AppModuleCommunicator,
    val defaultDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel(), KoinComponent {
    private val _networkConnectivityStatus = MutableSharedFlow<Boolean>(replay = 1)
    private val networkConnectivityStatus = _networkConnectivityStatus.asSharedFlow()
    private val tag = "NetworkConnectivityListenerVM"
    fun listenToNetworkConnectivityChange() = networkConnectivityStatus

    open fun onNetworkConnectivityChange(status: Boolean) {
        //Ignore
    }

    init {
        checkAndPostConnectivityStatus()
    }

    private fun checkAndPostConnectivityStatus() {
        viewModelScope.safeLaunch(
            defaultDispatcherProvider.io() + CoroutineName("$tag checkAndPostConnectivityStatus")
        ) {
            appModuleCommunicator.getInternetEvents().collect { isAvailable ->
                if (isAvailable.not()) onNetworkConnectivityChange(false)
                _networkConnectivityStatus.emit(isAvailable)
            }
        }
    }

    fun isActiveInternetAvailable(): Boolean = try {
        networkConnectivityStatus.replayCache.isNotEmpty() && networkConnectivityStatus.replayCache.first()
    } catch (e: Exception) {
        false
    }

}