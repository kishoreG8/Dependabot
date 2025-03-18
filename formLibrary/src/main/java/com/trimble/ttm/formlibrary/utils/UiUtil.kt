package com.trimble.ttm.formlibrary.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Base64
import android.util.DisplayMetrics
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

object UiUtil {

    fun encodeToBase64(image: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    fun decodeBase64StringToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedByte: ByteArray = Base64.decode(base64String, 0)
            BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
        } catch (e: Exception) {
            null
        }
    }

    fun setTextViewDrawableColor(textView: TextView, color: Int) {
        for (drawable in textView.compoundDrawables) {
            if (drawable != null) {
                drawable.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(
                        textView.context,
                        color
                    ), PorterDuff.Mode.SRC_IN
                )
            }
        }
    }

    fun getDisplayWidth(): Int =
        Resources.getSystem().displayMetrics.widthPixels

    fun getDisplayHeight(): Int =
        Resources.getSystem().displayMetrics.heightPixels

    fun isTablet(context: Context): Boolean =
        context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_LARGE

    fun hideKeyboard(activity: Activity?) {
        activity?.window?.decorView?.let {
            val imm =
                activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }

    fun hideKeyboard(context : Context, view: View){
        val imm =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun convertDpToPixel(dp: Float, context: Context) =
        dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)

}