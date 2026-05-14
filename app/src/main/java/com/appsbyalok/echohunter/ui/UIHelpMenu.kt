package com.appsbyalok.echohunter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.View
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.systems.EffectSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class UIHelpMenu(private val context: Context) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private var scrollY = 0f
    private var maxScroll = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val backBtnRect = RectF()
    private val repairBtnRect = RectF()

    var repairFadeTimer = 0f

    private var isBooting = false
    private var bootTimer = 0f
    private val bootLogs = listOf(
        "> INITIATING OVERRIDE...",
        "> FLUSHING CORRUPTED CACHE...",
        "> BYPASSING ADMIN FIREWALL...",
        "> SECURING UPLINK PORT..."
    )

    private val stringCache = SparseArray<String>()
    private fun getCachedString(resId: Int): String {
        var str = stringCache.get(resId)
        if (str == null) {
            str = context.getString(resId)
            stringCache.put(resId, str)
        }
        return str
    }

    fun update(dt: Float) {
        if (repairFadeTimer > 0f) repairFadeTimer -= dt
        if (isBooting) {
            bootTimer += dt
            // After 2.5 seconds boot
            if (bootTimer > 2.5f) {
                isBooting = false
                bootTimer = 0f
                repairFadeTimer = 2.0f
                StoryProtocol.isGlitchActive = false
                StoryProtocol.popupTimer = 0f
            }
        }
    }

    fun draw(c: Canvas, scale: Float, targetW: Float, targetH: Float, gs: GameState) {
        c.drawColor(0xEE05050A.toInt()) // Dark Background

        val isPortrait = targetW < targetH
        val btnW = if (isPortrait) scale * 0.45f else scale * 0.3f
        val btnH = scale * 0.1f
        backBtnRect.set(
            targetW / 2f - btnW / 2f,
            targetH * 0.88f - btnH / 2f,
            targetW / 2f + btnW / 2f,
            targetH * 0.88f + btnH / 2f
        )

        // --- Glitch Hijack Check (Easter Egg Repair UI) ---
        if (StoryProtocol.isGlitchActive || isBooting) {
            var titleSize = scale * 0.08f
            pText.textSize = titleSize
            val glitchTitle = "UPLINK CORRUPTED"
            while (pText.measureText(glitchTitle) > targetW * 0.9f) {
                titleSize *= 0.95f
                pText.textSize = titleSize
            }

            pText.textAlign = Paint.Align.CENTER
            pText.color = GameColors.RED
            pText.setShadowLayer(20f, 0f, 0f, GameColors.RED)
            c.drawText(glitchTitle, targetW / 2f, targetH * 0.3f, pText)
            pText.clearShadowLayer()

            val ry = targetH * 0.6f

            if (isBooting) {
                // --- THE TERMINAL BOOT ANIMATION ---
                pText.textAlign = Paint.Align.LEFT
                pText.textSize = scale * 0.035f
                pText.color = GameColors.HP

                var logY = ry - scale * 0.1f
                val progress = bootTimer / 2.5f
                val linesToShow = (progress * (bootLogs.size + 1)).toInt()

                for (i in 0 until min(linesToShow, bootLogs.size)) {
                    c.drawText(bootLogs[i], targetW * 0.2f, logY, pText)
                    logY += scale * 0.05f
                }

                // Blinking cursor
                if ((bootTimer * 10).toInt() % 2 == 0) {
                    c.drawText("_", targetW * 0.2f, logY, pText)
                }
            } else if (repairFadeTimer > 0f) {
                val fadeAlpha = max(0, min(255, (repairFadeTimer * 127).toInt()))

                var fadeTextSize = scale * 0.08f
                pText.textSize = fadeTextSize
                val repairedStr = getCachedString(R.string.ui_system_repaired)
                while (pText.measureText(repairedStr) > targetW * 0.9f) {
                    fadeTextSize *= 0.95f
                    pText.textSize = fadeTextSize
                }

                pText.color = (fadeAlpha shl 24) or (GameColors.CLARITY and 0xFFFFFF)
                pText.setShadowLayer(15f, 0f, 0f, GameColors.CLARITY)
                c.drawText(repairedStr, targetW / 2f, ry, pText)
                pText.clearShadowLayer()
            } else {
                val repairBtnW = if (isPortrait) scale * 0.6f else scale * 0.3f
                repairBtnRect.set(
                    targetW / 2f - repairBtnW / 2f,
                    ry - btnH / 2f,
                    targetW / 2f + repairBtnW / 2f,
                    ry + btnH / 2f
                )

                val pulse = (sin(gs.timeSinceStart * 3f) * 20 + 40).toInt()
                p.style = Paint.Style.FILL
                p.color = (pulse shl 24) or (GameColors.HP and 0xFFFFFF)
                c.drawRoundRect(repairBtnRect, scale * 0.01f, scale * 0.01f, p)

                p.style = Paint.Style.STROKE
                p.strokeWidth = scale * 0.005f
                p.color = GameColors.HP
                c.drawRoundRect(repairBtnRect, scale * 0.01f, scale * 0.01f, p)

                var btnTextSize = scale * 0.045f
                pText.textSize = btnTextSize
                val btnStr = "RESTORE CONNECTION"
                while (pText.measureText(btnStr) > repairBtnW * 0.9f) {
                    btnTextSize *= 0.95f
                    pText.textSize = btnTextSize
                }

                pText.color = GameColors.HP
                pText.setShadowLayer(15f, 0f, 0f, GameColors.HP)
                // Center text vertically inside button
                c.drawText(btnStr, targetW / 2f, ry + btnTextSize * 0.35f, pText)
                pText.clearShadowLayer()
            }
            return
        }


        // --- NORMAL HELP UI (Scrollable & Responsive) ---
        var titleSize = scale * 0.08f
        pText.textSize = titleSize
        val headerTitle = "UPLINK TERMINAL : DATALOG"
        while (pText.measureText(headerTitle) > targetW * 0.95f) {
            titleSize *= 0.95f
            pText.textSize = titleSize
        }

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.PULSE
        pText.setShadowLayer(20f, 0f, 0f, GameColors.PULSE)
        c.drawText(headerTitle, targetW / 2f, targetH * 0.12f, pText)
        pText.clearShadowLayer()

        c.save()
        c.clipRect(0f, targetH * 0.18f, targetW, targetH * 0.78f)

        var sy = targetH * 0.25f + scrollY
        val lh = scale * 0.045f
        val headerH = scale * 0.07f

        // Responsive margins
        val leftMargin = if (isPortrait) targetW * 0.08f else targetW * 0.25f
        val maxTextW = targetW - (leftMargin * 2f) // Keeps text centered-ish

        pText.textAlign = Paint.Align.LEFT

        // Auto-wrapping logic for any screen size
        fun drawLine(text: String, color: Int, isHeader: Boolean = false) {
            pText.color = color
            pText.textSize = if (isHeader) scale * 0.04f else scale * 0.03f

            val words = text.split(" ")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val textWidth = pText.measureText(testLine)

                if (textWidth > maxTextW && currentLine.isNotEmpty()) {
                    c.drawText(currentLine, leftMargin, sy, pText)
                    sy += lh // Body wraps just move down by standard line height

                    // Maintain bullet indentation on new wrapped lines
                    val indent = if (text.startsWith("- ") && !isHeader) "  " else ""
                    currentLine = indent + word
                } else {
                    currentLine = testLine
                }
            }

            // Draw remainder of the line
            if (currentLine.isNotEmpty()) {
                c.drawText(currentLine, leftMargin, sy, pText)
                sy += if (isHeader) headerH else lh
            }
        }

        drawLine("MISSION DIRECTIVE", GameColors.YELLOW, true)
        drawLine("- You are remotely hijacking maintenance drone PROBE-7.", GameColors.TEXT)
        drawLine("- Extract Corrupted Data (KB) without alerting the Admin.", GameColors.TEXT)
        sy += lh * 0.5f

        drawLine("1. THE SANDBOX (Rings 1-14)", GameColors.PULSE, true)
        drawLine(
            "- A quarantined test zone. Reach Ring 15 to breach the Mainframe.", GameColors.TEXT
        )
        sy += lh * 0.5f

        drawLine("2. TACTICAL CONTROLS", GameColors.SHIELD, true)
        drawLine("- LEFT JOYSTICK: Move drone.", GameColors.TEXT)
        drawLine("- SONAR [Pulse]: Reveal map and enemy patrols.", GameColors.TEXT)
        drawLine("- ATK [Spike]: Fire Malware. Hits build Overclock.", GameColors.TEXT)
        drawLine("- OVR [Overclock]: Ultimate. Ram enemies to destroy them!", GameColors.TEXT)
        sy += lh * 0.5f

        drawLine("3. WARDENS & SECURITY", GameColors.RED, true)
        drawLine("- Multiples of 5: Warden Encounter (Boss).", GameColors.TEXT)
        drawLine("- Avoid direct contact unless Overclock is active.", GameColors.TEXT)
        sy += lh * 0.5f

        drawLine("4. DECOMPILER (Upgrades)", GameColors.COOLANT, true)
        drawLine("- Use stolen Data to patch your Firmware.", GameColors.TEXT)
        drawLine("- Upgrades persist across all runs.", GameColors.TEXT)
        sy += lh * 0.5f

        drawLine("5. BLACKOUT PROTOCOL (APT)", GameColors.OVERCLOCK, true)
        drawLine("- Breach the Mainframe 4 times in a row to become an APT.", GameColors.TEXT)
        drawLine("- Unlocks HARD MODE: Extreme Admin hostility & fast fade.", GameColors.TEXT)
        sy += lh

        val isHardModeUnlocked = SaveManager.isHardModeUnlocked
        drawLine(
            "HARD MODE: ${if (isHardModeUnlocked) "UNLOCKED" else "LOCKED"}",
            if (isHardModeUnlocked) GameColors.RED else GameColors.HP
        )

        c.restore()

        val totalHeight = sy - (targetH * 0.25f + scrollY)
        val viewableArea = (targetH * 0.78f) - (targetH * 0.25f)
        maxScroll = max(0f, totalHeight - viewableArea + targetH * 0.05f)

        // Draw Fixed Back Button
        p.style = Paint.Style.FILL
        p.color = 0xFF330000.toInt()
        c.drawRoundRect(backBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE
        p.color = GameColors.RED
        p.strokeWidth = scale * 0.005f
        c.drawRoundRect(backBtnRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.RED
        pText.textSize = scale * 0.04f
        c.drawText(
            "DISCONNECT", backBtnRect.centerX(), backBtnRect.centerY() + scale * 0.012f, pText
        )
    }

    fun onTouch(
        x: Float,
        y: Float,
        action: Int,
        scale: Float,
        gs: GameState,
        view: View,
        effectSys: EffectSystem,
        onClose: () -> Unit,
    ): Boolean {
        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                lastTouchY = y
                isDragging = false
            }

            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = y - lastTouchY
                if (kotlin.math.abs(dy) > scale * 0.02f) isDragging = true

                scrollY += dy
                if (scrollY > 0f) scrollY = 0f
                if (scrollY < -maxScroll) scrollY = -maxScroll

                lastTouchY = y
            }

            android.view.MotionEvent.ACTION_UP -> {
                if (!isDragging && gs.stateTimer > 0.2f) {
                    if (StoryProtocol.isGlitchActive && repairFadeTimer <= 0f) {
                        if (repairBtnRect.contains(x, y)) {
                            isBooting = true
                            bootTimer = 0f
                            gs.chromaticIntensity = 0f
                            gs.shakeAmount = 0f
                            gs.damageFlash = 0f
                            effectSys.reset()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 100)
                            effectSys.spawnParticles(
                                repairBtnRect.centerX(), repairBtnRect.centerY(), 15, scale
                            )
                        }
                    } else if (backBtnRect.contains(x, y) || repairFadeTimer > 0f) {
                        onClose()
                    }
                }
            }
        }
        return true
    }
}