package com.appsbyalok.echohunter.ui.arsenal

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.appsbyalok.echohunter.ui.components.UIMenuCard
import com.appsbyalok.echohunter.ui.components.UIScrollList
import com.appsbyalok.echohunter.ui.components.UIScrollView
import com.appsbyalok.echohunter.utils.GameColors

class ArsenalListView {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    val itemReacts = mutableMapOf<Int, RectF>()
    private val scrollList = UIScrollList<Int>()
    val scroller: UIScrollView
        get() = scrollList.scroller
    private val card = UIMenuCard()

    fun hitItem(x: Float, y: Float): Int? = scrollList.hit(x, y)

    fun draw(
        c: Canvas,
        targetW: Float,
        targetH: Float,
        scale: Float,
        title: String,
        currentActive: Int,
        items: Array<String>,
        descs: Array<String>,
        headerHeight: Float,
        hitOnDown: Int,
        dt: Float,
        insetR: Float = 0f
    ) {
        val isPortrait = targetH > targetW

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.YELLOW
        pText.textSize = if (isPortrait) scale * 0.06f else scale * 0.05f
        
        val titleY = headerHeight + (if(isPortrait) scale * 0.12f else scale * 0.10f)
        c.drawText(title, targetW / 2f, titleY, pText)

        val listTop = titleY + scale * 0.05f
        val listBottom = targetH - scale * 0.15f
        
        scroller.viewport.set(0f, listTop, targetW, listBottom)
        scroller.updatePhysics(dt)
        scroller.begin(c)

        val itemHeight = if (isPortrait) scale * 0.18f else scale * 0.15f
        val spacing = scale * 0.03f
        val boxWidth = if (isPortrait) targetW * 0.85f else targetW * 0.65f
        val startX = (targetW - boxWidth) / 2f

        itemReacts.clear()
        scrollList.clear()
        var currY = 0f // Relative to scroller's viewport

        for (i in items.indices) {
            val rect = RectF(startX, currY, startX + boxWidth, currY + itemHeight)
            itemReacts[i] = rect
            scrollList.put(i, rect)

            val isActive = (i == currentActive)
            val isPressed = (hitOnDown == i) && !scroller.isDragging

            card.draw(
                c = c,
                rect = rect,
                scale = scale,
                paint = p,
                active = isActive,
                pressed = isPressed,
                fillColor = 0xFF0A1010.toInt(),
                activeFillColor = 0xFF002222.toInt(),
                strokeColor = 0x5500FFFF,
                activeStrokeColor = GameColors.PULSE,
                radius = scale * 0.015f
            )

            // Title
            pText.textAlign = Paint.Align.LEFT
            pText.textSize = scale * 0.045f
            pText.color = if (isActive) GameColors.CLARITY else GameColors.PULSE
            c.drawText(if (isActive) "[ ACTIVE ] ${items[i]}" else "> ${items[i]}", rect.left + scale * 0.04f, rect.top + scale * 0.07f, pText)

            // Description
            pText.textSize = scale * 0.028f
            pText.color = if (isActive) GameColors.YELLOW else 0xFF888888.toInt()
            c.drawText(descs[i], rect.left + scale * 0.04f, rect.bottom - scale * 0.04f, pText)

            // Stat Comparison (Mock-up for specific types)
            if (isActive) {
                pText.textSize = scale * 0.022f
                pText.color = GameColors.HP
                pText.textAlign = Paint.Align.RIGHT
                val statText = when(title) {
                    "WEAPON PROTOCOLS" -> "DMG: +15% | SPD: NOMINAL"
                    "TRAP MODULES" -> "DUR: +2.0s | EFF: 110%"
                    else -> "LOGIC: VERIFIED"
                }
                c.drawText(statText, rect.right - scale * 0.04f, rect.top + scale * 0.06f, pText)
            }

            currY += itemHeight + spacing
        }
        
        scroller.end(c, currY, scale, insetR)
    }
}
