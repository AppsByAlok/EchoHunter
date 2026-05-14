package com.appsbyalok.echohunter.input

import android.view.MotionEvent
import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt

class TouchController(private val gs: GameState) {

    var onPauseClicked: (() -> Unit)? = null
    var onPulseTriggered: (() -> Unit)? = null
    var onAttackTriggered: (() -> Unit)? = null
    var onOverclockTriggered: (() -> Unit)? = null

    // Track which finger (pointerId) is doing what
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
        // We only handle gameplay touches
        if (gs.state != 1 && gs.state != 8) return true

        val action = e.actionMasked
        val pointerIndex = e.actionIndex
        val pointerId = e.getPointerId(pointerIndex)

        // Convert raw touch to our 1920x1080 Virtual Resolution
        val vx = (e.getX(pointerIndex) - offsetX) / gameScale
        val vy = (e.getY(pointerIndex) - offsetY) / gameScale

        // --- Layout Definitions ---
        val joyMaxRadius = scale * 0.15f
        val edgeMargin = scale * 0.05f
        val pauseSize = scale * 0.1f
        val isClickingPause = vx > targetW - edgeMargin - pauseSize && vy < edgeMargin + pauseSize

        // Right side buttons (Radius & Positions)
        val btnRadius = scale * 0.08f
        val btnSpaceX = targetW - scale * 0.15f
        val btnPulseY = targetH - scale * 0.15f
        val btnAttackX = targetW - scale * 0.32f
        val btnAttackY = targetH - scale * 0.22f
        val btnOcX = targetW - scale * 0.12f
        val btnOcY = targetH - scale * 0.38f

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                gs.isTouching = true

                if (isClickingPause) {
                    onPauseClicked?.invoke()
                    return true
                }

                // RIGHT SIDE TOUCH (Buttons)
                if (vx > targetW / 2f) {
                    // Check Pulse
                    if (isInsideCircle(vx, vy, btnSpaceX, btnPulseY, btnRadius)) {
                        onPulseTriggered?.invoke()
                    }
                    // Check Attack (Malware Spike)
                    else if (isInsideCircle(vx, vy, btnAttackX, btnAttackY, btnRadius)) {
                        gs.isAttackPressed = true
                        onAttackTriggered?.invoke()
                    }
                    // Check Overclock
                    else if (isInsideCircle(vx, vy, btnOcX, btnOcY, btnRadius)) {
                        gs.isOverclockPressed = true
                        onOverclockTriggered?.invoke()
                    }
                }
                // LEFT SIDE TOUCH (Joystick)
                else {
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
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Multi-touch Move Loop
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val mx = (e.getX(i) - offsetX) / gameScale
                    val my = (e.getY(i) - offsetY) / gameScale

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
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == joyPointerId) {
                    joyPointerId = -1
                    gs.isJoyActive = false
                    gs.joyDirX = 0f
                    gs.joyDirY = 0f
                }

                // If lifted right side buttons
                if (vx > targetW / 2f) {
                    gs.isAttackPressed = false
                    gs.isOverclockPressed = false
                }

                if (e.pointerCount == 1) { // Last finger lifted
                    gs.isTouching = false
                }
            }
        }
        return true
    }

    private fun isInsideCircle(tx: Float, ty: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val dx = tx - cx
        val dy = ty - cy
        return (dx * dx + dy * dy) <= (radius * radius)
    }
}