package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.repo.ContactsRepository
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.util.*

class ContactsUseCase(private val contactsRepository: ContactsRepository, private val cacheGroupsUseCase: CacheGroupsUseCase) {
    private lateinit var applicationScopeJob: Job

    fun cacheGroupIdsFormIdsAndUserIdsFromServer(
        cid: String,
        obcId: String,
        applicationScope: CoroutineScope,
        tag: String
    ) {
        applicationScopeJob = applicationScope.safeLaunch(Dispatchers.IO + SupervisorJob()) {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                cid, obcId, applicationScope = applicationScope, tag
            ).also { isSyncSuccess ->
                if (isSyncSuccess) getContacts(cid, obcId.toSafeLong(), isSyncSuccess)
            }
        }
    }

    fun getContactListFlow() = contactsRepository.getContactsListFlow()

    suspend fun getContacts(customerId: String, obcId: Long, shouldFetchFromServer: Boolean = false) =
        contactsRepository.getContacts(customerId, obcId, shouldFetchFromServer)

    fun resetValueInContactRepo() {
        if (::applicationScopeJob.isInitialized) {
            applicationScopeJob.cancel()
        }
        contactsRepository.resetPagination()
    }

    fun sortUsersAlphabetically(userSet: MutableSet<User>):  MutableSet<User>{
        return userSet.sortedBy {
            it.username.lowercase(Locale.getDefault())
        }.toMutableSet()
    }
}
