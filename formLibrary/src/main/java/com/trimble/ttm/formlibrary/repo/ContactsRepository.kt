package com.trimble.ttm.formlibrary.repo

import com.trimble.ttm.formlibrary.model.User
import kotlinx.coroutines.flow.Flow

interface ContactsRepository: BasePaginationRepo {
    suspend fun getContacts(customerId: String, obcId: Long, shouldFetchFromServer:Boolean)
    fun getContactsListFlow(): Flow<MutableSet<User>>
}