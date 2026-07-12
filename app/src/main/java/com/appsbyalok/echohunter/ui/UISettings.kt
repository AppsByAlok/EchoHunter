package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.abs

class UISettings {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val backBtnRect = RectF()
    private val optionRects = Array(7) { RectF() }
    private var hitOnDown = -1

    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var maxScroll = 0f

    private var showWipeConfirm = false
    private val confirmDialogRect = RectF()
    private val confirmYesRect = RectF()
    private val confirmNoRect = RectF()

    private val options = arrayOf(
        "SFX AUDIO",
        "HAPTIC FEEDBACK",
        "VISUAL EFFECTS",
        "AUTO-NEXT LEVEL",
        "ATTACK MODE",
        "SCREEN ROTATION",
        "WIPE ALL DATA"
    )

    fun draw(c: Canvas, targetW: Float, targetH: Float, scale: Float, gs: GameState) {
        c.drawColor(0xEE050A0F.toInt())

        val isPortrait = targetH > targetW

        // --- HEADER (Fixed) ---
        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.07f
        pText.color = GameColors.PULSE
        c.drawText("SYSTEM CONFIG", targetW / 2f, scale * 0.12f, pText)

        // --- SCROLLABLE CONTENT ---
        val startY = scale * 0.22f
        val itemH = scale * 0.11f
        val gap = scale * 0.02f
        val itemW = if (isPortrait) targetW * 0.85f else targetW * 0.6f
        val startX = (targetW - itemW) / 2f

        val totalContentHeight = options.size * (itemH + gap)
        val viewportHeight = targetH - startY - (scale * 0.15f) // Reserve space for Return button
        maxScroll = maxOf(0f, totalContentHeight - viewportHeight)
        
        c.save()
        c.clipRect(0f, startY - gap, targetW, targetH - scale * 0.14f)
        c.translate(0f, -scrollY)

        for (i in options.indices) {
            val rect = optionRects[i]
            val top = startY + i * (itemH + gap)
            rect.set(startX, top, startX + itemW, top + itemH)

            val isPressed = (hitOnDown == i) && !isDragging
            p.style = Paint.Style.FILL
            p.color = if (isPressed) 0xFF102530.toInt() else 0xFF0A1520.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            p.style = Paint.Style.STROKE
            p.color = if (i == 6) GameColors.RED else GameColors.PULSE
            p.strokeWidth = scale * 0.004f
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            pText.textAlign = Paint.Align.LEFT
            pText.textSize = scale * 0.038f
            pText.color = GameColors.CLARITY
            c.drawText(options[i], rect.left + scale * 0.04f, rect.centerY() + scale * 0.015f, pText)

            // Value / Toggle State
            pText.textAlign = Paint.Align.RIGHT
            val valueText = when (i) {
                0 -> if (SaveManager.isSoundEnabled) "ON" else "OFF"
                1 -> if (SaveManager.isVibrationEnabled) "ON" else "OFF"
                2 -> if (SaveManager.isEffectsEnabled) "ON" else "OFF"
                3 -> if (SaveManager.isAutoNextLevelEnabled) "ON" else "OFF"
                4 -> gs.controls.activeAttackMode.name.replace("_", " ")
                5 -> when(SaveManager.screenOrientation) {
                    0 -> "AUTO ROTATE"
                    1 -> "PORTRAIT"
                    2 -> "LANDSCAPE"
                    else -> "DEVICE DEFAULT"
                }
                6 -> "DANGER"
                else -> ""
            }
            pText.color = if (valueText == "OFF" || i == 6) GameColors.RED else GameColors.PULSE
            c.drawText(valueText, rect.right - scale * 0.04f, rect.centerY() + scale * 0.015f, pText)
        }
        c.restore()

        // --- BACK BUTTON (Fixed at bottom) ---
        val btnW = scale * 0.3f
        val btnH = scale * 0.08f
        backBtnRect.set(targetW / 2f - btnW / 2f, targetH - btnH - scale * 0.04f, targetW / 2f + btnW / 2f, targetH - scale * 0.04f)

        p.style = Paint.Style.STROKE
        p.color = GameColors.CLARITY
        c.drawRoundRect(backBtnRect, scale * 0.01f, scale * 0.01f, p)

        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.035f
        pText.color = GameColors.CLARITY
        c.drawText("RETURN", backBtnRect.centerX(), backBtnRect.centerY() + scale * 0.012f, pText)

        if (showWipeConfirm) {
            drawWipeConfirm(c, targetW, targetH, scale)
        }
    }

