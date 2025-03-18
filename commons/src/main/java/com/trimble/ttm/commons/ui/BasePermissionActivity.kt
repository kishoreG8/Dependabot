package com.trimble.ttm.commons.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.ext.safeLaunch
import kotlinx.coroutines.CoroutineName

private const val ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 5469

/**
 * BasePermissionActivity is for requesting Overlay and Notification Permissions
 */
abstract class BasePermissionActivity : AppCompatActivity() {

    private var permissionsRequiredDialog: AlertDialog? = null
    private val basePermissionActivityTag = "BasePermissionActivity"

    override fun onResume() {
        super.onResume()
        lifecycleScope.safeLaunch(CoroutineName(basePermissionActivityTag)) {
            if (Settings.canDrawOverlays(this@BasePermissionActivity).not())
                showOverlayPermissionsRequiredDialog()
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkIfPermissionIsGranted().not()) {
                // Notifications are off by default from Android 13 so requesting notification permission
                showNotificationPermissionDialog()
            } else onAllPermissionsGranted()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkIfPermissionIsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun showNotificationPermissionDialog() {
        permissionsRequiredDialog = UiUtils.showAlertDialog(
            AlertDialogData(
                this,
                title = getString(R.string.permission_text),
                message = getString(R.string.notification_permission_message),
                positiveActionText = getString(R.string.go_to_settings),
                negativeActionText = EMPTY_STRING,
                isCancelable = false,
                positiveAction = { redirectToSettings() },
                negativeAction = {})
        )
    }

    override fun onPause() {
        super.onPause()
        permissionsRequiredDialog?.dismiss()
        permissionsRequiredDialog = null
    }

    private fun showOverlayPermissionsRequiredDialog() {
        permissionsRequiredDialog = UiUtils.showAlertDialog(
            AlertDialogData(
                this,
                title = getString(R.string.permission_text),
                message = getString(R.string.permission_msg),
                positiveActionText = getString(R.string.ok_text),
                negativeActionText = EMPTY_STRING,
                isCancelable = false,
                positiveAction = { checkOverlayPermission() },
                negativeAction = {})
        )
    }

    private fun checkOverlayPermission() {
        if (Settings.canDrawOverlays(this).not()) {
            startActivityForPermission(
                ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION
            )
        }

    }

    private fun redirectToSettings() {
        startActivityForPermission(
            -1,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        )
    }

    private fun startActivityForPermission(requestCode: Int = -1, settingsAction: String) {
        try {
            Intent(
                settingsAction,
                Uri.parse("package:$packageName")
            ).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }.also {
                if (requestCode > 0) {
                    try {
                        startForResult.launch(it)
                    } catch (ex: ActivityNotFoundException) {
                        Log.e(
                            basePermissionActivityTag,
                            "Application not installed : ${intent.action}"
                        )
                    }
                } else
                    startActivity(it)
            }
        } catch (e: Exception) {
            // Any exception occurs, open phone settings
            Intent(Settings.ACTION_SETTINGS).also {
                startActivity(it)
            }
        }
    }

    protected abstract suspend fun onAllPermissionsGranted()

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                // Refactor startForResult when overlay permission is removed
                Log.i(basePermissionActivityTag, "Activity result of overlay permission Cancelled")
            }
        }
}
