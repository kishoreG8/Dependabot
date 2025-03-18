package com.trimble.ttm.formlibrary.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentManager
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.IMG_UNIQUE_ID
import com.trimble.ttm.commons.utils.IMG_VIEW_ID
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.anim.SlideDownAnimation
import com.trimble.ttm.formlibrary.anim.SlideUpAnimation
import com.trimble.ttm.formlibrary.databinding.FormImageLayoutBinding
import com.trimble.ttm.formlibrary.eventbus.EventBus
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.ENCODED_IMAGE_SHARE_IN_FORM
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_IN_FORM_KEY
import com.trimble.ttm.formlibrary.ui.fragments.ImageReferenceDialogFragment
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.IMAGE_REFERENCE_RESULT
import com.trimble.ttm.formlibrary.utils.IS_FORM_FIELD_DRIVER_EDITABLE
import com.trimble.ttm.formlibrary.utils.UiUtil.decodeBase64StringToBitmap
import com.trimble.ttm.formlibrary.utils.UiUtil.setTextViewDrawableColor
import com.trimble.ttm.formlibrary.viewmodel.PreviewImageViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

private const val FRAGMENT_TAG_IMAGE_DIALOG = "dialog_signature"

@SuppressLint("ViewConstructor")
class FormImageReference(
    private val context: Context,
    val formField: FormField,
    private val formSaved: Boolean,
    private val stopId: Int,
    private val viewId: Int,
    val formDataStoreManager: FormDataStoreManager,
    val lifecycleScope: CoroutineScope,
    val supportFragmentManager: FragmentManager
) : LinearLayout(context), KoinComponent {
    private var imageFile: File? = null
    private var formImageReferenceBinding: FormImageLayoutBinding =
        FormImageLayoutBinding.inflate(LayoutInflater.from(context), this)
    private val tag = "FormImageReference"

    private val previewImageViewModel: PreviewImageViewModel by inject()
    private val appModuleCommunicator: AppModuleCommunicator by inject()

    init {
        setUpView()
    }

    fun setUpView() {
        Log.d(tag, "Setting up view")
        if (formField.driverEditable != IS_FORM_FIELD_DRIVER_EDITABLE) {
            this.isEnabled = false
            this.isFocusable = false
            this.isClickable = false
            this.background = ResourcesCompat.getDrawable(resources, R.drawable.noneditable_rounded_border_drawable, context.theme )
            formImageReferenceBinding.tvQtext.setTextColor(ContextCompat.getColor(context, R.color.dark_gray))
            formImageReferenceBinding.tvQtext.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.upload, 0)
            setTextViewDrawableColor(formImageReferenceBinding.tvQtext, R.color.dark_gray)
        } else {
            formImageReferenceBinding.tvQtext.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.upload, 0)
            if(formField.required == REQUIRED && formSaved.not())
                this.background = ResourcesCompat.getDrawable(resources, R.drawable.error_rounded_border_drawable, context.theme)
            else this.background = ResourcesCompat.getDrawable(resources, R.drawable.rounded_border_drawable, context.theme)
        }
        formImageReferenceBinding.imageReferenceLayout.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT, 150
        )
        setLayoutParameters(context)
        formImageReferenceBinding.rlImageLayout.visibility = if (formField.uiData.isEmpty()) View.GONE else View.VISIBLE
        formImageReferenceBinding.tvQtext.apply {
            if (formField.qtext.plus(formField.qnum) == formField.uiData) formField.uiData = EMPTY_STRING
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
        formImageReferenceBinding.ivRemove.setOnClickListener {
            lifecycleScope.launch(CoroutineName(tag) + Dispatchers.Main){
                if (!formSaved) {
                    slideDownAnimation(formImageReferenceBinding.ivUploadableImage)
                    formField.uiData = ""
                    formField.uniqueIdentifier = ""
                    formDataStoreManager.removeItem(stringPreferencesKey("form_image$stopId$viewId"))
                    if (formDataStoreManager.containsKey(ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID)) formDataStoreManager.removeItem(
                        ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID
                    )
                    if (formDataStoreManager.containsKey(ENCODED_IMAGE_SHARE_IN_FORM)) formDataStoreManager.removeItem(
                        ENCODED_IMAGE_SHARE_IN_FORM
                    )
                    imageFile = null
                }
            }
        }
        showImage()
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
                formImageReferenceBinding.rlImageLayout.visibility = View.GONE
                formImageReferenceBinding.tvQtext.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(animation: Animation) {
                //Ignore
            }
        })
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

        setOnClickListener {
            lifecycleScope.launch(CoroutineName(tag) + Dispatchers.Main){
                formDataStoreManager.setEncodedImage(stopId, viewId, formField.uiData)
                if (!formSaved) {
                    ImageReferenceDialogFragment.newInstance(formField, stopId, formDataStoreManager).apply {
                        show(
                            supportFragmentManager.beginTransaction(),
                            FRAGMENT_TAG_IMAGE_DIALOG
                        )
                    }
                }
            }
        }
    }

    private fun showImage() {
        if (formField.uiData.isNotEmpty()) {
            convertImageStringToImageAndSetInImageView(formField.uiData)
        }
    }

    private fun convertImageStringToImageAndSetInImageView(imageData: String) {
        decodeBase64StringToBitmap(imageData)?.let {
            formImageReferenceBinding.tvQtext.visibility = View.INVISIBLE
            formImageReferenceBinding.rlImageLayout.visibility = View.VISIBLE
            slideUpAnimation(formImageReferenceBinding.ivUploadableImage)
            formImageReferenceBinding.ivUploadableImage.setImageBitmap(it)
            if(formField.required == REQUIRED && formSaved.not()) this.background = ResourcesCompat.getDrawable(resources, R.drawable.error_rounded_border_drawable, context.theme)
            else this.background = ResourcesCompat.getDrawable(resources, R.drawable.rounded_border_drawable, context.theme)
            formImageReferenceBinding.errorText.visibility = View.GONE
        } ?: formImageReferenceBinding.tvQtext.visibility.apply {
            formImageReferenceBinding.rlImageLayout.visibility = View.INVISIBLE
            View.VISIBLE
        }
    }

    override fun onAttachedToWindow() {
        lifecycleScope.launch(CoroutineName(tag) + Dispatchers.IO) {
                EventBus.events.filter { it.action == IMAGE_REFERENCE_RESULT }.collectLatest { intent ->
                    formDataStoreManager.setValue(IS_IN_FORM_KEY, true)
                    val docId = intent.getStringExtra(IMG_UNIQUE_ID) ?: ""
                    val cid = appModuleCommunicator.doGetCid() // Cache if possible
                    val truckNumber =
                        appModuleCommunicator.doGetTruckNumber() // Cache if possible
                    Log.d(tag, "FormImageReference onAttachedToWindow uniqueIdentifier : $docId")
                    previewImageViewModel.fetchThumbnailImage(cid, truckNumber, docId, tag)
                        .let { encodedImage ->
                            if (encodedImage.isNotEmpty()) {
                                val viewId = intent.getIntExtra(IMG_VIEW_ID, -1)
                                if (viewId == formField.viewId) {
                                    formField.uiData = encodedImage
                                    formField.uniqueIdentifier = docId
                                    formField.needToSyncImage = true
                                    withContext(Dispatchers.Main) {
                                        showImage()
                                    }
                                    formDataStoreManager.setValue(
                                        ENCODED_IMAGE_SHARE_IN_FORM, encodedImage
                                    )
                                    formDataStoreManager.setValue(
                                        ENCODED_IMAGE_SHARE_IN_FORM_VIEW_ID, viewId.toString()
                                    )
                                }
                                else{
                                    Log.d(tag, "Received viewId is not matched ${viewId}, should match ${formField.viewId}")
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    ToastCompat.makeText(
                                        context,
                                        context.getString(R.string.image_cannot_be_displayed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                }
        }
        super.onAttachedToWindow()
    }
}