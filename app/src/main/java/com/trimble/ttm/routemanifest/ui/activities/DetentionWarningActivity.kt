package com.trimble.ttm.routemanifest.ui.activities

import android.app.AlertDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.utils.DETENTION_WARNING_TEXT
import org.koin.core.component.KoinComponent

class DetentionWarningActivity :
    AppCompatActivity(), KoinComponent {

    private var detentionWarningDialog: AlertDialog? = null
    private val tag = "DetentionWarningActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        setContentView(R.layout.activity_notification_redirection)

        detentionWarningDialog = AlertDialog.Builder(this, R.style
            .dtwDialogTheme)
            .setMessage(intent.getStringExtra(DETENTION_WARNING_TEXT))
            .setPositiveButton(R.string.dismiss) { _, _ ->
                detentionWarningDialog?.dismiss()
                finish()
            }
            .setCancelable(false)
            .create()
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        detentionWarningDialog?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

}