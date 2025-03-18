package com.trimble.ttm.formlibrary.customViews

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.ProgressErrorLayoutBinding
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.show

enum class STATE {
    PROGRESS,
    ERROR,
    NONE
}

const val PROGRESS_ANIM_DURATION = 500

class ProgressErrorView(context: Context, attributeSet: AttributeSet) :
    LinearLayout(context, attributeSet) {

    var currentState = STATE.NONE
    private var progressErrorView: ProgressErrorLayoutBinding =
        ProgressErrorLayoutBinding.inflate(LayoutInflater.from(context), this)

    init {

        this.orientation = VERTICAL
        this.visibility = View.GONE
        this.setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimary))
    }

    fun setStateText(text: String) {
        progressErrorView.tvStateText.text = text
    }

    fun setState(state: STATE) {
        currentState = state
        when (state) {
            STATE.PROGRESS -> {
                this.show( 0)
                progressErrorView.progressBar.show(0)
            }
            STATE.ERROR -> {
                this.show( 0)
                progressErrorView.progressBar.hide(PROGRESS_ANIM_DURATION)
            }
            STATE.NONE -> this.hide( PROGRESS_ANIM_DURATION)
        }
    }
}

fun ProgressErrorView.setProgressState(stateText: String) {
    this.setState(STATE.PROGRESS)
    this.setStateText(stateText)
}

fun ProgressErrorView.setErrorState(stateText: String) {
    this.setState(STATE.ERROR)
    this.setStateText(stateText)
}

fun ProgressErrorView.setNoState() {
    this.setState(STATE.NONE)
}