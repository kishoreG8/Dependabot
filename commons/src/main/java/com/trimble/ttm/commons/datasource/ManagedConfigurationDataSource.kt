package com.trimble.ttm.commons.datasource

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.MANAGED_CONFIG

class ManagedConfigurationDataSource(private val context: Context) {

    fun fetchManagedConfiguration(caller: String): Bundle? {
        Log.i(MANAGED_CONFIG, "fetching managed config data, caller: $caller")
        val restrictionsMgr =
            context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        val appRestrictions = restrictionsMgr.applicationRestrictions

        // Just for logging purpose
        if (appRestrictions.isEmpty.not()) {
            Log.d(MANAGED_CONFIG, "ManagedConfig Size: ${appRestrictions.keySet().size}")
        }
        // End

        return appRestrictions
    }

}