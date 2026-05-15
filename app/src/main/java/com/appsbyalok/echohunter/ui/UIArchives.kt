package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelType
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.abs
import kotlin.math.max

class UIArchives {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val levelButtons = mutableMapOf<Int, RectF>()
    private val closeBtnRect = RectF()
    private var cachedList = mutableListOf<Int>()
    private var lastMaxLevel = -1

    private val autoNextBoxRect = RectF()
    private val autoPilotBoxRect = RectF()

    private fun generateNodeList() {
        cachedList.clear()
        val maxLvl = SaveManager.maxCampaignLevel
        lastMaxLevel = maxLvl

        cachedList.add(maxLvl)
        val minRecent = max(1, maxLvl - 50)
        for (i in maxLvl - 1 downTo minRecent) cachedList.add(i)

        var importantCount = 0
        for (i in minRecent - 1 downTo 1) {
            val type = LevelEngine.getLevelConfig(i).type
            if (type == LevelType.BOSS || type == LevelType.DEFENSE || type == LevelType.DEFENSE_BOSS) {
                cachedList.add(i)
                importantCount++
                if (importantCount >= 25) break
            }
        }
    }

    fun draw(c: Canvas, width: Float, height: Float, gs: GameState, scale: Float) {
        c.drawColor(0xEE0A0A10.toInt())

        if (SaveManager.maxCampaignLevel != lastMaxLevel || cachedList.isEmpty()) generateNodeList()

        pText.textSize = scale * 0.08f
        pText.color = GameColors.COOLANT
        c.drawText("SANDBOX DIRECTORY", width / 2f, scale * 0.12f, pText)

        // Auto-Next Box (Left)
        autoNextBoxRect.set(width / 2f - scale * 0.4f, scale * 0.18f, width / 2f - scale * 0.02f, scale * 0.28f)
        p.style = Paint.Style.STROKE; p.color = if (SaveManager.isAutoNextLevelEnabled) GameColors.HP else GameColors.RED; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(autoNextBoxRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.FILL; p.color = 0x22000000
        c.drawRoundRect(autoNextBoxRect, scale * 0.02f, scale * 0.02f, p)
        pText.textSize = scale * 0.035f; pText.color = if (SaveManager.isAutoNextLevelEnabled) GameColors.HP else GameColors.RED
        c.drawText(if (SaveManager.isAutoNextLevelEnabled) "AUTO-NEXT: ON" else "AUTO-NEXT: OFF", autoNextBoxRect.centerX(), autoNextBoxRect.centerY() + scale * 0.012f, pText)

        // Auto-Pilot Box (Right)
        autoPilotBoxRect.set(width / 2f + scale * 0.02f, scale * 0.18f, width / 2f + scale * 0.4f, scale * 0.28f)
        p.style = Paint.Style.STROKE; p.color = if (gs.isAutoPilotActive) GameColors.HP else GameColors.RED; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(autoPilotBoxRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.FILL; p.color = 0x22000000
        c.drawRoundRect(autoPilotBoxRect, scale * 0.02f, scale * 0.02f, p)
        pText.textSize = scale * 0.035f; pText.color = if (gs.isAutoPilotActive) GameColors.HP else GameColors.RED
        c.drawText(if (gs.isAutoPilotActive) "AUTOPILOT: ON" else "AUTOPILOT: OFF", autoPilotBoxRect.centerX(), autoPilotBoxRect.centerY() + scale * 0.012f, pText)


        // Small Grid Setup
        val startY = scale * 0.35f + scrollY
        val boxSize = scale * 0.16f
        val gap = scale * 0.04f
        val columns = if (width > height) 6 else 4
        val totalW = columns * boxSize + (columns - 1) * gap
        val startX = (width - totalW) / 2f

        levelButtons.clear()

        var col = 0
        var row = 0

        for (lvl in cachedList) {
            val cx = startX + col * (boxSize + gap)
            val cy = startY + row * (boxSize + gap)
            val btnRect = RectF(cx, cy, cx + boxSize, cy + boxSize)
            levelButtons[lvl] = btnRect

            val config = LevelEngine.getLevelConfig(lvl)
            val isNextNode = (lvl == SaveManager.maxCampaignLevel)

            p.style = Paint.Style.FILL
            p.color = if (isNextNode) 0xFF113311.toInt() else 0xFF111115.toInt()
            c.drawRoundRect(btnRect, scale * 0.02f, scale * 0.02f, p)

            // Draw Icon based on Type
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.005f
            when (config.type) {
                LevelType.DEFENSE -> {
                    p.color = GameColors.SHIELD
                    c.drawCircle(btnRect.centerX(), btnRect.centerY(), boxSize * 0.3f, p)
                }
                LevelType.BOSS, LevelType.DEFENSE_BOSS -> {
                    p.color = GameColors.RED
                    c.drawRect(btnRect.centerX() - boxSize*0.25f, btnRect.centerY() - boxSize*0.25f, btnRect.centerX() + boxSize*0.25f, btnRect.centerY() + boxSize*0.25f, p)
                }
                else -> {
                    p.color = GameColors.PULSE
                }
            }
            c.drawRoundRect(btnRect, scale * 0.02f, scale * 0.02f, p)

            pText.color = GameColors.CLARITY
            pText.textSize = scale * 0.04f
            c.drawText("$lvl", btnRect.centerX(), btnRect.centerY() + scale * 0.015f, pText)

            col++
            if (col >= columns) { col = 0; row++ }
        }

        val totalHeight = (row + 1) * (boxSize + gap)
        maxScroll = max(0f, totalHeight - height + scale * 0.4f)

        closeBtnRect.set(width / 2f - scale * 0.2f, height - scale * 0.12f, width / 2f + scale * 0.2f, height - scale * 0.03f)
        p.style = Paint.Style.FILL; p.color = 0xFF330000.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.color = GameColors.RED; pText.textSize = scale * 0.04f
        c.drawText("RETURN", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.015f, pText)
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onSelect: (Int) -> Unit, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> { lastTouchY = y; isDragging = false }
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
                    if (closeBtnRect.contains(x, y)) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                        onBack(); return true
                    }
                    // --- FIXED: USE RECT CONTAINS FOR 100% ACCURACY ---
                    if (autoNextBoxRect.contains(x, y)) {
                        SaveManager.setAutoNextLevel(!SaveManager.isAutoNextLevelEnabled)
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                        return true
                    }
                    else if (autoPilotBoxRect.contains(x, y)) {
                        gs.isAutoPilotActive = !gs.isAutoPilotActive
                        if (gs.isAutoPilotActive) {
                            gs.autoPilotTimer = 600f // 10 minutes limit
                            EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                            gs.showGlobalMessage("AUTOPILOT ENGAGED.\nSELECT A LEVEL TO START.", 3f)
                        } else {
                            gs.autoPilotTimer = 0f
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                        }
                        return true
                    }

                    for ((lvl, rect) in levelButtons) {
                        if (rect.contains(x, y)) {
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                            onSelect(lvl); return true
                        }
                    }
                }
            }
        }
        return true
    }
}