package com.appsbyalok.echohunter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.appsbyalok.echohunter.R
import com.appsbyalok.echohunter.data.LevelFeature
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.StoryProtocol
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.systems.EffectSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.utils.LevelIcons
import kotlin.math.abs
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

    // --- NAYA: MOMENTUM SCROLL VARIABLES ---
    private var scrollVelocity = 0f
    private var lastTouchTime = 0L

    private val backBtnRect = RectF()
    private val repairBtnRect = RectF()

    var repairFadeTimer = 0f

    private var isBooting = false
    private var bootTimer = 0f
    private val bootLogs = listOf(
        "> INITIATING OVERRIDE...",
        "> FLUSHING CORRUPTED CACHE...",
        "> BYPASSING ADMIN FIREWALL...",
        "> RECONSTRUCTING DATA NODES...",
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
        val bgColor = 0xEE05050A.toInt()
        c.drawColor(bgColor) // Dark Hacker Background

        // --- NAYA: MOMENTUM / FLING PHYSICS ---
        if (!isDragging && abs(scrollVelocity) > 0.5f) {
            scrollY += scrollVelocity
            scrollVelocity *= 0.96f

            if (scrollY > 0f) {
                scrollY = 0f
                scrollVelocity = 0f
            } else if (scrollY < -maxScroll) {
                scrollY = -maxScroll
                scrollVelocity = 0f
            }
        }

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
        if (StoryProtocol.isGlitchActive || isBooting || repairFadeTimer > 0f) {
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
                    // Small random X offset for a "glitchy" boot feel
                    val glitchOffsetX =
                        if (Math.random() > 0.8) (Math.random() * scale * 0.01f).toFloat() else 0f
                    c.drawText(bootLogs[i], targetW * 0.2f + glitchOffsetX, logY, pText)
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
        // Clip view so text doesn't overlap the header and footer
        c.clipRect(0f, targetH * 0.15f, targetW, targetH * 0.85f)

        var sy = targetH * 0.22f + scrollY
        val lh = scale * 0.045f
        val headerH = scale * 0.07f

        // Responsive margins
        val leftMargin = if (isPortrait) targetW * 0.08f else targetW * 0.25f
        val baseMaxTextW = targetW - (leftMargin * 2f)

        pText.textAlign = Paint.Align.LEFT

        fun drawLine(
            text: String,
            color: Int,
            isHeader: Boolean = false,
            featureIcon: LevelFeature? = null,
        ) {
            pText.color = color
            pText.textSize = if (isHeader) scale * 0.045f else scale * 0.032f

            var currentX = leftMargin
            var textMaxW = baseMaxTextW

            // If an icon is provided, draw it and indent the text
            if (featureIcon != null) {
                val iconSize = scale * 0.045f
                val iconRect = RectF(
                    leftMargin, sy - iconSize * 0.85f, leftMargin + iconSize, sy + iconSize * 0.15f
                )

                // Fetch the exact feature color
                p.color = when (featureIcon) {
                    LevelFeature.CLASSIC -> GameColors.PULSE
                    LevelFeature.MAZE -> GameColors.TEXT
                    LevelFeature.DEFENSE -> GameColors.SHIELD
                    LevelFeature.BOSS -> GameColors.BOSS
                    LevelFeature.ESCAPE -> GameColors.YELLOW
                    LevelFeature.ELIMINATION -> GameColors.RED
                    LevelFeature.SPECIAL -> GameColors.OVERCLOCK
                    LevelFeature.ADMIN_BONUS -> GameColors.HP
                    LevelFeature.BOMB -> 0xFFFF0000.toInt()
                    LevelFeature.DARKNESS -> 0xFF000000.toInt()
                }

                // Draw icon using utility (bgColor matches the terminal background)
                LevelIcons.drawMicroIcon(c, featureIcon, iconRect, p, bgColor)

                // Shift text to the right of the icon
                val indent = iconSize + scale * 0.03f
                currentX += indent
                textMaxW -= indent
            }

            val words = text.split(" ")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                val textWidth = pText.measureText(testLine)

                if (textWidth > textMaxW && currentLine.isNotEmpty()) {
                    c.drawText(currentLine, currentX, sy, pText)
                    sy += lh // Move down for wrap

                    // Maintain bullet indentation on new wrapped lines (if it's not a header)
                    val indentStr = if (text.startsWith("- ") && !isHeader) "  " else ""
                    currentLine = indentStr + word
                } else {
                    currentLine = testLine
                }
            }

            // Draw remainder
            if (currentLine.isNotEmpty()) {
                c.drawText(currentLine, currentX, sy, pText)
                sy += if (isHeader) headerH else lh
            }
        }

        // --- THE EXPANDED LORE AND HELP CONTENT ---

        drawLine("MISSION DIRECTIVE", GameColors.YELLOW, true)
        drawLine(
            "- You are remotely hijacking maintenance drone PROBE-7.", GameColors.TEXT
        ); sy += lh * 0.2f
        drawLine("- Extract Kilobytes (KB) without alerting the System Admin.", GameColors.TEXT)
        sy += lh * 0.5f

        drawLine("1. TACTICAL CONTROLS", GameColors.PULSE, true)
        drawLine("- LEFT JOYSTICK: Move drone.", GameColors.TEXT); sy += lh * 0.2f
        drawLine("- SONAR [Pulse]: Reveal map and enemy patrols.", GameColors.TEXT); sy += lh * 0.2f
        drawLine(
            "- ATK [Spike]: Fire Malware. Hits build Overclock.", GameColors.TEXT
        ); sy += lh * 0.2f
        drawLine("- OVR [Overclock]: Ultimate. Ram enemies to destroy them!", GameColors.TEXT)
        sy += lh * 0.5f

        // --- NAYA: THREAT IDENTIFICATION (ICONS LEGEND) ---
        drawLine("2. NODE IDENTIFICATION (LEGEND)", GameColors.SHIELD, true)
        drawLine(
            "CLASSIC: Standard kilobyte payload extraction.",
            GameColors.TEXT,
            featureIcon = LevelFeature.CLASSIC
        ); sy += lh * 0.2f
        drawLine(
            "MAZE: High-density firewall routing. Tight corners.",
            GameColors.TEXT,
            featureIcon = LevelFeature.MAZE
        ); sy += lh * 0.2f
        drawLine(
            "QUARANTINE: Defend the central core from override.",
            GameColors.TEXT,
            featureIcon = LevelFeature.DEFENSE
        ); sy += lh * 0.2f
        drawLine(
            "WARDEN: High-threat Admin entity encounter.",
            GameColors.TEXT,
            featureIcon = LevelFeature.BOSS
        ); sy += lh * 0.2f
        drawLine(
            "EXTRACTION: Secure target data, then locate exit portal.",
            GameColors.TEXT,
            featureIcon = LevelFeature.ESCAPE
        ); sy += lh * 0.2f
        drawLine(
            "TERMINATION: Hunt and destroy priority security targets.",
            GameColors.TEXT,
            featureIcon = LevelFeature.ELIMINATION
        ); sy += lh * 0.2f
        drawLine(
            "ANOMALY: Unstable memory sector. Unexpected behavior.",
            GameColors.TEXT,
            featureIcon = LevelFeature.SPECIAL
        ); sy += lh * 0.2f
        drawLine(
            "ROOT ACCESS: Admin stash discovered. Massive payload.",
            GameColors.TEXT,
            featureIcon = LevelFeature.ADMIN_BONUS
        )
        sy += lh * 0.5f

        drawLine("3. DECOMPILER (Upgrades)", GameColors.COOLANT, true)
        drawLine(
            "- Use stolen KB to patch your Firmware in the OS.", GameColors.TEXT
        ); sy += lh * 0.2f
        drawLine("- All upgrades persist across simulation runs.", GameColors.TEXT)
        sy += lh * 0.5f

        drawLine("4. BLACKOUT PROTOCOL (APT)", GameColors.OVERCLOCK, true)
        drawLine(
            "- Breach the Mainframe 3 times in a row to become an APT.", GameColors.TEXT
        ); sy += lh * 0.2f
        drawLine("- Unlocks HARD MODE: Extreme Admin hostility & fast fade.", GameColors.TEXT)
        sy += lh

        val isHardModeUnlocked = SaveManager.isHardModeUnlocked
        drawLine(
            "HARD MODE STATUS: ${if (isHardModeUnlocked) "[ UNLOCKED ]" else "[ LOCKED ]"}",
            if (isHardModeUnlocked) GameColors.RED else GameColors.HP
        )

        c.restore()

        val totalHeight = sy - (targetH * 0.22f + scrollY)
        val viewableArea = (targetH * 0.85f) - (targetH * 0.15f)
        maxScroll = max(0f, totalHeight - viewableArea + targetH * 0.05f)

        // Draw Fixed Back Button (Footer)


        // --- FOOTER BUTTON AUR SPACING CODE ---
        p.style = Paint.Style.STROKE
        p.color = (0x33 or (GameColors.RED and 0xFFFFFF)) // 20% alpha red
        p.strokeWidth = scale * 0.002f
        c.drawLine(0f, targetH * 0.84f, targetW, targetH * 0.84f, p)
        backBtnRect.set(
            targetW / 2f - btnW / 2f,
            targetH * 0.91f - btnH / 2f,
            targetW / 2f + btnW / 2f,
            targetH * 0.91f + btnH / 2f
        )
        p.style = Paint.Style.FILL
        p.color = 0xFF1A0000.toInt()
        c.drawRoundRect(backBtnRect, scale * 0.02f, scale * 0.02f, p)
        p.style = Paint.Style.STROKE
        p.color = GameColors.RED
        p.strokeWidth = scale * 0.005f
        c.drawRoundRect(backBtnRect, scale * 0.02f, scale * 0.02f, p)
        pText.textAlign = Paint.Align.CENTER
        pText.color = GameColors.RED
        pText.textSize = scale * 0.04f

        pText.setShadowLayer(10f, 0f, 0f, GameColors.RED)
        val textHeightOffset = (pText.descent() + pText.ascent()) / 2f
        c.drawText(
            "DISCONNECT", backBtnRect.centerX(), backBtnRect.centerY() - textHeightOffset, pText
        )
        pText.clearShadowLayer()
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
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = y
                lastTouchTime = System.currentTimeMillis()
                scrollVelocity = 0f
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = y - lastTouchY
                val currentTime = System.currentTimeMillis()
                val dt = currentTime - lastTouchTime

                if (abs(dy) > scale * 0.02f) isDragging = true

                // Calculate momentum
                if (dt > 0) {
                    val rawVelocity = (dy / dt.toFloat()) * 16f
                    scrollVelocity = (scrollVelocity * 0.3f) + (rawVelocity * 0.7f)
                }

                scrollY += dy
                if (scrollY > 0f) scrollY = 0f
                if (scrollY < -maxScroll) scrollY = -maxScroll

                lastTouchY = y
                lastTouchTime = currentTime
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging && gs.stateTimer > 0.2f) {
                    if (StoryProtocol.isGlitchActive && repairFadeTimer <= 0f && !isBooting) {
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
                isDragging = false
            }
        }
        return true
    }
}