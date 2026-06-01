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
        gameScale: Float,
        targetW: Float,
        targetH: Float,
        scale: Float
    ): Boolean {
        if (gs.state != 1 && gs.state != 8) return true

        val action = e.actionMasked
        val pointerIndex = e.actionIndex
        val pointerId = e.getPointerId(pointerIndex)

        val vx = (e.getX(pointerIndex) - offsetX) / gameScale
        val vy = (e.getY(pointerIndex) - offsetY) / gameScale

        gs.lastTouchX = vx
        gs.lastTouchY = vy

        val joyMaxRadius = scale * 0.15f

        // --- 100% PERFECT HITBOX MATCH ---
        // Reads directly from what GameView calculated for the screen!
        val btnRadius = gs.uiBtnRadius
        val atkX = gs.uiAtkX; val atkY = gs.uiAtkY
        val ovrX = gs.uiOvrX; val ovrY = gs.uiOvrY
        val trapX = gs.uiTrapX; val trapY = gs.uiTrapY
        val pulseX = gs.uiPulseX; val pulseY = gs.uiPulseY
        val pauseX = gs.uiPauseX; val pauseY = gs.uiPauseY

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
                        gs.isJoyActive = true
                        gs.joyBaseX = vx
                        gs.joyBaseY = vy
                        gs.joyKnobX = vx
                        gs.joyKnobY = vy
                        gs.joyDirX = 0f
                        gs.joyDirY = 0f
                    }
                } else {
                    // Check Pause Button First (Top Right)
                    if (isInsideCircle(vx, vy, pauseX, pauseY, btnRadius * 1.5f)) {
                        onPauseClicked?.invoke()
                    }
                    // Check PULSE Button
                    else if (isInsideCircle(vx, vy, pulseX, pulseY, btnRadius * 1.5f)) {
                        onPulseTriggered?.invoke()
                    }
                    // Check TRAP Button
                    else if (isInsideCircle(vx, vy, trapX, trapY, btnRadius * 1.5f)) {
                        gs.isTrapPressed = true
                        gs.isAttackPressed = false
                        gs.isOverclockPressed = false
                    }
                    // Check OVERCLOCK Button
                    else if (isInsideCircle(vx, vy, gs.uiOvrX, gs.uiOvrY, btnRadius * 1.5f)) {
                        if (gs.overclockMeter >= 100f && !gs.isOverclocked) {
                            gs.isOverclockPressed = true // Trigger trigger trigger!
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_SUP_CONFIRM, 150)
                        } else {
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
                        }
                    }
                    // Check ATTACK Button (Fallback for bottom right area)
                    else if (isInsideCircle(vx, vy, atkX, atkY, btnRadius * 1.5f)) {
                        gs.isAttackPressed = true
                        gs.isOverclockPressed = false
                        gs.isTrapPressed = false
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val mx = (e.getX(i) - offsetX) / gameScale
                    val my = (e.getY(i) - offsetY) / gameScale
                    val id = e.getPointerId(i)

                    if (id == joyPointerId) {
                        val dx = mx - gs.joyBaseX
                        val dy = my - gs.joyBaseY
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > joyMaxRadius) {
                            val ratio = joyMaxRadius / dist
                            gs.joyKnobX = gs.joyBaseX + dx * ratio
                            gs.joyKnobY = gs.joyBaseY + dy * ratio
                            gs.joyDirX = dx / dist
                            gs.joyDirY = dy / dist
                        } else {
                            gs.joyKnobX = mx
                            gs.joyKnobY = my
                            gs.joyDirX = if (dist > 0) dx / joyMaxRadius else 0f
                            gs.joyDirY = if (dist > 0) dy / joyMaxRadius else 0f
                        }
                    } else if (mx > targetW / 2f && my > targetH / 2f) {
                        // DYNAMIC COMBAT SLIDING
                        if (isInsideCircle(mx, my, trapX, trapY, btnRadius * 1.5f)) {
                            gs.isTrapPressed = true
                            gs.isAttackPressed = false
                            gs.isOverclockPressed = false
                        } else if (isInsideCircle(mx, my, ovrX, ovrY, btnRadius * 1.5f)) {
                            gs.isOverclockPressed = true
                            gs.isAttackPressed = false
                            gs.isTrapPressed = false
                        } else if (isInsideCircle(mx, my, atkX, atkY, btnRadius * 1.5f)) {
                            gs.isAttackPressed = true
                            gs.isOverclockPressed = false
                            gs.isTrapPressed = false
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                // 1. Joystick pointer check
                if (pointerId == joyPointerId) {
                    joyPointerId = -1
                    gs.isJoyActive = false
                    gs.joyDirX = 0f
                    gs.joyDirY = 0f
                }

                // --- FIX: STICKING BUTTONS REMOVAL ---
                // Agar ACTION_UP (poori finger up) ya CANCEL event hua,
                // ya fir jo finger uthi hai wo right-half (combat side) par thi,
                // toh bina kisi condition ke attack aur trap states ko clear (false) karo!
                val upX = (e.getX(pointerIndex) - offsetX) / gameScale
                if (upX > targetW / 2f || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gs.isAttackPressed = false
                    gs.isOverclockPressed = false
                    gs.isTrapPressed = false
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