package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors

class HUDRenderer(private val context: Context) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rectPopup = RectF()

    fun drawHUD(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        // --- 0. MODE SPECIFIC OVERLAY (Restore StoryMode Node info etc.) ---
        gs.modeStrategy.drawModeSpecificHUD(context, c, gs, targetW, targetH, scale, pText)

        // --- 1. NEON HEALTH BLOCKS & SCORE INFO ---
        val hpStartX = scale * 0.05f
        val hpStartY = scale * 0.13f // Moved down to avoid overlap with Universal Toast
        val hpW = scale * 0.06f
        val hpH = scale * 0.02f // Slightly thinner
        val hpGap = scale * 0.01f

        p.style = Paint.Style.FILL
        for(i in 0 until gs.maxHp) {
            p.color = if (i < gs.hp) GameColors.HP else 0xFF333333.toInt()
            c.drawRect(hpStartX + (hpW + hpGap) * i, hpStartY, hpStartX + (hpW + hpGap) * i + hpW, hpStartY + hpH, p)
        }

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = scale * 0.035f
        pText.color = GameColors.CLARITY
        c.drawText("DATA: ${SaveManager.formatDataString(gs.collectedDataKB)}", scale * 0.05f, hpStartY + hpH + scale * 0.04f, pText)

        pText.color = GameColors.YELLOW
        c.drawText("SCORE: ${gs.score}", scale * 0.05f, hpStartY + hpH + scale * 0.09f, pText)
        if (gs.combo > 1) {
            pText.color = GameColors.OVERCLOCK
            c.drawText("COMBO x${gs.combo}", scale * 0.05f, hpStartY + hpH + scale * 0.14f, pText)
        }

        // --- 2. CENTER TOP UI (Dynamic Stacking for NO OVERLAPS) ---
        var currentY = scale * 0.16f // Moved down to avoid overlap with Top Notification Toast
        pText.textAlign = Paint.Align.CENTER

        // A. Autopilot Tag (Only if active)
        if (gs.isAutoPilotActive) {
            pText.color = GameColors.PULSE
            pText.textSize = scale * 0.025f
            c.drawText("AUTOPILOT ACTIVE", targetW / 2f, currentY, pText)
            currentY += scale * 0.03f
        } else {
            currentY += scale * 0.015f
        }

        // B. Overclock Bar (Smaller and tighter)
        val barW = scale * 0.35f
        val barH = scale * 0.015f
        val barX = targetW / 2f - barW / 2f

        p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f; p.color = GameColors.TEXT
        c.drawRect(barX, currentY, barX + barW, currentY + barH, p)

        p.style = Paint.Style.FILL; p.color = if (gs.isOverclocked) GameColors.OVERCLOCK else GameColors.PULSE
        c.drawRect(barX, currentY, barX + barW * (gs.overclockMeter / 100f), currentY + barH, p)

        currentY += barH + scale * 0.045f

        // C. Defense Mode Custom UI Header
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE) && gs.gameMode == 0) {
            
            pText.textAlign = Paint.Align.CENTER
            
            when (gs.defWaveState) {
                1 -> { // ACTIVE WAVE: Fighting Phase
                    pText.textSize = scale * 0.045f
                    pText.color = GameColors.RED
                    val enemiesLeft = gs.defEnemiesToSpawn + gs.defEnemiesAlive
                    c.drawText("ENEMIES LEFT: $enemiesLeft", targetW / 2f, currentY, pText)
                    
                    // Sub-header
                    pText.textSize = scale * 0.025f
                    pText.color = GameColors.TEXT
                    c.drawText("WAVE ${gs.defWaveCurrent} / ${gs.defWaveMax}", targetW / 2f, currentY + scale * 0.035f, pText)
                }
                0, 2 -> { // BUFFER / COOLDOWN: Waiting Phase
                    pText.textSize = scale * 0.045f
                    pText.color = GameColors.SHIELD
                    val secondsLeft = kotlin.math.max(0, gs.defWaveTimer.toInt())
                    c.drawText("NEXT WAVE IN: ${secondsLeft}s", targetW / 2f, currentY, pText)

                    // Sub-header
                    pText.textSize = scale * 0.025f
                    pText.color = GameColors.CLARITY
                    c.drawText("PREPARING WAVE ${gs.defWaveCurrent} / ${gs.defWaveMax}", targetW / 2f, currentY + scale * 0.035f, pText)
                }
            }
        }


        // --- 3. VIRTUAL JOYSTICK ---
        if (gs.controls.isMoveJoyActive) {
            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.01f; p.color = 0x55FFFFFF
            c.drawCircle(gs.touch.joyBaseX, gs.touch.joyBaseY, scale * 0.15f, p)
            p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt()
            c.drawCircle(gs.touch.joyKnobX, gs.touch.joyKnobY, scale * 0.05f, p)
        }

        // --- 4. ACTION BUTTONS (With Auto Locks) ---
        drawActionButton(c, gs.hudLayout.atkX, gs.hudLayout.atkY, gs.hudLayout.btnRadius, "ATK", if (gs.controls.isAttackPressed || gs.controls.isAutoFireLocked) GameColors.TEXT else GameColors.RED, gs.controls.isAutoFireLocked)

        val ovrColor = if (gs.overclockMeter >= 100f) GameColors.OVERCLOCK else GameColors.TEXT
        drawActionButton(c, gs.hudLayout.ovrX, gs.hudLayout.ovrY, gs.hudLayout.btnRadius, "OVR", if (gs.controls.isOverclockPressed) GameColors.CLARITY else ovrColor, false)

        val trapColor = if (gs.trapCooldownTimer <= 0f) GameColors.YELLOW else 0xFF555555.toInt()
        drawActionButton(c, gs.hudLayout.trapX, gs.hudLayout.trapY, gs.hudLayout.btnRadius, "TRAP", if (gs.controls.isTrapPressed) GameColors.CLARITY else trapColor, false)

        // SONAR is only visible when the environment is actually dark
        val isDarknessActive = gs.isDarknessLevel || StoryProtocol.isBlackoutActive

        if (isDarknessActive) {
            val pulseColor = if (gs.cooldownTimer <= 0f) GameColors.PULSE else 0xFF555555.toInt()
            drawActionButton(c, gs.hudLayout.pulseX, gs.hudLayout.pulseY, gs.hudLayout.btnRadius, "SONAR", if (gs.controls.isSonarPressed || gs.controls.isAutoSonarLocked) GameColors.CLARITY else pulseColor, gs.controls.isAutoSonarLocked)
        }


        // --- IN-GAME MESSAGE / STORY POPUP RENDERING ---
        val msgTimer = StoryProtocol.popupTimer

        if (msgTimer > 0f) {
            val msgText = StoryProtocol.currentPopupText
                ?: StoryProtocol.currentPopupRes.takeIf { it != 0 }?.let { context.getString(it) }
                ?: ""

            if (msgText.isNotBlank()) {

                val alpha = if (msgTimer < 0.5f) (msgTimer * 2 * 255).toInt().coerceIn(0, 255) else 255

                pText.textAlign = Paint.Align.CENTER
                pText.textSize = scale * 0.045f

                // 2. AUTO WORD-WRAP LOGIC (Multi-line split + Screen bounds check)
                val maxAllowedWidth = targetW * 0.8f // Screen width ka 80% max limit
                val wrappedLines = mutableListOf<String>()

                // Pehle explicit \n ko handle karein
                val paragraphs = msgText.split("\n")
                for (paragraph in paragraphs) {
                    val words = paragraph.split(" ")
                    var currentLine = ""

                    for (word in words) {
                        if (currentLine.isEmpty()) {
                            currentLine = word
                        } else {
                            val testLine = "$currentLine $word"
                            val textWidth = pText.measureText(testLine)

                            // Agar line limit se bahar ja rahi hai, toh current line ko save karke new line start karein
                            if (textWidth > maxAllowedWidth) {
                                wrappedLines.add(currentLine)
                                currentLine = word
                            } else {
                                currentLine = testLine
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        wrappedLines.add(currentLine)
                    }
                }

                // 3. Responsive Y Positioning
                val startY = c.height.toFloat() * 0.25f
                val lineHeight = pText.descent() - pText.ascent()

                // 4. Calculate Background Box Dimensions dynamically based on wrapped lines
                var maxLineWidth = 0f
                for (line in wrappedLines) {
                    val width = pText.measureText(line)
                    if (width > maxLineWidth) maxLineWidth = width
                }

                val paddingX = scale * 0.06f
                val paddingY = scale * 0.03f
                val cornerRadius = scale * 0.02f

                rectPopup.set(
                    (targetW / 2f) - (maxLineWidth / 2f) - paddingX,
                    startY + pText.ascent() - paddingY,
                    (targetW / 2f) + (maxLineWidth / 2f) + paddingX,
                    startY + (wrappedLines.size - 1) * lineHeight + pText.descent() + paddingY
                )

                // 5. Draw Semi-Transparent Dark Background Box
                p.style = Paint.Style.FILL
                p.color = ((alpha * 0.6f).toInt() shl 24) or 0x000000
                c.drawRoundRect(rectPopup, cornerRadius, cornerRadius, p)

                // Draw Box Border
                p.style = Paint.Style.STROKE
                p.strokeWidth = scale * 0.003f
                p.color = (alpha shl 24) or (GameColors.YELLOW and 0xFFFFFF)
                c.drawRoundRect(rectPopup, cornerRadius, cornerRadius, p)

                // 6. Draw Text Lines
                pText.color = (alpha shl 24) or (GameColors.YELLOW and 0xFFFFFF)
                pText.setShadowLayer(8f, 0f, 0f, (alpha shl 24) or 0x000000)

                for ((index, line) in wrappedLines.withIndex()) {
                    val lineY = startY + (index * lineHeight)
                    c.drawText(line, targetW / 2f, lineY, pText)
                }

                pText.clearShadowLayer()
            }
        }


        // Pause Button
        p.style = Paint.Style.STROKE; p.color = GameColors.YELLOW; p.strokeWidth = scale * 0.005f
        c.drawCircle(gs.hudLayout.pauseX, gs.hudLayout.pauseY, gs.hudLayout.btnRadius * 0.8f, p)
        p.style = Paint.Style.FILL
        c.drawRect(gs.hudLayout.pauseX - scale * 0.015f, gs.hudLayout.pauseY - scale * 0.02f, gs.hudLayout.pauseX - scale * 0.005f, gs.hudLayout.pauseY + scale * 0.02f, p)
        c.drawRect(gs.hudLayout.pauseX + scale * 0.005f, gs.hudLayout.pauseY - scale * 0.02f, gs.hudLayout.pauseX + scale * 0.015f, gs.hudLayout.pauseY + scale * 0.02f, p)
    }

    private fun drawActionButton(c: Canvas, x: Float, y: Float, radius: Float, label: String, color: Int, isAutoLocked: Boolean) {
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.05f; p.color = color
        c.drawCircle(x, y, radius * 0.8f, p)

        p.style = Paint.Style.FILL; p.color = (0x22 shl 24) or (color and 0xFFFFFF)
        c.drawCircle(x, y, radius * 0.8f, p)

        pText.color = color; pText.textSize = radius * 0.35f; pText.textAlign = Paint.Align.CENTER
        c.drawText(label, x, y + pText.textSize * 0.3f, pText)

        if (isAutoLocked) {
            pText.textSize = radius * 0.2f
            pText.color = GameColors.PULSE
            c.drawText("AUTO", x, y - radius * 0.9f, pText)
        }
    }

    fun renderOverclockText(c: Canvas, scale: Float, targetW: Float, targetH: Float) {
        pText.textSize = scale * 0.08f
        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.OVERCLOCK
        pText.setShadowLayer(15f, 0f, 0f, GameColors.OVERCLOCK)
        c.drawText("OVERRIDE ACCEPTED", targetW / 2f, targetH / 2f - scale * 0.2f, pText)
        pText.clearShadowLayer()
    }
}
