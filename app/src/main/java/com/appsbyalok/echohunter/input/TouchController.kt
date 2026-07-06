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

                    // 1. Check for Buttons First (Priority)
                    if (isInsideCircle(vx, vy, pauseX, pauseY, btnRadius * 1.5f)) {
                        onPauseClicked?.invoke()
                        return true
                    } else if (isInsideCircle(vx, vy, pulseX, pulseY, btnRadius * 1.1f)) {
                        if (gs.isDarknessLevel || com.appsbyalok.echohunter.data.StoryProtocol.isBlackoutActive) {
                            gs.touch.sonarTouchId = pointerId
                            gs.controls.isSonarPressed = true
                            gs.controls.sonarTouchX = vx
                            gs.controls.sonarTouchY = vy
                            return true
                        }
                    } else if (isInsideCircle(vx, vy, ovrX, ovrY, btnRadius * 1.1f)) {
                        if (gs.overclockMeter >= 100f && !gs.isOverclocked) {
                            gs.controls.isOverclockPressed = true
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_SUP_CONFIRM, 150)
                        } else {
                            EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
                        }
                        return true
                    } else if (isInsideCircle(vx, vy, trapX, trapY, btnRadius * 1.1f)) {
                        gs.touch.trapTouchId = pointerId
                        gs.controls.isTrapPressed = true
                        gs.controls.trapTouchX = vx
                        gs.controls.trapTouchY = vy
                        return true
                    } else if (isInsideCircle(vx, vy, atkX, atkY, btnRadius * 1.2f)) {
                        gs.touch.attackTouchId = pointerId
                        gs.controls.isAttackTouching = true
                        gs.controls.attackTouchX = vx
                        gs.controls.attackTouchY = vy
                        
                        // FIX: Only trigger manual aim IF we are already in Manual Mode
                        if (gs.controls.activeAttackMode == AttackMode.MANUAL_AIM) {
                            gs.touch.manualAimTouchId = pointerId
                            gs.controls.manualAimActive = true
                            gs.touch.manualAimBaseX = atkX
                            gs.touch.manualAimBaseY = atkY
                            gs.touch.manualAimCurrentX = vx
                            gs.touch.manualAimCurrentY = vy
                        }
                        return true
                    }

                    // 2. Fallback to Manual Aim Joystick (if in manual mode AND NOT hitting another button)
                    if (gs.controls.activeAttackMode == AttackMode.MANUAL_AIM &&
                        gs.hudLayout.manualAimRect.contains(vx, vy)) {

                        // Extra safety: Don't steal if we are already touching something else here
                        if (gs.touch.attackTouchId == -1 && gs.touch.trapTouchId == -1 && gs.touch.sonarTouchId == -1) {
                            gs.touch.manualAimTouchId = pointerId
                            gs.controls.manualAimActive = true
                            gs.touch.manualAimBaseX = vx
                            gs.touch.manualAimBaseY = vy
                            gs.touch.manualAimCurrentX = vx
                            gs.touch.manualAimCurrentY = vy
                            return true
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val mx = e.getX(i) - offsetX
                    val my = e.getY(i) - offsetY
                    val id = e.getPointerId(i)

                    if (id == gs.touch.manualAimTouchId && gs.controls.manualAimActive) {
                        gs.touch.manualAimCurrentX = mx
                        gs.touch.manualAimCurrentY = my
                    }

                    if (id == gs.touch.moveTouchId) {
                        gs.touch.moveCurrentX = mx
                        gs.touch.moveCurrentY = my
                    }

                    if (id == gs.touch.attackTouchId) {
                        gs.controls.attackTouchX = mx
                        gs.controls.attackTouchY = my
                    }
                    if (id == gs.touch.trapTouchId) {
                        gs.controls.trapTouchX = mx
                        gs.controls.trapTouchY = my
                    }
                    if (id == gs.touch.sonarTouchId) {
                        gs.controls.sonarTouchX = mx
                        gs.controls.sonarTouchY = my
                    }
                }
            }


            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == gs.touch.manualAimTouchId) {
                    gs.controls.manualAimActive = false
                    gs.touch.manualAimTouchId = -1
                }

                if (pointerId == gs.touch.moveTouchId) {
                    gs.touch.moveTouchId = -1
                    gs.controls.isMoveJoyActive = false
                    gs.controls.moveDirX = 0f; gs.controls.moveDirY = 0f
                }
                
                if (pointerId == gs.touch.attackTouchId) {
                    gs.touch.attackTouchId = -1
                    gs.controls.isAttackTouching = false
                }
                if (pointerId == gs.touch.trapTouchId) {
                    gs.touch.trapTouchId = -1
                    gs.controls.isTrapPressed = false
                }
                if (pointerId == gs.touch.sonarTouchId) {
                    gs.touch.sonarTouchId = -1
                    gs.controls.isSonarPressed = false
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gs.controls.isAttackTouching = false
                    gs.controls.isTrapPressed = false
                    gs.controls.isSonarPressed = false
                    gs.controls.isOverclockPressed = false
                    gs.touch.moveTouchId = -1
                    gs.touch.attackTouchId = -1
                    gs.touch.trapTouchId = -1
                    gs.touch.sonarTouchId = -1
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
