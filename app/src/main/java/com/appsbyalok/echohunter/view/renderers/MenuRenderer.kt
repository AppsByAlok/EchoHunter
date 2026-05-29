package com.appsbyalok.echohunter.view.renderers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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

    private val stringCache = SparseArray<String>()

    private fun getCachedString(resId: Int): String {
        var str = stringCache.get(resId)
        if (str == null) {
            str = context.getString(resId)
            stringCache.put(resId, str)
        }
        return str
    }

    fun drawPause(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        // Semi-transparent dark background
        val pBg = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = 0xDD000000.toInt() }
        c.drawRect(0f, 0f, targetW, targetH, pBg)

        pText.textAlign = Paint.Align.CENTER

        // PAUSED Title
        pText.textSize = scale * 0.1f
        pText.color = GameColors.YELLOW
        pText.setShadowLayer(10f, 0f, 0f, GameColors.YELLOW)
        c.drawText(getCachedString(R.string.pause_title), targetW / 2f, targetH * 0.35f, pText)
        pText.clearShadowLayer()

        // --- DYNAMIC RESUME TEXT ---
        pText.textSize = scale * 0.05f
        if (gs.isRotationWarning) {
            // RED warning text telling them exactly what to do
            pText.color = GameColors.RED
            c.drawText(">> ROTATE DEVICE BACK TO RESUME <<", targetW / 2f, targetH * 0.55f, pText)
        } else {
            // Normal white resume text
            pText.color = GameColors.CLARITY
            c.drawText("> ${getCachedString(R.string.pause_resume)} <", targetW / 2f, targetH * 0.55f, pText)
        }

        // DISCONNECT option
        pText.color = GameColors.RED
        c.drawText("> ${getCachedString(R.string.pause_menu)} <", targetW / 2f, targetH * 0.75f, pText)


        // NAYA: MOD MENU OPTION
        pText.color = GameColors.PULSE
        c.drawText("> MOD MENU <", targetW / 2f, targetH * 0.88f, pText)
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

        val alpha = ((sin(gs.timeSinceStart * 5.0) + 1) / 2 * 155 + 100).toInt()
        pText.color = (alpha shl 24) or 0xFFFFFF
        pText.textSize = scale * 0.04f
        c.drawText("TAP TO CONTINUE", targetW / 2f, targetH * 0.8f, pText)
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
        val lh = scale * 0.05f // Line height for wrapped lines

        var y = targetH * 0.25f // Start slightly higher

        // --- NAYA CONTINUOUS TYPEWRITER MATH ---
        val typeSpeed = 35f // Typing speed (Characters per second)
        val pauseBetweenLines = 15 // Invisible characters for a natural pause between lines

        // Using stateTimer so it starts from 0 exactly when the story screen opens
        var charsAllowed = (gs.stateTimer * typeSpeed).toInt()

        // If player tapped to skip, show all characters instantly
        if (currentStoryStep >= lines.size) {
            charsAllowed = Int.MAX_VALUE
        }

        var linesFullyTyped = 0

        for (i in lines.indices) {
            if (charsAllowed <= 0) break // Stop rendering if we run out of allowed characters

            val fullText = getCachedString(lines[i])
            val charsForThisLine = min(fullText.length, charsAllowed)
            val isCurrentlyTyping = charsForThisLine < fullText.length

            // Blinking cursor
            val cursor = if (isCurrentlyTyping && (gs.timeSinceStart * 15).toInt() % 2 == 0) "_" else ""
            val textToDraw = fullText.substring(0, charsForThisLine) + cursor

            // Play Terminal tick sound while typing (every few characters)
            if (isCurrentlyTyping && charsAllowed % 3 == 0) {
                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 20)
            }

            // AUTO-WRAPPING LOGIC
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

            // Draw remainder of the paragraph
            if (currentLine.isNotEmpty()) {
                c.drawText(currentLine, leftMargin, y, pText)
                y += scale * 0.08f // Extra spacing between separate paragraphs
            }

            // Deduct chars used by this line, plus add a pause before the next line starts
            charsAllowed -= (fullText.length + pauseBetweenLines)

            if (!isCurrentlyTyping) {
                linesFullyTyped++
            }
        }

        // Tap to continue prompt
        if (linesFullyTyped >= lines.size) {
            val alpha = ((sin(gs.timeSinceStart * 5.0) + 1) / 2 * 155 + 100).toInt()
            pText.color = (alpha shl 24) or 0xFFFFFF
            pText.textAlign = Paint.Align.CENTER
            c.drawText(getCachedString(R.string.ui_tap_continue), targetW / 2f, targetH * 0.9f, pText)

            return lines.size // Tell GameView the animation is fully complete
        }

        return max(currentStoryStep, linesFullyTyped)
    }
}