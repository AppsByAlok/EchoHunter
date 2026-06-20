package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.cos
import kotlin.math.sin

class HUDRenderer(private val context: Context) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rectPopup = RectF()

    fun drawHUD(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        gs.modeStrategy.drawModeSpecificHUD(context, c, gs, targetW, targetH, scale, pText)

        // --- 0. TOP OVERLAY (Gradient & HP Bar) ---
        drawTopOverlay(c, scale, gs, targetW)

        // --- 1. STATS & STATUS ---
        drawHealthAndScore(c, scale, gs)
        drawTopStatus(c, scale, gs, targetW)

        // --- 2. MOVEMENT JOYSTICK ---
        if (gs.controls.isMoveJoyActive) {
            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.01f; p.color = 0x55FFFFFF
            c.drawCircle(gs.touch.moveBaseX, gs.touch.moveBaseY, scale * 0.15f, p)
            p.style = Paint.Style.FILL; p.color = 0xAAFFFFFF.toInt()
            c.drawCircle(gs.touch.moveKnobX, gs.touch.moveKnobY, scale * 0.05f, p)
        }

        // --- 3. MANUAL AIM TOUCHPAD (Visual Boundary) ---
        if (gs.controls.activeAttackMode == AttackMode.MANUAL_AIM) {
            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.001f; p.color = 0x11FF0000
            c.drawRect(gs.hudLayout.manualAimRect, p)
        }

        // --- 4. ACTION BUTTONS (Labels & Icons Restored) ---
        drawAttackUI(c, scale, gs)
        
        val ovrColor = if (gs.overclockMeter >= 100f) GameColors.OVERCLOCK else GameColors.TEXT
        drawActionButton(c, gs.hudLayout.ovrX, gs.hudLayout.ovrY, gs.hudLayout.btnRadius, "OVR", if (gs.controls.isOverclockPressed) GameColors.CLARITY else ovrColor, false)

        val trapColor = if (gs.trapCooldownTimer <= 0f) GameColors.YELLOW else 0xFF555555.toInt()
        drawActionButton(c, gs.hudLayout.trapX, gs.hudLayout.trapY, gs.hudLayout.btnRadius, "TRAP", if (gs.controls.isTrapPressed) GameColors.CLARITY else trapColor, false)

        if (gs.isDarknessLevel || StoryProtocol.isBlackoutActive) {
            val pulseColor = if (gs.sonarTimer <= 0f) GameColors.PULSE else 0xFF555555.toInt()
            drawActionButton(c, gs.hudLayout.pulseX, gs.hudLayout.pulseY, gs.hudLayout.btnRadius, "SONAR", if (gs.controls.isSonarPressed || gs.controls.isAutoSonarLocked) GameColors.CLARITY else pulseColor, gs.controls.isAutoSonarLocked)
        }

        // --- 5. RADIAL MENUS (Upper Arc Distribution) ---
        if (gs.controls.isWeaponMenuOpen) {
            drawRadialMenu(c, scale, gs.hudLayout.atkX, gs.hudLayout.atkY, arrayOf("ATK", "AUTO", "MAN"), intArrayOf(GameColors.RED, GameColors.SHIELD, GameColors.RED), gs.controls.selectedWeaponIdx)
        }
        if (gs.controls.isTrapMenuOpen) {
            drawRadialMenu(c, scale, gs.hudLayout.trapX, gs.hudLayout.trapY, arrayOf("MINE", "STUN", "EMP"), intArrayOf(GameColors.TEXT, GameColors.OVERCLOCK, GameColors.SHIELD), gs.controls.selectedTrapIdx)
        }
        if (gs.controls.isSonarMenuOpen) {
            drawRadialMenu(c, scale, gs.hudLayout.pulseX, gs.hudLayout.pulseY, arrayOf("MANUAL", "LOCK"), intArrayOf(GameColors.TEXT, GameColors.SHIELD), gs.controls.selectedSonarIdx)
        }

        // --- 6. PAUSE & STORY POPUPS ---
        drawStoryPopups(c, scale, targetW, gs)

        p.style = Paint.Style.STROKE; p.color = GameColors.YELLOW; p.strokeWidth = scale * 0.005f
        c.drawCircle(gs.hudLayout.pauseX, gs.hudLayout.pauseY, gs.hudLayout.btnRadius * 0.8f, p)
        p.style = Paint.Style.FILL
        c.drawRect(gs.hudLayout.pauseX - scale * 0.015f, gs.hudLayout.pauseY - scale * 0.02f, gs.hudLayout.pauseX - scale * 0.005f, gs.hudLayout.pauseY + scale * 0.02f, p)
        c.drawRect(gs.hudLayout.pauseX + scale * 0.005f, gs.hudLayout.pauseY - scale * 0.02f, gs.hudLayout.pauseX + scale * 0.015f, gs.hudLayout.pauseY + scale * 0.02f, p)
    }

    private fun drawHealthAndScore(c: Canvas, scale: Float, gs: GameState) {
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = scale * 0.035f
        pText.color = GameColors.CLARITY
        
        val statsY = scale * 0.15f // Status Bar Margin
        c.drawText("DATA: ${SaveManager.formatDataString(gs.collectedDataKB)}", scale * 0.05f, statsY, pText)

        pText.color = GameColors.YELLOW
        c.drawText("SCORE: ${gs.score}", scale * 0.05f, statsY + scale * 0.05f, pText)
        if (gs.combo > 1) {
            pText.color = GameColors.OVERCLOCK
            c.drawText("COMBO x${gs.combo}", scale * 0.05f, statsY + scale * 0.10f, pText)
        }
    }

    private fun drawTopOverlay(c: Canvas, scale: Float, gs: GameState, targetW: Float) {
        // 1. Subtle Top Gradient Fade for visibility
        val gradientH = scale * 0.22f // Reduced height to match tighter layout
        p.shader = LinearGradient(0f, 0f, 0f, gradientH, 0xCC000000.toInt(), 0, Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, targetW, gradientH, p)
        p.shader = null

        // 2. Full Width HP Bar at the absolute top (Status Bar area)
        val hpH = scale * 0.02f
        val hpGap = scale * 0.005f
        val totalGap = hpGap * (gs.maxHp - 1)
        val hpW = (targetW - totalGap) / gs.maxHp
        
        p.style = Paint.Style.FILL
        for(i in 0 until gs.maxHp) {
            p.color = if (i < gs.hp) GameColors.HP else 0xFF333333.toInt()
            c.drawRect(i * (hpW + hpGap), 0f, i * (hpW + hpGap) + hpW, hpH, p)
        }
    }

    private fun drawTopStatus(c: Canvas, scale: Float, gs: GameState, targetW: Float) {
        var currentY = scale * 0.14f // Compact shift for center text
        pText.textAlign = Paint.Align.CENTER

        if (gs.isAutoPilotActive) {
            pText.color = GameColors.PULSE; pText.textSize = scale * 0.025f
            c.drawText("AUTOPILOT ACTIVE", targetW / 2f, currentY, pText)
            currentY += scale * 0.03f
        }

        if (gs.objectiveLabel.isNotEmpty()) {
            pText.color = GameColors.YELLOW; pText.textSize = scale * 0.025f
            c.drawText(gs.objectiveLabel, targetW / 2f, currentY, pText)
            currentY += scale * 0.025f
        }

        val barW = scale * 0.45f // Slightly wider for better visibility
        val barH = scale * 0.015f
        val barX = targetW / 2f - barW / 2f

        if (gs.objectiveLabel.isNotEmpty()) {
            // Priority 1: Draw Objective Progress Bar
            p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.003f; p.color = GameColors.YELLOW
            c.drawRect(barX, currentY, barX + barW, currentY + barH, p)
            p.style = Paint.Style.FILL; p.color = GameColors.YELLOW
            c.drawRect(barX, currentY, barX + barW * gs.objectiveProgress, currentY + barH, p)
            currentY += barH + scale * 0.03f
        }

        // Always draw Overclock Bar (Secondary, more compact)
        val ocW = scale * 0.3f
        val ocH = scale * 0.008f
        val ocX = targetW / 2f - ocW / 2f
        p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.002f; p.color = 0x88FFFFFF.toInt()
        c.drawRect(ocX, currentY, ocX + ocW, currentY + ocH, p)
        p.style = Paint.Style.FILL; p.color = if (gs.isOverclocked) GameColors.OVERCLOCK else GameColors.PULSE
        c.drawRect(ocX, currentY, ocX + ocW * (gs.overclockMeter / 100f), currentY + ocH, p)
    }

    private fun drawAttackUI(c: Canvas, scale: Float, gs: GameState) {
        val atkX = gs.hudLayout.atkX
        val atkY = gs.hudLayout.atkY
        val radius = gs.hudLayout.btnRadius
        val mode = gs.controls.activeAttackMode

        when (mode) {
            AttackMode.DEFAULT -> {
                val color = if (gs.controls.attackRequested) GameColors.TEXT else GameColors.RED
                drawActionButton(c, atkX, atkY, radius, "", color, false)
                // Technical Fire Icon (Triangle)
                p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.08f; p.color = color
                val path = android.graphics.Path()
                path.moveTo(atkX - radius * 0.2f, atkY - radius * 0.3f)
                path.lineTo(atkX + radius * 0.35f, atkY)
                path.lineTo(atkX - radius * 0.2f, atkY + radius * 0.3f)
                path.close()
                c.drawPath(path, p)
            }
            AttackMode.AUTO_AIM -> {
                val color = if (gs.controls.attackRequested) GameColors.OVERCLOCK else GameColors.SHIELD
                drawActionButton(c, atkX, atkY, radius, "", color, false)
                // Technical Tracking Icon
                p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.05f; p.color = color
                c.drawCircle(atkX, atkY, radius * 0.35f, p)
                c.drawLine(atkX - radius * 0.55f, atkY, atkX - radius * 0.2f, atkY, p)
                c.drawLine(atkX + radius * 0.55f, atkY, atkX + radius * 0.2f, atkY, p)
                c.drawLine(atkX, atkY - radius * 0.55f, atkX, atkY - radius * 0.2f, p)
                c.drawLine(atkX, atkY + radius * 0.55f, atkX, atkY + radius * 0.2f, p)
                p.style = Paint.Style.FILL; c.drawCircle(atkX, atkY, radius * 0.08f, p)
            }
            AttackMode.MANUAL_AIM -> {
                // Joystick Base Ring (Draw at base touch position if active, else at button position)
                val bx = if (gs.controls.manualAimActive) gs.touch.manualAimBaseX else atkX
                val by = if (gs.controls.manualAimActive) gs.touch.manualAimBaseY else atkY
                
                p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.008f; p.color = 0x44FF0000
                c.drawCircle(bx, by, scale * 0.15f, p)
                
                // Draw Base Button Ring (No Label)
                drawActionButton(c, atkX, atkY, radius, "", GameColors.RED, false)

                // Knob position
                val kx = if (gs.controls.manualAimActive) gs.touch.manualAimKnobX else atkX
                val ky = if (gs.controls.manualAimActive) gs.touch.manualAimKnobY else atkY
                
                // RESTORED JOYSTICK KNOB
                p.style = Paint.Style.FILL; p.color = 0xAAFF0000.toInt()
                c.drawCircle(kx, ky, radius * 0.6f, p)
                p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.006f
                c.drawCircle(kx, ky, radius * 0.6f, p)
                
                p.style = Paint.Style.FILL; p.color = GameColors.HP
                c.drawCircle(kx, ky, radius * 0.15f, p)
            }
        }
    }

    private fun drawRadialMenu(c: Canvas, scale: Float, cx: Float, cy: Float, labels: Array<String>, colors: IntArray, selectedIdx: Int) {
        val radius = scale * 0.25f
        val count = labels.size
        // Shifted Arc distribution: Centered more towards the left (180 to 300 degrees)
        // This prevents the right-most item from going off-screen
        val startAngle = 180f
        val arcRange = 120f 
        val step = if (count > 1) arcRange / (count - 1) else 0f

        p.style = Paint.Style.FILL; p.color = 0x88000000.toInt()
        c.drawCircle(cx, cy, radius + scale * 0.1f, p)

        for (i in 0 until count) {
            val angleDeg = startAngle + i * step
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val lx = cx + cos(angleRad).toFloat() * radius
            val ly = cy + sin(angleRad).toFloat() * radius
            val isSelected = (i == selectedIdx)
            val color = colors[i % colors.size]

            if (isSelected) {
                p.style = Paint.Style.FILL; p.color = color; p.alpha = 180
                c.drawCircle(lx, ly, scale * 0.09f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.015f; p.alpha = 255
                c.drawCircle(lx, ly, scale * 0.09f, p)
            } else {
                p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.006f; p.color = 0xCCFFFFFF.toInt()
                c.drawCircle(lx, ly, scale * 0.07f, p)
            }
            pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.035f
            pText.color = if (isSelected) color else 0xDDFFFFFF.toInt()
            c.drawText(labels[i], lx, ly + pText.textSize * 0.35f, pText)
        }
    }

    private fun drawActionButton(c: Canvas, x: Float, y: Float, radius: Float, label: String, color: Int, isAutoLocked: Boolean) {
        p.style = Paint.Style.STROKE; p.strokeWidth = radius * 0.06f; p.color = color
        c.drawCircle(x, y, radius * 0.85f, p)
        p.style = Paint.Style.FILL; p.color = (0x33 shl 24) or (color and 0xFFFFFF)
        c.drawCircle(x, y, radius * 0.85f, p)
        pText.color = color; pText.textSize = radius * 0.38f; pText.textAlign = Paint.Align.CENTER
        c.drawText(label, x, y + pText.textSize * 0.35f, pText)
    }

    private fun drawStoryPopups(c: Canvas, scale: Float, targetW: Float, gs: GameState) {
        val msgTimer = StoryProtocol.popupTimer
        if (msgTimer <= 0f) return
        val msgText = StoryProtocol.currentPopupText ?: ""
        if (msgText.isBlank()) return

        val alpha = if (msgTimer < 0.5f) (msgTimer * 2 * 255).toInt().coerceIn(0, 255) else 255
        pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.045f
        val maxW = targetW * 0.8f
        val wrappedLines = mutableListOf<String>()
        msgText.split("\n").forEach { p ->
            var line = ""
            p.split(" ").forEach { w ->
                if (pText.measureText("$line $w") > maxW) { wrappedLines.add(line); line = w } else line = if(line.isEmpty()) w else "$line $w"
            }
            wrappedLines.add(line)
        }

        val startY = c.height * 0.28f
        val lh = pText.descent() - pText.ascent()
        val padding = scale * 0.05f
        
        var maxWidth = 0f
        wrappedLines.forEach { maxWidth = Math.max(maxWidth, pText.measureText(it)) }
        
        rectPopup.set(targetW/2f - maxWidth/2f - padding, startY + pText.ascent() - padding, 
                     targetW/2f + maxWidth/2f + padding, startY + (wrappedLines.size - 1) * lh + pText.descent() + padding)
        
        p.style = Paint.Style.FILL; p.color = (alpha * 0.85f).toInt() shl 24; c.drawRoundRect(rectPopup, 25f, 25f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.006f; p.color = (alpha shl 24) or (GameColors.YELLOW and 0xFFFFFF)
        c.drawRoundRect(rectPopup, 25f, 25f, p)
        
        pText.color = (alpha shl 24) or (GameColors.YELLOW and 0xFFFFFF)
        wrappedLines.forEachIndexed { i, l -> c.drawText(l, targetW/2f, startY + i * lh, pText) }
    }

    fun renderOverclockText(c: Canvas, scale: Float, targetW: Float, targetH: Float) {
        pText.textSize = scale * 0.08f; pText.textAlign = Paint.Align.CENTER; pText.color = GameColors.OVERCLOCK
        c.drawText("OVERRIDE ACCEPTED", targetW / 2f, targetH / 2f - scale * 0.2f, pText)
    }
}
