package com.appsbyalok.echohunter.ui.components

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent

class UIConfirmDialog {
    private val panel = RectF()
    private val confirm = RectF()
    private val cancel = RectF()
    private var title = ""
    private var detail = ""
    private var confirmLabel = "CONFIRM"
    private var onConfirm: (() -> Unit)? = null
    private var downTarget = 0

    var visible = false
        private set

    fun show(title: String, detail: String, confirmLabel: String, onConfirm: () -> Unit) {
        this.title = title
        this.detail = detail
        this.confirmLabel = confirmLabel
        this.onConfirm = onConfirm
        visible = true
    }

    fun draw(c: Canvas, width: Float, height: Float, scale: Float, paint: Paint, text: Paint, accentColor: Int) {
        if (!visible) return
        c.drawColor(0xAA000000.toInt())
        val panelW = minOf(width * 0.78f, scale * 0.82f)
        val panelH = scale * 0.30f
        panel.set(width / 2f - panelW / 2f, height / 2f - panelH / 2f, width / 2f + panelW / 2f, height / 2f + panelH / 2f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0A1520.toInt()
        c.drawRect(panel, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.004f
        paint.color = accentColor
        c.drawRect(panel, paint)
        text.textAlign = Paint.Align.CENTER
        text.textSize = scale * 0.042f
        text.color = accentColor
        c.drawText(title, panel.centerX(), panel.top + scale * 0.09f, text)
        text.textSize = scale * 0.025f
        text.color = 0xFFFFFFFF.toInt()
        c.drawText(detail, panel.centerX(), panel.top + scale * 0.145f, text)

        val gap = scale * 0.025f
        val buttonW = (panel.width() - gap * 3f) / 2f
        val buttonH = scale * 0.07f
        val y = panel.bottom - buttonH - scale * 0.045f
        cancel.set(panel.left + gap, y, panel.left + gap + buttonW, y + buttonH)
        confirm.set(panel.right - gap - buttonW, y, panel.right - gap, y + buttonH)
        drawButton(c, cancel, "CANCEL", 0xFFB0D4E8.toInt(), scale, paint, text)
        drawButton(c, confirm, confirmLabel, accentColor, scale, paint, text)
    }

    fun onTouch(x: Float, y: Float, action: Int): Boolean {
        if (!visible) return false
        when (action) {
            MotionEvent.ACTION_DOWN -> downTarget = when {
                confirm.contains(x, y) -> 1
                cancel.contains(x, y) -> 2
                else -> 0
            }
            MotionEvent.ACTION_UP -> {
                if (downTarget == 1 && confirm.contains(x, y)) onConfirm?.invoke()
                if (downTarget == 1 || downTarget == 2) dismiss()
                downTarget = 0
            }
        }
        return true
    }

    fun dismiss() {
        visible = false
        onConfirm = null
        downTarget = 0
    }

    private fun drawButton(c: Canvas, rect: RectF, label: String, color: Int, scale: Float, paint: Paint, text: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = scale * 0.003f
        paint.color = color
        c.drawRect(rect, paint)
        text.textAlign = Paint.Align.CENTER
        text.textSize = scale * 0.022f
        text.color = color
        c.drawText(label, rect.centerX(), rect.centerY() + text.textSize * 0.35f, text)
    }
}
