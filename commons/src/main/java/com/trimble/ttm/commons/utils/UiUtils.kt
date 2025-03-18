package com.trimble.ttm.commons.utils

import android.content.Context
import android.util.DisplayMetrics
import com.trimble.ttm.commons.model.AlertDialogData

object UiUtils {

    fun convertDpToPixel(dp: Float, context: Context) =
        dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)

    fun showAlertDialog(
        showAlertDialogData: AlertDialogData
    ): androidx.appcompat.app.AlertDialog {
        androidx.appcompat.app.AlertDialog.Builder(
            showAlertDialogData.context, showAlertDialogData.theme
        ).let { alertDialogBuilder ->
            if (showAlertDialogData.title.isNotEmpty())
                alertDialogBuilder.setTitle(showAlertDialogData.title)
            alertDialogBuilder.setMessage(showAlertDialogData.message)
            alertDialogBuilder.setPositiveButton(showAlertDialogData.positiveActionText) { dialog, _ ->
                dialog.dismiss()
                showAlertDialogData.positiveAction()
            }
            if (showAlertDialogData.negativeActionText.isNotEmpty()) {
                alertDialogBuilder.setNegativeButton(showAlertDialogData.negativeActionText) { dialog, _ ->
                    dialog.dismiss()
                    showAlertDialogData.negativeAction()
                }
            }
            alertDialogBuilder.create()
        }.also { alertDialog ->
            alertDialog.setCancelable(showAlertDialogData.isCancelable)
            alertDialog.show()
            //To Prevent window leak, return dialog.so that invoking class, can dismiss based on the lifecycle
            return alertDialog
        }
    }


}