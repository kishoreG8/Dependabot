package com.trimble.ttm.formlibrary.utils

import android.text.InputFilter
import android.text.Spanned


class DecimalDigitsInputFilter(private val digits: Int) : InputFilter {
    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        var dotPos = -1
        val len = dest.length
        for (i in 0 until len) {
            val c = dest[i]
            if (c == '.') {
                dotPos = i
                break
            }
        }
        if (dotPos >= 0) { // protects against many dots
            if (source == ".") {
                return ""
            }
            // if the text is entered before the dot
            if (dend <= dotPos) {
                return null
            }
            if (len - dotPos > digits) {
                return ""
            }
        }
        return null
    }
}
