package com.trimble.ttm.formlibrary.utils.ext

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE

private const val DEFAULT_CLICK_DEBOUNCE_THRESHOLD = 3000L

fun View.show() {
    this.visibility = VISIBLE
}

fun View.hide() {
    this.visibility = GONE
}

fun View.hide(duration: Int) {
    with(ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)) {
        setDuration(duration.toLong())
        addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                visibility = GONE
            }
        })
        start()
    }
}

fun View.show(duration: Int) {
    with(ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)) {
        setDuration(duration.toLong())
        addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                visibility = VISIBLE
            }
        })
        start()
    }
}

fun View.setDebounceClickListener(
    periodsInMillis: Long = DEFAULT_CLICK_DEBOUNCE_THRESHOLD,
    action: () -> Unit
) {
    this.setOnClickListener(object : View.OnClickListener {
        var lastTime = 0L
        override fun onClick(v: View?) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTime < periodsInMillis) {
                return
            }
            lastTime = currentTime
            action()
        }
    })
}