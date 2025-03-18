package com.trimble.ttm.routemanifest.utils.ext

import android.view.View

private const val DEFAULT_CLICK_DEBOUNCE_THRESHOLD = 3000L

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.hide() {
    this.visibility = View.GONE
}

fun View.setVisibility(canShow: Boolean) {
    this.visibility = if (canShow) View.VISIBLE else View.INVISIBLE
}

