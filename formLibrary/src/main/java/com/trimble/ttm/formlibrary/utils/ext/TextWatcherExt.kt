package com.trimble.ttm.formlibrary.utils.ext

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

fun EditText.textChanged(textChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            //Do nothing.
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            textChanged.invoke(p0.toString())
        }

        override fun afterTextChanged(editable: Editable?) {
            //Do nothing
        }
    })
}

fun EditText.afterTextChanged(afterTextChanged: (TextWatcher, String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            //Do nothing.
        }

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            //Do nothing
        }

        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(this, editable.toString())
        }
    })
}

