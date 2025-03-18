package com.trimble.ttm.formlibrary.utils.ext

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI

fun Fragment.findNavControllerSafely(): NavController? {
    return if (isAdded) {
        findNavController()
    } else {
        null
    }
}

fun NavController.navigateTo(currentDestination: Int, navigateActionId: Int, bundle: Bundle = Bundle()) {
    if (this.currentDestination?.id == currentDestination) {
        navigate(navigateActionId, bundle)
    }
}

fun NavController.navigateBack(currentDestination: Int) {
    if (this.currentDestination?.id == currentDestination) {
        NavigationUI.navigateUp(
                this,
                AppBarConfiguration.Builder(graph).build()
        )
    }
}