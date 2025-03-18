package com.trimble.ttm.formlibrary.http

import com.trimble.ttm.formlibrary.model.CollectionDeleteResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CollectionDeleteApi {
    @GET("MessagesDeleteAllApi")
    suspend fun deleteCollection(
        @Query("collectionPath") collectionPath: String
    ): CollectionDeleteResponse
}