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
    private val cardRects = Array(4) { RectF() }

    // Card Titles & Subtitles
    private val titles = arrayOf(
        "</> EXPLOIT.exe",
        "[+] LOADOUT.sys",
        "[■] ARCHIVES.dir",
        ">_ ROOT_TERM.sh"
    )
    private val subs = arrayOf(
        "Firmware Upgrades",
        "Hardware Weapons",
        "Simulation Memory",
        "[ ENCRYPTED ]"
    )

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, time: Float) {
        c.drawColor(0xEE050A0F.toInt()) // Deep Hacker Blue-Black BG

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
            // Portrait: 1 Column, 4 Rows
            val cardW = targetW * 0.85f
            val cardH = scale * 0.2f
            val startX = (targetW - cardW) / 2f
            var currY = startY

            for (i in 0..3) {
                cardRects[i].set(startX, currY, startX + cardW, currY + cardH)
                drawCard(c, cardRects[i], i, scale)
                currY += cardH + gap
            }
        } else {
            // Landscape: 2x2 Grid
            val cardW = targetW * 0.4f
            val cardH = scale * 0.18f
            val startX1 = targetW / 2f - cardW - gap / 2f
            val startX2 = targetW / 2f + gap / 2f

            // Row 1
            cardRects[0].set(startX1, startY, startX1 + cardW, startY + cardH)
            cardRects[1].set(startX2, startY, startX2 + cardW, startY + cardH)
            // Row 2
            cardRects[2].set(startX1, startY + cardH + gap, startX1 + cardW, startY + cardH * 2 + gap)
            cardRects[3].set(startX2, startY + cardH + gap, startX2 + cardW, startY + cardH * 2 + gap)

            for (i in 0..3) drawCard(c, cardRects[i], i, scale)
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

        p.style = Paint.Style.FILL; p.color = 0xFF330000.toInt()
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE; p.color = GameColors.RED; p.strokeWidth = scale * 0.005f
        c.drawRoundRect(closeBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.RED; pText.textSize = scale * 0.045f
        c.drawText("DISCONNECT", closeBtnRect.centerX(), closeBtnRect.centerY() + scale * 0.015f, pText)
    }

    private fun drawCard(c: Canvas, rect: RectF, index: Int, scale: Float) {
        val isLocked = (index == 3 && !SaveManager.isHardModeUnlocked)

        p.style = Paint.Style.FILL
        p.color = if (isLocked) 0xFF110505.toInt() else 0xFF051515.toInt()
        c.drawRoundRect(rect, scale * 0.03f, scale * 0.03f, p)

        p.style = Paint.Style.STROKE
        p.color = if (isLocked) 0xFF550000.toInt() else GameColors.PULSE
        p.strokeWidth = scale * 0.005f
        c.drawRoundRect(rect, scale * 0.03f, scale * 0.03f, p)

        pText.textAlign = Paint.Align.LEFT
        pText.textSize = scale * 0.05f
        pText.color = if (isLocked) GameColors.RED else GameColors.PULSE
        c.drawText(titles[index], rect.left + scale * 0.05f, rect.top + scale * 0.08f, pText)

        pText.textSize = scale * 0.035f
        pText.color = if (isLocked) 0xFF880000.toInt() else GameColors.CLARITY
        c.drawText(subs[index], rect.left + scale * 0.05f, rect.bottom - scale * 0.04f, pText)
    }

    fun onTouch(x: Float, y: Float, action: Int, onAppSelect: (Int) -> Unit, onDisconnect: () -> Unit): Boolean {
        if (action == MotionEvent.ACTION_UP) {
            if (closeBtnRect.contains(x, y)) {
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                onDisconnect()
                return true
            }

            for (i in 0..3) {
                if (cardRects[i].contains(x, y)) {
                    if (i == 3 && !SaveManager.isHardModeUnlocked) {
                        // Terminal is Locked until Hard Mode is unlocked
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
                    } else {
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                        onAppSelect(i) // i: 0=Decompiler, 1=Arsenal, 2=Archives, 3=Terminal
                    }
                    return true
                }
            }
        }
        return true
    }
}