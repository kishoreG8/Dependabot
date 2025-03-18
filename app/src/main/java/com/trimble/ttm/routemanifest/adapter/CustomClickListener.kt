package com.trimble.ttm.routemanifest.adapter

import com.trimble.ttm.routemanifest.model.StopDetail

interface CustomClickListener {
    fun cardClicked(stopDetail: StopDetail)
}