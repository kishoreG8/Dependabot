package com.trimble.ttm.formlibrary.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.IMG_STOP_ID
import com.trimble.ttm.commons.utils.IMG_UNIQUE_IDENTIFIER
import com.trimble.ttm.commons.utils.IMG_VIEW_ID
import com.trimble.ttm.formlibrary.databinding.LayoutImageReferenceDialogBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.model.MediaLaunchType
import com.trimble.ttm.formlibrary.ui.activities.ImportImageUriActivity
import com.trimble.ttm.formlibrary.ui.activities.PreviewImageActivity
import com.trimble.ttm.formlibrary.utils.MEDIA_LAUNCH_ACTION
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

private const val ENCODED_IMAGE = "ImagePath"
private const val SIGNATURE_VIEW_PADDING = 30
class ImageReferenceDialogFragment : DialogFragment() {
    private val logTag = "ImageReferenceDialogFrag"
    private var formField: FormField? = null
    private var stopId : Int = -1

    private lateinit var layoutImageReferenceDialogBinding: LayoutImageReferenceDialogBinding


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        layoutImageReferenceDialogBinding = LayoutImageReferenceDialogBinding.inflate(inflater, container, false)

        return layoutImageReferenceDialogBinding.root
            .apply {
                layoutImageReferenceDialogBinding.tvPreviewImage.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag Preview Image clicked")
                    lifecycleScope.launch(CoroutineName(logTag)) {
                        formDataStoreManager.setValue(IS_IN_FORM_KEY, false)
                        Intent(context, PreviewImageActivity::class.java).apply {
                            putExtra(IMG_VIEW_ID, formField?.viewId)
                            putExtra(IMG_STOP_ID, stopId)
                            putExtra(IMG_UNIQUE_IDENTIFIER, formField?.uniqueIdentifier)
                            startActivity(this)
                            dismiss()
                        }
                    }
                }
                layoutImageReferenceDialogBinding.tvGallery.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag Gallery clicked")
                    callActivity(MediaLaunchType.GALLERY.ordinal)
                }
                layoutImageReferenceDialogBinding.tvCamera.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag Camera clicked")
                    callActivity(MediaLaunchType.CAMERA.ordinal)
                }
                arguments?.getParcelable<Parcelable>(ENCODED_IMAGE)?.let {
                    formField = it as FormField
                }
                arguments?.getInt(IMG_STOP_ID)?.let {
                    stopId = it
                }
                formField?.let {
                    layoutImageReferenceDialogBinding.tvPreviewImage.visibility =
                        if (it.uiData.isEmpty()) View.GONE else View.VISIBLE
                } ?:  layoutImageReferenceDialogBinding.tvPreviewImage.visibility.apply { View.GONE }
            }
    }

    private fun callActivity(ordinal: Int) {
        lifecycleScope.launch(CoroutineName(logTag)) {
            formDataStoreManager.setValue(IS_IN_FORM_KEY, false)
            Intent(activity, ImportImageUriActivity::class.java).apply {
                putExtra(MEDIA_LAUNCH_ACTION, ordinal)
                putExtra(IMG_VIEW_ID, formField?.viewId)
                putExtra(IMG_STOP_ID, stopId)
                putExtra(IMG_UNIQUE_IDENTIFIER, formField?.uniqueIdentifier)
                startActivity(this)
            }
            dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        val params = dialog?.window?.attributes?.apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        dialog?.window?.setBackgroundDrawable(
            InsetDrawable(
                ColorDrawable(Color.TRANSPARENT),
                SIGNATURE_VIEW_PADDING
            )
        )
        dialog?.window?.attributes = params as android.view.WindowManager.LayoutParams
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
    }

    companion object {
        private lateinit var formDataStoreManager: FormDataStoreManager
        fun newInstance(
            formField: FormField,
            stopId: Int,
            formDataStoreManager: FormDataStoreManager
        ): ImageReferenceDialogFragment =
            ImageReferenceDialogFragment().apply {
                this@Companion.formDataStoreManager = formDataStoreManager
                arguments = Bundle().apply {
                    putParcelable(ENCODED_IMAGE, formField)
                    putInt(IMG_STOP_ID, stopId)
                }
            }
    }
}