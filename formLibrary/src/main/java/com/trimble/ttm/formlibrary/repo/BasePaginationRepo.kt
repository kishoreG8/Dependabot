package com.trimble.ttm.formlibrary.repo

import kotlinx.coroutines.flow.Flow

interface BasePaginationRepo {
    fun didLastItemReached(): Flow<Boolean>
    fun resetPagination()
}