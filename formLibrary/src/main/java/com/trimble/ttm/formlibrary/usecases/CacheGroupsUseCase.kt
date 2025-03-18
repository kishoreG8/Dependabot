package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.model.Form
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.repo.CacheGroupsRepo
import kotlinx.coroutines.CoroutineScope
import java.util.*

class CacheGroupsUseCase(private val cacheGroupsRepo: CacheGroupsRepo) {

    suspend fun getVidFromVUnitCollection(
        customerId: String,
        obcId: Long,
        tag: String
    ): Pair<Long, Boolean> =
        cacheGroupsRepo.getVidFromVUnitCollection(customerId, obcId, tag)

    suspend fun getGroupIdsFromGroupUnitCollection(
        customerId: String,
        obcId: Long,
        vId: Long,
        tag: String,
        shouldFetchFromServer: Boolean = false
    ): Pair<Set<Long>, Boolean> =
        cacheGroupsRepo.getGroupIdsFromGroupUnitCollection(
            customerId,
            obcId,
            vId,
            tag,
            shouldFetchFromServer
        )

    suspend fun getFormIdsFromGroups(
        groupIds: Set<Long>,
        customerId: String,
        obcId: Long,
        tag: String,
        shouldFetchFromServer: Boolean = false
    ): Triple<Map<Double, FormDef>, Boolean, Boolean> =
        cacheGroupsRepo.getFormIdsFromGroups(
            groupIds,
            customerId,
            obcId,
            tag,
            shouldFetchFromServer
        )

    suspend fun getUserIdsFromGroups(
        groupIds: Set<Long>,
        customerId: String,
        obcId: Long,
        tag: String,
        shouldFetchFromServer: Boolean = false
    ): Triple<MutableSet<User>, Boolean, Boolean> =
        cacheGroupsRepo.getUserIdsFromGroups(
            groupIds,
            customerId,
            obcId,
            tag,
            shouldFetchFromServer
        )

    suspend fun cacheFormTemplate(formId: String, isFreeForm: Boolean): Form =
        cacheGroupsRepo.cacheFormTemplate(formId, isFreeForm)

    suspend fun checkAndUpdateCacheForGroupsFromServer(
        cid: String,
        obcId: String,
        applicationScope: CoroutineScope,
        tag: String
    ): Boolean =
        cacheGroupsRepo.checkAndUpdateCacheForGroupsFromServer(cid, obcId, applicationScope, tag)

    internal fun sortFormByName(unSortedForms: Map<Double, FormDef>, resultingSortedForms: MutableMap<Double, FormDef>) {
        return unSortedForms.toList().sortedBy {
            it.second.name.trim().lowercase(Locale.getDefault())
        }.toMap().run {
            resultingSortedForms.clear()
            resultingSortedForms.putAll(this)
        }
    }

    fun sortFormsAlphabetically(formSet: MutableSet<FormDef>):  MutableSet<FormDef> =
        formSet.sortedBy { it.name.trim().lowercase(Locale.getDefault()) }.toMutableSet()

    fun getFirestoreExceptionNotifier() = cacheGroupsRepo.getFirestoreExceptionNotifier()

}