package com.trimble.ttm.routemanifest

import androidx.annotation.StringRes

interface MockableResource {
    fun getString(@StringRes id: Int): String
}