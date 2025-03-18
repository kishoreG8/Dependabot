package com.trimble.ttm.routemanifest.adapter

import com.trimble.ttm.routemanifest.model.Dispatch

interface IDispatchClickListener {
    fun dispatchClicked(dispatch: Dispatch)
}