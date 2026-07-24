package com.tuapp.mt_guard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cyan = Color.parseColor("#42D8FF")
    private val blue = Color.parseColor("#168CFF")

    private val circlePaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(
            105,
            66,
            216,
            255
        )
    }

    private val axisPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(
            45,
            66,
            216,
            255
        )
    }

    private val centerPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
        color = cyan
    }

    private val glowPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
        color = Color.argb(
            50,
            22,
            140,
            255
        )
    }

    private val beamPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
    }

    private val gradientMatrix = Matrix()

    private var beamGradient: SweepGradient? = null
    private var currentAngle = 0f

    private val animator = ValueAnimator.ofFloat(
        0f,
        360f
    ).apply {
        duration = 2_100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()

        addUpdateListener {
            currentAngle = it.animatedValue as Float
            invalidate()
        }
    }

    fun setScanning(scanning: Boolean) {
        if (scanning) {
            if (!animator.isStarted) {
                animator.start()
            }
        } else {
            animator.cancel()
            currentAngle = 0f
            invalidate()
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int
    ) {
        super.onSizeChanged(
            width,
            height,
            oldWidth,
            oldHeight
        )

        val centerX = width / 2f
        val centerY = height / 2f

        beamGradient = SweepGradient(
            centerX,
            centerY,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(
                    15,
                    22,
                    140,
                    255
                ),
                Color.argb(
                    120,
                    22,
                    140,
                    255
                ),
                Color.TRANSPARENT
            ),
            floatArrayOf(
                0f,
                0.03f,
                0.12f,
                0.18f
            )
        )

        beamPaint.shader = beamGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f

        val radius = min(
            width,
            height
        ) * 0.44f

        beamGradient?.let {
            gradientMatrix.setRotate(
                currentAngle,
                centerX,
                centerY
            )

            it.setLocalMatrix(
                gradientMatrix
            )

            canvas.drawCircle(
                centerX,
                centerY,
                radius,
                beamPaint
            )
        }

        canvas.drawLine(
            centerX - radius,
            centerY,
            centerX + radius,
            centerY,
            axisPaint
        )

        canvas.drawLine(
            centerX,
            centerY - radius,
            centerX,
            centerY + radius,
            axisPaint
        )

        for (index in 1..4) {
            canvas.drawCircle(
                centerX,
                centerY,
                radius * index / 4f,
                circlePaint
            )
        }

        canvas.drawCircle(
            centerX,
            centerY,
            18f,
            glowPaint
        )

        canvas.drawCircle(
            centerX,
            centerY,
            7f,
            centerPaint
        )
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}