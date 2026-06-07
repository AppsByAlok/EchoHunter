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
    val pauseRestartRect = RectF()
    val victoryNextRect = RectF()
    val victoryHomeRect = RectF()

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
        pText.isFakeBoldText = true // Ensure readability in all resolutions
        // Vertically center text perfectly inside the rect
        val textY = rect.centerY() - (pText.descent() + pText.ascent()) / 2f
        c.drawText(text, rect.centerX(), textY, pText)
        pText.isFakeBoldText = false
    }



    private val pauseBgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = 0xF50A0C10.toInt() }
    private val hudLinePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f }
    private val iconBgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = 0x1AFFFFFF }
    fun drawPause(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float): Float {
        c.drawRect(0f, 0f, targetW, targetH, pauseBgPaint)

        // --- TOP RIGHT FAST-CLOSE (ESC) INDICATOR ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.045f
        pText.color = GameColors.TEXT
        c.drawText("[ ESC ]", targetW - scale * 0.12f, scale * 0.08f, pText)

        val isPortrait = targetH > targetW

        // --- LAYOUT DIMENSIONS & MATH ---
        val btnH = scale * 0.12f // Slightly optimized height for better fit
        val btnGap = scale * 0.035f
        val totalBtnHeight = (5 * btnH) + (4 * btnGap)
        val btnW = if (isPortrait) targetW * 0.7f else targetW * 0.35f

        val titleSize = scale * 0.1f
        val nodeSize = scale * 0.045f
        val iconSize = scale * 0.09f
        val textGap = scale * 0.06f

        val headerHeight = titleSize + textGap + nodeSize + textGap + iconSize

        // Dynamic Anchors
        val headerStartY: Float
        var btnStartY: Float
        val headerCenterX: Float
        val btnStartX: Float

        if (isPortrait) {
            // Portrait: Vertical Stack centering
            val midGap = scale * 0.1f
            val totalContentH = headerHeight + midGap + totalBtnHeight

            headerStartY = (targetH - totalContentH) / 2f + titleSize * 0.8f // 0.8f adjusting for baseline
            btnStartY = headerStartY + (headerHeight - titleSize * 0.8f) + midGap

            headerCenterX = targetW / 2f
            btnStartX = targetW / 2f - btnW / 2f

            // NAYA: Subtle horizontal divider
            val lineY = btnStartY - midGap / 2f
            c.drawLine(targetW * 0.15f, lineY, targetW * 0.85f, lineY, hudLinePaint)
        } else {
            // Landscape: Split-Screen Perfect Centering
            headerStartY = (targetH - headerHeight) / 2f + titleSize * 0.8f
            btnStartY = (targetH - totalBtnHeight) / 2f

            headerCenterX = targetW * 0.28f // Left block centered at 28%
            btnStartX = targetW * 0.72f - btnW / 2f // Right block centered at 72%

            // NAYA: Subtle vertical divider separating Intel and Actions
            c.drawLine(targetW / 2f, targetH * 0.2f, targetW / 2f, targetH * 0.8f, hudLinePaint)
        }

        // ========================================================
        // 1. LEFT COLUMN (OR TOP IN PORTRAIT): INTEL & ICONS
        // ========================================================

        pText.textAlign = Paint.Align.CENTER
        pText.textSize = titleSize
        pText.color = GameColors.YELLOW
        pText.setShadowLayer(15f, 0f, 0f, GameColors.YELLOW)
        c.drawText(getCachedString(R.string.pause_title), headerCenterX, headerStartY, pText)
        pText.clearShadowLayer()

        pText.textSize = nodeSize
        pText.color = GameColors.CLARITY
        c.drawText("CURRENT NODE : ${gs.currentLevel}", headerCenterX, headerStartY + textGap, pText)

        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        val iconGap = scale * 0.04f
        val totalIconsW = (config.features.size * iconSize) + ((config.features.size - 1) * iconGap)

        var currentIconX = headerCenterX - (totalIconsW / 2f)
        val iconY = headerStartY + textGap + nodeSize

        val pIcon = Paint().apply { isAntiAlias = true }
        for (f in config.features) {
            val iconRect = RectF(currentIconX, iconY, currentIconX + iconSize, iconY + iconSize)

            // NAYA: Icon ka apna background (glass/hud effect)
            c.drawRoundRect(iconRect, scale * 0.02f, scale * 0.02f, iconBgPaint)

            pIcon.color = when (f) {
                com.appsbyalok.echohunter.data.LevelFeature.CLASSIC -> GameColors.PULSE
                com.appsbyalok.echohunter.data.LevelFeature.MAZE -> GameColors.TEXT
                com.appsbyalok.echohunter.data.LevelFeature.DEFENSE -> GameColors.SHIELD
                com.appsbyalok.echohunter.data.LevelFeature.BOSS -> GameColors.BOSS
                com.appsbyalok.echohunter.data.LevelFeature.ESCAPE -> GameColors.YELLOW
                com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION -> GameColors.RED
                com.appsbyalok.echohunter.data.LevelFeature.SPECIAL -> GameColors.OVERCLOCK
                com.appsbyalok.echohunter.data.LevelFeature.ADMIN_BONUS -> GameColors.HP
            }
            // Passing pseudo-dark color for cutout blending (to match pause bg)
            com.appsbyalok.echohunter.utils.LevelIcons.drawMicroIcon(c, f, iconRect, pIcon, 0xFF1A1C20.toInt())
            currentIconX += iconSize + iconGap
        }

        // ========================================================
        // 2. RIGHT COLUMN (OR BOTTOM IN PORTRAIT): BUTTONS
        // ========================================================

        // 1. RESUME BUTTON
        pauseResumeRect.set(btnStartX, btnStartY, btnStartX + btnW, btnStartY + btnH)
        if (gs.isRotationWarning) {
            drawButton(c, pauseResumeRect, ">> ROTATE DEVICE BACK <<", GameColors.RED, scale)
        } else {
            drawButton(c, pauseResumeRect, "> ${getCachedString(R.string.pause_resume)} <", GameColors.CLARITY, scale)
        }
        btnStartY += btnH + btnGap

        // 2. RESTART BUTTON
        pauseRestartRect.set(btnStartX, btnStartY, btnStartX + btnW, btnStartY + btnH)
        drawButton(c, pauseRestartRect, "> RESTART LEVEL <", GameColors.YELLOW, scale)
        btnStartY += btnH + btnGap

        // 3. AUTOPILOT BUTTON
        pauseAutoRect.set(btnStartX, btnStartY, btnStartX + btnW, btnStartY + btnH)
        val autoText = "> AUTOPILOT: ${if (gs.isAutoPilotActive) "ON" else "OFF"} <"
        val autoColor = if (gs.isAutoPilotActive) GameColors.HP else GameColors.CLARITY
        drawButton(c, pauseAutoRect, autoText, autoColor, scale)
        btnStartY += btnH + btnGap

        // 4. MOD MENU BUTTON
        pauseModRect.set(btnStartX, btnStartY, btnStartX + btnW, btnStartY + btnH)
        drawButton(c, pauseModRect, "> MOD MENU <", GameColors.PULSE, scale)
        btnStartY += btnH + btnGap

        // 5. DISCONNECT BUTTON
        pauseDiscRect.set(btnStartX, btnStartY, btnStartX + btnW, btnStartY + btnH)
        drawButton(c, pauseDiscRect, "> ${getCachedString(R.string.pause_menu)} <", GameColors.RED, scale)

        // Return 0f explicitly kyuki ab hume scrolling limits nahi chahiye.
        // GameView ka drag physics safely isko ignore kar dega.
        return 0f
    }



    fun drawLevelVictory(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float) {
        c.drawColor(0xEE051105.toInt())

        val isPortrait = targetH > targetW

        var titleSize = if (isPortrait) scale * 0.09f else scale * 0.08f
        pText.textSize = titleSize
        val titleStr = "NODE SECURED"
        while (pText.measureText(titleStr) > targetW * 0.9f) {
            titleSize *= 0.95f
            pText.textSize = titleSize
        }

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.HP
        pText.setShadowLayer(20f, 0f, 0f, GameColors.HP)
        c.drawText(titleStr, targetW / 2f, targetH * 0.3f, pText)
        pText.clearShadowLayer()

        var subSize = if (isPortrait) scale * 0.045f else scale * 0.04f
        pText.textSize = subSize
        val subStr = "DATA EXTRACTED: +${SaveManager.formatDataString(gs.collectedDataKB)}"
        while (pText.measureText(subStr) > targetW * 0.95f) {
            subSize *= 0.95f
            pText.textSize = subSize
        }

        pText.color = GameColors.CLARITY
        c.drawText(subStr, targetW / 2f, targetH * 0.45f, pText)

        // --- RESPONSIVE VICTORY BUTTONS ---
        val btnW = if (isPortrait) targetW * 0.75f else targetW * 0.4f
        val btnH = scale * 0.12f
        val gap = scale * 0.04f
        
        val btnX = targetW / 2f - btnW / 2f
        var btnY = targetH * 0.58f

        // 1. NEXT LEVEL BUTTON
        victoryNextRect.set(btnX, btnY, btnX + btnW, btnY + btnH)
        val btnTextNext = if (SaveManager.isAutoNextLevelEnabled) "AUTO-PROCEEDING..." else "NEXT LEVEL"
        drawButton(c, victoryNextRect, btnTextNext, GameColors.HP, scale)

        btnY += btnH + gap

        // 2. HOME / MAIN MENU BUTTON
        victoryHomeRect.set(btnX, btnY, btnX + btnW, btnY + btnH)
        drawButton(c, victoryHomeRect, "RETURN TO MENU", GameColors.RED, scale)
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