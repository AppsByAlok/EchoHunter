package com.appsbyalok.echohunter.input

import android.view.MotionEvent
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager

class TouchController(private val gs: GameState) {

    var onPauseClicked: (() -> Unit)? = null
    var onPulseTriggered: (() -> Unit)? = null

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

        val btnRadius = gs.hudLayout.btnRadius

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    gs.touch.moveTouchId = -1
                    gs.touch.attackTouchId = -1
                }

                if (vx < targetW / 2f) {
                    // MOVEMENT JOYSTICK (Raw State)
                    if (gs.touch.moveTouchId == -1) {
                        gs.touch.moveTouchId = pointerId
                        gs.controls.isMoveJoyActive = true
                        gs.touch.moveBaseX = vx
                        gs.touch.moveBaseY = vy
                        gs.touch.moveCurrentX = vx
                        gs.touch.moveCurrentY = vy
                    }
                } else {
                    // COMBAT SIDE (Raw State)
                    val atkX = gs.hudLayout.atkX; val atkY = gs.hudLayout.atkY
                    val ovrX = gs.hudLayout.ovrX; val ovrY = gs.hudLayout.ovrY
                    val trapX = gs.hudLayout.trapX; val trapY = gs.hudLayout.trapY
                    val pulseX = gs.hudLayout.pulseX; val pulseY = gs.hudLayout.pulseY
                    val pauseX = gs.hudLayout.pauseX; val pauseY = gs.hudLayout.pauseY

                    if (isInsideCircle(vx, vy, pauseX, pauseY, btnRadius * 1.5f)) {
                        onPauseClicked?.invoke()
                    } else if (isInsideCircle(vx, vy, pulseX, pulseY, btnRadius * 1.2f)) {
                        onPulseTriggered?.invoke()
                    } else if (isInsideCircle(vx, vy, ovrX, ovrY, btnRadius * 1.2f)) {
                        if (gs.overclockMeter >= 100f && !gs.isOverclocked) {
                            gs.controls.isOverclockPressed = true
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_SUP_CONFIRM, 150)
                        } else {
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
                        }
                    } else if (isInsideCircle(vx, vy, trapX, trapY, btnRadius * 1.2f)) {
                        gs.controls.isTrapPressed = true
                    } else if (isInsideCircle(vx, vy, atkX, atkY, btnRadius * 1.5f)) {
                        // ATTACK (Just store raw touch)
                        gs.touch.attackTouchId = pointerId
                        gs.controls.isAttackTouching = true
                        gs.controls.attackTouchX = vx
                        gs.controls.attackTouchY = vy
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val mx = e.getX(i) - offsetX
                    val my = e.getY(i) - offsetY
                    val id = e.getPointerId(i)

                    if (id == gs.touch.moveTouchId) {
                        gs.touch.moveCurrentX = mx
                        gs.touch.moveCurrentY = my
                    }
                    
                    if (id == gs.touch.attackTouchId) {
                        // Pass raw coordinates to ControlsState
                        gs.controls.attackTouchX = mx
                        gs.controls.attackTouchY = my
                    }
                }
            }


            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == gs.touch.moveTouchId) {
                    gs.touch.moveTouchId = -1
                    gs.controls.isMoveJoyActive = false
                    gs.controls.moveDirX = 0f; gs.controls.moveDirY = 0f
                }
                
                if (pointerId == gs.touch.attackTouchId) {
                    gs.touch.attackTouchId = -1
                    gs.controls.isAttackTouching = false
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gs.controls.isAttackTouching = false
                    gs.controls.isTrapPressed = false
                    gs.controls.isOverclockPressed = false
                    gs.touch.moveTouchId = -1
                    gs.touch.attackTouchId = -1
                }
            }
        }
        return true
    }

    private fun isInsideCircle(tx: Float, ty: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val dx = tx - cx; val dy = ty - cy
        return dx * dx + dy * dy <= radius * radius
    }
}
