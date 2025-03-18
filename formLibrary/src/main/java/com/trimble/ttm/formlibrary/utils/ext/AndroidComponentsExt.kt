package com.trimble.ttm.formlibrary.utils.ext

import android.content.Context
import android.widget.Toast
import me.drakeet.support.toast.ToastCompat

fun Context.showToast(message: CharSequence) =
    ToastCompat.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Context.showToast(messageStrRes: Int) =
    ToastCompat.makeText(this, messageStrRes, Toast.LENGTH_SHORT).show()

fun Context.showLongToast(messageStrRes: Int) =
    ToastCompat.makeText(this, messageStrRes, Toast.LENGTH_LONG).show()

