package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.formlibrary.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface CacheGroupsRepo {

    suspend fun getVidFromVUnitCollection(
        customerId: String,
        obcId: Long,
        tag: String
    ): Pair<Long, Boolean>

    suspend fun getGroupIdsFromGroupUnitCollection(
        customerId: String,
        obcId: Long,
        vId: Long,
        tag: String,
        shouldFetchFromServer: Boolean = false
    ): Pair<Set<Long>, Boolean>

    suspend fun getFormIdsFromGroups(
        groupIds: Set<Long>,
        customerId: String,
        obcId: Long,
        tag: String,
        shouldFetchFromServer: Boolean = false
    ): Triple<Map<Double, FormDef>, Boolean, Boolean>

    suspend fun getUserIdsFromGroups(
        groupIds: Set<Long>,
        customerId: String,
        obcId: Long,
        tag: String,
        shouldFetchFromServer: Boolean = false
    ): Triple<MutableSet<User>, Boolean, Boolean>

    suspend fun cacheFormTemplate(formId: String, isFreeForm: Boolean): Form

    suspend fun checkAndUpdateCacheForGroupsFromServer(
        cid: String,
        obcId: String,
        applicationScope: CoroutineScope,
        tag: String
    ): Boolean

    fun getFirestoreExceptionNotifier():Flow<Unit>

}