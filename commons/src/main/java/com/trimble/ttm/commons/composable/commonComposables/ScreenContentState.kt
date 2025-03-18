package com.trimble.ttm.commons.composable.commonComposables

import com.trimble.ttm.commons.utils.EMPTY_STRING

sealed class ScreenContentState{
    data class Error(val errorMessage: String) : ScreenContentState()
    data class Loading(val loadingMessage: String = EMPTY_STRING) : ScreenContentState()
    data class Success(val successMessage: String = EMPTY_STRING): ScreenContentState()
}