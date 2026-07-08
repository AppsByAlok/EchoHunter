package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.LevelEngine
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
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

    private var scrollVelocity = 0f
    private var lastTouchTime = 0L

    private val levelButtons = mutableMapOf<Int, RectF>()
    private val closeBtnRect = RectF()
    private var cachedList = mutableListOf<Int>()
    private var lastMaxLevel = -1

    private val autoNextBoxRect = RectF()
    private val autoPilotBoxRect = RectF()
    private var hitOnDown = -1

    // Garbage Collector stutter variables optimized out using object pools
    private val reusableRect = RectF()
    private val badgeRect = RectF()
    private val featureEnumValues = LevelFeature.entries.toTypedArray()

    private fun generateNodeList() {
        cachedList.clear()
        val maxLvl = SaveManager.maxCampaignLevel
        lastMaxLevel = maxLvl

        cachedList.add(maxLvl)
        val minRecent = max(1, maxLvl - 150)
        for (i in maxLvl - 1 downTo minRecent) cachedList.add(i)

        var importantCount = 0
        for (i in minRecent - 1 downTo 1) {
            val config = LevelEngine.getLevelConfig(i)
            if (config.features.contains(LevelFeature.BOSS) || config.features.contains(LevelFeature.DEFENSE) || config.features.contains(LevelFeature.ESCAPE) || config.features.contains(LevelFeature.ELIMINATION)) {
                cachedList.add(i)
                importantCount++
                if (importantCount >= 50) break
            }
        }
    }

    // High-performance Manual ARGB Blend Logic via Bitwise Masks
    private fun mixColorsManual(colorA: Int, colorB: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio

        val aA = (colorA shr 24 and 0xFF)
        val aR = (colorA shr 16 and 0xFF)
        val aG = (colorA shr 8 and 0xFF)
        val aaVal = colorA and 0xFF

        val bA = (colorB shr 24 and 0xFF)
        val bR = (colorB shr 16 and 0xFF)
        val bG = (colorB shr 8 and 0xFF)
        val bbVal = colorB and 0xFF

        val outA = ((aA * inverseRatio) + (bA * ratio)).toInt()
        val outR = ((aR * inverseRatio) + (bR * ratio)).toInt()
        val outG = ((aG * inverseRatio) + (bG * ratio)).toInt()
        val outB = ((aaVal * inverseRatio) + (bbVal * ratio)).toInt()

        return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    fun draw(c: Canvas, width: Float, height: Float, gs: GameState, scale: Float) {
        c.drawColor(0xEE050508.toInt()) // Dark cyber hacking base background

        if (!isDragging && abs(scrollVelocity) > 0.5f) {
            scrollY += scrollVelocity
            scrollVelocity *= 0.96f

            if (scrollY > 0f) {
                scrollY = 0f
                scrollVelocity = 0f
            } else if (scrollY < -maxScroll) {
                scrollY = -maxScroll
                scrollVelocity = 0f
            }
        }

        if (SaveManager.maxCampaignLevel != lastMaxLevel || cachedList.isEmpty()) generateNodeList()

        pText.textSize = scale * 0.07f
        pText.color = GameColors.PULSE
        c.drawText("SYSTEM ARCHIVES", width / 2f, scale * 0.11f, pText)

        val boxWidth = if (width > height) scale * 0.35f else scale * 0.38f

        // Auto-Next Checkbox Button Layout
        autoNextBoxRect.set(width / 2f - boxWidth, scale * 0.17f, width / 2f - scale * 0.015f, scale * 0.26f)
        p.style = Paint.Style.STROKE; p.color = if (SaveManager.isAutoNextLevelEnabled) GameColors.HP else GameColors.RED; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(autoNextBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.FILL; p.color = 0x1F000000
        c.drawRoundRect(autoNextBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * 0.032f; pText.color = if (SaveManager.isAutoNextLevelEnabled) GameColors.HP else GameColors.RED
        c.drawText(if (SaveManager.isAutoNextLevelEnabled) "AUTO-NEXT: ON" else "AUTO-NEXT: OFF", autoNextBoxRect.centerX(), autoNextBoxRect.centerY() + scale * 0.011f, pText)

        // Autopilot Checkbox Button Layout
        autoPilotBoxRect.set(width / 2f + scale * 0.015f, scale * 0.17f, width / 2f + boxWidth, scale * 0.26f)
        p.style = Paint.Style.STROKE; p.color = if (gs.isAutoPilotActive) GameColors.HP else GameColors.RED; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(autoPilotBoxRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.FILL; p.color = 0x1F000000
        c.drawRoundRect(autoPilotBoxRect, scale * 0.01f, scale * 0.01f, p)
        pText.textSize = scale * 0.032f; pText.color = if (gs.isAutoPilotActive) GameColors.HP else GameColors.RED
        c.drawText(if (gs.isAutoPilotActive) "AUTOPILOT: ON" else "AUTOPILOT: OFF", autoPilotBoxRect.centerX(), autoPilotBoxRect.centerY() + scale * 0.011f, pText)

        // Dynamic Row Generation Layout Config
        val startY = scale * 0.32f + scrollY
        val boxSize = scale * 0.16f
        val gap = scale * 0.035f
        val columns = if (width > height) 6 else 4
        val totalW = columns * boxSize + (columns - 1) * gap
        val startX = (width - totalW) / 2f

        levelButtons.clear()

        var col = 0
        var row = 0

        val topClipLimit = scale * 0.28f
        val bottomClipLimit = height - scale * 0.13f

        for (lvl in cachedList) {
            val cx = startX + col * (boxSize + gap)
            val cy = startY + row * (boxSize + gap)

            reusableRect.set(cx, cy, cx + boxSize, cy + boxSize)

            if (reusableRect.bottom >= topClipLimit && reusableRect.top <= bottomClipLimit) {
                levelButtons[lvl] = RectF(reusableRect)

                val config = LevelEngine.getLevelConfig(lvl)
                val isNextNode = (lvl == SaveManager.maxCampaignLevel)

                // --- 1. DYNAMIC ACCENT TONE BLENDING ENGINE ---
                var mixedColor = GameColors.GRID
                var colorCount = 0

                for (f in featureEnumValues) {
                    if (config.features.contains(f)) {
                        val featureColor = when (f) {
                            LevelFeature.CLASSIC -> GameColors.PULSE
                            LevelFeature.MAZE -> GameColors.TEXT
                            LevelFeature.DEFENSE -> GameColors.SHIELD
                            LevelFeature.BOSS -> GameColors.BOSS
                            LevelFeature.ESCAPE -> GameColors.YELLOW
                            LevelFeature.ELIMINATION -> GameColors.RED
                            LevelFeature.SPECIAL -> GameColors.OVERCLOCK
                            LevelFeature.ADMIN_BONUS -> GameColors.HP
                            LevelFeature.BOMB -> 0xFFFF0000.toInt()
                            LevelFeature.DARKNESS -> 0xFF000000.toInt()
                            LevelFeature.CLEAN_SWEEP -> GameColors.COOLANT
                        }
                        mixedColor = if (colorCount == 0) featureColor else mixColorsManual(mixedColor, featureColor, 0.5f)
                        colorCount++
                    }
                }

                val baseBgTone = if (isNextNode) 0xFF052510.toInt() else GameColors.BG
                val finalBgColor = mixColorsManual(baseBgTone, mixedColor, 0.25f)

                // Background Matrix Render
                p.style = Paint.Style.FILL; p.color = finalBgColor
                c.drawRoundRect(reusableRect, scale * 0.015f, scale * 0.015f, p)

                // Circuit Board Framing Borders
                p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f
                p.color = if (isNextNode) GameColors.HP else mixColorsManual(0x22FFFFFF, mixedColor, 0.4f)
                c.drawRoundRect(reusableRect, scale * 0.015f, scale * 0.015f, p)

                // --- 2. MICRO-CHIP ICONS ROW LAYOUT ---
                if (colorCount > 0) {
                    val badgeSize = boxSize * 0.25f // Square size for icons
                    val badgeGap = boxSize * 0.05f
                    val totalBadgesW = (colorCount * badgeSize) + ((colorCount - 1) * badgeGap)

                    var currentBadgeX = reusableRect.centerX() - (totalBadgesW / 2f)
                    val badgeY = reusableRect.bottom - boxSize * 0.32f

                    for (f in featureEnumValues) {
                        if (config.features.contains(f)) {
                            // Assign unique color for the icon
                            p.color = when (f) {
                                LevelFeature.CLASSIC -> GameColors.PULSE
                                LevelFeature.MAZE -> GameColors.TEXT
                                LevelFeature.DEFENSE -> GameColors.SHIELD
                                LevelFeature.BOSS -> GameColors.BOSS
                                LevelFeature.ESCAPE -> GameColors.YELLOW
                                LevelFeature.ELIMINATION -> GameColors.RED
                                LevelFeature.SPECIAL -> GameColors.OVERCLOCK
                                LevelFeature.ADMIN_BONUS -> GameColors.HP
                                LevelFeature.BOMB -> 0xFFFF0000.toInt()
                                LevelFeature.DARKNESS -> 0xFF000000.toInt()
                                LevelFeature.CLEAN_SWEEP -> GameColors.COOLANT
                            }

                            badgeRect.set(currentBadgeX, badgeY, currentBadgeX + badgeSize, badgeY + badgeSize)
                            com.appsbyalok.echohunter.utils.LevelIcons.drawMicroIcon(c, f, badgeRect, p, finalBgColor)

                            currentBadgeX += badgeSize + badgeGap
                        }
                    }
                }

                // --- 3. HARDWARE SIGNAL TEXT LAYER ---
                pText.color = GameColors.CLARITY
                pText.textSize = scale * 0.04f
                c.drawText("$lvl", reusableRect.centerX(), reusableRect.centerY() - boxSize * 0.05f, pText)
            }

            col++
            if (col >= columns) { col = 0; row++ }
        }

        val totalHeight = (row + 1) * (boxSize + gap)
        maxScroll = max(0f, totalHeight - height + scale * 0.45f)

        // Micro-console Terminate Core System Button
        closeBtnRect.set(width / 2f - scale * 0.16f, height - scale * 0.10f, width / 2f + scale * 0.16f, height - scale * 0.03f)
        p.style = Paint.Style.FILL; p.color = 0xFF220505.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.01f, scale * 0.01f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.004f
        c.drawRoundRect(closeBtnRect, scale * 0.01f, scale * 0.01f, p)

        pText.color = GameColors.RED; pText.textSize = scale * 0.035f
        c.drawText("DISCONNECT", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.012f, pText)
    }

    private var touchDownX = 0f
    private var touchDownY = 0f

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, gs: GameState, onSelect: (Int) -> Unit, onBack: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x
                touchDownY = y
                lastTouchY = y
                lastTouchTime = System.currentTimeMillis()
                scrollVelocity = 0f
                isDragging = false
                hitOnDown = when {
                    closeBtnRect.contains(x, y) -> -2
                    autoNextBoxRect.contains(x, y) -> -3
                    autoPilotBoxRect.contains(x, y) -> -4
                    else -> {
                        var hit = -1
                        for ((lvl, rect) in levelButtons) {
                            if (rect.contains(x, y)) {
                                hit = lvl
                                break
                            }
                        }
                        hit
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val currentTime = System.currentTimeMillis()
                val dt = currentTime - lastTouchTime
                val dy = y - lastTouchY
                val dx = x - touchDownX

                val distSq = dx * dx + (y - touchDownY) * (y - touchDownY)
                val threshold = scale * scale * 0.05f

                if (abs(dy) > scale * 0.02f) {
                    isDragging = true
                    hitOnDown = -1
                } else if (distSq > threshold) {
                    hitOnDown = -1
                }

                // VELOCITY CALCULATION
                if (dt > 0) {
                    val rawVelocity = (dy / dt.toFloat()) * 20f
                    scrollVelocity = (scrollVelocity * 0.4f) + (rawVelocity * 0.6f)
                }
                scrollY += dy
                // Hard stops check
                if (scrollY > 0f) {
                    scrollY = 0f
                    scrollVelocity = 0f
                }
                if (scrollY < -maxScroll) {
                    scrollY = -maxScroll
                    scrollVelocity = 0f
                }

                lastTouchY = y
                lastTouchTime = currentTime
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging && hitOnDown != -1) {
                    val hitOnUp = when {
                        closeBtnRect.contains(x, y) -> -2
                        autoNextBoxRect.contains(x, y) -> -3
                        autoPilotBoxRect.contains(x, y) -> -4
                        else -> {
                            var hit = -1
                            for ((lvl, rect) in levelButtons) {
                                if (rect.contains(x, y)) {
                                    hit = lvl
                                    break
                                }
                            }
                            hit
                        }
                    }

                    if (hitOnUp != -1 && hitOnUp == hitOnDown) {
                        when (hitOnUp) {
                            -2 -> {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                                onBack()
                            }
                            -3 -> {
                                SaveManager.setAutoNextLevel(!SaveManager.isAutoNextLevelEnabled)
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                            }
                            -4 -> {
                                gs.isAutoPilotActive = !gs.isAutoPilotActive
                                if (gs.isAutoPilotActive) {
                                    gs.autoPilotTimer = 600f
                                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
                                    gs.showGlobalMessage("AUTOPILOT ENGAGED.\nSELECT A LEVEL TO START.", 3f)
                                } else {
                                    gs.autoPilotTimer = 0f
                                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                                }
                            }
                            else -> {
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                                onSelect(hitOnUp)
                            }
                        }
                    }
                }
                isDragging = false
                hitOnDown = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                hitOnDown = -1
            }
        }
        return true
    }
}