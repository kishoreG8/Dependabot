package com.trimble.ttm.formlibrary.ui.fragments

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.zxing.integration.android.IntentIntegrator
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.BARCODE_VIEW_ID
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.databinding.LayoutDialogFormBarCodeScannerBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.utils.BARCODE_RESULT_RECEIVER
import com.trimble.ttm.formlibrary.utils.BARCODE_RESULT_STRING_ARRAY
import com.trimble.ttm.formlibrary.utils.CAMERA_PERMISSION_REQUEST_CODE
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import me.drakeet.support.toast.ToastCompat


private const val DIALOG_VIEW_PADDING = 30
private const val SAVED_STATE = "saved_state"
private const val SAVED_STATE_SCANNED_CONTENT = "scanned_barcode"
private const val SCAN_DESCRIPTION = "scan_description_text"
private const val EXISTING_BAR_CODE_LIST = "existing_bar_code_list"
private const val FORM_FIELD = "form_field"

class CustomBarCodeScannerDialogFragment : DialogFragment() {

    private lateinit var viewBarCodeParent: LinearLayout
    private var isFurtherScanningAllowed: Boolean = true
    private var scannedBarCodeContentList: ArrayList<String>? = null
    private lateinit var formField: FormField

    private lateinit var layoutDialogFormBarCodeScannerBinding: LayoutDialogFormBarCodeScannerBinding
    private val logTag = "CustomBarCodeScannerDialogFragment"

    companion object {
        private lateinit var formDataStoreManager: FormDataStoreManager
        private lateinit var uiScope: CoroutineScope
        fun newInstance(
            formField: FormField?,
            formDataStoreManager: FormDataStoreManager,
            uiScope: CoroutineScope
        ): CustomBarCodeScannerDialogFragment =
            CustomBarCodeScannerDialogFragment().apply {
                this@Companion.formDataStoreManager = formDataStoreManager
                this@Companion.uiScope = uiScope
                arguments = Bundle().apply {
                    var arrayListBarCode = java.util.ArrayList<String>()
                    formField?.uiData?.let {
                        if (it.isNotEmpty()) {
                            arrayListBarCode =
                                it.split(",").map { barCodeList -> barCodeList.trim() } as java.util.ArrayList<String>
                        }
                    }
                    putStringArrayList(
                        EXISTING_BAR_CODE_LIST,
                        arrayListBarCode
                    )
                    formField?.description?.let {
                        putString(SCAN_DESCRIPTION, it)
                    }
                    putParcelable(FORM_FIELD, formField)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        layoutDialogFormBarCodeScannerBinding = LayoutDialogFormBarCodeScannerBinding.inflate(inflater, container, false)
        return layoutDialogFormBarCodeScannerBinding.root
            .apply {
                scannedBarCodeContentList = ArrayList()
                savedInstanceState?.getBundle(SAVED_STATE)?.let {
                    scannedBarCodeContentList?.clear()
                    scannedBarCodeContentList?.addAll(
                        it.getStringArrayList(
                            SAVED_STATE_SCANNED_CONTENT
                        ) as Collection<String>
                    )
                }
                arguments?.getParcelable<Parcelable>(FORM_FIELD)?.let {
                    formField = it as FormField
                }
                if (scannedBarCodeContentList?.size == 0) {
                    scannedBarCodeContentList?.clear()
                    scannedBarCodeContentList?.addAll(
                        arguments?.getStringArrayList(EXISTING_BAR_CODE_LIST)!!
                    )
                }
                viewBarCodeParent = layoutDialogFormBarCodeScannerBinding.llBarcodeView
                arguments?.getString(SCAN_DESCRIPTION)?.let {
                    layoutDialogFormBarCodeScannerBinding.tvTap.text = context?.let {  context ->
                        String.format(
                            context.getString(R.string.scan_bar_code_description_place_holder),
                            it
                        )
                    }
                }

                layoutDialogFormBarCodeScannerBinding.buttonCancel.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag cancel button clicked")
                    lifecycleScope.safeLaunch(CoroutineName("$tag Cancel button click")) {
                        formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
                    }
                    Intent(BARCODE_RESULT_RECEIVER).apply {
                        context?.let { it1 ->
                            LocalBroadcastManager.getInstance(it1)
                                .sendBroadcast(this)
                        }
                    }
                }

                layoutDialogFormBarCodeScannerBinding.buttonSave.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag save button clicked")
                    lifecycleScope.safeLaunch(CoroutineName("$tag Barcode scanner save")) {
                        formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
                    }
                    Intent(BARCODE_RESULT_RECEIVER).apply {
                        putExtra(BARCODE_RESULT_STRING_ARRAY, scannedBarCodeContentList)
                        putExtra(BARCODE_VIEW_ID, formField.viewId)
                        context?.let { it1 ->
                            LocalBroadcastManager.getInstance(it1)
                                .sendBroadcast(this)
                        }
                    }
                }

                layoutDialogFormBarCodeScannerBinding.llScanBarcode.setOnClickListener {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag scan barcode clicked")
                    lifecycleScope.safeLaunch(CoroutineName("$tag Scan barcode click")) {
                        formDataStoreManager.setValue(IS_IN_FORM_KEY, false)
                    }
                    if (!isFurtherScanningAllowed)
                        ToastCompat.makeText(
                            context,
                            context?.getString(R.string.scan_bar_code_error_exceed_count),
                            Toast.LENGTH_LONG
                        ).show()
                    else {
                        if (context?.let { it1 ->
                                checkSelfPermission(
                                    it1,
                                    android.Manifest.permission.CAMERA
                                )
                            }
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            openScanView()
                        } else {
                            requestPermissions(
                                arrayOf(android.Manifest.permission.CAMERA),
                                CAMERA_PERMISSION_REQUEST_CODE
                            )
                        }

                    }
                }

                scannedBarCodeContentList?.isNotEmpty().let {
                    viewBarCodeParent.removeAllViews()
                    scannedBarCodeContentList?.forEach {
                        inflateScannedBarCodeViews(it, false)
                    }
                }
            }

    }

