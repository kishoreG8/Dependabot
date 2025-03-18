package com.trimble.ttm.commons.composable.commonComposables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.trimble.ttm.commons.R

@Composable
fun ProgressErrorComposable(screenContentState: ScreenContentState){
    when(screenContentState){
        is ScreenContentState.Loading -> {
            LoadingScreen(progressText = stringResource(id = R.string.loading_text), show = true)
        }

        is ScreenContentState.Success -> {
            LoadingScreen(progressText = screenContentState.successMessage, show = false)
        }

        is ScreenContentState.Error -> {
            LoadingScreen(progressText = screenContentState.errorMessage, show = false)
        }
    }
}