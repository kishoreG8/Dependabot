package com.trimble.ttm.formlibrary.anim

import android.view.animation.TranslateAnimation

open class SlideDownAnimation(height : Float) : TranslateAnimation(
    0f,  // fromXDelta
    0f,  // toXDelta
    0f,  // fromYDelta
    height){

    init {
        duration = 300
        fillAfter = true
    }
}