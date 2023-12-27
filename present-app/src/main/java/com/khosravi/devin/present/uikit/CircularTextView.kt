package com.khosravi.devin.present.uikit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import com.khosravi.devin.present.R

class CircularTextView @JvmOverloads constructor(
    context: Context, attr: AttributeSet? = null, defStyle: Int = 0
) : androidx.appcompat.widget.AppCompatTextView(context, attr, defStyle) {

    var strokeColor: Int?
        get() {
            return strokePaint.color
        }
        set(value) {
            if (value != null) {
                strokePaint.color = value
            }
        }

    var solidColor: Int?
        get() {
            return circlePaint.color
        }
        set(value) {
            if (value != null) {
                circlePaint.color = value
            }
        }


    var isEnableStroke = false
    private var strokeWidth = 0f
    private var isInit = false
    private val circlePaint = Paint()
    private val strokePaint = Paint()
    private var radius = 0
    private var diameter = 0

    init {
        initAttrs(context, attr)
    }

    private fun initAttrs(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircularTextView)
        solidColor = typedArray.getColor(R.styleable.CircularTextView_ctvSolidColor, Color.BLACK)
        setTextColor(Color.WHITE)
        typedArray.recycle()
        gravity = Gravity.CENTER
    }

    override fun draw(canvas: Canvas) {
        if (!isInit) {
            circlePaint.flags = Paint.ANTI_ALIAS_FLAG
            strokePaint.flags = Paint.ANTI_ALIAS_FLAG
            val h = this.height
            val w = this.width
            diameter = if (h > w) h else w
            radius = diameter / 2
            this.height = diameter
            this.width = diameter
            isInit = true
        }
        if (isEnableStroke) {
            canvas.drawCircle(diameter / 2.toFloat(), diameter / 2.toFloat(), radius.toFloat(), strokePaint)
        }
        canvas.drawCircle(diameter / 2.toFloat(), diameter / 2.toFloat(), radius - strokeWidth, circlePaint)
        super.draw(canvas)
    }

    fun setStrokeWidth(dp: Int) {
        val scale = context.resources.displayMetrics.density
        strokeWidth = dp * scale
    }

}