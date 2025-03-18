package com.trimble.ttm.routemanifest.utils.ext

import android.widget.ImageView
import androidx.databinding.BindingAdapter

@BindingAdapter("setResource")
fun setImageViewResource(view: ImageView, resId: Int) {
    view.setImageResource(resId)
}
