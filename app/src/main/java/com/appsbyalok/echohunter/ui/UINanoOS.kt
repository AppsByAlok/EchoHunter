package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.sin

class UINanoOS {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val closeBtnRect = RectF()
    private val cardRects = Array(5) { RectF() }
    private var hitOnDown = -1
    private var downX = 0f
    private var downY = 0f

    // Card Titles & Subtitles
    private val titles = arrayOf(
        "</> EXPLOIT.exe",
        "[+] LOADOUT.sys",
        "[■] ARCHIVES.dir",
        ">_ ROOT_TERM.sh",
        "[*] SETTINGS.cfg"
    )
    private val subs = arrayOf(
        "Firmware Upgrades",
        "Hardware Weapons",
        "Simulation Memory",
        "System Control & Logs",
        "System Configuration"
    )

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, time: Float) {
        c.drawColor(0xEE050A0F.toInt()) // Deep Hacker Blue-Black BG

        // --- SCANLINE EFFECT ---
        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.002f
        p.color = 0x0AFFFFFF
        var slY = 0f
        while (slY < targetH) {
            c.drawLine(0f, slY, targetW, slY, p)
            slY += scale * 0.012f
        }

        val isPortrait = targetH > targetW

        // --- 1. TOP HEADER (STATUS BAR) ---
        val headerH = if (isPortrait) scale * 0.12f else scale * 0.08f
        p.style = Paint.Style.FILL
        p.color = 0xFF0A1520.toInt()
        c.drawRect(0f, 0f, targetW, headerH, p)

        p.color = GameColors.PULSE
        c.drawRect(0f, headerH - scale * 0.005f, targetW, headerH, p)

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = if (isPortrait) scale * 0.032f else scale * 0.04f
        pText.color = GameColors.HP

        // Blinking Uplink Dot
        val blink = (sin(time * 5f) > 0)
        val dotX = scale * 0.05f
        if (blink) c.drawCircle(dotX, headerH * 0.5f, scale * 0.012f, p)
        c.drawText("UPLINK: STABLE", dotX + scale * 0.04f, headerH * 0.62f, pText)

        pText.textAlign = Paint.Align.RIGHT
        pText.color = GameColors.CLARITY
        c.drawText(SaveManager.formatDataString(SaveManager.dataCoinsKB), targetW - scale * 0.05f, headerH * 0.62f, pText)

        // --- 2. THE APP GRID (MODULE CARDS) ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.06f
        pText.color = GameColors.CLARITY
        val titleY = if (isPortrait) targetH * 0.2f else targetH * 0.25f
        c.drawText("NANO-OS DASHBOARD", targetW / 2f, titleY, pText)

        val startY = titleY + scale * 0.1f
        val gap = scale * 0.05f

        if (isPortrait) {
            // Portrait: 1 Column, 5 Rows
            val cardW = targetW * 0.85f
            val cardH = scale * 0.16f
            val startX = (targetW - cardW) / 2f
            var currY = startY

            for (i in 0..4) {
                cardRects[i].set(startX, currY, startX + cardW, currY + cardH)
                drawCard(c, cardRects[i], i, scale)
                currY += cardH + gap
            }
        } else {
            // Landscape: Grid (3 on top, 2 on bottom or similar)
            val cardW = targetW * 0.3f
            val cardH = scale * 0.16f
            val startX1 = targetW / 2f - cardW * 1.5f - gap
            val startX2 = targetW / 2f - cardW / 2f
            val startX3 = targetW / 2f + cardW / 2f + gap

            // Row 1
            cardRects[0].set(startX1, startY, startX1 + cardW, startY + cardH)
            cardRects[1].set(startX2, startY, startX2 + cardW, startY + cardH)
            cardRects[2].set(startX3, startY, startX3 + cardW, startY + cardH)
            
            // Row 2
            val row2Y = startY + cardH + gap
            val startX2_1 = targetW / 2f - cardW - gap / 2f
            val startX2_2 = targetW / 2f + gap / 2f
            cardRects[3].set(startX2_1, row2Y, startX2_1 + cardW, row2Y + cardH)
            cardRects[4].set(startX2_2, row2Y, startX2_2 + cardW, row2Y + cardH)

            for (i in 0..4) drawCard(c, cardRects[i], i, scale)
        }

        // --- 3. BOTTOM FOOTER (DISCONNECT) ---
        val btnW = if (isPortrait) targetW * 0.7f else scale * 0.4f
        val btnH = scale * 0.09f
        val bottomMargin = scale * 0.05f

        closeBtnRect.set(
            targetW / 2f - btnW / 2f,
            targetH - btnH - bottomMargin,
            targetW / 2f + btnW / 2f,
            targetH - bottomMargin
        )

        val isPressed = (hitOnDown == 5)
        val baseRed = 0xFF330000.toInt()
        p.style = Paint.Style.FILL; p.color = if (isPressed) GameColors.mixColors(baseRed, GameColors.RED, 0.4f) else baseRed
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.RED; pText.textSize = scale * 0.045f
        if (isPressed) pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        c.drawText("DISCONNECT", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.015f, pText)
        pText.clearShadowLayer()
    }

    private fun drawCard(c: Canvas, rect: RectF, index: Int, scale: Float) {
        val isPressed = (hitOnDown == index)
        val baseCardColor = 0xFF051515.toInt()

        p.style = Paint.Style.FILL
        p.color = if (isPressed) GameColors.mixColors(baseCardColor, GameColors.PULSE, 0.15f) else baseCardColor
        c.drawRoundRect(rect, scale * 0.03f, scale * 0.03f, p)

        p.style = Paint.Style.STROKE
        p.color = if (isPressed) GameColors.CLARITY else GameColors.PULSE
        p.strokeWidth = scale * 0.005f
        c.drawRoundRect(rect, scale * 0.03f, scale * 0.03f, p)

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = scale * 0.05f
        pText.color = GameColors.PULSE
        c.drawText(titles[index], rect.left + scale * 0.05f, rect.top + scale * 0.08f, pText)

        pText.textSize = scale * 0.035f
        pText.color = GameColors.CLARITY
        c.drawText(subs[index], rect.left + scale * 0.05f, rect.bottom - scale * 0.04f, pText)
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, onAppSelect: (Int) -> Unit, onDisconnect: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                hitOnDown = when {
                    closeBtnRect.contains(x, y) -> 5
                    else -> {
                        var hit = -1
                        for (i in 0..4) {
                            if (cardRects[i].contains(x, y)) {
                                hit = i
                                break
                            }
                        }
                        hit
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (hitOnDown != -1) {
                    val dx = x - downX
                    val dy = y - downY
                    if (dx * dx + dy * dy > scale * scale * 0.05f) {
                        hitOnDown = -1
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (hitOnDown != -1) {
                    val hitOnUp = when {
                        closeBtnRect.contains(x, y) -> 5
                        else -> {
                            var hit = -1
                            for (i in 0..4) {
                                if (cardRects[i].contains(x, y)) {
                                    hit = i
                                    break
                                }
                            }
                            hit
                        }
                    }

                    if (hitOnUp == hitOnDown) {
                        if (hitOnUp == 5) {
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                            onDisconnect()
                        } else {
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                            onAppSelect(hitOnUp) // i: 0=Decompiler, 1=Arsenal, 2=Archives, 3=Terminal, 4=Settings
                        }
                    }
                }
                hitOnDown = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                hitOnDown = -1
            }
        }
        return true
    }
}