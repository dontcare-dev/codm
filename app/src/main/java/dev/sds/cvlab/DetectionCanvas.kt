package dev.sds.cvlab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.objects.DetectedObject

class DetectionCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var frame: Bitmap? = null
    private var detections: List<DetectedObject> = emptyList()

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 211, 151)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(13, 38, 32)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    fun showFrame(bitmap: Bitmap, objects: List<DetectedObject>) {
        frame = bitmap
        detections = objects
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(18, 20, 20))
        val bitmap = frame ?: return
        val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val left = (width - bitmap.width * scale) / 2f
        val top = (height - bitmap.height * scale) / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)

        detections.forEachIndexed { index, item ->
            val rect = RectF(item.boundingBox)
            canvas.drawRect(rect, boxPaint)
            val label = item.labels.firstOrNull()?.text ?: "Object ${index + 1}"
            val confidence = item.labels.firstOrNull()?.confidence?.let { " ${(it * 100).toInt()}%" } ?: ""
            val text = label + confidence
            val textWidth = labelPaint.measureText(text)
            val labelTop = (rect.top - 38f).coerceAtLeast(0f)
            canvas.drawRoundRect(
                rect.left,
                labelTop,
                rect.left + textWidth + 22f,
                labelTop + 34f,
                7f,
                7f,
                labelBackground
            )
            canvas.drawText(text, rect.left + 11f, labelTop + 26f, labelPaint)
        }
        canvas.restore()
    }
}
