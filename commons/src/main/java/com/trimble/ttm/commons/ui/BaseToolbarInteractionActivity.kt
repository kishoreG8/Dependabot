package com.trimble.ttm.commons.ui

import android.content.res.Configuration
import com.trimble.ttm.toolbar.ui.ToolbarViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

open class BaseToolbarInteractionActivity : BasePermissionActivity() {
    val toolbarViewModel: ToolbarViewModel by viewModel()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            toolbarViewModel.configurationChanged()
        } catch (e: Exception) {
            //Ignore
        }
    }

    override suspend fun onAllPermissionsGranted() {
        // Ignore
    }
}