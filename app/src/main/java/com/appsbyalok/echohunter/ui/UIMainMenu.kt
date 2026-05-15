package com.appsbyalok.echohunter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.ToneGenerator
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
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
import kotlin.random.Random

class UIMainMenu(private val context: Context) {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

//    private val stringCache = SparseArray<String>()

    private val stringCache = SparseArray<String>()
    private val cablePath = Path()

    // --- NAYA: 3 Ports System ---
    private val menuTitles = arrayOf("SANDBOX", "MAINFRAME", "NANO-OS")
    private val menuSubs = arrayOf("Simulation Ring", "Core Access", "System Dashboard")

    private var plugX = 0f; private var plugY = 0f
    private var targetPlugX = 0f; private var targetPlugY = 0f
    private var plugRestX = 0f; private var plugRestY = 0f
    private var isDraggingPlug = false
    private var connectedMode = -1
    private var animatingToPort = -1
    private var isSwitchOn = false

    // NAYA: Array size 4 kar diya
    private val portX = FloatArray(3)
    private val portY = FloatArray(3)
    private var touchDownX = 0f; private var touchDownY = 0f
    private var wasSwitchHitOnDown = false

    private fun getCachedString(resId: Int): String {
        var str = stringCache.get(resId)
        if (str == null) { str = context.getString(resId); stringCache.put(resId, str) }
        return str
    }

    fun initLayout(targetW: Float, targetH: Float) {
        val isPortrait = targetW < targetH
        plugRestX = targetW * 0.85f
        plugRestY = if (isPortrait) targetH * 0.90f else targetH * 0.85f
        plugX = plugRestX; plugY = plugRestY
        targetPlugX = plugRestX; targetPlugY = plugRestY

        // --- 3-PORT CLEAN LAYOUT ---
        if (isPortrait) {
            // Triangle Layout
            portX[0] = targetW * 0.28f; portY[0] = targetH * 0.58f // Sandbox (Top Left)
            portX[1] = targetW * 0.72f; portY[1] = targetH * 0.58f // Mainframe (Top Right)
            portX[2] = targetW * 0.50f; portY[2] = targetH * 0.74f // Nano-OS (Bottom Center)
        } else {
            // 3 in a row for Landscape
            portX[0] = targetW * 0.25f; portY[0] = targetH * 0.65f
            portX[1] = targetW * 0.50f; portY[1] = targetH * 0.65f
            portX[2] = targetW * 0.75f; portY[2] = targetH * 0.65f
        }
    }

    fun turnOffSwitch() {
        isSwitchOn = false
    }

    fun disconnect() {
        connectedMode = -1
        animatingToPort = -1
        targetPlugX = plugRestX
        targetPlugY = plugRestY
        plugX = plugRestX
        plugY = plugRestY
        isSwitchOn = false
        isDraggingPlug = false
    }

    fun update(dt: Float, targetW: Float, targetH: Float, view: View, effectSys: EffectSystem, gs: GameState, onRouteConnection: (Int) -> Unit) {
        if (!isDraggingPlug && connectedMode == -1) {
            plugX += (targetPlugX - plugX) * 12f * dt
            plugY += (targetPlugY - plugY) * 12f * dt

            if (animatingToPort != -1) {
                val dx = targetPlugX - plugX
                val dy = targetPlugY - plugY
                if (dx * dx + dy * dy < 25f) {
                    plugX = targetPlugX
                    plugY = targetPlugY
                    connectedMode = animatingToPort
                    animatingToPort = -1

                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                    effectSys.spawnParticles(portX[connectedMode], portY[connectedMode], 8, min(targetW, targetH))

                    if (isSwitchOn) {
                        view.postDelayed({
                            if (isSwitchOn && connectedMode != -1 && gs.state == 0) {
                                if (connectedMode != 1 || !SaveManager.isStoryModeUnlocked) onRouteConnection(connectedMode)
                            }
                        }, 250)
                    }
                }
            }
        } else if (!isDraggingPlug) {
            plugX = portX[connectedMode]
            plugY = portY[connectedMode]
        }
    }

