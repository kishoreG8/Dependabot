package com.trimble.ttm.routemanifest.utils.ext

import android.view.View
import android.view.ViewGroup

fun ViewGroup.show() {
    this.visibility = View.VISIBLE
}

fun ViewGroup.hide() {
    this.visibility = View.GONE
}