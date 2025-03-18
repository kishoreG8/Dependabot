package com.trimble.ttm.commons.composable.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.composable.androidViews.SignatureCanvasView
import com.trimble.ttm.commons.composable.commonComposables.CustomAnnotatedText
import com.trimble.ttm.commons.composable.commonComposables.CustomImage
import com.trimble.ttm.commons.composable.commonComposables.CustomSpacer
import com.trimble.ttm.commons.composable.commonComposables.CustomText
import com.trimble.ttm.commons.composable.commonComposables.CustomTextButton
import com.trimble.ttm.commons.composable.commonComposables.ImageViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.SpaceViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextButtonViewBuilder
import com.trimble.ttm.commons.composable.commonComposables.TextViewBuilder
import com.trimble.ttm.commons.composable.uiutils.styles.darkSlateBlue
import com.trimble.ttm.commons.composable.uiutils.styles.goldenRod
import com.trimble.ttm.commons.composable.utils.formfieldutils.checkIfTheFieldIsEditable
import com.trimble.ttm.commons.composable.utils.formfieldutils.getAlphaModifier
import com.trimble.ttm.commons.composable.utils.formfieldutils.getLabelText
import com.trimble.ttm.commons.composable.utils.formfieldutils.getSignatureAndImageRefBorderColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getSignatureAndImageRefTextColor
import com.trimble.ttm.commons.composable.utils.formfieldutils.getTextStyle
import com.trimble.ttm.commons.composable.utils.formfieldutils.performSendButtonClick
import com.trimble.ttm.commons.composable.utils.formfieldutils.restoreFormFieldUiDataAfterConfigChange
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.model.checkForDriverNonEditableFieldInDriverForm
import com.trimble.ttm.commons.utils.EMPTY_STRING
import com.trimble.ttm.commons.utils.GzipCompression
import com.trimble.ttm.commons.utils.NEWLINE
import com.trimble.ttm.commons.utils.ext.showLongToast
import com.trimble.ttm.commons.viewModel.SignatureDialogViewModel
import kotlin.collections.set

@Composable
fun CustomSignatureField(
    formField: FormField,
    isFormSaved: Boolean,
    sendButtonState: State<Boolean?>,
    isFormInReadOnlyMode: Boolean,
    signatureCanvasView: SignatureCanvasView,
    signatureView: View,
    signatureDialogViewModel: SignatureDialogViewModel
) {
    val imageDataState = rememberSaveable { mutableStateOf(formField.uiData) }
    val errorText = rememberSaveable { mutableStateOf(formField.errorMessage) }
    val isEditable = checkIfTheFieldIsEditable(formField = formField, isFormSaved = isFormSaved)
    val signatureDialogState = rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    restoreFormFieldUiDataAfterConfigChange(textState = imageDataState, formField = formField)
    Column(
        modifier = getAlphaModifier(isFormInReadOnlyMode = isFormInReadOnlyMode)
            .fillMaxWidth()
            .padding(
                start = dimensionResource(id = R.dimen.padding_5dp),
                end = dimensionResource(id = R.dimen.padding_5dp),
                bottom = dimensionResource(id = R.dimen.padding_size_15)
            )
            .background(color = darkSlateBlue)
            .border(
                width = dimensionResource(id = R.dimen.border_stroke_1dp),
                shape = RoundedCornerShape(dimensionResource(id = R.dimen.corner_radius_3dp)),
                color = getSignatureAndImageRefBorderColor(
                    isEditable = formField
                        .checkForDriverNonEditableFieldInDriverForm()
                        .not() && isFormSaved.not(),
                    errorText = errorText
                )
            )
            .clickable(enabled = isEditable, onClick = {
                signatureDialogState.value = true
            })
            .wrapContentHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimensionResource(id = R.dimen.signature_image_height))
        ) {
            if (imageDataState.value.isEmpty()) {
                CustomAnnotatedText(
                    textViewBuilder = TextViewBuilder(
                        annotatedText = getLabelText(
                            required = formField.required,
                            labelText = formField.qtext,
                            isEditable = isEditable,
                            isFormSaved = isFormSaved,
                            isReadOnlyView = isFormInReadOnlyMode
                        ),
                        modifier = Modifier.align(Alignment.Center),
                        textStyle = getTextStyle(getSignatureAndImageRefTextColor(isEditable = isEditable))
                    )
                )
            } else {
                formField.errorMessage = EMPTY_STRING
                errorText.value = EMPTY_STRING
                getImageBitmapFormBase64String(
                    base64String = formField.uiData,
                    formField = formField
                )?.asImageBitmap()
                    ?.let {
                        Image(
                            bitmap = it,
                            contentDescription = EMPTY_STRING,
                            modifier = getSignatureModifier(context = context)
                                .align(Alignment.Center)
                        )
                    }
                if (!isFormSaved && !isFormInReadOnlyMode && imageDataState.value.isNotEmpty()) {
                    CustomImage(
                        imageViewBuilder = ImageViewBuilder(
                            image = painterResource(id = R.drawable.ic_delete_white),
                            modifier = Modifier
                                .fillMaxHeight()
                                .wrapContentWidth()
                                .align(Alignment.TopEnd)
                                .padding(start = dimensionResource(id = R.dimen.padding_5dp))
                                .clickable {
                                    formField.uiData = EMPTY_STRING
                                    signatureDialogViewModel.clearCachedSignatureByteArray(viewId = formField.viewId)
                                    signatureCanvasView.clearCanvas()
                                    imageDataState.value = EMPTY_STRING
                                }
                        )
                    )
                }
            }
        }
        performSendButtonClick(
            sendButtonState = sendButtonState,
            errorText = errorText,
            formField = formField
        )
        DisplayErrorMessage(errorText = errorText)
    }

    SignatureDialogComposable(
        signatureDialogState = signatureDialogState,
        imageDataState = imageDataState,
        context = context,
        formField = formField,
        signatureView = signatureView,
        signatureCanvasView = signatureCanvasView,
        signatureDialogViewModel = signatureDialogViewModel
    )

}