    fun draw(c: Canvas, scale: Float, gs: GameState, targetW: Float, targetH: Float, effectSys: EffectSystem) {
        val pw = scale * 0.08f
        val ph = scale * 0.045f
        val isPortrait = targetW < targetH

        pText.textAlign = Paint.Align.LEFT
        pText.color = GameColors.YELLOW
        pText.textSize = scale * 0.035f
        c.drawText("HIGH SCORE: ${SaveManager.highScore}", scale * 0.05f, scale * 0.08f, pText)
        pText.color = GameColors.CLARITY
        c.drawText("DATA: ${SaveManager.formatDataString(SaveManager.dataCoinsKB)}", scale * 0.05f, scale * 0.13f, pText)

        if (SaveManager.isHardModeUnlocked) {
            pText.color = if (gs.difficulty == 0) GameColors.TEXT else GameColors.RED
            pText.textAlign = Paint.Align.LEFT; pText.textSize = scale * 0.045f
            pText.setShadowLayer(15f, 0f, 0f, (if (gs.difficulty == 0) GameColors.PULSE else GameColors.RED))
            c.drawText(getCachedString(if (gs.difficulty == 0) R.string.ui_mode_easy else R.string.ui_mode_hard), scale * 0.05f, targetH - scale * 0.05f, pText)
            pText.clearShadowLayer()
        }

        pText.color = GameColors.TEXT; pText.textAlign = Paint.Align.RIGHT
        pText.setShadowLayer(15f, 0f, 0f, GameColors.PULSE)
        c.drawText(getCachedString(R.string.ui_help_btn), targetW - scale * 0.05f, scale * 0.08f, pText)
        pText.clearShadowLayer()

        pText.textAlign = Paint.Align.CENTER; pText.letterSpacing = 0.05f
        if (StoryProtocol.isGlitchActive) {
            val glitchRoll = Random.nextDouble()
            if (glitchRoll < 0.08) {
                var titleSize = scale * 0.15f
                pText.textSize = titleSize
                val str1 = getCachedString(R.string.ui_title_echo)
                val str2 = getCachedString(R.string.ui_title_hunter)
                while (pText.measureText(str1) > targetW * 0.9f || pText.measureText(str2) > targetW * 0.9f) {
                    titleSize *= 0.95f; pText.textSize = titleSize
                }

                val flashOffsetX = (Random.nextFloat() - 0.5f) * scale * 0.03f
                pText.color = GameColors.PULSE
                pText.setShadowLayer(25f, 0f, 0f, GameColors.PULSE)
                c.drawText(str1, targetW / 2f + flashOffsetX, targetH * 0.23f, pText)

                pText.color = GameColors.CLARITY
                pText.setShadowLayer(25f, 0f, 0f, GameColors.CLARITY)
                c.drawText(str2, targetW / 2f - flashOffsetX, targetH * 0.35f, pText)
            } else {
                var titleSize = scale * 0.12f
                pText.textSize = titleSize
                val str1 = getCachedString(R.string.ui_title_system)
                val str2 = getCachedString(R.string.ui_title_corrupted)
                while (pText.measureText(str1) > targetW * 0.9f || pText.measureText(str2) > targetW * 0.9f) {
                    titleSize *= 0.95f; pText.textSize = titleSize
                }

                val jitterX = (Random.nextFloat() - 0.5f) * scale * 0.02f
                val jitterY = (Random.nextFloat() - 0.5f) * scale * 0.02f

                pText.color = GameColors.RED
                pText.setShadowLayer(25f, 0f, 0f, GameColors.RED)
                c.drawText(str1, targetW / 2f + jitterX, targetH * 0.23f + jitterY, pText)
                c.drawText(str2, targetW / 2f - jitterX, targetH * 0.35f - jitterY, pText)
            }
        } else {
            var titleSize = scale * 0.15f
            pText.textSize = titleSize
            val str1 = getCachedString(R.string.ui_title_echo)
            val str2 = getCachedString(R.string.ui_title_hunter)
            while (pText.measureText(str1) > targetW * 0.9f || pText.measureText(str2) > targetW * 0.9f) {
                titleSize *= 0.95f; pText.textSize = titleSize
            }

            pText.color = GameColors.PULSE
            pText.setShadowLayer(25f, 0f, 0f, GameColors.PULSE)
            c.drawText(str1, targetW / 2f, targetH * 0.23f, pText)

            pText.color = GameColors.RED
            pText.setShadowLayer(25f, 0f, 0f, GameColors.RED)
            c.drawText(str2, targetW / 2f, targetH * 0.35f, pText)
        }
        pText.clearShadowLayer()

        pText.letterSpacing = 0.05f
        pText.textSize = scale * 0.035f
        val hintY = if (isPortrait) targetH * 0.46f else targetH * 0.48f

        // NAYA: Drawing the Memory Cards if plugged into Mainframe
        if (connectedMode == 1 && isSwitchOn && SaveManager.isStoryModeUnlocked) {
            drawMemoryCards(c, scale, hintY - scale * 0.05f, targetW, gs)
        } else if (connectedMode == -1) {
            pText.color = GameColors.YELLOW
            c.drawText(getCachedString(R.string.ui_hint_connect), targetW / 2f, hintY, pText)
        } else {
            if (!isSwitchOn) {
                pText.color = GameColors.RED
                c.drawText(getCachedString(R.string.ui_hint_toggle), targetW / 2f, hintY, pText)
            } else {
                pText.color = GameColors.HP
                c.drawText(getCachedString(R.string.ui_hint_initializing), targetW / 2f, hintY, pText)
            }
        }
        pText.letterSpacing = 0f

        for (i in 0..2) {
            val pinTipX = plugX - pw - scale * 0.04f
            val dxHover = pinTipX - portX[i]
            val dyHover = plugY - portY[i]
            val isHovered = isDraggingPlug && (dxHover * dxHover + dyHover * dyHover) < (scale * 0.15f) * (scale * 0.15f)

            p.style = Paint.Style.STROKE
            p.strokeWidth = if (isHovered) scale * 0.02f else scale * 0.015f
            p.color = if (connectedMode == i) GameColors.PULSE else if (isHovered) GameColors.CLARITY else 0xFF444444.toInt()
            c.drawCircle(portX[i], portY[i], scale * 0.045f, p)

            p.style = Paint.Style.FILL
            p.color = 0xFF0A0A0A.toInt()
            c.drawCircle(portX[i], portY[i], scale * 0.035f, p)

            p.color = if (connectedMode == i && isSwitchOn) GameColors.PULSE else 0xFF000000.toInt()
            c.drawRect(portX[i] - scale*0.02f, portY[i] - scale*0.008f, portX[i] + scale*0.02f, portY[i] + scale*0.008f, p)

            p.color = 0xFF333333.toInt()
            c.drawRect(portX[i] - scale*0.015f, portY[i] - scale*0.004f, portX[i] + scale*0.015f, portY[i] + scale*0.004f, p)

            val isLocked = (i == 1 && !SaveManager.isStoryModeUnlocked)
            val titleStr = if (isLocked) "LOCKED" else menuTitles[i]
            val subStr = if (isLocked) "Reach Ring 15" else menuSubs[i]
            val textColor = if (isLocked) GameColors.RED else (if (connectedMode == i) GameColors.CLARITY else GameColors.TEXT)

            var portTitleSize = if (connectedMode == i) scale * 0.05f else scale * 0.04f
            pText.textSize = portTitleSize
            val maxPortTextW = if (isPortrait) targetW * 0.45f else targetW * 0.3f
            while (pText.measureText(titleStr) > maxPortTextW) {
                portTitleSize *= 0.95f
                pText.textSize = portTitleSize
            }

            pText.textAlign = Paint.Align.CENTER
            pText.color = textColor
            pText.setShadowLayer(if(connectedMode == i && !isLocked) 15f else 0f, 0f, 0f, if(isLocked) GameColors.RED else GameColors.PULSE)
            c.drawText(titleStr, portX[i], portY[i] + scale * 0.11f, pText)
            pText.clearShadowLayer()

            pText.textSize = scale * 0.025f
            pText.color = if (isLocked) 0xFF550000.toInt() else (if (connectedMode == i) GameColors.PULSE else 0xFF888888.toInt())
            c.drawText(subStr, portX[i], portY[i] + scale * 0.15f, pText)
        }

        cablePath.reset()
        cablePath.moveTo(targetW, targetH * 0.85f)
        if (connectedMode == -1) {
            val cx = (targetW + plugX) / 2f
            val cy = max(targetH * 0.85f, plugY) + scale * 0.2f
            cablePath.quadTo(cx, cy, plugX, plugY)
        } else {
            val cx = plugX + scale * 0.1f
            val cy = plugY + ph + scale * 0.3f
            cablePath.quadTo(cx, cy, plugX, plugY + ph - scale * 0.01f)
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = scale * 0.025f
        p.color = 0xFF181818.toInt()
        c.drawPath(cablePath, p)

        p.strokeWidth = scale * 0.008f
        p.color = if (isSwitchOn) GameColors.PULSE else 0xFF444444.toInt()
        c.drawPath(cablePath, p)

        if (connectedMode == -1) {
            p.style = Paint.Style.FILL
            p.color = 0xFF999999.toInt()
            c.drawRect(plugX - pw - scale * 0.04f, plugY - scale * 0.015f, plugX - pw, plugY + scale * 0.015f, p)

            p.color = 0xFF222222.toInt()
            c.drawRoundRect(plugX - pw, plugY - ph, plugX + pw, plugY + ph, scale * 0.015f, scale * 0.015f, p)

            p.color = 0xFF111111.toInt()
            p.strokeWidth = scale * 0.005f
            p.style = Paint.Style.STROKE
            for(i in -1..1) {
                val gx = plugX + (i * scale * 0.02f)
                c.drawLine(gx, plugY - ph + scale*0.01f, gx, plugY + ph - scale*0.01f, p)
            }

            val swSize = scale * 0.025f
            val swX = plugX + pw * 0.4f
            val swColor = if (isSwitchOn) GameColors.HP else GameColors.RED
            val swOffset = if (isSwitchOn) -scale * 0.01f else scale * 0.01f

            p.style = Paint.Style.FILL
            p.color = 0xFF050505.toInt()
            c.drawRoundRect(swX - swSize * 0.8f, plugY - swSize * 1.5f, swX + swSize * 0.8f, plugY + swSize * 1.5f, scale*0.01f, scale*0.01f, p)

            p.color = swColor
            c.drawRoundRect(swX - swSize, plugY - swSize + swOffset, swX + swSize, plugY + swSize + swOffset, scale*0.01f, scale*0.01f, p)

            p.style = Paint.Style.STROKE
            p.color = 0x55FFFFFF
            p.strokeWidth = scale * 0.003f
            c.drawRoundRect(swX - swSize, plugY - swSize + swOffset, swX + swSize, plugY + swSize + swOffset, scale*0.01f, scale*0.01f, p)
        } else {
            p.style = Paint.Style.FILL
            p.color = 0xFF1B1B1B.toInt()
            c.drawRoundRect(plugX - pw, plugY - ph, plugX + pw, plugY + ph, scale * 0.015f, scale * 0.015f, p)

            p.color = 0xFF222222.toInt()
            c.drawRoundRect(plugX - pw * 0.8f, plugY - ph * 0.8f, plugX + pw * 0.8f, plugY + ph * 0.8f, scale * 0.01f, scale * 0.01f, p)

            val swSize = scale * 0.025f
            val swColor = if (isSwitchOn) GameColors.HP else GameColors.RED
            val swOffset = if (isSwitchOn) -scale * 0.01f else scale * 0.01f

            p.color = 0xFF050505.toInt()
            c.drawRoundRect(plugX - swSize * 1.5f, plugY - swSize * 0.8f, plugX + swSize * 1.5f, plugY + swSize * 0.8f, scale*0.01f, scale*0.01f, p)

            p.color = swColor
            c.drawRoundRect(plugX - swSize + swOffset, plugY - swSize, plugX + swSize + swOffset, plugY + swSize, scale*0.01f, scale*0.01f, p)

            p.style = Paint.Style.STROKE
            p.color = 0x55FFFFFF
            p.strokeWidth = scale * 0.003f
            c.drawRoundRect(plugX - swSize + swOffset, plugY - swSize, plugX + swSize + swOffset, plugY + swSize, scale*0.01f, scale*0.01f, p)
        }

        effectSys.drawParticles(c, 0f, 0f, scale)
    }

    private fun drawMemoryCards(c: Canvas, scale: Float, startY: Float, targetW: Float, gs: GameState) {
        val cardW = scale * 0.15f
        val cardH = scale * 0.22f
        val gap = scale * 0.05f
        val totalW = (cardW * 3) + (gap * 2)
        var startX = (targetW - totalW) / 2f

        val currentStreak = if (gs.difficulty == 1) SaveManager.currentHardStreak else SaveManager.currentStoryStreak
        val unlockedStreak = if (gs.difficulty == 1) SaveManager.unlockedHardStreak else SaveManager.unlockedStoryStreak

        val isFullyBeatenCurrent = currentStreak >= 3

        for (i in 0..2) {
            val pCard = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = scale * 0.005f }
            val pFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
            val pText = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = scale * 0.05f }

            val rect = android.graphics.RectF(startX, startY, startX + cardW, startY + cardH)

            if (isFullyBeatenCurrent || i == currentStreak) {
                // NEXT TARGET: FULL GLOW
                pCard.color = GameColors.PULSE
                pCard.setShadowLayer(15f, 0f, 0f, GameColors.PULSE)
                pFill.color = 0x4400FFFF
                pText.color = GameColors.CLARITY
            } else if (i <= unlockedStreak) {
                // BEATEN: DIM GLOW
                pCard.color = 0xAA00FFFF.toInt()
                pFill.color = 0x1100FFFF
                pText.color = 0xAAFFFFFF.toInt()
            } else {
                // LOCKED
                pCard.color = 0x55FFFFFF
                pFill.color = 0x22000000
                pText.color = 0x55FFFFFF
            }

            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, pFill)
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, pCard)
            pCard.clearShadowLayer()

            c.drawText("ACT", startX + cardW/2f, startY + cardH * 0.4f, pText)
            c.drawText("${i+1}", startX + cardW/2f, startY + cardH * 0.7f, pText)

            startX += cardW + gap
        }
    }

    fun onTouch(
        vx: Float, vy: Float, action: Int, scale: Float, targetW: Float, targetH: Float,
        view: View, gs: GameState, onDifficultyToggle: () -> Unit, onHelpOpen: () -> Unit, onRouteConnection: (Int) -> Unit
    ): Boolean {
        val pw = scale * 0.08f
        val ph = scale * 0.045f

        val hitSwitch: Boolean
        val hitPlug: Boolean

        if (connectedMode == -1) {
            val swSize = scale * 0.025f
            val swX = plugX + pw * 0.4f
            hitSwitch = vx in (swX - swSize * 2.5f)..(swX + swSize * 2.5f) && vy in (plugY - ph * 1.5f)..(plugY + ph * 1.5f)
            hitPlug = vx in (plugX - pw - scale*0.06f)..(plugX + pw + scale*0.06f) && vy in (plugY - ph - scale*0.06f)..(plugY + ph + scale*0.06f)
        } else {
            val swSize = scale * 0.025f
            hitSwitch = vx in (plugX - swSize * 2.5f)..(plugX + swSize * 2.5f) && vy in (plugY - swSize * 2.5f)..(plugY + swSize * 2.5f)
            hitPlug = vx in (plugX - pw - scale*0.06f)..(plugX + pw + scale*0.06f) && vy in (plugY - ph - scale*0.06f)..(plugY + ph + scale*0.06f)
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (hitSwitch || hitPlug) {
                    isDraggingPlug = true
                    touchDownX = vx
                    touchDownY = vy
                    wasSwitchHitOnDown = hitSwitch
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingPlug) {
                    val dx = vx - touchDownX
                    val dy = vy - touchDownY

                    if (connectedMode != -1 && (dx * dx + dy * dy > (scale * 0.05f) * (scale * 0.05f))) {
                        connectedMode = -1
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 50)
                        wasSwitchHitOnDown = false
                    }

                    if (connectedMode == -1) {
                        plugX = vx
                        plugY = vy
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (vx < targetW * 0.2f && vy > targetH * 0.8f) {
                    onDifficultyToggle()
                    return true
                }
                if (vx > targetW * 0.8f && vy < targetH * 0.2f) {
                    onHelpOpen()
                    return true
                }

                // TOUCH CARDS LOGIC
                if (connectedMode == 1 && isSwitchOn && SaveManager.isStoryModeUnlocked) {
                    val cardW = scale * 0.15f
                    val cardH = scale * 0.22f
                    val gap = scale * 0.05f
                    val totalW = (cardW * 3) + (gap * 2)
                    var startX = (targetW - totalW) / 2f
                    val isPortrait = targetW < targetH
                    val hintY = if (isPortrait) targetH * 0.46f else targetH * 0.48f
                    val startY = hintY - scale * 0.05f

                    for (i in 0..2) {
                        if (vx in startX..(startX + cardW) && vy in startY..(startY + cardH)) {
                            val unlocked = if (gs.difficulty == 1) SaveManager.unlockedHardStreak else SaveManager.unlockedStoryStreak
                            if (i <= unlocked) {
                                gs.selectedStoryAct = i
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 50)
                                onRouteConnection(1)
                            } else {
                                EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 50)
                            }
                            return true
                        }
                        startX += cardW + gap
                    }
                }

                if (isDraggingPlug) {
                    isDraggingPlug = false

                    val dx = vx - touchDownX
                    val dy = vy - touchDownY
                    val isTap = (dx * dx + dy * dy) < (scale * 0.05f) * (scale * 0.05f)

                    if (isTap && wasSwitchHitOnDown) {
                        isSwitchOn = !isSwitchOn
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 50)

                        if (isSwitchOn && connectedMode != -1) {
                            view.postDelayed({
                                if (isSwitchOn && connectedMode != -1 && gs.state == 0) {
                                    if (connectedMode != 1 || !SaveManager.isStoryModeUnlocked) onRouteConnection(connectedMode)
                                }
                            }, 250)
                        }
                        return true
                    }

                    if (connectedMode == -1) {
                        var snapped = false
                        for (i in 0..2) {
                            val pinTipX = plugX - pw - scale * 0.04f
                            val pdx = pinTipX - portX[i]
                            val pdy = plugY - portY[i]

                            if (pdx * pdx + pdy * pdy < (scale * 0.15f) * (scale * 0.15f)) {
                                animatingToPort = i
                                targetPlugX = portX[i]
                                targetPlugY = portY[i]
                                snapped = true
                                break
                            }
                        }

                        if (!snapped) {
                            animatingToPort = -1
                            targetPlugX = plugRestX
                            targetPlugY = plugRestY
                            EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
                        }
                    }
                } else {
                    if (connectedMode == -1 && animatingToPort == -1) {
                        for (i in 0..2) {
                            val dx = vx - portX[i]
                            val dy = vy - portY[i]
                            if (dx * dx + dy * dy < (scale * 0.15f) * (scale * 0.15f)) {
                                animatingToPort = i
                                targetPlugX = portX[i]
                                targetPlugY = portY[i]
                                EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 50)
                                break
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}