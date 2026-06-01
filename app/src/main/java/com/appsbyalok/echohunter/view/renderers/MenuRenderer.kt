package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.util.SparseArray
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MenuRenderer(private val context: Context) {
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    // Dedicated paint for UI buttons to avoid memory churn in onDraw
    private val pBtn = Paint().apply { isAntiAlias = true }

    // --- PUBLIC HITBOXES FOR TOUCH HANDLING ---
    val pauseResumeRect = RectF()
    val pauseAutoRect = RectF()
    val pauseDiscRect = RectF()
    val pauseModRect = RectF()
    val victoryNextRect = RectF()

    private val stringCache = SparseArray<String>()

    private fun getCachedString(resId: Int): String {
        var str = stringCache.get(resId)
        if (str == null) {
            str = context.getString(resId)
            stringCache.put(resId, str)
        }
        return str
    }

    // --- REUSABLE RESPONSIVE BUTTON DRAWING ---
    private fun drawButton(c: Canvas, rect: RectF, text: String, color: Int, scale: Float) {
        // Button Background
        pBtn.style = Paint.Style.FILL
        pBtn.color = 0xAA050505.toInt() // Dark solid background for contrast
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, pBtn)

        // Neon Border
        pBtn.style = Paint.Style.STROKE
        pBtn.strokeWidth = scale * 0.005f
        pBtn.color = color
        c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, pBtn)

        // Centered Text
        pText.color = color
        pText.textSize = scale * 0.045f
        pText.textAlign = Paint.Align.CENTER
        // Vertically center text perfectly inside the rect
        val textY = rect.centerY() - (pText.descent() + pText.ascent()) / 2f
        c.drawText(text, rect.centerX(), textY, pText)
    }

    fun drawPause(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        // Semi-transparent dark overlay
        val pBg = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = 0xEE000000.toInt() }
        c.drawRect(0f, 0f, targetW, targetH, pBg)

        // PAUSED Title
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.12f
        pText.color = GameColors.YELLOW
        pText.setShadowLayer(15f, 0f, 0f, GameColors.YELLOW)
        c.drawText(getCachedString(R.string.pause_title), targetW / 2f, targetH * 0.25f, pText)
        pText.clearShadowLayer()

        // --- RESPONSIVE LAYOUT MATH ---
        // Prevents buttons from getting too wide on landscape, or too squished on portrait
        val btnW = min(targetW * 0.7f, scale * 0.8f)
        val btnH = scale * 0.13f
        val gap = scale * 0.04f
        val startX = targetW / 2f - btnW / 2f
        var startY = targetH * 0.35f

        // 1. RESUME BUTTON
        pauseResumeRect.set(startX, startY, startX + btnW, startY + btnH)
        if (gs.isRotationWarning) {
            drawButton(c, pauseResumeRect, ">> ROTATE DEVICE BACK <<", GameColors.RED, scale)
        } else {
            drawButton(c, pauseResumeRect, "> ${getCachedString(R.string.pause_resume)} <", GameColors.CLARITY, scale)
        }
        startY += btnH + gap

        // 2. AUTOPILOT BUTTON
        pauseAutoRect.set(startX, startY, startX + btnW, startY + btnH)
        val autoText = "> AUTOPILOT: ${if (gs.isAutoPilotActive) "ON" else "OFF"} <"
        val autoColor = if (gs.isAutoPilotActive) GameColors.HP else GameColors.CLARITY
        drawButton(c, pauseAutoRect, autoText, autoColor, scale)
        startY += btnH + gap

        // 3. DISCONNECT BUTTON
        pauseDiscRect.set(startX, startY, startX + btnW, startY + btnH)
        drawButton(c, pauseDiscRect, "> ${getCachedString(R.string.pause_menu)} <", GameColors.RED, scale)
        startY += btnH + gap

        // 4. MOD MENU BUTTON
        pauseModRect.set(startX, startY, startX + btnW, startY + btnH)
        drawButton(c, pauseModRect, "> MOD MENU <", GameColors.PULSE, scale)
    }

    fun drawLevelVictory(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        c.drawColor(0xEE051105.toInt())

        var titleSize = scale * 0.1f
        pText.textSize = titleSize
        val titleStr = "NODE SECURED"
        while (pText.measureText(titleStr) > targetW * 0.9f) {
            titleSize *= 0.95f
            pText.textSize = titleSize
        }

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.HP
        pText.setShadowLayer(20f, 0f, 0f, GameColors.HP)
        c.drawText(titleStr, targetW / 2f, targetH * 0.35f, pText)
        pText.clearShadowLayer()

        var subSize = scale * 0.05f
        pText.textSize = subSize
        val subStr = "DATA EXTRACTED: +${SaveManager.formatDataString(gs.collectedDataKB)}"
        while (pText.measureText(subStr) > targetW * 0.95f) {
            subSize *= 0.95f
            pText.textSize = subSize
        }

        pText.color = GameColors.CLARITY
        c.drawText(subStr, targetW / 2f, targetH * 0.5f, pText)

        // --- RESPONSIVE VICTORY BUTTON ---
        val btnW = min(targetW * 0.6f, scale * 0.7f)
        val btnH = scale * 0.14f
        val btnX = targetW / 2f - btnW / 2f
        val btnY = targetH * 0.65f

        victoryNextRect.set(btnX, btnY, btnX + btnW, btnY + btnH)
        val btnText = if (SaveManager.isAutoNextLevelEnabled) "RETURN TO MENU" else "NEXT LEVEL"
        drawButton(c, victoryNextRect, btnText, GameColors.HP, scale)
    }

    fun drawStory(c: Canvas, lines: IntArray, scale: Float, gs: GameState, targetW: Float, targetH: Float, currentStoryStep: Int): Int {
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = scale * 0.04f
        pText.color = when (gs.state) {
            4 -> GameColors.RED
            6 -> if (lines.contentEquals(StoryProtocol.storyPerfectEnding)) GameColors.YELLOW else GameColors.HP
            else -> GameColors.PULSE
        }

        val isPortrait = targetW < targetH
        val leftMargin = if (isPortrait) targetW * 0.05f else targetW * 0.1f
        val maxTextW = targetW - (leftMargin * 2f)
        val lh = scale * 0.05f

        var y = targetH * 0.25f
        val typeSpeed = 35f
        val pauseBetweenLines = 15

        var charsAllowed = (gs.stateTimer * typeSpeed).toInt()
        if (currentStoryStep >= lines.size) charsAllowed = Int.MAX_VALUE

        var linesFullyTyped = 0

        for (i in lines.indices) {
            if (charsAllowed <= 0) break

            val fullText = getCachedString(lines[i])
            val charsForThisLine = min(fullText.length, charsAllowed)
            val isCurrentlyTyping = charsForThisLine < fullText.length

            val cursor = if (isCurrentlyTyping && (gs.timeSinceStart * 15).toInt() % 2 == 0) "_" else ""
            val textToDraw = fullText.substring(0, charsForThisLine) + cursor

            if (isCurrentlyTyping && charsAllowed % 3 == 0) {
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 20)
            }

            val words = textToDraw.split(" ")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val textWidth = pText.measureText(testLine)

                if (textWidth > maxTextW && currentLine.isNotEmpty()) {
                    c.drawText(currentLine, leftMargin, y, pText)
                    y += lh
                    currentLine = word
                } else {
                    currentLine = testLine
                }
            }

            if (currentLine.isNotEmpty()) {
                c.drawText(currentLine, leftMargin, y, pText)
                y += scale * 0.08f
            }

            charsAllowed -= (fullText.length + pauseBetweenLines)
            if (!isCurrentlyTyping) linesFullyTyped++
        }

        if (linesFullyTyped >= lines.size) {
            val alpha = ((sin(gs.timeSinceStart * 5.0) + 1) / 2 * 155 + 100).toInt()
            pText.color = (alpha shl 24) or 0xFFFFFF
            pText.textAlign = Paint.Align.CENTER
            c.drawText(getCachedString(R.string.ui_tap_continue), targetW / 2f, targetH * 0.9f, pText)
            return lines.size
        }

        return max(currentStoryStep, linesFullyTyped)
    }
}