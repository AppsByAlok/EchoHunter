package com.appsbyalok.echohunter.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.appsbyalok.echohunter.utils.GameColors

class UIMenuCard {
    fun draw(
        c: Canvas,
        rect: RectF,
        scale: Float,
        paint: Paint,
        active: Boolean = false,
        pressed: Boolean = false,
        fillColor: Int = 0xFF0A1010.toInt(),
        activeFillColor: Int = 0xFF002222.toInt(),
        strokeColor: Int = 0x5500FFFF,
        activeStrokeColor: Int = GameColors.PULSE,
        radius: Float = scale * 0.015f
    ) {
        val baseFill = if (active) activeFillColor else fillColor
        paint.style = Paint.Style.FILL
        paint.color = if (pressed) GameColors.mixColors(baseFill, GameColors.CLARITY, 0.25f) else baseFill
        c.drawRoundRect(rect, radius, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.color = if (pressed) GameColors.CLARITY else if (active) activeStrokeColor else strokeColor
        paint.strokeWidth = if (active) scale * 0.006f else scale * 0.003f
        c.drawRoundRect(rect, radius, radius, paint)
    }
}
