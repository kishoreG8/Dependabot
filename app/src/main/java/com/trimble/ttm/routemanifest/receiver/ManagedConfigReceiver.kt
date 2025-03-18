package com.trimble.ttm.routemanifest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trimble.ttm.commons.repo.ManagedConfigurationRepo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ManagedConfigReceiver: BroadcastReceiver(), KoinComponent {

    private val tag = "ManagedConfigReceiver"
    private val managedConfigurationRepo: ManagedConfigurationRepo by inject()

    override fun onReceive(context: Context, intent: Intent) {
        managedConfigurationRepo.fetchManagedConfigDataFromServer(tag)
    }
}