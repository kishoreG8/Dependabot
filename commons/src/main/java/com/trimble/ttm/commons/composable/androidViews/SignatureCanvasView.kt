package com.trimble.ttm.commons.composable.androidViews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.trimble.ttm.commons.R
import com.trimble.ttm.commons.utils.SIGNATURE_WIDTH_DP
import com.trimble.ttm.commons.utils.UiUtils.convertDpToPixel
import com.trimble.ttm.commons.utils.ext.isNull
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

private const val OUTPUT_SIGNATURE_BYTE_ARRAY_COMPRESS_QUALITY = 100
private val outputSignatureImageType = Bitmap.CompressFormat.JPEG

class SignatureCanvasView(context: Context, attr: AttributeSet?, byteArray: ByteArray?) :
    View(context, attr) {
    constructor(context: Context) : this(context, null, null)

    private val signatureLineThickness = 4f
    private var signatureBitmapView: Bitmap? = null
    private var signatureCanvas: Canvas? = null
    private val signaturePath: Path = Path()
    private val bitmapPaint: Paint = Paint(Paint.DITHER_FLAG)
    private val canvasPaint: Paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = ResourcesCompat.getColor(
            context.resources,
            R.color.canvasPenColor, null
        )
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = signatureLineThickness
    }
    private var canvasXPos: Float = 0f
    private var canvasYPos: Float = 0f
    private val canvasTouchTolerance = 4f
    private var enteredSignatureIfAny: Bitmap? = null
    private var isCanvasEmpty: Boolean = true

    init {
        byteArray?.let {
            val options = BitmapFactory.Options()
            options.inMutable = true
            val bmp: Bitmap = BitmapFactory.decodeByteArray(it, 0, it.size, options)
            enteredSignatureIfAny = bmp
        }
    }

    fun setSignatureBitMap(bitmap: Bitmap?){
        enteredSignatureIfAny = bitmap
    }

    override fun onSizeChanged(
        changedWidth: Int,
        changedHeight: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {
        super.onSizeChanged(changedWidth, changedHeight, oldWidth, oldHeight)
        signatureBitmapView = Bitmap.createBitmap(
            changedWidth,
            if (changedHeight > 0) changedHeight else (this.parent as View).height,
            Bitmap.Config.ARGB_8888
        )
        signatureCanvas = Canvas(signatureBitmapView!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (enteredSignatureIfAny != null) {
            isCanvasEmpty = false
            signatureBitmapView = Bitmap.createScaledBitmap(
                enteredSignatureIfAny!!,
                width,
                height,
                true
            )
            enteredSignatureIfAny = null
        }
        createBitMapViewIfNull()
        signatureCanvas?.drawColor(
            ResourcesCompat.getColor(
                context.resources,
                R.color.white,
                null
            )
        )
        signatureBitmapView?.let {
            canvas.drawBitmap(signatureBitmapView!!, 0f, 0f, bitmapPaint)
            canvas.drawPath(signaturePath, canvasPaint)
        }
    }

    private fun touchStart(touchXPos: Float, touchYPos: Float) {
        signaturePath.moveTo(touchXPos, touchYPos)
        canvasXPos = touchXPos
        canvasYPos = touchYPos
    }

    private fun touchMove(touchXPos: Float, touchYPos: Float) {
        val dx = abs(touchXPos - canvasXPos)
        val dy = abs(touchYPos - canvasYPos)

        if (dx >= canvasTouchTolerance || dy >= canvasTouchTolerance) {
            signaturePath.quadTo(
                canvasXPos,
                canvasYPos,
                (touchXPos + canvasXPos) / 2,
                (touchYPos + canvasYPos) / 2
            )
            canvasXPos = touchXPos
            canvasYPos = touchYPos
        }
    }

    private fun touchUp() {
        if (!signaturePath.isEmpty) {
            signaturePath.lineTo(canvasXPos, canvasYPos)
            signatureCanvas?.drawPath(signaturePath, canvasPaint)
        } else {
            signatureCanvas?.drawPoint(canvasXPos, canvasYPos, canvasPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        val eventXPos = event.x
        val eventYPos = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(eventXPos, eventYPos)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                isCanvasEmpty = false
                touchMove(eventXPos, eventYPos)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate()
            }
        }
        return true
    }

    fun clearCanvas() {
        signatureCanvas?.drawColor(
            ResourcesCompat.getColor(
                context.resources,
                R.color.white,
                null
            )
        )
        enteredSignatureIfAny = null
        signatureBitmapView = null
        signaturePath.reset()
        isCanvasEmpty = true
        invalidate()
    }

    fun getBytes(): ByteArray? {
        val signatureBitmap = getBitmap()
        //To check the empty Signature data
        if (isCanvasEmpty) {
            return null
        }
        val ratio = signatureBitmap.height.toFloat()/signatureBitmap.width.toFloat()
        val width = convertDpToPixel(SIGNATURE_WIDTH_DP, context).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(signatureBitmap,  width, (width*ratio).roundToInt(), true)
        val byteArrayOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(
            outputSignatureImageType,
            OUTPUT_SIGNATURE_BYTE_ARRAY_COMPRESS_QUALITY,
            byteArrayOutputStream
        )
        return byteArrayOutputStream.toByteArray()
    }

    private fun getBitmap(): Bitmap {
        val canvasView = this.parent as View
        val bitmap =
            Bitmap.createBitmap(canvasView.width, canvasView.height, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(bitmap)
        canvasView.layout(canvasView.left, canvasView.top, canvasView.right, canvasView.bottom)
        canvasView.draw(newCanvas)

        return bitmap
    }

    private fun createBitMapViewIfNull() {
        if (signatureBitmapView.isNull()) {
            val canvasView = this.parent as View
            signatureBitmapView = Bitmap.createBitmap(
                canvasView.width, canvasView.height,
                Bitmap.Config.ARGB_8888
            )
            signatureCanvas = Canvas(signatureBitmapView!!)
        }
    }
}