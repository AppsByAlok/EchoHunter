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
import com.appsbyalok.echohunter.engine.GameColors
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.max

class UIArchives {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val levelButtons = mutableMapOf<Int, RectF>()
    private val closeBtnRect = RectF()

    // Cache the list so we don't calculate the node math on every single frame
    private var cachedList = mutableListOf<Int>()
    private var lastMaxLevel = -1

    /**
     * Generates the custom list of nodes to display:
     * 1. Next/Current Node
     * 2. Last 50 nodes played
     * 3. Up to 25 older IMPORTANT nodes (Bosses/Defense)
     */
    private fun generateNodeList() {
        cachedList.clear()
        val maxLvl = SaveManager.maxCampaignLevel
        lastMaxLevel = maxLvl

        // 1. Current / Next Node (Always at the top)
        cachedList.add(maxLvl)

        // 2. Previous 50 continuous nodes
        val minRecent = max(1, maxLvl - 50)
        for (i in maxLvl - 1 downTo minRecent) {
            cachedList.add(i)
        }

        // 3. Up to 25 older IMPORTANT nodes (Boss / Defense / Admin)
        var importantCount = 0
        for (i in minRecent - 1 downTo 1) {
            val type = LevelEngine.getLevelConfig(i).type
            if (type == LevelType.BOSS || type == LevelType.DEFENSE || type == LevelType.DEFENSE_BOSS || type == LevelType.ADMIN_BONUS) {
                cachedList.add(i)
                importantCount++
                if (importantCount >= 25) break
            }
        }
    }

    fun draw(c: Canvas, width: Float, height: Float, scale: Float) {
        c.drawColor(0xEE0A0A10.toInt()) // Deep blueish dark background

        // Regenerate the list only if the player cleared a new level
        if (SaveManager.maxCampaignLevel != lastMaxLevel || cachedList.isEmpty()) {
            generateNodeList()
        }

        // --- Title Header ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.08f
        pText.color = GameColors.COOLANT
        pText.setShadowLayer(15f, 0f, 0f, GameColors.COOLANT)
        c.drawText("NODE SELECTOR", width / 2f, scale * 0.12f, pText)
        pText.clearShadowLayer()

        pText.textSize = scale * 0.035f
        pText.color = 0xFFAAAAAA.toInt()
        c.drawText("REPLAYING PAST NODES YIELDS 50% REWARDS", width / 2f, scale * 0.18f, pText)

        // --- Scrollable Grid/List of Levels ---
        val startY = scale * 0.25f + scrollY
        var currentY = startY
        val btnHeight = scale * 0.15f
        val marginX = scale * 0.1f

        levelButtons.clear()

        // Loop through our custom generated node list
        for (lvl in cachedList) {
            // Setup bounding box for the touch button
            val btnRect = RectF(marginX, currentY, width - marginX, currentY + btnHeight - scale * 0.02f)
            levelButtons[lvl] = btnRect

            val config = LevelEngine.getLevelConfig(lvl)
            val isNextNode = (lvl == SaveManager.maxCampaignLevel)

            // Draw Background Card
            p.style = Paint.Style.FILL
            p.color = if (isNextNode) 0xFF113311.toInt() else 0xFF111115.toInt() // Current level highlighted green
            c.drawRoundRect(btnRect, scale * 0.02f, scale * 0.02f, p)

            // Draw Stroke border based on Level Type
            p.style = Paint.Style.STROKE
            p.color = when {
                isNextNode -> GameColors.HP
                config.type == LevelType.BOSS || config.type == LevelType.DEFENSE_BOSS -> GameColors.RED
                config.type == LevelType.DEFENSE -> GameColors.SHIELD
                config.type == LevelType.ADMIN_BONUS -> GameColors.YELLOW
                else -> GameColors.PULSE
            }
            p.strokeWidth = scale * 0.005f
            c.drawRoundRect(btnRect, scale * 0.02f, scale * 0.02f, p)

            // Draw Node Text
            pText.textAlign = Paint.Align.LEFT
            pText.color = if (isNextNode) GameColors.HP else GameColors.CLARITY
            pText.textSize = scale * 0.05f
            c.drawText(if(isNextNode) "RING $lvl [NEXT]" else "RING $lvl", marginX + scale * 0.05f, currentY + scale * 0.07f, pText)

            // Draw Sub-text (Level Type Lore)
            pText.textSize = scale * 0.03f
            pText.color = 0xFFAAAAAA.toInt()
            val typeStr = when (config.type) {
                LevelType.BOSS -> "WARDEN ENCOUNTER"
                LevelType.DEFENSE -> "CORE DEFENSE"
                LevelType.DEFENSE_BOSS -> "WARDEN + CORE DEFENSE"
                LevelType.ADMIN_BONUS -> "ADMIN PRIVILEGE"
                else -> "STANDARD SWEEP"
            }
            c.drawText("TYPE: $typeStr", marginX + scale * 0.05f, currentY + scale * 0.11f, pText)

            currentY += btnHeight
        }

        // Calculate scroll limits so the player can't scroll into empty space
        val totalListHeight = currentY - scrollY
        maxScroll = max(0f, totalListHeight - height + scale * 0.2f)

        // --- Draw "RETURN" (Close) Button at bottom ---
        closeBtnRect.set(width / 2f - scale * 0.2f, height - scale * 0.12f, width / 2f + scale * 0.2f, height - scale * 0.03f)
        p.style = Paint.Style.FILL
        p.color = 0xFF330000.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE
        p.color = GameColors.RED
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.RED
        pText.textSize = scale * 0.04f
        c.drawText("RETURN", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.015f, pText)
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, onSelect: (Int) -> Unit, onBack: () -> Unit): Boolean {
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
                // If the player tapped without dragging, check which node they clicked
                if (!isDragging) {
                    for ((lvl, rect) in levelButtons) {
                        if (rect.contains(x, y)) {
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                            onSelect(lvl)
                            return true
                        }
                    }
                }
            }
        }
        return true
    }
}