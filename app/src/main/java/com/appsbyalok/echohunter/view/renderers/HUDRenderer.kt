package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.SparseArray
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.engine.GameColors
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class HUDRenderer(private val context: Context) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    val pauseRect = RectF()
    private val btnRect = RectF() // Pre-allocated for drawing circular arcs to avoid GC overhead

    // --- Cached Strings for Zero-GC Render Loop ---
    private var lastScore = -1; private var scoreStr = ""
    private var lastCombo = -1; private var comboStr = ""
    private var lastBankedData = -1; private var bankedStr = ""

    private val stringCache = SparseArray<String>()

    private fun getCachedString(resId: Int): String {
        var str = stringCache.get(resId)
        if (str == null) {
            str = context.getString(resId)
            stringCache.put(resId, str)
        }
        return str
    }

    fun drawHUD(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float, isOverclocked: Boolean) {
        val topMargin = scale * 0.06f
        val edgeMargin = scale * 0.05f

        // --- PAUSE BUTTON ---
        val pauseSize = scale * 0.1f
        pauseRect.set(targetW - edgeMargin - pauseSize, edgeMargin, targetW - edgeMargin, edgeMargin + pauseSize)
        p.style = Paint.Style.FILL
        p.color = 0x33FFFFFF
        c.drawRoundRect(pauseRect, scale*0.02f, scale*0.02f, p)
        pText.color = GameColors.CLARITY; pText.textSize = scale*0.04f; pText.textAlign = Paint.Align.CENTER
        c.drawText("||", pauseRect.centerX(), pauseRect.centerY() + scale*0.015f, pText)

        // --- TOP LEFT HUD (Score & Data) ---
        pText.textAlign = Paint.Align.LEFT
        if (gs.score != lastScore) {
            scoreStr = context.getString(R.string.ui_data, gs.score)
            lastScore = gs.score
        }
        pText.color = GameColors.PULSE; pText.textSize = scale * 0.055f
        pText.setShadowLayer(10f, 0f, 0f, GameColors.PULSE)
        c.drawText(scoreStr, edgeMargin, topMargin + scale * 0.02f, pText)
        pText.clearShadowLayer()

        if (SaveManager.totalData != lastBankedData) {
            lastBankedData = SaveManager.totalData
            bankedStr = context.getString(R.string.ui_banked, lastBankedData)
        }
        pText.color = 0xFFAAAAAA.toInt(); pText.textSize = scale * 0.035f
        c.drawText(bankedStr, edgeMargin, topMargin + scale * 0.07f, pText)

        pText.textAlign = Paint.Align.CENTER
        gs.modeStrategy.drawModeSpecificHUD(context, c, gs, targetW, targetH, scale, pText)

        // --- METERS (HP, Clarity, Overclock) ---
        val barW = scale * 0.06f; val barH = scale * 0.015f; val gap = scale * 0.008f
        val metersTopY = edgeMargin + pauseSize + gap * 3
        val metersRightX = targetW - edgeMargin

        val isHpFlashing = gs.hp == 1 && sin(gs.timeSinceStart * 15f) > 0

        p.style = Paint.Style.FILL
        for (i in 0 until gs.maxHp) {
            p.color = if (i < gs.hp) (if (isHpFlashing) GameColors.RED else GameColors.HP) else 0xFF333333.toInt()
            val rx = metersRightX - (i + 1) * (barW + gap) + gap
            c.drawRect(rx, metersTopY, rx + barW, metersTopY + barH, p)
        }

        val totalMeterW = (gs.maxHp * (barW + gap)) - gap
        val meterLeftX = metersRightX - totalMeterW

        val cRy = metersTopY + barH + gap
        p.color = 0xFF333333.toInt(); c.drawRect(meterLeftX, cRy, metersRightX, cRy + barH, p)
        p.color = GameColors.CLARITY; c.drawRect(meterLeftX, cRy, meterLeftX + totalMeterW * min(1f, gs.visionClarity), cRy + barH, p)

        val ocRy = cRy + barH + gap
        p.color = 0xFF333333.toInt(); c.drawRect(meterLeftX, ocRy, metersRightX, ocRy + barH * 1.5f, p)

        val isOcFlashing = isOverclocked && sin(gs.timeSinceStart * 20f) > 0
        p.color = if (isOcFlashing) GameColors.CLARITY else GameColors.OVERCLOCK
        c.drawRect(meterLeftX, ocRy, meterLeftX + totalMeterW * (gs.overclockMeter / 100f), ocRy + barH * 1.5f, p)

        pText.textAlign = Paint.Align.RIGHT; pText.textSize = scale * 0.025f; pText.color = GameColors.TEXT
        c.drawText(getCachedString(R.string.ui_vis), meterLeftX - gap, cRy + barH * 0.9f, pText)
        c.drawText(getCachedString(R.string.ui_ovr), meterLeftX - gap, ocRy + barH * 1.2f, pText)

        // --- CENTER COMBO ---
        if (gs.combo > 1) {
            pText.textAlign = Paint.Align.CENTER
            val bounce = sin(gs.timeSinceStart * 15f) * scale * 0.005f
            pText.textSize = scale * 0.065f + bounce
            val comboColor = when { gs.combo >= 15 -> GameColors.OVERCLOCK; gs.combo >= 8 -> GameColors.YELLOW; else -> GameColors.PULSE }
            pText.color = comboColor; pText.setShadowLayer(25f, 0f, 0f, comboColor)
            if (gs.combo != lastCombo) {
                comboStr = context.getString(R.string.ui_combo, gs.combo)
                lastCombo = gs.combo
            }
            c.drawText(comboStr, targetW / 2f, targetH * 0.28f, pText)
            pText.clearShadowLayer()
        }

        // --- COMBO BREAK TEXT ---
        if (gs.comboBreakTimer > 0f) {
            pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.05f; pText.color = GameColors.RED
            pText.alpha = (gs.comboBreakTimer * 255).toInt()
            c.drawText(getCachedString(R.string.ui_combo_broken), targetW / 2f, targetH * 0.35f, pText)
            pText.alpha = 255
        }

        // --- STORY/POPUP TEXT ---
        if (StoryProtocol.popupTimer > 0f) {
            pText.textAlign = Paint.Align.CENTER; pText.textSize = scale * 0.05f; pText.color = GameColors.RED
            pText.alpha = min(255, (StoryProtocol.popupTimer * 255).toInt())
            pText.setShadowLayer(20f, 0f, 0f, GameColors.RED)
            val textX = targetW / 2f + if(StoryProtocol.isGlitchActive) (Random.nextFloat()-0.5f)*10f else 0f

            val textToDraw = StoryProtocol.currentPopupText ?: getCachedString(StoryProtocol.currentPopupRes)

            val lines = textToDraw.split("\n")
            var yPos = targetH * 0.42f
            for (line in lines) {
                c.drawText(line, textX, yPos, pText)
                yPos += scale * 0.06f
            }

            pText.alpha = 255; pText.clearShadowLayer()
        }

        // --- DRAW VIRTUAL CONTROLS (Joystick & Buttons) ---
        drawControls(c, scale, gs, targetW, targetH)
    }

    private fun drawControls(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        // --- 1. THE VIRTUAL JOYSTICK ---
        val joyMaxRadius = scale * 0.15f
        if (gs.isJoyActive) {
            // Draw Active Outer Ring
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.005f
            p.color = 0x55FFFFFF
            c.drawCircle(gs.joyBaseX, gs.joyBaseY, joyMaxRadius, p)

            // Draw Active Inner Knob
            p.style = Paint.Style.FILL
            p.color = 0xAAFFFFFF.toInt()
            c.drawCircle(gs.joyKnobX, gs.joyKnobY, joyMaxRadius * 0.4f, p)
        } else {
            // Draw Faint Hint Ring (When not touched)
            p.style = Paint.Style.STROKE
            p.strokeWidth = scale * 0.005f
            p.color = 0x22FFFFFF
            c.drawCircle(scale * 0.25f, targetH - scale * 0.25f, joyMaxRadius, p)

            p.style = Paint.Style.FILL
            p.color = 0x22FFFFFF
            c.drawCircle(scale * 0.25f, targetH - scale * 0.25f, joyMaxRadius * 0.4f, p)
        }

        // --- 2. TACTICAL ACTION BUTTONS ---
        val btnRadius = scale * 0.08f

        // Exact Layout Matching TouchController.kt
        val btnPulseX = targetW - scale * 0.15f
        val btnPulseY = targetH - scale * 0.15f
        val btnAttackX = targetW - scale * 0.32f
        val btnAttackY = targetH - scale * 0.22f
        val btnOcX = targetW - scale * 0.12f
        val btnOcY = targetH - scale * 0.38f

        // A. PULSE (SONAR) BUTTON
        val isPulseCooldown = gs.cooldownTimer > 0f
        drawTacticalButton(c, "SONAR", btnPulseX, btnPulseY, btnRadius, GameColors.PULSE, false, isPulseCooldown)
        if (isPulseCooldown) {
            // Radial Cooldown Sweep Effect
            val sweep = 360f * (gs.cooldownTimer / 0.25f) // 0.25f is max cooldown from TouchController
            btnRect.set(btnPulseX - btnRadius, btnPulseY - btnRadius, btnPulseX + btnRadius, btnPulseY + btnRadius)
            p.style = Paint.Style.FILL
            p.color = 0x77000000
            c.drawArc(btnRect, -90f, sweep, true, p)
        }

        // B. ATTACK (MALWARE SPIKE) BUTTON
        drawTacticalButton(c, "ATK", btnAttackX, btnAttackY, btnRadius, GameColors.RED, gs.isAttackPressed, false)

        // C. OVERCLOCK (ULTIMATE) BUTTON
        val ocReady = gs.overclockMeter >= 100f
        val ocColor = if (ocReady) GameColors.OVERCLOCK else 0xFF555555.toInt()
        drawTacticalButton(c, "OVR", btnOcX, btnOcY, btnRadius, ocColor, gs.isOverclockPressed, !ocReady)
    }

    private fun drawTacticalButton(
        c: Canvas, text: String, x: Float, y: Float, r: Float,
        color: Int, isPressed: Boolean, isDisabled: Boolean
    ) {
        // Button Inner Background (Filled if pressed, faint if idle)
        p.style = Paint.Style.FILL
        p.color = if (isPressed) color else 0x33000000
        c.drawCircle(x, y, r, p)

        // Button Outer Ring
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.08f
        p.color = if (isDisabled) 0x55FFFFFF else color
        c.drawCircle(x, y, r, p)

        // Button Label
        pText.color = if (isPressed) GameColors.BG else (if (isDisabled) 0x55FFFFFF else color)
        pText.textSize = r * 0.4f
        pText.textAlign = Paint.Align.CENTER

        // Add subtle shadow for unpressed text to give a neon glow look
        if (!isPressed && !isDisabled) pText.setShadowLayer(10f, 0f, 0f, color) else pText.clearShadowLayer()

        c.drawText(text, x, y + r * 0.15f, pText)
        pText.clearShadowLayer()
    }

    fun renderOverclockText(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        pText.color = GameColors.OVERCLOCK; pText.textSize = scale * 0.06f; pText.textAlign = Paint.Align.CENTER
        pText.alpha = (min(1f, gs.showOverclockTextTimer) * 255).toInt()
        pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText(getCachedString(R.string.ui_overclock_ready), targetW / 2f, targetH * 0.45f, pText)
        pText.alpha = 255; pText.clearShadowLayer()
    }
}