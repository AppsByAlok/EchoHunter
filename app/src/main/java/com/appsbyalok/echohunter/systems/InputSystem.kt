package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import kotlin.math.sqrt

class InputSystem(private val gs: GameState) {

    fun update(dt: Float, scale: Float, enemySys: EnemySystem?) {
        // 1. MOVEMENT JOYSTICK LOGIC
        if (gs.controls.isMoveJoyActive) {
            val dx = gs.touch.moveCurrentX - gs.touch.moveBaseX
            val dy = gs.touch.moveCurrentY - gs.touch.moveBaseY
            val dist = sqrt(dx * dx + dy * dy)
            val joyMaxRadius = scale * 0.15f

            if (dist > joyMaxRadius) {
                val ratio = joyMaxRadius / dist
                gs.touch.moveKnobX = gs.touch.moveBaseX + dx * ratio
                gs.touch.moveKnobY = gs.touch.moveBaseY + dy * ratio
                gs.controls.moveDirX = dx / dist
                gs.controls.moveDirY = dy / dist
            } else {
                gs.touch.moveKnobX = gs.touch.moveCurrentX
                gs.touch.moveKnobY = gs.touch.moveCurrentY
                gs.controls.moveDirX = if (dist > 0) dx / joyMaxRadius else 0f
                gs.controls.moveDirY = if (dist > 0) dy / joyMaxRadius else 0f
            }

            gs.lastFacingX = gs.controls.moveDirX
            gs.lastFacingY = gs.controls.moveDirY
        } else {
            // Reset knob to base
            gs.touch.moveKnobX = gs.touch.moveBaseX
            gs.touch.moveKnobY = gs.touch.moveBaseY
        }

        // 2. ATTACK LOGIC STRATEGY
        when (gs.controls.activeAttackMode) {
            AttackMode.DEFAULT -> handleDefaultAttack()
            AttackMode.AUTO_AIM -> handleAutoAim(enemySys)
            AttackMode.MANUAL_AIM -> handleManualAim(scale)
        }
    }

    private fun handleDefaultAttack() {
        // Classic behavior: Fire in facing direction on tap/hold
        gs.controls.attackRequested = gs.controls.isAttackTouching
        
        // Aim is always where we are facing
        gs.controls.aimDirX = if (gs.lastFacingX == 0f && gs.lastFacingY == 0f) 1f else gs.lastFacingX
        gs.controls.aimDirY = gs.lastFacingY
        gs.controls.attackPullDist = 0f
    }

    private fun handleAutoAim(enemySys: EnemySystem?) {
        gs.controls.attackRequested = gs.controls.isAttackTouching
        gs.controls.attackPullDist = 0f

        if (enemySys != null) {
            var minDist = Float.MAX_VALUE
            var targetIdx = -1

            for (i in 0 until enemySys.n) {
                if (enemySys.ex[i] > -1000f) {
                    val dx = enemySys.ex[i] - gs.px
                    val dy = enemySys.ey[i] - gs.py
                    val d2 = dx * dx + dy * dy
                    if (d2 < minDist) {
                        minDist = d2
                        targetIdx = i
                    }
                }
            }

            if (targetIdx != -1) {
                val dx = enemySys.ex[targetIdx] - gs.px
                val dy = enemySys.ey[targetIdx] - gs.py
                val dist = sqrt(dx * dx + dy * dy)
                gs.controls.aimDirX = dx / dist
                gs.controls.aimDirY = dy / dist
                return
            }
        }
        // Fallback to default if no enemy
        gs.controls.aimDirX = gs.lastFacingX
        gs.controls.aimDirY = gs.lastFacingY
    }

    private fun handleManualAim(scale: Float) {
        if (gs.controls.isAttackTouching) {
            val dx = gs.controls.attackTouchX - gs.hudLayout.atkX
            val dy = gs.controls.attackTouchY - gs.hudLayout.atkY
            val dist = sqrt(dx * dx + dy * dy)
            val joyMaxR = scale * 0.15f

            if (dist > 0) {
                gs.controls.aimDirX = dx / dist
                gs.controls.aimDirY = dy / dist
                gs.controls.attackPullDist = (dist / joyMaxR).coerceIn(0f, 1f)
                
                // Intent: If pulled more than 20%, it's a directional aim
                // If just tapped (dist very small), it fires forward
                gs.controls.attackRequested = true 
                
                // Override movement facing while aiming
                gs.lastFacingX = gs.controls.aimDirX
                gs.lastFacingY = gs.controls.aimDirY
            }
        } else {
            gs.controls.attackRequested = false
            gs.controls.attackPullDist = 0f
        }
    }
}
