package com.trimble.ttm.commons.utils.ext

import android.content.Context
import android.widget.Toast
import me.drakeet.support.toast.ToastCompat

fun Context.showLongToast(messageStrRes: Int) =
    ToastCompat.makeText(this, messageStrRes, Toast.LENGTH_LONG).show()