package com.trimble.ttm.formlibrary.ui.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.composable.commonComposables.LoadingScreen
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.IMG_STOP_ID
import com.trimble.ttm.commons.utils.IMG_UNIQUE_IDENTIFIER
import com.trimble.ttm.commons.utils.IMG_VIEW_ID
import com.trimble.ttm.commons.utils.STORAGE
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.ActivityImagePreviewBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.utils.UiUtil.decodeBase64StringToBitmap
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.viewmodel.PreviewImageViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PreviewImageActivity : AppCompatActivity(), KoinComponent {
    private var viewId: Int = -1
    private var stopId: Int = -1
    private var uniqueIdentifier: String = ""
    private val formDataStoreManager: FormDataStoreManager by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val previewImageViewModel: PreviewImageViewModel by inject()

    private lateinit var activityImagePreviewBinding: ActivityImagePreviewBinding
    private val tag = "PreviewImageActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        activityImagePreviewBinding = ActivityImagePreviewBinding.inflate(layoutInflater)
        setContentView(activityImagePreviewBinding.root)
        setUpToolbar()
        intent?.let {
            viewId = it.getIntExtra(IMG_VIEW_ID, -1)
            stopId = it.getIntExtra(IMG_STOP_ID, -1)
            uniqueIdentifier = it.getStringExtra(IMG_UNIQUE_IDENTIFIER) ?: ""
        }
        val composeView = findViewById<ComposeView>(R.id.compose_spinner_view)
        composeView.setContent {
            LoadingScreen(getString(R.string.loading_text), true)
        }

        lifecycleScope.launch(CoroutineName("$tag On create - set preview image")) {
            if (uniqueIdentifier.startsWith(STORAGE)) {
                composeView.visibility = View.VISIBLE
                try {
                    val bitmap = previewImageViewModel.fetchPreviewImage(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        uniqueIdentifier,
                        tag
                    )
                    if (bitmap != null) {
                        activityImagePreviewBinding.ivPreviewImage.setImageBitmap(bitmap)
                    } else {
                        showToast(R.string.image_cannot_be_displayed)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error while fetching image ${e.message}")
                }finally {
                    composeView.visibility = View.GONE
                }
            } else if (viewId != -1 && stopId != -1) {
                // TO remove after few releases. Storing image data into data store is not a recommended practice
                formDataStoreManager.getEncodedImage(stopId, viewId).let {
                    if (it.isNotEmpty())
                        activityImagePreviewBinding.ivPreviewImage.setImageBitmap(
                            decodeBase64StringToBitmap(it)
                        )
                }
            } else showToast(R.string.image_cannot_be_displayed)
        }
    }

    private fun setUpToolbar() {
        activityImagePreviewBinding.toolbar.setNavigationIcon(R.drawable.ic_cancel_white)
        setSupportActionBar(activityImagePreviewBinding.toolbar)
        supportActionBar?.title = ""
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                Log.logUiInteractionInInfoLevel(tag, "$tag onBackPress")
                super.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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
        Log.logLifecycle(tag, "$tag onDestroy")
        appModuleCommunicator.getAppModuleApplicationScope().safeLaunch(CoroutineName("$tag onDestroy")) {
            if (isChangingConfigurations.not()) formDataStoreManager.removeItem(stringPreferencesKey("form_image$stopId$viewId"))
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(IMG_VIEW_ID, viewId)
        outState.putInt(IMG_STOP_ID, stopId)
        outState.putString(IMG_UNIQUE_IDENTIFIER, uniqueIdentifier)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.let {
            viewId = it.getInt(IMG_VIEW_ID, -1)
            stopId = it.getInt(IMG_STOP_ID, -1)
            uniqueIdentifier = it.getString(IMG_UNIQUE_IDENTIFIER) ?: ""
        }
    }
}
