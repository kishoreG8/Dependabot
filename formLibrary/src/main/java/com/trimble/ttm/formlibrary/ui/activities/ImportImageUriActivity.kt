package com.trimble.ttm.formlibrary.ui.activities

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.IMG_STOP_ID
import com.trimble.ttm.commons.utils.IMG_UNIQUE_ID
import com.trimble.ttm.commons.utils.IMG_UNIQUE_IDENTIFIER
import com.trimble.ttm.commons.utils.IMG_VIEW_ID
import com.trimble.ttm.commons.utils.STORAGE
import com.trimble.ttm.formlibrary.BuildConfig
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.eventbus.EventBus
import com.trimble.ttm.formlibrary.model.MediaLaunchType
import com.trimble.ttm.formlibrary.utils.CAMERA_PERMISSION_REQUEST_CODE
import com.trimble.ttm.formlibrary.utils.IMAGE_REFERENCE_RESULT
import com.trimble.ttm.formlibrary.utils.MEDIA_LAUNCH_ACTION
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ext.showLongToast
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.viewmodel.ImportImageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import java.util.UUID

class ImportImageUriActivity : AppCompatActivity(), KoinComponent {
    private val tag = "ImportImageUriAct"
    var viewId: Int = -1
    var stopId: Int = -1
    private var uniqueId: String = ""

    private val importImageViewModel: ImportImageViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        viewId = intent.getIntExtra(IMG_VIEW_ID, -1)
        stopId = intent.getIntExtra(IMG_STOP_ID, -1)
        uniqueId = intent.getStringExtra(IMG_UNIQUE_IDENTIFIER) ?: EMPTY_STRING

        if (intent.getIntExtra(MEDIA_LAUNCH_ACTION, -1)
            == MediaLaunchType.CAMERA.ordinal
        ) {
            checkPermissionaAndLaunchCamera()
        } else if (intent.getIntExtra(MEDIA_LAUNCH_ACTION, -1)
            == MediaLaunchType.GALLERY.ordinal
        ) {
            launchGallery()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }


    private fun launchGallery() {
        imageReferenceRequest.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        })
    }

    private fun checkPermissionaAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }


    private fun launchCamera() {
        Utils.getOutputMediaFile(this.applicationContext)?.let { importImageViewModel.setFile(it) } ?: return
        val outputFileUri = FileProvider.getUriForFile(
            this, BuildConfig.LIBRARY_PACKAGE_NAME + ".provider",
            importImageViewModel.file!!
        )
        try {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri)
                }
                .also {
                    cameraImageReferenceRequest.launch(it)
                }
        } catch (e: ActivityNotFoundException) {
            Log.e(tag, "Camera application not found", e)
            showToast(R.string.camera_app_not_found)
        }
    }

    private val cameraImageReferenceRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                importImageViewModel.file?.toUri()?.let { uri ->
                    processImage(uri)
                }
            } else {
                showLongToast(R.string.camera_app_not_enabled_or_aborted)
                finish()
            }
        }

    private fun processImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            uniqueId = getUniqueId()
            try {
                if (importImageViewModel.saveImageLocally(Dispatchers.IO, uri, uniqueId)) {
                    postEncodedStringIntoEventBus(uniqueId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Show appropriate error message based on context
                    if (uri == importImageViewModel.file?.toUri()) { // Check if it's from camera
                        showLongToast(R.string.image_processing_error)
                    } else {
                        showToast(R.string.erorr_saving_image)
                    }
                }
                Log.d(tag, "Error processing image for $uniqueId : with error ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private val imageReferenceRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processImage(uri)
            }
        } else {
            showLongToast(R.string.image_not_valid)
            finish()
        }
    }

    private fun getUniqueId(): String {
        return uniqueId.ifEmpty { STORAGE + UUID.randomUUID().toString() }
    }

    private fun postEncodedStringIntoEventBus(uniqueId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(tag, "postEncodedStringIntoEventBus uniqueId $uniqueId viewID $viewId")
            Intent(IMAGE_REFERENCE_RESULT).apply {
                putExtra(IMG_VIEW_ID, viewId)
                putExtra(IMG_UNIQUE_ID, uniqueId)
            }.also {
                EventBus.postEvent(it)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            // Check Camera permission is granted or not
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                ToastCompat.makeText(
                    applicationContext,
                    getString(R.string.request_permission_camera_image_capture_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}