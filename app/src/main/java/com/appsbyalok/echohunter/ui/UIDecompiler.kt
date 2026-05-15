package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.data.UpgradeType
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.abs
import kotlin.math.max

class UIDecompiler {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val buyButtons = mutableMapOf<UpgradeType, RectF>()
    private val closeBtnRect = RectF()

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float) {
        c.drawColor(0xEE020A05.toInt()) // Terminal Dark Greenish BG

        // --- Header ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.08f
        pText.color = GameColors.HP
        pText.setShadowLayer(15f, 0f, 0f, GameColors.HP)
        c.drawText("EXPLOIT COMPILER", targetW / 2f, scale * 0.12f, pText)
        pText.clearShadowLayer()

        pText.textSize = scale * 0.035f
        pText.color = GameColors.CLARITY
        c.drawText("AVAILABLE DATA: ${SaveManager.formatDataString(SaveManager.dataCoinsKB)}", targetW / 2f, scale * 0.18f, pText)

        // --- Scrollable Shop List ---
        val startY = scale * 0.25f + scrollY
        var currentY = startY
        val itemHeight = scale * 0.18f
        val marginX = scale * 0.1f

        buyButtons.clear()

        for ((type, config) in UpgradeSystem.catalog) {
            val itemRect = RectF(marginX, currentY, targetW - marginX, currentY + itemHeight - scale * 0.02f)

            val currentLvl = UpgradeSystem.getLevel(type)
            val isMaxed = currentLvl >= config.maxLevel
            val cost = UpgradeSystem.getNextLevelCost(type)
            val canAfford = !isMaxed && SaveManager.dataCoinsKB >= cost

            // Draw Background Box
            p.style = Paint.Style.FILL
            p.color = if (isMaxed) 0xFF051105.toInt() else 0xFF111111.toInt()
            c.drawRoundRect(itemRect, scale * 0.02f, scale * 0.02f, p)

            // Draw Border
            p.style = Paint.Style.STROKE
            p.color = if (isMaxed) GameColors.HP else if (canAfford) GameColors.PULSE else 0xFF555555.toInt()
            p.strokeWidth = scale * 0.005f
            c.drawRoundRect(itemRect, scale * 0.02f, scale * 0.02f, p)

            // Draw Texts
            pText.textAlign = Paint.Align.LEFT
            pText.color = if (isMaxed) GameColors.HP else GameColors.CLARITY
            pText.textSize = scale * 0.045f
            c.drawText("> ${config.nameStr} [v$currentLvl/${config.maxLevel}]", marginX + scale * 0.04f, currentY + scale * 0.06f, pText)

            pText.textSize = scale * 0.028f
            pText.color = 0xFFAAAAAA.toInt()
            c.drawText(config.descStr, marginX + scale * 0.04f, currentY + scale * 0.11f, pText)

            // Inject Button
            val btnW = scale * 0.25f
            val btnRect = RectF(itemRect.right - btnW - scale*0.02f, itemRect.top + scale*0.02f, itemRect.right - scale*0.02f, itemRect.bottom - scale*0.02f)

            if (!isMaxed) buyButtons[type] = btnRect

            p.style = Paint.Style.FILL
            p.color = if (isMaxed) 0xFF003300.toInt() else if (canAfford) 0xFF003333.toInt() else 0xFF330000.toInt()
            c.drawRoundRect(btnRect, scale*0.01f, scale*0.01f, p)

            p.style = Paint.Style.STROKE
            p.color = if (isMaxed) GameColors.HP else if (canAfford) GameColors.PULSE else GameColors.RED
            c.drawRoundRect(btnRect, scale*0.01f, scale*0.01f, p)

            pText.textAlign = Paint.Align.CENTER
            pText.color = p.color
            pText.textSize = scale * 0.035f

            val btnText = if (isMaxed) "MAXED" else "INJECT\n${SaveManager.formatDataString(cost)}"
            val lines = btnText.split("\n")
            if (lines.size == 1) {
                c.drawText(lines[0], btnRect.centerX(), btnRect.centerY() + scale * 0.012f, pText)
            } else {
                c.drawText(lines[0], btnRect.centerX(), btnRect.centerY() - scale * 0.005f, pText)
                c.drawText(lines[1], btnRect.centerX(), btnRect.centerY() + scale * 0.035f, pText)
            }

            currentY += itemHeight
        }

        val totalListHeight = currentY - scrollY
        maxScroll = max(0f, totalListHeight - targetH + scale * 0.2f)

        // --- DISCONNECT BUTTON ---
        closeBtnRect.set(targetW / 2f - scale * 0.2f, targetH - scale * 0.12f, targetW / 2f + scale * 0.2f, targetH - scale * 0.03f)
        p.style = Paint.Style.FILL; p.color = 0xFF330000.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER; pText.color = GameColors.RED; pText.textSize = scale * 0.04f
        c.drawText("DISCONNECT", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.015f, pText)
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = y
                isDragging = false

                if (closeBtnRect.contains(x, y)) {
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                    onBack()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = y - lastTouchY
                if (abs(dy) > scale * 0.02f) isDragging = true
                scrollY += dy
                if (scrollY > 0f) scrollY = 0f
                if (scrollY < -maxScroll) scrollY = -maxScroll
                lastTouchY = y
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    for ((type, rect) in buyButtons) {
                        if (rect.contains(x, y)) {
                            if (UpgradeSystem.purchaseUpgrade(type)) {
                                EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                                gs.showGlobalMessage("SCRIPT INJECTED.\nFIRMWARE OVERRIDDEN.", 2f)
                            } else {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                                gs.showGlobalMessage("ERROR: INSUFFICIENT DATA.\nREQUIRE MORE KILOBYTES.", 2f)
                            }
                            return true
                        }
                    }
                }
            }
        }
        return true
    }
}