@Composable
fun SignatureDialogComposable(
    signatureDialogState: MutableState<Boolean>,
    imageDataState: MutableState<String>,
    context: Context,
    formField: FormField,
    signatureView: View,
    signatureCanvasView: SignatureCanvasView,
    signatureDialogViewModel: SignatureDialogViewModel
) {
    if (signatureDialogState.value) {
        DisplaySignatureDialog(
            signatureDialogState = signatureDialogState,
            imageDataState = imageDataState,
            context = context,
            formField = formField,
            signatureView = signatureView,
            signatureCanvasView = signatureCanvasView,
            signatureDialogViewModel = signatureDialogViewModel
        )
    }
}

@Composable
fun DisplayErrorMessage(errorText: State<String>) {
    if (errorText.value.isNotEmpty()) {
        CustomText(
            textViewBuilder = TextViewBuilder(
                text = errorText.value,
                modifier = Modifier,
                textStyle = getTextStyle().copy(
                    color = goldenRod,
                    textAlign = TextAlign.Start
                )
            )
        )
    }
}

private fun getImageBitmapFormBase64String(base64String: String, formField: FormField): Bitmap? {
    var bitmap: Bitmap? = null
    val byteArray: ByteArray = Base64.decode(base64String, 0)
    GzipCompression.gzipDecompress(byteArray).let { signatureArray ->
        BitmapFactory.decodeByteArray(signatureArray, 0, signatureArray.size)?.let {
            formField.signViewHeight = it.height
            formField.signViewWidth = it.width
            bitmap = it
        }
    }
    return bitmap
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DisplaySignatureDialog(
    signatureDialogState: MutableState<Boolean>,
    imageDataState: MutableState<String>,
    context: Context,
    formField: FormField,
    signatureView: View,
    signatureCanvasView: SignatureCanvasView,
    signatureDialogViewModel: SignatureDialogViewModel
) {

    Dialog(
        onDismissRequest = {
            clearSignatureCanvasOnDialogDismiss(
                formField = formField,
                signatureCanvasView = signatureCanvasView,
                signatureDialogViewModel = signatureDialogViewModel
            )
            signatureDialogState.value = false
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {

        SignatureDialogLifeCycleObserver(signatureCanvasView = signatureCanvasView, signatureDialogViewModel = signatureDialogViewModel, formField = formField)

        CustomSignature(
            signatureDialogState = signatureDialogState,
            imageDataState = imageDataState,
            context = context,
            formField = formField,
            signatureView = signatureView,
            signatureCanvasView = signatureCanvasView,
            signatureDialogViewModel = signatureDialogViewModel
        )
    }

}

@Composable
fun CustomSignature(
    signatureDialogState: MutableState<Boolean>,
    imageDataState: MutableState<String>,
    context: Context,
    formField: FormField,
    signatureView: View,
    signatureCanvasView: SignatureCanvasView,
    signatureDialogViewModel: SignatureDialogViewModel
) {

    Column(
        modifier = Modifier
            .background(darkSlateBlue)
            .fillMaxWidth()
            .padding(
                dimensionResource(id = R.dimen.padding_size_20)
            )
    ) {
        CustomText(
            textViewBuilder = TextViewBuilder(
                text = stringResource(id = R.string.signature),
                modifier = Modifier.align(Alignment.Start),
                textStyle = getTextStyle().copy(fontSize = dimensionResource(id = R.dimen.signature_title_size).value.sp)
            )
        )
        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )
        AndroidView(factory = {
            signatureView
        }, modifier = Modifier.height(dimensionResource(id = R.dimen.signature_canvas_view_height)))
        CustomSpacer(
            spaceViewBuilder = SpaceViewBuilder(
                modifier = Modifier.height(dimensionResource(id = R.dimen.padding_size_15))
            )
        )
        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val (clearButton, cancelButton, saveButton) = createRefs()
            CustomTextButton(
                textButtonViewBuilder = TextButtonViewBuilder(
                    onclick = {
                        signatureCanvasView.clearCanvas()
                        signatureDialogViewModel.clearCachedSignatureByteArray(viewId = formField.viewId)
                    },
                    modifier = Modifier.constrainAs(clearButton) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                    },
                    colors = ButtonDefaults.textButtonColors()
                ), textViewBuilder = TextViewBuilder(
                    text = stringResource(id = R.string.clear_text),
                    modifier = Modifier,
                    textStyle = getTextStyle().copy(color = goldenRod)
                )
            )
            CustomTextButton(
                textButtonViewBuilder = TextButtonViewBuilder(
                    onclick = {
                        clearSignatureCanvasOnDialogDismiss(
                            formField = formField,
                            signatureCanvasView = signatureCanvasView,
                            signatureDialogViewModel = signatureDialogViewModel
                        )
                        signatureDialogState.value = false
                    },
                    modifier = Modifier.constrainAs(cancelButton) {
                        end.linkTo(saveButton.start, context.resources.getDimension(R.dimen.padding_8dp).dp)
                        baseline.linkTo(saveButton.baseline)
                    },
                    colors = ButtonDefaults.textButtonColors()
                ), textViewBuilder = TextViewBuilder(
                    text = stringResource(id = R.string.cancel_text),
                    modifier = Modifier,
                    textStyle = getTextStyle().copy(color = goldenRod)
                )
            )
            CustomTextButton(
                textButtonViewBuilder = TextButtonViewBuilder(
                    onclick = {
                        val signatureData = convertByteArrayToBase64String(
                            byteArray = signatureCanvasView.getBytes(),
                            context = context
                        )
                        if(signatureData.isNotEmpty()) {
                            formField.uiData = signatureData
                            signatureDialogViewModel.cacheSignatureByteArray(
                                    byteArray = signatureCanvasView.getBytes(),
                                    viewId = formField.viewId
                            )
                            imageDataState.value = formField.uiData
                            signatureDialogState.value = false
                        }
                    },
                    modifier = Modifier.constrainAs(saveButton) {
                        end.linkTo(parent.end)
                        top.linkTo(parent.top)
                    },
                    colors = ButtonDefaults.textButtonColors()
                ), textViewBuilder = TextViewBuilder(
                    text = stringResource(id = R.string.save),
                    modifier = Modifier,
                    textStyle = getTextStyle().copy(color = goldenRod)
                )
            )
        }
    }
}

