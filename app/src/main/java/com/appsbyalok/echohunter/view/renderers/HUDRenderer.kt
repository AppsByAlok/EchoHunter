package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.utils.GameColors

class HUDRenderer(private val context: Context) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rectPopup = RectF()

    fun drawHUD(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        // --- 0. MODE SPECIFIC OVERLAY ---
        gs.modeStrategy.drawModeSpecificHUD(context, c, gs, targetW, targetH, scale, pText)

        // --- 1. NEON HEALTH BLOCKS & SCORE INFO ---
        val hpStartX = scale * 0.05f
        val hpStartY = scale * 0.13f
        val hpW = scale * 0.06f
        val hpH = scale * 0.02f
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

        // --- 2. CENTER TOP UI ---
        var currentY = scale * 0.16f
        pText.textAlign = Paint.Align.CENTER

        if (gs.isAutoPilotActive) {
            pText.color = GameColors.PULSE
            pText.textSize = scale * 0.025f
            c.drawText("AUTOPILOT ACTIVE", targetW / 2f, currentY, pText)
            currentY += scale * 0.03f
        } else {
            currentY += scale * 0.015f
        }

        val barW = scale * 0.35f
        val barH = scale * 0.015f
        val barX = targetW / 2f - barW / 2f

        p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f; p.color = GameColors.TEXT
        c.drawRect(barX, currentY, barX + barW, currentY + barH, p)

        p.style = Paint.Style.FILL; p.color = if (gs.isOverclocked) GameColors.OVERCLOCK else GameColors.PULSE
        c.drawRect(barX, currentY, barX + barW * (gs.overclockMeter / 100f), currentY + barH, p)

        currentY += barH + scale * 0.045f

        // --- 3. MOVEMENT JOYSTICK ---
        if (gs.controls.isMoveJoyActive) {
            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.01f; p.color = 0x55FFFFFF
            c.drawCircle(gs.touch.moveBaseX, gs.touch.moveBaseY, scale * 0.15f, p)
            p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt()
            c.drawCircle(gs.touch.moveKnobX, gs.touch.moveKnobY, scale * 0.05f, p)
        }

        // --- 4. DYNAMIC ATTACK UI ---
        drawAttackUI(c, scale, gs)

        // --- 5. OTHER ACTION BUTTONS ---
        val ovrColor = if (gs.overclockMeter >= 100f) GameColors.OVERCLOCK else GameColors.TEXT
        drawActionButton(c, gs.hudLayout.ovrX, gs.hudLayout.ovrY, gs.hudLayout.btnRadius, "OVR", if (gs.controls.isOverclockPressed) GameColors.CLARITY else ovrColor, false)

        val trapColor = if (gs.trapCooldownTimer <= 0f) GameColors.YELLOW else 0xFF555555.toInt()
        drawActionButton(c, gs.hudLayout.trapX, gs.hudLayout.trapY, gs.hudLayout.btnRadius, "TRAP", if (gs.controls.isTrapPressed) GameColors.CLARITY else trapColor, false)

        if (gs.isDarknessLevel || StoryProtocol.isBlackoutActive) {
            val pulseColor = if (gs.cooldownTimer <= 0f) GameColors.PULSE else 0xFF555555.toInt()
            drawActionButton(c, gs.hudLayout.pulseX, gs.hudLayout.pulseY, gs.hudLayout.btnRadius, "SONAR", if (gs.controls.isSonarPressed || gs.controls.isAutoSonarLocked) GameColors.CLARITY else pulseColor, gs.controls.isAutoSonarLocked)
        }

        // --- STORY POPUPS & PAUSE ---
        drawStoryPopups(c, scale, targetW, gs)

        p.style = Paint.Style.STROKE; p.color = GameColors.YELLOW; p.strokeWidth = scale * 0.005f
        c.drawCircle(gs.hudLayout.pauseX, gs.hudLayout.pauseY, gs.hudLayout.btnRadius * 0.8f, p)
        p.style = Paint.Style.FILL
        c.drawRect(gs.hudLayout.pauseX - scale * 0.015f, gs.hudLayout.pauseY - scale * 0.02f, gs.hudLayout.pauseX - scale * 0.005f, gs.hudLayout.pauseY + scale * 0.02f, p)
        c.drawRect(gs.hudLayout.pauseX + scale * 0.005f, gs.hudLayout.pauseY - scale * 0.02f, gs.hudLayout.pauseX + scale * 0.015f, gs.hudLayout.pauseY + scale * 0.02f, p)
    }

    private fun drawAttackUI(c: Canvas, scale: Float, gs: GameState) {
        val atkX = gs.hudLayout.atkX
        val atkY = gs.hudLayout.atkY
        val radius = gs.hudLayout.btnRadius
        val mode = gs.controls.activeAttackMode

        when (mode) {
            AttackMode.DEFAULT -> {
                val color = if (gs.controls.attackRequested) GameColors.TEXT else GameColors.RED
                drawActionButton(c, atkX, atkY, radius, "ATK", color, false)
            }
            AttackMode.AUTO_AIM -> {
                val color = if (gs.controls.attackRequested) GameColors.OVERCLOCK else GameColors.SHIELD
                drawActionButton(c, atkX, atkY, radius, "AUTO", color, false)
                // Add a small crosshair icon inside
                p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.05f; p.color = color
                c.drawCircle(atkX, atkY, radius * 0.3f, p)
                c.drawLine(atkX - radius * 0.4f, atkY, atkX + radius * 0.4f, atkY, p)
                c.drawLine(atkX, atkY - radius * 0.4f, atkX, atkY + radius * 0.4f, p)
            }
            AttackMode.MANUAL_AIM -> {
                // Background Base
                p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.008f; p.color = 0x44FF0000.toInt()
                c.drawCircle(atkX, atkY, scale * 0.15f, p)
                
                // Active Joystick
                if (gs.controls.isAttackTouching) {
                    val joyMaxR = scale * 0.15f
                    val knobX = atkX + gs.controls.aimDirX * (gs.controls.attackPullDist * joyMaxR)
                    val knobY = atkY + gs.controls.aimDirY * (gs.controls.attackPullDist * joyMaxR)
                    
                    p.style = Paint.Style.FILL; p.color = 0x88FF0000.toInt()
                    c.drawCircle(knobX, knobY, radius * 0.6f, p)
                    
                    p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.005f
                    c.drawCircle(knobX, knobY, radius * 0.6f, p)
                } else {
                    // Idle Joystick Button
                    drawActionButton(c, atkX, atkY, radius, "MAN", GameColors.RED, false)
                }
            }
        }
    }

    private fun drawActionButton(c: Canvas, x: Float, y: Float, radius: Float, label: String, color: Int, isAutoLocked: Boolean) {
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.05f; p.color = color
        c.drawCircle(x, y, radius * 0.8f, p)
        p.style = Paint.Style.FILL; p.color = (0x22 shl 24) or (color and 0xFFFFFF)
        c.drawCircle(x, y, radius * 0.8f, p)
        pText.color = color; pText.textSize = radius * 0.35f; pText.textAlign = Paint.Align.CENTER
        c.drawText(label, x, y + pText.textSize * 0.3f, pText)
        if (isAutoLocked) {
            pText.textSize = radius * 0.2f; pText.color = GameColors.PULSE
            c.drawText("AUTO", x, y - radius * 0.9f, pText)
        }
    }

    private fun drawStoryPopups(c: Canvas, scale: Float, targetW: Float, gs: GameState) {
        val msgTimer = StoryProtocol.popupTimer
        if (msgTimer <= 0f) return
        val msgText = StoryProtocol.currentPopupText ?: StoryProtocol.currentPopupRes.takeIf { it != 0 }?.let { context.getString(it) } ?: ""
        if (msgText.isBlank()) return

        val alpha = if (msgTimer < 0.5f) (msgTimer * 2 * 255).toInt().coerceIn(0, 255) else 255
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.045f
        val maxAllowedWidth = targetW * 0.8f
        val wrappedLines = mutableListOf<String>()
        val paragraphs = msgText.split("\n")
        for (paragraph in paragraphs) {
            val words = paragraph.split(" ")
            var currentLine = ""
            for (word in words) {
                if (currentLine.isEmpty()) currentLine = word
                else {
                    val testLine = "$currentLine $word"
                    if (pText.measureText(testLine) > maxAllowedWidth) {
                        wrappedLines.add(currentLine); currentLine = word
                    } else currentLine = testLine
                }
            }
            if (currentLine.isNotEmpty()) wrappedLines.add(currentLine)
        }

        val startY = c.height.toFloat() * 0.25f
        val lineHeight = pText.descent() - pText.ascent()
        var maxLineWidth = 0f
        for (line in wrappedLines) {
            val width = pText.measureText(line)
            if (width > maxLineWidth) maxLineWidth = width
        }

        val paddingX = scale * 0.06f; val paddingY = scale * 0.03f; val cornerRadius = scale * 0.02f
        rectPopup.set((targetW / 2f) - (maxLineWidth / 2f) - paddingX, startY + pText.ascent() - paddingY, (targetW / 2f) + (maxLineWidth / 2f) + paddingX, startY + (wrappedLines.size - 1) * lineHeight + pText.descent() + paddingY)

        p.style = Paint.Style.FILL; p.color = ((alpha * 0.6f).toInt() shl 24) or 0x000000
        c.drawRoundRect(rectPopup, cornerRadius, cornerRadius, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f; p.color = (alpha shl 24) or (GameColors.YELLOW and 0xFFFFFF)
        c.drawRoundRect(rectPopup, cornerRadius, cornerRadius, p)

        pText.color = (alpha shl 24) or (GameColors.YELLOW and 0xFFFFFF)
        pText.setShadowLayer(8f, 0f, 0f, (alpha shl 24) or 0x000000)
        for ((index, line) in wrappedLines.withIndex()) {
            c.drawText(line, targetW / 2f, startY + (index * lineHeight), pText)
        }
        pText.clearShadowLayer()
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
