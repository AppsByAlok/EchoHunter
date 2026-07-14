package com.appsbyalok.echohunter.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import com.appsbyalok.echohunter.utils.GameColors

class UIMenuScreenChrome {
    fun drawBackground(c: Canvas, metrics: UIMenuMetrics, paint: Paint, bgColor: Int = 0xEE050A0F.toInt(), scanlineColor: Int = 0x0AFFFFFF) {
        c.drawColor(bgColor)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = metrics.scale * 0.002f
        paint.color = scanlineColor
        var y = 0f
        while (y < metrics.height) {
            c.drawLine(0f, y, metrics.width, y, paint)
            y += metrics.scale * 0.012f
        }
    }

    fun drawHeader(
        c: Canvas,
        metrics: UIMenuMetrics,
        paint: Paint,
        textPaint: Paint,
        title: String,
        subtitle: String? = null,
        fillColor: Int = 0xFF0A1520.toInt(),
        accentColor: Int = GameColors.PULSE
    ) {
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        c.drawRect(0f, 0f, metrics.width, metrics.headerHeight, paint)
        paint.color = accentColor
        c.drawRect(0f, metrics.headerHeight - metrics.scale * 0.005f, metrics.width, metrics.headerHeight, paint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = metrics.scale * 0.04f
        textPaint.color = accentColor
        c.drawText(title, metrics.insetLeft + metrics.scale * 0.05f, metrics.insetTop + (metrics.headerHeight - metrics.insetTop) * 0.62f, textPaint)

        if (subtitle != null) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = GameColors.CLARITY
            c.drawText(subtitle, metrics.width - metrics.insetRight - metrics.scale * 0.05f, metrics.insetTop + (metrics.headerHeight - metrics.insetTop) * 0.62f, textPaint)
        }
    }
}