    private fun drawWipeConfirm(c: Canvas, targetW: Float, targetH: Float, scale: Float) {
        c.drawColor(0xAA000000.toInt())
        val dw = scale * 0.75f
        val dh = scale * 0.38f
        confirmDialogRect.set(targetW/2f - dw/2f, targetH/2f - dh/2f, targetW/2f + dw/2f, targetH/2f + dh/2f)

        p.style = Paint.Style.FILL
        p.color = 0xFF0A1520.toInt()
        c.drawRoundRect(confirmDialogRect, scale * 0.02f, scale * 0.02f, p)

        p.style = Paint.Style.STROKE
        p.color = GameColors.RED
        p.strokeWidth = scale * 0.005f
        c.drawRoundRect(confirmDialogRect, scale * 0.02f, scale * 0.02f, p)

        pText.textAlign = Paint.Align.CENTER
        pText.textSize = scale * 0.045f
        pText.color = GameColors.CLARITY
        c.drawText("WIPE ALL DATA?", targetW/2f, confirmDialogRect.top + scale * 0.1f, pText)

        pText.textSize = scale * 0.028f
        pText.color = GameColors.RED
        c.drawText("THIS ACTION IS PERMANENT", targetW/2f, confirmDialogRect.top + scale * 0.16f, pText)

        val bw = scale * 0.25f
        val bh = scale * 0.08f
        confirmNoRect.set(targetW/2f - bw - scale * 0.02f, confirmDialogRect.bottom - bh - scale * 0.05f, targetW/2f - scale * 0.02f, confirmDialogRect.bottom - scale * 0.05f)
        confirmYesRect.set(targetW/2f + scale * 0.02f, confirmDialogRect.bottom - bh - scale * 0.05f, targetW/2f + bw + scale * 0.02f, confirmDialogRect.bottom - scale * 0.05f)

        // No
        p.style = Paint.Style.FILL
        p.color = if (hitOnDown == 21) 0xFF1A3040.toInt() else 0xFF102530.toInt()
        c.drawRoundRect(confirmNoRect, scale * 0.01f, scale * 0.01f, p)
        pText.color = GameColors.CLARITY
        c.drawText("CANCEL", confirmNoRect.centerX(), confirmNoRect.centerY() + scale * 0.012f, pText)

        // Yes
        p.style = Paint.Style.FILL
        p.color = if (hitOnDown == 20) 0xFF401010.toInt() else 0xFF300A0A.toInt()
        c.drawRoundRect(confirmYesRect, scale * 0.01f, scale * 0.01f, p)
        pText.color = GameColors.RED
        c.drawText("WIPE", confirmYesRect.centerX(), confirmYesRect.centerY() + scale * 0.012f, pText)
    }

    fun onTouch(vx: Float, vy: Float, action: Int, scale: Float, gs: GameState, onClose: () -> Unit, onWipe: () -> Unit, onOrientChange: () -> Unit): Boolean {
        if (showWipeConfirm) {
            // ... (keep wipe confirm logic as is, it's overlay)
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    hitOnDown = -1
                    if (confirmYesRect.contains(vx, vy)) hitOnDown = 20
                    if (confirmNoRect.contains(vx, vy)) hitOnDown = 21
                }
                MotionEvent.ACTION_UP -> {
                    if (hitOnDown == 20 && confirmYesRect.contains(vx, vy)) {
                        SaveManager.clearAllData()
                        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 200)
                        showWipeConfirm = false
                        onWipe()
                    } else if (hitOnDown == 21 && confirmNoRect.contains(vx, vy)) {
                        showWipeConfirm = false
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                    }
                    hitOnDown = -1
                }
            }
            val returnValue = true
            return returnValue
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                hitOnDown = -1
                lastTouchY = vy
                isDragging = false
                
                // Adjust for scroll when checking hits
                val adjustedVy = vy + scrollY
                for (i in options.indices) {
                    if (optionRects[i].contains(vx, adjustedVy)) {
                        hitOnDown = i
                        break
                    }
                }
                if (backBtnRect.contains(vx, vy)) hitOnDown = 10
            }
            MotionEvent.ACTION_MOVE -> {
                val diff = lastTouchY - vy
                if (abs(diff) > scale * 0.01f) {
                    isDragging = true
                }
                if (isDragging) {
                    scrollY += diff
                    scrollY = scrollY.coerceIn(0f, maxScroll)
                    lastTouchY = vy
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && hitOnDown != -1) {
                    if (backBtnRect.contains(vx, vy) && hitOnDown == 10) {
                        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)
                        onClose()
                    } else if (hitOnDown < options.size) {
                         // Check with adjusted Y for items
                        val adjustedVy = vy + scrollY
                        if (optionRects[hitOnDown].contains(vx, adjustedVy)) {
                            EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
                            when (hitOnDown) {
                                0 -> SaveManager.setSoundEnabled(!SaveManager.isSoundEnabled)
                                1 -> SaveManager.setVibrationEnabled(!SaveManager.isVibrationEnabled)
                                2 -> SaveManager.setEffectsEnabled(!SaveManager.isEffectsEnabled)
                                3 -> SaveManager.setAutoNextLevel(!SaveManager.isAutoNextLevelEnabled)
                                4 -> {
                                    val modes = AttackMode.entries.toTypedArray()
                                    val next = (gs.controls.activeAttackMode.ordinal + 1) % modes.size
                                    gs.controls.activeAttackMode = modes[next]
                                    SaveManager.setAttackMode(next)
                                }
                                5 -> {
                                    val next = (SaveManager.screenOrientation + 1) % 4
                                    SaveManager.setScreenOrientation(next)
                                    onOrientChange()
                                }
                                6 -> {
                                    showWipeConfirm = true
                                }
                            }
                        }
                    }
                }
                hitOnDown = -1
                isDragging = false
            }
        }
        return true
    }
}
