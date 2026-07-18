package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.ui.components.UIMenuButton
import com.appsbyalok.echohunter.ui.components.UIMenuCard
import com.appsbyalok.echohunter.ui.components.UIMenuMetrics
import com.appsbyalok.echohunter.ui.components.UIMenuScreenChrome
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

    private val closeButton = UIMenuButton()
    private val chrome = UIMenuScreenChrome()
    private val card = UIMenuCard()
    private val cardRects = Array(5) { RectF() }
    private var hitOnDown = -1
    private var downX = 0f
    private var downY = 0f

    // Card Titles & Subtitles
    private val titles = arrayOf(
        "</> EXPLOIT.exe",
        "[+] LOADOUT.sys",
        "[Σ] MAINFRAME.sys",
        ">_ ROOT_TERM.sh",
        "[*] SETTINGS.cfg"
    )
    private val subs = arrayOf(
        "Firmware Upgrades",
        "Hardware Weapons",
        "Simulation Control",
        "System Control & Logs",
        "System Configuration"
    )

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, time: Float) {
        val metrics = UIMenuMetrics(targetW, targetH, scale)
        chrome.drawBackground(c, metrics, p, bgColor = 0xEE050A0F.toInt())
        val isPortrait = metrics.isPortrait

        // --- 1. TOP HEADER (STATUS BAR) ---
        val insetT = metrics.insetTop
        val insetL = metrics.insetLeft
        val insetR = metrics.insetRight

        val headerH = metrics.headerHeight
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
        val dotX = scale * 0.05f + insetL
        if (blink) c.drawCircle(dotX, insetT + (headerH - insetT) * 0.5f, scale * 0.012f, p)
        c.drawText("UPLINK: STABLE", dotX + scale * 0.04f, insetT + (headerH - insetT) * 0.62f, pText)

        pText.textAlign = Paint.Align.RIGHT
        pText.color = GameColors.CLARITY
        c.drawText(SaveManager.formatDataString(SaveManager.dataCoinsKB), targetW - scale * 0.05f - insetR, insetT + (headerH - insetT) * 0.62f, pText)

        // --- 2. THE APP GRID (MODULE CARDS) ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.055f
        pText.color = GameColors.CLARITY
        pText.isFakeBoldText = true
        val titleY = if (isPortrait) targetH * 0.2f else targetH * 0.25f
        
        // Header Underline
        val titleW = pText.measureText("NANO-OS DASHBOARD")
        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.003f
        p.color = GameColors.CLARITY
        c.drawLine(targetW/2f - titleW*0.6f, titleY + scale*0.02f, targetW/2f + titleW*0.6f, titleY + scale*0.02f, p)
        
        c.drawText("NANO-OS DASHBOARD", targetW / 2f, titleY, pText)
        pText.isFakeBoldText = false

        val startY = titleY + scale * 0.12f
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
            // Landscape: Grid (3 on top, 2 on bottom) - Symmetric Centering
            val contentW = targetW - insetL - insetR
            val cardW = (scale * 0.62f).coerceAtMost((contentW - gap * 3f) / 3f)
            val cardH = scale * 0.18f
            
            val row1W = cardW * 3 + gap * 2
            val startX = insetL + (contentW - row1W) / 2f

            // Row 1 (3 items)
            for (i in 0..2) {
                val rx = startX + i * (cardW + gap)
                cardRects[i].set(rx, startY, rx + cardW, startY + cardH)
            }
            
            // Row 2 (2 items)
            val row2Y = startY + cardH + gap
            val row2W = cardW * 2 + gap
            val row2StartX = insetL + (contentW - row2W) / 2f
            for (i in 3..4) {
                val rx = row2StartX + (i - 3) * (cardW + gap)
                cardRects[i].set(rx, row2Y, rx + cardW, row2Y + cardH)
            }

            for (i in 0..4) drawCard(c, cardRects[i], i, scale)
        }

        // --- 3. BOTTOM FOOTER (DISCONNECT) ---
        val btnW = if (isPortrait) targetW * 0.7f else scale * 0.4f
        val btnH = scale * 0.09f
        val bottomMargin = scale * 0.05f

        closeButton.set(
            targetW / 2f - btnW / 2f,
            targetH - btnH - bottomMargin,
            targetW / 2f + btnW / 2f,
            targetH - bottomMargin
        )

        closeButton.draw(
            c = c,
            scale = scale,
            paint = p,
            textPaint = pText,
            label = "DISCONNECT",
            pressed = hitOnDown == 5,
            fillColor = 0xFF330000.toInt(),
            strokeColor = GameColors.RED,
            textColor = GameColors.RED,
            radius = scale * 0.02f,
            textSize = scale * 0.045f
        )
    }

    private fun drawCard(c: Canvas, rect: RectF, index: Int, scale: Float) {
        val isPressed = (hitOnDown == index)
        val baseCardColor = 0xFF051515.toInt()

        card.draw(
            c = c,
            rect = rect,
            scale = scale,
            paint = p,
            pressed = isPressed,
            fillColor = baseCardColor,
            strokeColor = GameColors.PULSE,
            radius = scale * 0.03f
        )

        val padding = scale * 0.04f
        val maxTextW = rect.width() - padding * 2f
        pText.textAlign = Paint.Align.LEFT

        // Auto-scale Title text
        val tSize = scale * 0.048f
        pText.textSize = tSize
        val tw = pText.measureText(titles[index])
        val titleFinalSize = if (tw > maxTextW) tSize * (maxTextW / tw) else tSize
        pText.textSize = titleFinalSize
        pText.color = GameColors.PULSE
        c.drawText(titles[index], rect.left + padding, rect.top + rect.height() * 0.45f, pText)

        // Auto-scale Subtitle text
        val sSize = scale * 0.032f
        pText.textSize = sSize
        val sw = pText.measureText(subs[index])
        val subFinalSize = if (sw > maxTextW) sSize * (maxTextW / sw) else sSize
        pText.textSize = subFinalSize
        pText.color = GameColors.CLARITY
        c.drawText(subs[index], rect.left + padding, rect.bottom - rect.height() * 0.22f, pText)
    }

    fun onTouch(x: Float, y: Float, action: Int, scale: Float, onAppSelect: (Int) -> Unit, onDisconnect: () -> Unit): Boolean {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                hitOnDown = when {
                    closeButton.contains(x, y) -> 5
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
                        closeButton.contains(x, y) -> 5
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
