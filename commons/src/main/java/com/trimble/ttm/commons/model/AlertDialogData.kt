package com.trimble.ttm.commons.model

import android.content.Context
import android.graphics.Color
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.utils.EMPTY_STRING

data class AlertDialogData(val context: Context,
                           val message: String,
                           val positiveActionText: String = EMPTY_STRING,
                           val negativeActionText: String = EMPTY_STRING,
                           var positiveButtonColor: Int = Color.WHITE,
                           var negativeButtonColor: Int = Color.WHITE,
                           val isCancelable: Boolean,
                           val positiveAction: (() -> Unit) = {},
                           val negativeAction: (() -> Unit) = {},
                           val title: String = EMPTY_STRING,
                           val theme: Int = R.style.PermissionDialogTheme)