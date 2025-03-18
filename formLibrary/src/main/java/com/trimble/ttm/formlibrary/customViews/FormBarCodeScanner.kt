package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.InputType
import android.view.Gravity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.textfield.TextInputLayout
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.BARCODE_VIEW_ID
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.ui.fragments.CustomBarCodeScannerDialogFragment
import com.trimble.ttm.formlibrary.utils.BARCODE_RESULT_RECEIVER
import com.trimble.ttm.formlibrary.utils.BARCODE_RESULT_STRING_ARRAY
import com.trimble.ttm.formlibrary.utils.IS_FORM_FIELD_DRIVER_EDITABLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

private const val FRAGMENT_TAG_BAR_CODE_SCANNER_DIALOG = "dialog_bar_code_scan"

@SuppressLint("ViewConstructor")
class FormBarCodeScanner(
    context: Context,
    textInputLayout: TextInputLayout,
    formField: FormField,
    isEditable: Boolean,
    private val formDataStoreManager: FormDataStoreManager,
    val supportFragmentManager: FragmentManager
) : FormEditText(context, textInputLayout, formField, isEditable) {

    private val codeScannerJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.IO + codeScannerJob)

    init {
        setLayoutParameters()
        formFieldManager.isFormFieldNotEditable(isFormSaved,formField.driverEditable).let {
            if(it){
                formFieldManager.assignValueForEditableProperties(false)
                disableEditText()
            }
        }
    }

    private fun setLayoutParameters() {
        formField?.driverEditable.let {
            if (it == IS_FORM_FIELD_DRIVER_EDITABLE) {
                setClickListeners()
                this.isClickable = true
                this.isFocusable = false
                this.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_barcode, 0)
            } else {
                this.isEnabled = false
                this.isFocusable = false
                this.isClickable = false
                this.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_date_time_dark_grey, 0)
            }
        }
        this.textInputLayout?.editText?.gravity = Gravity.START or Gravity.CENTER_HORIZONTAL
        this.inputType = InputType.TYPE_NULL
    }

    private fun setClickListeners() {
        this.setOnClickListener {
            uiScope.safeLaunch {
                formDataStoreManager.setValue(IS_IN_FORM_KEY, false)
                CustomBarCodeScannerDialogFragment.newInstance(
                    formField,
                    formDataStoreManager,
                    uiScope
                ).apply {
                    this.show(
                        supportFragmentManager.beginTransaction(),
                        FRAGMENT_TAG_BAR_CODE_SCANNER_DIALOG
                    )
                }
            }
        }
    }

    private fun dismissDialogFragment() {
        val fragmentSignatureDialog =
            supportFragmentManager.findFragmentByTag(
                FRAGMENT_TAG_BAR_CODE_SCANNER_DIALOG
            )
        fragmentSignatureDialog?.let {
            val dialogFragment = fragmentSignatureDialog as DialogFragment?
            dialogFragment?.dismiss()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LocalBroadcastManager.getInstance(context).registerReceiver(
            barcodeResultReceiver,
            IntentFilter(BARCODE_RESULT_RECEIVER)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        LocalBroadcastManager.getInstance(context).unregisterReceiver(barcodeResultReceiver)
    }

    //ToDo Revisit code for any alternate implementation
    private val barcodeResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BARCODE_RESULT_RECEIVER) {
                if (intent.getStringArrayListExtra(BARCODE_RESULT_STRING_ARRAY) != null && intent.getIntExtra(
                        BARCODE_VIEW_ID,
                        -1
                    ) == formField.viewId
                ) {
                    saveClicked(intent.getStringArrayListExtra(BARCODE_RESULT_STRING_ARRAY))
                } else {
                    dismissDialogFragment()
                }
            }
        }
    }

    private fun saveClicked(stringArrayBarCodeList: ArrayList<String>?) {
        val dialogFragmentSignature =
            supportFragmentManager.findFragmentByTag(
                FRAGMENT_TAG_BAR_CODE_SCANNER_DIALOG
            )
        dialogFragmentSignature?.let {
            val dialogFragment = dialogFragmentSignature as DialogFragment?
            dialogFragment?.dismiss()
        }
        setScannedView(stringArrayBarCodeList)
    }

    private fun setScannedView(stringArrayBarCodeList: ArrayList<String>?) {
        stringArrayBarCodeList?.let {
            setText(it.joinToString(separator = ","))
            textInputLayout?.error = null
        }
    }

}