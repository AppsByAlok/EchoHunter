package com.appsbyalok.echohunter.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.appsbyalok.echohunter.utils.GameColors

class UIMenuButton {
    val rect = RectF()

    fun set(bounds: RectF) {
        rect.set(bounds)
    }

    fun set(left: Float, top: Float, right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
    }

    fun contains(x: Float, y: Float): Boolean = rect.contains(x, y)

    fun draw(
        c: Canvas,
        scale: Float,
        paint: Paint,
        textPaint: Paint,
        label: String,
        pressed: Boolean,
        fillColor: Int = 0xFF220505.toInt(),
        strokeColor: Int = GameColors.RED,
        textColor: Int = strokeColor,
        radius: Float = scale * 0.01f,
        textSize: Float = scale * 0.035f
    ) {
        val oldAlign = textPaint.textAlign
        val oldSize = textPaint.textSize
        val oldColor = textPaint.color

        paint.style = Paint.Style.FILL
        paint.color = if (pressed) GameColors.mixColors(fillColor, strokeColor, 0.35f) else fillColor
        c.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.color = strokeColor
        paint.strokeWidth = scale * 0.004f
        c.drawRoundRect(rect, radius, radius, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = textSize
        textPaint.color = textColor
        if (pressed) textPaint.setShadowLayer(10f, 0f, 0f, textColor)
        val offset = (textPaint.descent() + textPaint.ascent()) / 2f
        c.drawText(label, rect.centerX(), rect.centerY() - offset, textPaint)
        textPaint.clearShadowLayer()

        textPaint.textAlign = oldAlign
        textPaint.textSize = oldSize
        textPaint.color = oldColor
    }
}
