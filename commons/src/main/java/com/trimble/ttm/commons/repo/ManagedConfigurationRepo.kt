package com.trimble.ttm.commons.repo

import com.trimble.ttm.commons.model.DeepLinkConfigurationData
import com.trimble.ttm.commons.model.ManagedConfigurationData

interface ManagedConfigurationRepo {

    fun fetchManagedConfigDataFromCache(caller: String): ManagedConfigurationData?

    fun fetchManagedConfigDataFromServer(caller: String)

    fun getAppPackageForWorkflowEventsCommunicationFromManageConfiguration(caller: String): String?

    fun getPolygonalOptOutFromManageConfiguration(caller: String): Boolean

    fun getDeepLinkDataFromManagedConfiguration(caller: String) : DeepLinkConfigurationData?

}