    private fun openScanView() {
        IntentIntegrator.forSupportFragment(this@CustomBarCodeScannerDialogFragment)
            .apply {
                this.setOrientationLocked(false)
                this.setBeepEnabled(false)
                this.setPrompt(context?.getString(R.string.scan_bar_code_info))
                startForResult.launch(this.createScanIntent())
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
                openScanView()
            } else {
                ToastCompat.makeText(
                    context,
                    getString(R.string.request_permission_camera_error),
                    Toast.LENGTH_LONG
                ).show()
            }

        }
    }
    private val startForResult  = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {

        IntentIntegrator.parseActivityResult(it.resultCode, it.data)?.contents?.let { result ->
            if (isThereDuplicateEntry(result)
                && isBarcodeLengthValid(result.length)
            ) inflateScannedBarCodeViews(result, true)
        }
    }

    override fun onDestroy() {
        uiScope.safeLaunch  {
            formDataStoreManager.setValue(
                IS_IN_FORM_KEY,
                true
            )
        }
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
    }

    /**
     * Duplicate entry check,if bcAllowDuplicate==0,duplicate entries are not allowed.
     */
    private fun isThereDuplicateEntry(
        resultContent: String
    ): Boolean {
        formField.bcAllowDuplicate?.let {
            if (formField.bcAllowDuplicate == 0 && !scannedBarCodeContentList.isNullOrEmpty()
            ) {
                scannedBarCodeContentList!!.forEach {
                    if (it.equals(resultContent, false)) {
                        ToastCompat.makeText(
                            context,
                            getString(R.string.scan_bar_code_error_duplicate),
                            Toast.LENGTH_LONG
                        ).show()
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun isBarcodeLengthValid(
        resultContentLength: Int
    ): Boolean {
        if ((formField.bcMaxLength != 0 && resultContentLength > formField.bcMaxLength)
            || (formField.bcMinLength != 0 && resultContentLength < formField.bcMinLength)
        ) {
            ToastCompat.makeText(
                context,
                getString(R.string.scan_bar_code_error_length),
                Toast.LENGTH_LONG
            ).show()

            return false
        }
        return true
    }

    private fun inflateScannedBarCodeViews(
        scanContent: String,
        isNotAfterViewDestroyed: Boolean
    ) {
        layoutInflater.inflate(R.layout.item_barcode, null).apply {
            this.findViewById<TextView>(R.id.tv_scanned_barcode).apply { text = scanContent }
        }.also { view ->
            if (isNotAfterViewDestroyed) {
                scannedBarCodeContentList?.add(scanContent)
            }
            view.findViewById<ImageView>(R.id.ivCancel).setOnClickListener {
                viewBarCodeParent.removeView(view)
                scannedBarCodeContentList?.remove(scanContent)
                isFurtherScanningAllowed = true
            }
            viewBarCodeParent.addView(view)
        }

        /**
         * Duplicate and multiple entries are only allowed if bcAllowMultiple==1,
         * otherwise the bcLimitMultiples and bcAllowDuplicate values will not be considered
         */
        if (viewBarCodeParent.childCount == 1 && formField.bcAllowMultiple == 0) {
            isFurtherScanningAllowed = false
            ToastCompat.makeText(
                context,
                getString(R.string.scan_bar_code_info_allowed_count),
                Toast.LENGTH_LONG
            ).show()
        }

        if (isFurtherScanningAllowed && !checkForAllowedMaximumNumberOfBarCodes()) {
            ToastCompat.makeText(
                context,
                getString(R.string.scan_bar_code_info_allowed_count),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkForAllowedMaximumNumberOfBarCodes(
    ): Boolean {
        var isNotExceedingMaximumNumberOfCodes = true
        formField.bcLimitMultiples.let {
            if (viewBarCodeParent.childCount >= formField.bcLimitMultiples) {
                isFurtherScanningAllowed = false
                isNotExceedingMaximumNumberOfCodes = false
            }
        }
        return isNotExceedingMaximumNumberOfCodes
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        dialog?.window?.attributes.apply {
            this?.width = ViewGroup.LayoutParams.MATCH_PARENT
            this?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }.also {
            dialog?.window?.setBackgroundDrawable(
                InsetDrawable(
                    ColorDrawable(Color.TRANSPARENT),
                    DIALOG_VIEW_PADDING
                )
            )
            dialog?.window?.attributes = it as android.view.WindowManager.LayoutParams
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(
            SAVED_STATE,
            Bundle().apply {
                putStringArrayList(
                    SAVED_STATE_SCANNED_CONTENT,
                    scannedBarCodeContentList
                )
            })
        super.onSaveInstanceState(outState)
    }
}