package com.trimble.ttm.formlibrary.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.trimble.ttm.commons.composable.androidViews.SignatureCanvasView
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.GzipCompression
import com.trimble.ttm.commons.utils.SIGN_STOP_ID
import com.trimble.ttm.commons.utils.SIGN_VIEW_ID
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.utils.NEWLINE
import com.trimble.ttm.formlibrary.utils.SIGNATURE_RESULT_BYTE_ARRAY
import com.trimble.ttm.formlibrary.utils.SIGNATURE_RESULT_RECEIVER
import com.trimble.ttm.formlibrary.utils.ext.showLongToast
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.formlibrary.viewmodel.CustomSignatureDialogViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent


private const val SIGNATURE_CONTENT = "signature_content"
private const val SIGNATURE_VIEW_PADDING = 30

class CustomSignatureDialogFragment : DialogFragment(), KoinComponent {
    private var signature: SignatureCanvasView? = null
    private var signatureContent: ByteArray? = null
    private var stopId: Int = -1
    private var viewId: Int = -1

    private val viewModel: CustomSignatureDialogViewModel by viewModel()
    private val logTag = "CustomSignatureDialogFragment"

    companion object {
        fun newInstance(
            byteArray: ByteArray?,
            stopId: Int,
            viewId: Int
        ): CustomSignatureDialogFragment =
            CustomSignatureDialogFragment().apply {
                arguments = Bundle().apply {
                    byteArray?.let {
                        putByteArray(SIGNATURE_CONTENT, it)
                    }
                    putInt(SIGN_STOP_ID, stopId)
                    putInt(SIGN_VIEW_ID, viewId)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        val params = dialog?.window?.attributes
        params?.width = ViewGroup.LayoutParams.MATCH_PARENT
        params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        val back = ColorDrawable(Color.TRANSPARENT)
        val inset = InsetDrawable(back, SIGNATURE_VIEW_PADDING)
        dialog?.window?.setBackgroundDrawable(inset)
        dialog?.window?.attributes = params as android.view.WindowManager.LayoutParams
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        return inflater.inflate(R.layout.layout_dialog_form_signature, container, false).apply {
            viewModel.getCachedSignatureByteArray().let { byteArray ->
                if (byteArray.isNotEmpty()) {
                    signatureContent = byteArray
                }
            }
            arguments?.let { arg ->
                if (signatureContent.isNull()) {
                    signatureContent = arg.getByteArray(SIGNATURE_CONTENT)
                }
                stopId = arg.getInt(SIGN_STOP_ID, -1)
                viewId = arg.getInt(SIGN_VIEW_ID, -1)
            }
            signature = SignatureCanvasView(context, null, signatureContent)
            findViewById<LinearLayout>(R.id.ll_signature_view).addView(signature)
            findViewById<Button>(R.id.buttonClear).setOnClickListener {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag signature clear button clicked")
                signature?.clearCanvas()
                viewModel.clearCachedSignatureByteArray()
            }
            findViewById<Button>(R.id.buttonCancel).setOnClickListener {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag signature cancel button clicked")
                Intent(SIGNATURE_RESULT_RECEIVER).apply {
                    LocalBroadcastManager.getInstance(context)
                        .sendBroadcast(this)
                }
                viewModel.clearCachedSignatureByteArray()
            }
            findViewById<Button>(R.id.buttonSave).setOnClickListener {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag signature save button clicked")
                signature?.getBytes()?.let { signature ->
                    GzipCompression.gzipCompress(signature).let { compressedSignature ->
                        Base64.encodeToString(
                            compressedSignature,
                            Base64.DEFAULT
                        )?.let { base64String ->
                            Intent(SIGNATURE_RESULT_RECEIVER).apply {
                                putExtra(
                                    SIGNATURE_RESULT_BYTE_ARRAY,
                                    base64String.replace(NEWLINE, "")
                                )
                                putExtra(SIGN_VIEW_ID, viewId)
                                LocalBroadcastManager.getInstance(context).sendBroadcast(this)
                            }
                        }
                            ?: context.showLongToast(R.string.could_not_process)
                    }
                }
                    ?: context.showLongToast(R.string.signature_empty_customer_warning_text)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.logLifecycle(logTag, "$logTag onDestroyView")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        signature?.getBytes()?.let { byteArray ->
            viewModel.cacheSignatureByteArray(byteArray)
        }
        super.onSaveInstanceState(outState)
    }
}