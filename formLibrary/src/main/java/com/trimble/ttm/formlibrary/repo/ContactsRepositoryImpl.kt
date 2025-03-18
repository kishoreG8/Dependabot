package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.usecases.CacheGroupsUseCase
import com.trimble.ttm.formlibrary.utils.defaultValue
import com.trimble.ttm.formlibrary.utils.getCallbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("UNCHECKED_CAST")
class ContactsRepositoryImpl(private val cacheGroupsUseCase: CacheGroupsUseCase) : ContactsRepository {
    private val tag = "ContactsRepositoryImpl"
    private val customerIdLogKey = "customer id"
    private val obcIdLogKey = "obc id"
    private val contactListFlowPair = getCallbackFlow<MutableSet<User>>()

    private var customerId = ""
    private var obcId = defaultValue
    private var vId = defaultValue
    private var groupIds = setOf<Long>()
    private var userSet = mutableSetOf<User>()
    private var lastFetchedUserCount = 0L
    private var shouldFetchFromServer = false

    override suspend fun getContacts(customerId: String, obcId: Long, shouldFetchFromServer: Boolean) {
        this.customerId = customerId
        this.obcId = obcId
        this.shouldFetchFromServer = shouldFetchFromServer
        cacheGroupsUseCase.getVidFromVUnitCollection(customerId, obcId, tag).let { vUnitPair ->
            vId = vUnitPair.first
            if (vUnitPair.second) {
                cacheGroupsUseCase.getGroupIdsFromGroupUnitCollection(customerId, obcId, vId, tag, shouldFetchFromServer)
                    .let { groupUnitPair ->
                        groupIds = groupUnitPair.first
                        fetchContactsFromAllVehicleGroups(groupUnitPair.second)
                    }
            } else {
                notifyUserList(mutableSetOf())
                Log.e(tag, "No Contacts Available - getVidFromVUnitCollection")
            }
        }
    }

    private suspend fun fetchContactsFromAllVehicleGroups(shouldFetchContacts: Boolean) {
        if (shouldFetchContacts) {
            cacheGroupsUseCase.getUserIdsFromGroups(groupIds, customerId, obcId, tag, shouldFetchFromServer).let { userIdsFetchResultTriple ->
                userSet.clear()
                userSet.addAll(userIdsFetchResultTriple.first)
                if (userIdsFetchResultTriple.second) {
                    getContacts()
                } else {
                    notifyUserList(mutableSetOf())
                    Log.e(tag, "No Contacts Available - getUsersOfAllVehicleGroups")
                }
            }
        } else {
            notifyUserList(mutableSetOf())
            Log.e(tag, "No Contacts Available - getGroupIdsFromGroupUnitCollection")
        }
    }

    private fun getContacts() {
        if (userSet.isNotEmpty()) {
            notifyUserList(userSet)
        } else {
            notifyUserList(mutableSetOf())
            Log.e(
                tag,
                "No Contacts Available - User ids are empty",
                null,
                customerIdLogKey to customerId,
                obcIdLogKey to obcId,
                "vid" to vId
            )
        }
    }

    override fun resetPagination() {
        lastFetchedUserCount = 0L
        customerId = ""
        obcId = defaultValue
        vId = defaultValue
        groupIds = setOf()
        userSet = mutableSetOf()
    }

    override fun didLastItemReached(): Flow<Boolean> {
        return flowOf(false)
    }

    override fun getContactsListFlow() = contactListFlowPair.second

    private fun notifyUserList(userDefSet: MutableSet<User>) {
        contactListFlowPair.first.notify(userDefSet)
    }
}