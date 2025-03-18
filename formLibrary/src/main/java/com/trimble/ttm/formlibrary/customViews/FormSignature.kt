package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.GzipCompression
import com.trimble.ttm.commons.utils.SIGN_VIEW_ID
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.anim.SlideDownAnimation
import com.trimble.ttm.formlibrary.anim.SlideUpAnimation
import com.trimble.ttm.formlibrary.databinding.FormImageLayoutBinding
import com.trimble.ttm.formlibrary.ui.fragments.CustomSignatureDialogFragment
import com.trimble.ttm.formlibrary.utils.IS_FORM_FIELD_DRIVER_EDITABLE
import com.trimble.ttm.formlibrary.utils.SIGNATURE_RESULT_BYTE_ARRAY
import com.trimble.ttm.formlibrary.utils.SIGNATURE_RESULT_RECEIVER

private const val FRAGMENT_TAG_SIGNATURE_DIALOG = "dialog_signature"

@SuppressLint("ViewConstructor")
class FormSignature(
    private val context: Context,
    private val formField: FormField,
    formSaved: Boolean,
    private var stopId: Int,
    val supportFragmentManager: FragmentManager
) : LinearLayout(context) {

    /**Member to hold the Signature byte array if available in the canvas view or coming from form field data*/
    private var signatureByteArray: ByteArray? = null
    private var formSignatureBinding: FormImageLayoutBinding =
        FormImageLayoutBinding.inflate(LayoutInflater.from(context), this)
    private var formSaved = false

    init {
        this.formSaved = formSaved
        if (formField.driverEditable != IS_FORM_FIELD_DRIVER_EDITABLE) {
            this.isEnabled = false
            this.isFocusable = false
            this.isClickable = false
            this.background = ResourcesCompat.getDrawable(resources, R.drawable.noneditable_rounded_border_drawable, context.theme)
            formSignatureBinding.tvQtext.setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
        } else {
            if(formField.required == REQUIRED && formSaved.not())
                this.background = ResourcesCompat.getDrawable(resources, R.drawable.error_rounded_border_drawable, context.theme)
            else this.background = ResourcesCompat.getDrawable(resources, R.drawable.rounded_border_drawable, context.theme)
        }
        formSignatureBinding.imageReferenceLayout.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, 250
        )
        formSignatureBinding.ivRemove.setOnClickListener {
            if (!formSaved) {
                slideDownAnimation(formSignatureBinding.ivUploadableImage)
                formField.uiData = ""
                signatureByteArray = null
            }
        }

        formSignatureBinding.rlImageLayout.visibility = if (formField.uiData.isEmpty()) View.GONE else View.VISIBLE
        formSignatureBinding.tvQtext.apply {
            visibility = if (formField.uiData.isEmpty()) View.VISIBLE else View.GONE
            text = if (formField.required == REQUIRED) String.format(
                resources.getString(R.string.component_hint_required),
                formField.qtext
            )
            else String.format(
                resources.getString(R.string.component_hint_optional),
                formField.qtext
            )
        }
        setLayoutParameters(context)
        setImageView(formField.uiData)
        formField.driverEditable.let {
            if (it == IS_FORM_FIELD_DRIVER_EDITABLE && !formSaved) setClickListeners()
        }
    }

    private fun setLayoutParameters(context: Context?) {
        val layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(
            context?.resources?.getDimension(R.dimen.defaultLeftRightMargin)!!.toInt(),
            context.resources?.getDimension(R.dimen.defaultTopBottomMargin)!!.toInt(),
            context.resources?.getDimension(R.dimen.defaultLeftRightMargin)!!.toInt(),
            context.resources?.getDimension(R.dimen.defaultTopBottomMargin)!!.toInt()
        )
        layoutParams.gravity = Gravity.CENTER
        this.layoutParams = layoutParams
    }

    private fun setClickListeners() {
        this.setOnClickListener {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            val signatureDialogFragment =
                CustomSignatureDialogFragment.newInstance(
                    signatureByteArray,
                    stopId,
                    formField.viewId
                )
            signatureDialogFragment.show(fragmentTransaction, FRAGMENT_TAG_SIGNATURE_DIALOG)
        }
    }

    private fun dismissDialogFragment() {
        val fragmentSignatureDialog =
            supportFragmentManager.findFragmentByTag(FRAGMENT_TAG_SIGNATURE_DIALOG)
        fragmentSignatureDialog?.let {
            val dialogFragment = fragmentSignatureDialog as DialogFragment?
            dialogFragment?.dismiss()
        }
    }

    private fun saveClicked(base64String: String?) {
        val dialogFragmentSignature =
            supportFragmentManager.findFragmentByTag(
                FRAGMENT_TAG_SIGNATURE_DIALOG
            )
        dialogFragmentSignature?.let {
            val dialogFragment = dialogFragmentSignature as DialogFragment?
            dialogFragment?.dismiss()
        }
        base64String?.let {
            formField.uiData = it
            setImageView(it)
        }
    }

    private fun setImageView(base64String: String) {
        val byteArray: ByteArray = Base64.decode(base64String, 0)
        GzipCompression.gzipDecompress(byteArray).let { signatureByteArray ->
            BitmapFactory.decodeByteArray(signatureByteArray, 0, signatureByteArray.size)?.let {
                formField.signViewHeight = it.height
                formField.signViewWidth = it.width
                this.signatureByteArray = signatureByteArray
                formSignatureBinding.rlImageLayout.visibility = View.VISIBLE
                slideUpAnimation(formSignatureBinding.ivUploadableImage)
                formSignatureBinding.tvQtext.visibility = View.GONE
                formSignatureBinding.ivUploadableImage.setImageBitmap(it)
                if(formField.required == REQUIRED && formSaved.not()) this.background = ResourcesCompat.getDrawable(resources, R.drawable.error_rounded_border_drawable, context.theme)
                else this.background = ResourcesCompat.getDrawable(resources, R.drawable.rounded_border_drawable, context.theme)
                formSignatureBinding.errorText.visibility = View.GONE
            } ?: formSignatureBinding.tvQtext.visibility.apply {
                formSignatureBinding.rlImageLayout.visibility = View.GONE
                View.VISIBLE
            }
        }
    }

    private fun slideUpAnimation(view: View) {
        view.visibility = View.VISIBLE
        view.startAnimation(SlideUpAnimation(view.height.toFloat()))
    }

    private fun slideDownAnimation(view: View) {
        val slideDownAnimation = SlideDownAnimation(view.height.toFloat())
        view.startAnimation(slideDownAnimation)
        slideDownAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                //Ignore
            }
            override fun onAnimationEnd(animation: Animation) {
                formSignatureBinding.rlImageLayout.visibility = View.GONE
                formSignatureBinding.tvQtext.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(animation: Animation) {
                //Ignore
            }
        })
    }


    override fun onAttachedToWindow() {
        LocalBroadcastManager.getInstance(context).registerReceiver(
            signatureResultReceiver,
            IntentFilter(SIGNATURE_RESULT_RECEIVER)
        )
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(signatureResultReceiver)
        super.onDetachedFromWindow()
    }

    //ToDo Revisit code for any alternate implementation
    private val signatureResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SIGNATURE_RESULT_RECEIVER) {
                if (intent.getStringExtra(SIGNATURE_RESULT_BYTE_ARRAY) != null && intent.getIntExtra(
                        SIGN_VIEW_ID,
                        -1
                    ) == formField.viewId
                ) {
                    saveClicked(intent.getStringExtra(SIGNATURE_RESULT_BYTE_ARRAY))
                } else {
                    dismissDialogFragment()
                }

            }
        }

    }
}