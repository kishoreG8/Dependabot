package com.trimble.ttm.formlibrary.repo

import kotlinx.coroutines.flow.SharedFlow

interface DraftingRepo {

    val draftProcessFinished: SharedFlow<Boolean>

    val initDraftProcessing: SharedFlow<Boolean>

    suspend fun setInitDraftProcessing(isStarted:Boolean)

    suspend fun setDraftProcessFinished(isFinished:Boolean)

    suspend fun restoreDraftProcessFinished()

    suspend fun restoreInitDraftProcessing()

}