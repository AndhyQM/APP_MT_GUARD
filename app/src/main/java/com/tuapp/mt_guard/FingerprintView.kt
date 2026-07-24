package com.tuapp.mt_guard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class FingerprintView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cyan = Color.parseColor("#42D8FF")
    private val blue = Color.parseColor("#168CFF")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()

        if (size <= 0f) return

        val offsetX = (width - size) / 2f
        val offsetY = (height - size) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)

        paint.strokeWidth = size * 0.042f

        // Arco exterior
        dibujarArco(
            canvas = canvas,
            size = size,
            left = 0.10f,
            top = 0.08f,
            right = 0.90f,
            bottom = 0.84f,
            startAngle = 198f,
            sweepAngle = 144f,
            color = cyan
        )

        // Segundo arco
        dibujarArco(
            canvas = canvas,
            size = size,
            left = 0.21f,
            top = 0.19f,
            right = 0.79f,
            bottom = 0.75f,
            startAngle = 198f,
            sweepAngle = 144f,
            color = cyan
        )

        // Tercer arco
        dibujarArco(
            canvas = canvas,
            size = size,
            left = 0.32f,
            top = 0.30f,
            right = 0.68f,
            bottom = 0.66f,
            startAngle = 200f,
            sweepAngle = 140f,
            color = blue
        )

        // Línea exterior izquierda
        dibujarCurva(
            canvas,
            size,
            cyan,
            0.10f, 0.57f,
            0.10f, 0.72f,
            0.08f, 0.83f,
            0.04f, 0.91f
        )

        // Línea exterior derecha
        dibujarCurva(
            canvas,
            size,
            cyan,
            0.90f, 0.57f,
            0.90f, 0.72f,
            0.92f, 0.83f,
            0.96f, 0.91f
        )

        // Línea media izquierda
        dibujarCurva(
            canvas,
            size,
            cyan,
            0.22f, 0.59f,
            0.22f, 0.72f,
            0.19f, 0.83f,
            0.14f, 0.91f
        )

        // Línea media derecha
        dibujarCurva(
            canvas,
            size,
            cyan,
            0.78f, 0.59f,
            0.78f, 0.72f,
            0.81f, 0.83f,
            0.86f, 0.91f
        )

        // Línea interior izquierda
        dibujarCurva(
            canvas,
            size,
            cyan,
            0.33f, 0.60f,
            0.33f, 0.72f,
            0.30f, 0.82f,
            0.25f, 0.90f
        )

        // Línea interior derecha
        dibujarCurva(
            canvas,
            size,
            cyan,
            0.67f, 0.60f,
            0.67f, 0.72f,
            0.70f, 0.82f,
            0.75f, 0.90f
        )

        // Curva central principal
        path.reset()
        path.moveTo(size * 0.40f, size * 0.57f)

        path.cubicTo(
            size * 0.40f,
            size * 0.46f,
            size * 0.44f,
            size * 0.40f,
            size * 0.50f,
            size * 0.40f
        )

        path.cubicTo(
            size * 0.57f,
            size * 0.40f,
            size * 0.61f,
            size * 0.47f,
            size * 0.61f,
            size * 0.57f
        )

        path.cubicTo(
            size * 0.61f,
            size * 0.70f,
            size * 0.58f,
            size * 0.81f,
            size * 0.53f,
            size * 0.91f
        )

        paint.color = blue
        canvas.drawPath(path, paint)

        // Curva central secundaria
        dibujarCurva(
            canvas,
            size,
            blue,
            0.40f, 0.58f,
            0.40f, 0.70f,
            0.37f, 0.80f,
            0.32f, 0.88f
        )

        canvas.restore()
    }

    private fun dibujarArco(
        canvas: Canvas,
        size: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        color: Int
    ) {
        rect.set(
            size * left,
            size * top,
            size * right,
            size * bottom
        )

        paint.color = color

        canvas.drawArc(
            rect,
            startAngle,
            sweepAngle,
            false,
            paint
        )
    }

    private fun dibujarCurva(
        canvas: Canvas,
        size: Float,
        color: Int,
        startX: Float,
        startY: Float,
        control1X: Float,
        control1Y: Float,
        control2X: Float,
        control2Y: Float,
        endX: Float,
        endY: Float
    ) {
        path.reset()

        path.moveTo(
            size * startX,
            size * startY
        )

        path.cubicTo(
            size * control1X,
            size * control1Y,
            size * control2X,
            size * control2Y,
            size * endX,
            size * endY
        )

        paint.color = color
        canvas.drawPath(path, paint)
    }
}