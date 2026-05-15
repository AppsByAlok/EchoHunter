package com.appsbyalok.echohunter.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ToneGenerator
import android.view.MotionEvent
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.utils.GameColors
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max

class UIModMenu {
    var isOpen = false
    private var holdingLevelDir = 0
    private var holdStartTime = 0L
    private var lastLevelChangeTime = 0L

    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val pDebug = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private fun updateModMenuHoldLogic() {
        if (holdingLevelDir != 0) {
            val currentTime = System.currentTimeMillis()
            val holdDuration = currentTime - holdStartTime
            val delayMs = max(20L, 300L - (holdDuration / 5L))

            if (currentTime - lastLevelChangeTime >= delayMs) {
                changeLevel(holdingLevelDir)
                lastLevelChangeTime = currentTime
            }
        }
    }

    private fun changeLevel(dir: Int) {
        val newLevel = max(1, SaveManager.maxCampaignLevel + dir)
        SaveManager.debugSetLevel(newLevel)
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 30)
    }

    fun draw(c: Canvas, scale: Float, targetW: Float, targetH: Float, gs: GameState) {
        if (!isOpen) return

        updateModMenuHoldLogic()
        p.style = Paint.Style.FILL
        p.color = 0xEE050505.toInt()
        c.drawRect(0f, 0f, targetW, targetH, p)

        val panelRect = RectF(targetW * 0.15f, targetH * 0.1f, targetW * 0.85f, targetH * 0.9f)
        p.color = 0xFF111111.toInt()
        c.drawRoundRect(panelRect, scale * 0.05f, scale * 0.05f, p)
        p.style = Paint.Style.STROKE
        p.color = GameColors.RED
        p.strokeWidth = scale * 0.01f
        c.drawRoundRect(panelRect, scale * 0.05f, scale * 0.05f, p)

        pText.color = GameColors.RED
        pText.textSize = scale * 0.08f
        c.drawText("[ DEVELOPER MOD MENU ]", targetW / 2f, targetH * 0.22f, pText)

        val btnHeight = scale * 0.12f
        fun drawButton(
            text: String,
            cx: Float,
            cy: Float,
            width: Float,
            color: Int,
            isPressed: Boolean = false,
        ) {
            val rect = RectF(cx - width / 2, cy - btnHeight / 2, cx + width / 2, cy + btnHeight / 2)

            p.style = Paint.Style.FILL
            p.color = if (isPressed) 0xFF444444.toInt() else 0xFF222222.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            p.style = Paint.Style.STROKE
            p.color = color
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            pText.textSize = scale * 0.05f
            pText.color = color
            c.drawText(text, cx, cy - (pText.descent() + pText.ascent()) / 2, pText)
        }

        val startY = targetH * 0.4f
        val gap = scale * 0.16f

        pText.textSize = scale * 0.06f
        pText.color = GameColors.CLARITY
        c.drawText(
            "LEVEL: ${SaveManager.maxCampaignLevel}",
            targetW / 2f,
            startY - (pText.descent() + pText.ascent()) / 2,
            pText
        )

        val sideBtnWidth = scale * 0.25f
        val leftCx = targetW / 2f - scale * 0.45f
        val rightCx = targetW / 2f + scale * 0.45f

        drawButton("<< -1", leftCx, startY, sideBtnWidth, GameColors.CLARITY, holdingLevelDir == -1)
        drawButton("+1 >>", rightCx, startY, sideBtnWidth, GameColors.CLARITY, holdingLevelDir == 1)

        drawButton("+ 1 MB DATA", targetW / 2f, startY + gap, scale * 0.6f, GameColors.OVERCLOCK)
        drawButton(
            if (gs.showDebugHitboxes) "DEBUG UI: ON" else "DEBUG UI: OFF",
            targetW / 2f,
            startY + gap * 2,
            scale * 0.6f,
            GameColors.PULSE
        )
        drawButton("CLOSE MENU", targetW / 2f, startY + gap * 3.5f, scale * 0.6f, GameColors.YELLOW)
    }

    fun onTouch(
        vx: Float,
        vy: Float,
        action: Int,
        scale: Float,
        targetW: Float,
        targetH: Float,
        gs: GameState,
    ): Boolean {
        if (!isOpen) return false

        val startY = targetH * 0.4f
        val gap = scale * 0.16f
        val btnHeight = scale * 0.12f

        val leftBtnRight = targetW / 2f - scale * 0.45f + (scale * 0.125f)
        val rightBtnLeft = targetW / 2f + scale * 0.45f - (scale * 0.125f)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (vy in (startY - btnHeight / 2)..(startY + btnHeight / 2)) {
                    if (vx < leftBtnRight) {
                        holdingLevelDir = -1
                        holdStartTime = System.currentTimeMillis()
                        lastLevelChangeTime = holdStartTime
                        changeLevel(-1)
                    } else if (vx > rightBtnLeft) {
                        holdingLevelDir = 1
                        holdStartTime = System.currentTimeMillis()
                        lastLevelChangeTime = holdStartTime
                        changeLevel(1)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                holdingLevelDir = 0
                if (vy in (startY + gap - btnHeight / 2)..(startY + gap + btnHeight / 2)) {
                    SaveManager.addData(1024L)
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                } else if (vy in (startY + gap * 2 - btnHeight / 2)..(startY + gap * 2 + btnHeight / 2)) {
                    gs.showDebugHitboxes = !gs.showDebugHitboxes
                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
                } else if (vy in (startY + gap * 3.5f - btnHeight / 2)..(startY + gap * 3.5f + btnHeight / 2)) {
                    isOpen = false
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                holdingLevelDir = 0
            }
        }
        return true
    }

    fun drawDebugHitboxes(c: Canvas, gs: GameState, scale: Float) {
        if (!gs.showDebugHitboxes) return

        pDebug.style = Paint.Style.STROKE
        pDebug.color = android.graphics.Color.RED
        c.drawCircle(gs.uiAtkX, gs.uiAtkY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.GREEN
        c.drawCircle(gs.uiOvrX, gs.uiOvrY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.BLUE
        c.drawCircle(gs.uiTrapX, gs.uiTrapY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.CYAN
        c.drawCircle(gs.uiPulseX, gs.uiPulseY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.YELLOW
        c.drawCircle(gs.uiPauseX, gs.uiPauseY, gs.uiBtnRadius * 1.5f, pDebug)

        if (gs.lastTouchX > 0f) {
            pDebug.style = Paint.Style.FILL
            pDebug.color = android.graphics.Color.MAGENTA
            c.drawCircle(gs.lastTouchX, gs.lastTouchY, 20f, pDebug)
        }
    }


    fun drawModMenu(c: Canvas, scale: Float, targetW: Float, targetH: Float, gs: GameState) {

        val p = Paint().apply { isAntiAlias = true; color = 0xEE050505.toInt() }
        c.drawRect(0f, 0f, targetW, targetH, p)

        val panelRect = RectF(targetW * 0.15f, targetH * 0.1f, targetW * 0.85f, targetH * 0.9f)
        p.color = 0xFF111111.toInt()
        c.drawRoundRect(panelRect, scale * 0.05f, scale * 0.05f, p)
        p.style = Paint.Style.STROKE
        p.color = com.appsbyalok.echohunter.utils.GameColors.RED
        p.strokeWidth = scale * 0.01f
        c.drawRoundRect(panelRect, scale * 0.05f, scale * 0.05f, p)

        val pText = Paint().apply {
            isAntiAlias = true
            typeface =
                Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        pText.color = com.appsbyalok.echohunter.utils.GameColors.RED
        pText.textSize = scale * 0.08f
        c.drawText("[ DEVELOPER MOD MENU ]", targetW / 2f, targetH * 0.22f, pText)

        val btnHeight = scale * 0.12f
        fun drawButton(
            text: String,
            cx: Float,
            cy: Float,
            width: Float,
            color: Int,
            isPressed: Boolean = false,
        ) {
            val rect = RectF(
                cx - width / 2, cy - btnHeight / 2, cx + width / 2, cy + btnHeight / 2
            )

            p.style = Paint.Style.FILL
            p.color = if (isPressed) 0xFF444444.toInt() else 0xFF222222.toInt()
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            p.style = Paint.Style.STROKE
            p.color = color
            c.drawRoundRect(rect, scale * 0.02f, scale * 0.02f, p)

            pText.textSize = scale * 0.05f
            pText.color = color
            c.drawText(text, cx, cy - (pText.descent() + pText.ascent()) / 2, pText)
        }

        val startY = targetH * 0.4f
        val gap = scale * 0.16f

        pText.textSize = scale * 0.06f
        pText.color = com.appsbyalok.echohunter.utils.GameColors.CLARITY
        c.drawText(
            "LEVEL: ${SaveManager.maxCampaignLevel}",
            targetW / 2f,
            startY - (pText.descent() + pText.ascent()) / 2,
            pText
        )

        val sideBtnWidth = scale * 0.25f
        val leftCx = targetW / 2f - scale * 0.45f
        val rightCx = targetW / 2f + scale * 0.45f

        drawButton(
            "<< -1",
            leftCx,
            startY,
            sideBtnWidth,
            com.appsbyalok.echohunter.utils.GameColors.CLARITY,
            holdingLevelDir == -1
        )
        drawButton(
            "+1 >>",
            rightCx,
            startY,
            sideBtnWidth,
            com.appsbyalok.echohunter.utils.GameColors.CLARITY,
            holdingLevelDir == 1
        )

        drawButton(
            "+ 1 MB DATA",
            targetW / 2f,
            startY + gap,
            scale * 0.6f,
            com.appsbyalok.echohunter.utils.GameColors.OVERCLOCK
        )
        drawButton(
            if (gs.showDebugHitboxes) "DEBUG UI: ON" else "DEBUG UI: OFF",
            targetW / 2f,
            startY + gap * 2,
            scale * 0.6f,
            com.appsbyalok.echohunter.utils.GameColors.PULSE
        )
        drawButton(
            "CLOSE MENU",
            targetW / 2f,
            startY + gap * 3.5f,
            scale * 0.6f,
            com.appsbyalok.echohunter.utils.GameColors.YELLOW
        )
    }

    fun handleModMenuTouch(
        vx: Float,
        vy: Float,
        action: Int,
        scale: Float,
        targetW: Float,
        targetH: Float,
        gs: GameState,
    ) {
        val startY = targetH * 0.4f
        val gap = scale * 0.16f
        val btnHeight = scale * 0.12f

        val leftBtnRight = targetW / 2f - scale * 0.45f + (scale * 0.125f)
        val rightBtnLeft = targetW / 2f + scale * 0.45f - (scale * 0.125f)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (vy in (startY - btnHeight / 2)..(startY + btnHeight / 2)) {
                    if (vx < leftBtnRight) {
                        holdingLevelDir = -1
                        holdStartTime = System.currentTimeMillis()
                        lastLevelChangeTime = holdStartTime
                        changeLevel(-1)
                    } else if (vx > rightBtnLeft) {
                        holdingLevelDir = 1
                        holdStartTime = System.currentTimeMillis()
                        lastLevelChangeTime = holdStartTime
                        changeLevel(1)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                holdingLevelDir = 0
                if (vy in (startY + gap - btnHeight / 2)..(startY + gap + btnHeight / 2)) {
                    SaveManager.addData(1024L)
                    EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 100)
                } else if (vy in (startY + gap * 2 - btnHeight / 2)..(startY + gap * 2 + btnHeight / 2)) {
                    gs.showDebugHitboxes = !gs.showDebugHitboxes
                    EchoAudioManager.playSound(ToneGenerator.TONE_PROP_BEEP, 100)
                } else if (vy in (startY + gap * 3.5f - btnHeight / 2)..(startY + gap * 3.5f + btnHeight / 2)) {
                    isOpen = false
                    EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 100)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                holdingLevelDir = 0
            }
        }
    }


    fun drawDebugHitboxes(c: Canvas, scale: Float, gs: GameState) {
        val pDebug = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f
        }

        pDebug.color = android.graphics.Color.RED
        c.drawCircle(gs.uiAtkX, gs.uiAtkY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.GREEN
        c.drawCircle(gs.uiOvrX, gs.uiOvrY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.BLUE
        c.drawCircle(gs.uiTrapX, gs.uiTrapY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.CYAN
        c.drawCircle(gs.uiPulseX, gs.uiPulseY, gs.uiBtnRadius * 1.5f, pDebug)
        pDebug.color = android.graphics.Color.YELLOW
        c.drawCircle(gs.uiPauseX, gs.uiPauseY, gs.uiBtnRadius * 1.5f, pDebug)

        if (gs.lastTouchX > 0f) {
            pDebug.style = Paint.Style.FILL
            pDebug.color = android.graphics.Color.MAGENTA
            c.drawCircle(gs.lastTouchX, gs.lastTouchY, 20f, pDebug)
        }
    }
}