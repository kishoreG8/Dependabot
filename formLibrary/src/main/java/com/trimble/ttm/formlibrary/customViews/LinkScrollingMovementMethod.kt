package com.trimble.ttm.formlibrary.customViews

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Selection
import android.text.Spannable
import android.text.method.ScrollingMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import com.trimble.ttm.commons.utils.GOOGLE_URL
import com.trimble.ttm.formlibrary.R

class LinkScrollingMovementMethod : ScrollingMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        // Handle link clicks
        if (event.action == MotionEvent.ACTION_UP) {
            var x = event.x.toInt()
            var y = event.y.toInt()

            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop

            x += widget.scrollX
            y += widget.scrollY

            val layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())

            val link = buffer.getSpans(off, off, ClickableSpan::class.java)

            if (link.isNotEmpty()) {
                if (widget.context.isBrowserAvailable()) {
                    link[0].onClick(widget)
                } else {
                    Toast.makeText(widget.context, R.string.no_browser_found, Toast.LENGTH_SHORT)
                        .show()
                }
                return true
            } else {
                Selection.removeSelection(buffer)
            }
        }

        // Handle scrolling
        return super.onTouchEvent(widget, buffer, event)
    }
}

fun Context.isBrowserAvailable(): Boolean {
    val packageManager = this.packageManager
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_URL))
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolveInfo != null
}