@Composable
fun SignatureDialogLifeCycleObserver(signatureCanvasView: SignatureCanvasView, signatureDialogViewModel: SignatureDialogViewModel, formField: FormField){
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestLifecycleEvent = remember { mutableStateOf(Lifecycle.Event.ON_ANY) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            latestLifecycleEvent.value = event
        }
        lifecycle.addObserver(observer)
        onDispose {
            if(latestLifecycleEvent.value == Lifecycle.Event.ON_DESTROY) {
                signatureDialogViewModel.cacheSignatureByteArray(
                    byteArray = signatureCanvasView.getBytes(),
                    viewId = formField.viewId)
                signatureCanvasView.clearCanvas()
                lifecycle.removeObserver(observer)
            }
        }
    }
}


fun convertByteArrayToBase64String(
    byteArray: ByteArray?,
    context: Context
): String {
    var base64String = EMPTY_STRING
    byteArray?.let { signature ->
        GzipCompression.gzipCompress(signature).let { compressedSignature ->
            Base64.encodeToString(
                compressedSignature,
                Base64.DEFAULT
            )?.let {
                base64String = it.replace(NEWLINE, EMPTY_STRING)
            } ?: context.showLongToast(R.string.could_not_process)
        }
    } ?: context.showLongToast(R.string.signature_empty_customer_warning_text)

    return base64String
}


fun clearSignatureCanvasOnDialogDismiss(
    formField: FormField,
    signatureCanvasView: SignatureCanvasView,
    signatureDialogViewModel: SignatureDialogViewModel
) {
    signatureCanvasView.clearCanvas()
    if (formField.uiData.isNotEmpty()) {
        signatureCanvasView.setSignatureBitMap(
            bitmap = getImageBitmapFormBase64String(
                base64String = formField.uiData,
                formField = formField
            )
        )
        signatureDialogViewModel.cachedSignatureByteArrayMap[formField.viewId] =
            signatureCanvasView.getBytes()
    }
}

@SuppressLint("ModifierFactoryExtensionFunction")
fun getSignatureModifier(context: Context): Modifier {
    return if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Modifier
            .fillMaxSize()
            .padding(
                top = context.resources.getDimension(R.dimen.padding_10dp).dp,
                bottom = context.resources.getDimension(R.dimen.padding_10dp).dp
            )
    } else {
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.8f)
            .padding(
                top = context.resources.getDimension(R.dimen.padding_5dp).dp,
                bottom = context.resources.getDimension(R.dimen.padding_5dp).dp
            )
    }
}