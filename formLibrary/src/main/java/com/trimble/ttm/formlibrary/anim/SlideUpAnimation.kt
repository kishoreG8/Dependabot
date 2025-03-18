package com.trimble.ttm.formlibrary.anim

import android.view.animation.TranslateAnimation

open class SlideUpAnimation(height: Float) : TranslateAnimation(
    0f,  // fromXDelta
    0f,
    height,
    0f
) {

    init {
        duration = 100
        fillAfter = true
    }
}