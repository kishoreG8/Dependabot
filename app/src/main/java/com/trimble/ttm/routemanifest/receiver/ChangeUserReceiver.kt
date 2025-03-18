package com.trimble.ttm.routemanifest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.logger.CHANGE_USER
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.usecase.VehicleDriverMappingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class ChangeUserReceiver : BroadcastReceiver(), KoinComponent {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val vehicleDriverMappingUseCase: VehicleDriverMappingUseCase by inject()
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(CHANGE_USER, "Instinct User Change is detected")
        scope.launch {
            //Update the Vehicle number to addressBook Collection in Firestore when Change User happens
           vehicleDriverMappingUseCase.updateVehicleDriverMapping()
        }
    }
}