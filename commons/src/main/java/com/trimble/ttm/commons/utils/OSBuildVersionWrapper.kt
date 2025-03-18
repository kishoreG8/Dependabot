package com.trimble.ttm.commons.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object OSBuildVersionWrapper {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun isOsVersionOfOreoOrAbove(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}