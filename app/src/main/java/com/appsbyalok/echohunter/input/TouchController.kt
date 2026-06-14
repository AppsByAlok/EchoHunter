package com.appsbyalok.echohunter.input

import android.view.MotionEvent
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.sqrt

class TouchController(private val gs: GameState) {

    var onPauseClicked: (() -> Unit)? = null
    var onPulseTriggered: (() -> Unit)? = null

    private var joyPointerId = -1

    fun handleTouch(
        e: MotionEvent,
        offsetX: Float,
        offsetY: Float,
        targetW: Float,
        targetH: Float,
        scale: Float
    ): Boolean {
        if (gs.state != 1 && gs.state != 8) return true

        val action = e.actionMasked
        val pointerIndex = e.actionIndex
        val pointerId = e.getPointerId(pointerIndex)

        val vx = e.getX(pointerIndex) - offsetX
        val vy = e.getY(pointerIndex) - offsetY

        gs.touch.lastTouchX = vx
        gs.touch.lastTouchY = vy

        val joyMaxRadius = scale * 0.15f

        // --- 100% PERFECT HITBOX MATCH ---
        // Reads directly from what GameView calculated for the screen!
        val btnRadius = gs.hudLayout.btnRadius
        val atkX = gs.hudLayout.atkX; val atkY = gs.hudLayout.atkY
        val ovrX = gs.hudLayout.ovrX; val ovrY = gs.hudLayout.ovrY
        val trapX = gs.hudLayout.trapX; val trapY = gs.hudLayout.trapY
        val pulseX = gs.hudLayout.pulseX; val pulseY = gs.hudLayout.pulseY
        val pauseX = gs.hudLayout.pauseX; val pauseY = gs.hudLayout.pauseY

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {

                // --- FIX: AUTO-HEAL GHOST POINTER ---
                // Agar first finger screen par touch, toh old stuck IDs ko reset kar do!
                if (action == MotionEvent.ACTION_DOWN) {
                    joyPointerId = -1
                }

                if (vx < targetW / 2f) {
                    if (joyPointerId == -1) {
                        joyPointerId = pointerId
                        gs.controls.isMoveJoyActive = true
                        gs.touch.joyBaseX = vx
                        gs.touch.joyBaseY = vy
                        gs.touch.joyKnobX = vx
                        gs.touch.joyKnobY = vy
                        gs.controls.moveDirX = 0f
                        gs.controls.moveDirY = 0f
                    }
                } else {
                    // Check Pause Button First (Top Right)
                    if (isInsideCircle(vx, vy, pauseX, pauseY, btnRadius * 1.5f)) {
                        onPauseClicked?.invoke()
                    }
                    // Check PULSE Button
                    val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
                    val isDarkness = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DARKNESS)
                    val isStoryBlackout = gs.gameMode == 1 && com.appsbyalok.echohunter.data.SaveManager.unlockedStoryStreak >= 3
                    if ((isDarkness || isStoryBlackout) && isInsideCircle(vx, vy, pulseX, pulseY, btnRadius * 1.2f)) {
                            onPulseTriggered?.invoke()
                    }
                    // Check TRAP Button
                    else if (isInsideCircle(vx, vy, trapX, trapY, btnRadius * 1.2f)) {
                        gs.controls.isTrapPressed = true
                        gs.controls.isAttackPressed = false
                        gs.controls.isOverclockPressed = false
                    }
                    // Check OVERCLOCK Button
                    else if (isInsideCircle(vx, vy, ovrX, ovrY, btnRadius * 1.2f)) {
                        if (gs.overclockMeter >= 100f && !gs.isOverclocked) {
                            gs.controls.isOverclockPressed = true // Trigger trigger trigger!
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_SUP_CONFIRM, 150)
                        } else {
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
                        }
                    }
                    // Check ATTACK Button (Fallback for bottom right area)
                    else if (isInsideCircle(vx, vy, atkX, atkY, btnRadius * 1.2f)) {
                        gs.controls.isAttackPressed = true
                        gs.controls.isOverclockPressed = false
                        gs.controls.isTrapPressed = false
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val mx = e.getX(i) - offsetX
                    val my = e.getY(i) - offsetY
                    val id = e.getPointerId(i)

                    if (id == joyPointerId) {
                        val dx = mx - gs.touch.joyBaseX
                        val dy = my - gs.touch.joyBaseY
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > joyMaxRadius) {
                            val ratio = joyMaxRadius / dist
                            gs.touch.joyKnobX = gs.touch.joyBaseX + dx * ratio
                            gs.touch.joyKnobY = gs.touch.joyBaseY + dy * ratio
                            gs.controls.moveDirX = dx / dist
                            gs.controls.moveDirY = dy / dist
                        } else {
                            gs.touch.joyKnobX = mx
                            gs.touch.joyKnobY = my
                            gs.controls.moveDirX = if (dist > 0) dx / joyMaxRadius else 0f
                            gs.controls.moveDirY = if (dist > 0) dy / joyMaxRadius else 0f
                        }
                    } else if (mx > targetW / 2f && my > targetH / 2f) {
                        // DYNAMIC COMBAT SLIDING
                        if (isInsideCircle(mx, my, trapX, trapY, btnRadius * 1.2f)) {
                            gs.controls.isTrapPressed = true
                            gs.controls.isAttackPressed = false
                            gs.controls.isOverclockPressed = false
                        } else if (isInsideCircle(mx, my, ovrX, ovrY, btnRadius * 1.2f)) {
                            gs.controls.isOverclockPressed = true
                            gs.controls.isAttackPressed = false
                            gs.controls.isTrapPressed = false
                        } else if (isInsideCircle(mx, my, atkX, atkY, btnRadius * 1.2f)) {
                            gs.controls.isAttackPressed = true
                            gs.controls.isOverclockPressed = false
                            gs.controls.isTrapPressed = false
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                // 1. Joystick pointer check
                if (pointerId == joyPointerId) {
                    joyPointerId = -1
                    gs.controls.isMoveJoyActive = false
                    gs.controls.moveDirX = 0f
                    gs.controls.moveDirY = 0f
                }

                // --- FIX: STICKING BUTTONS REMOVAL ---
                // Agar ACTION_UP (poori finger up) ya CANCEL event hua,
                // ya fir jo finger uthi hai wo right-half (combat side) par thi,
                // toh bina kisi condition ke attack aur trap states ko clear (false) karo!
                val upX = e.getX(pointerIndex) - offsetX
                if (upX > targetW / 2f || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gs.controls.isAttackPressed = false
                    gs.controls.isOverclockPressed = false
                    gs.controls.isTrapPressed = false
                }

                if (e.pointerCount <= 1 || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gs.isTouching = false
                }
            }
        }
        val returnVal = true
        return returnVal
    }

    private fun isInsideCircle(tx: Float, ty: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val dx = tx - cx
        val dy = ty - cy
        return dx * dx + dy * dy <= radius * radius
    }
}
