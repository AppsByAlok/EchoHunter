package com.appsbyalok.echohunter.view.renderers

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors

class HUDRenderer {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    fun drawHUD(c: Canvas, scale: Float, gs: GameState, targetW: Float) {

        // --- 1. NEON HEALTH BLOCKS & SCORE INFO ---
        val hpStartX = scale * 0.05f
        val hpStartY = scale * 0.05f
        val hpW = scale * 0.06f
        val hpH = scale * 0.025f
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
        var currentY = scale * 0.06f // Starting height for the center elements

        // A. Level/Act Title
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.04f
        pText.color = GameColors.CLARITY
        val modeTitle = if (gs.gameMode == 1) "ACT ${gs.selectedStoryAct + 1} - SECTOR ${gs.currentSector}" else "LEVEL ${gs.currentLevel}"
        c.drawText(modeTitle, targetW / 2f, currentY, pText)

        currentY += scale * 0.035f

        // B. Autopilot Tag
        if (gs.isAutoPilotActive) {
            pText.color = GameColors.PULSE
            pText.textSize = scale * 0.03f
            c.drawText("AUTOPILOT ENGAGED", targetW / 2f, currentY, pText)
            currentY += scale * 0.025f
        } else {
            currentY += scale * 0.01f // Normal gap
        }

        // C. Overclock Bar
        val barW = scale * 0.4f
        val barH = scale * 0.02f
        val barX = targetW / 2f - barW / 2f

        p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.005f; p.color = GameColors.TEXT
        c.drawRect(barX, currentY, barX + barW, currentY + barH, p)

        p.style = Paint.Style.FILL; p.color = if (gs.isOverclocked) GameColors.OVERCLOCK else GameColors.PULSE
        c.drawRect(barX, currentY, barX + barW * (gs.overclockMeter / 100f), currentY + barH, p)

        currentY += barH + scale * 0.05f

        // D. Defense Mode Uplink Timer
        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE)) {
            val secondsLeft = kotlin.math.max(0, gs.defenseTimer.toInt())

            // Defence Timer text
            if (secondsLeft > 0) {
                pText.color = when {
                    secondsLeft <= 5 -> GameColors.SHIELD
                    secondsLeft <= 10 -> GameColors.YELLOW
                    else -> GameColors.CLARITY
                }

                pText.textSize = scale * 0.04f
                if (secondsLeft <= 5) {
                    c.drawText("UPLINK SECURES IN: ${secondsLeft}s", targetW / 2f, currentY, pText)
                } else {
                    c.drawText("UPLINK STABLE: ${secondsLeft}s", targetW / 2f, currentY, pText)
                }
            }
       }


        // --- 3. VIRTUAL JOYSTICK ---
        if (gs.isJoyActive) {
            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.01f; p.color = 0x55FFFFFF
            c.drawCircle(gs.joyBaseX, gs.joyBaseY, scale * 0.15f, p)
            p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt()
            c.drawCircle(gs.joyKnobX, gs.joyKnobY, scale * 0.05f, p)
        }

        // --- 4. ACTION BUTTONS (With Auto Locks) ---
        drawActionButton(c, gs.uiAtkX, gs.uiAtkY, gs.uiBtnRadius, "ATK", if (gs.isAttackPressed || gs.isAutoFireLocked) GameColors.TEXT else GameColors.RED, gs.isAutoFireLocked)

        val ovrColor = if (gs.overclockMeter >= 100f) GameColors.OVERCLOCK else GameColors.TEXT
        drawActionButton(c, gs.uiOvrX, gs.uiOvrY, gs.uiBtnRadius, "OVR", if (gs.isOverclockPressed) GameColors.CLARITY else ovrColor, false)

        val trapColor = if (gs.trapCooldownTimer <= 0f) GameColors.YELLOW else 0xFF555555.toInt()
        drawActionButton(c, gs.uiTrapX, gs.uiTrapY, gs.uiBtnRadius, "TRAP", if (gs.isTrapPressed) GameColors.CLARITY else trapColor, false)

        val pulseColor = if (gs.cooldownTimer <= 0f) GameColors.PULSE else 0xFF555555.toInt()
        drawActionButton(c, gs.uiPulseX, gs.uiPulseY, gs.uiBtnRadius, "SONAR", if (gs.isSonarPressed || gs.isAutoSonarLocked) GameColors.CLARITY else pulseColor, gs.isAutoSonarLocked)

        // Pause Button
        p.style = Paint.Style.STROKE; p.color = GameColors.YELLOW; p.strokeWidth = scale * 0.005f
        c.drawCircle(gs.uiPauseX, gs.uiPauseY, gs.uiBtnRadius * 0.8f, p)
        p.style = Paint.Style.FILL
        c.drawRect(gs.uiPauseX - scale * 0.015f, gs.uiPauseY - scale * 0.02f, gs.uiPauseX - scale * 0.005f, gs.uiPauseY + scale * 0.02f, p)
        c.drawRect(gs.uiPauseX + scale * 0.005f, gs.uiPauseY - scale * 0.02f, gs.uiPauseX + scale * 0.015f, gs.uiPauseY + scale * 0.02f, p)
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