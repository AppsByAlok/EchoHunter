package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import kotlin.math.atan2
import kotlin.math.sqrt

class InputSystem(private val gs: GameState) {

    fun update(dt: Float, scale: Float, enemySys: EnemySystem?) {
        val menuThreshold = scale * 0.18f // Increased to prevent accidental triggers
        val selectThreshold = scale * 0.28f 

        // --- ATTACK / WEAPON MENU ---
        if (gs.controls.isAttackTouching) {
            val adx = gs.controls.attackTouchX - gs.hudLayout.atkX
            val ady = gs.controls.attackTouchY - gs.hudLayout.atkY
            val aDist = sqrt(adx * adx + ady * ady)

            // FIX: Large threshold in manual mode to avoid conflicting with aiming swipes
            val dynamicThreshold = if (gs.controls.activeAttackMode == AttackMode.MANUAL_AIM) scale * 0.35f else menuThreshold

            if (aDist > dynamicThreshold) {
                gs.controls.isWeaponMenuOpen = true
                gs.controls.selectedWeaponIdx = getIndexByAngle(adx, ady, 3)
            } else {
                if (!gs.controls.isWeaponMenuOpen) gs.controls.selectedWeaponIdx = -1
            }
        } else {
            if (gs.controls.isWeaponMenuOpen) {
                if (gs.controls.selectedWeaponIdx != -1) {
                    gs.controls.activeAttackMode = AttackMode.values()[gs.controls.selectedWeaponIdx]
                }
                gs.controls.isWeaponMenuOpen = false
                gs.controls.selectedWeaponIdx = -1
            }
        }

        // --- TRAP MENU ---
        if (gs.controls.isTrapPressed) {
            val tdx = gs.controls.trapTouchX - gs.hudLayout.trapX
            val tdy = gs.controls.trapTouchY - gs.hudLayout.trapY
            val tDist = sqrt(tdx * tdx + tdy * tdy)

            if (tDist > menuThreshold) {
                gs.controls.isTrapMenuOpen = true
                gs.controls.selectedTrapIdx = getIndexByAngle(tdx, tdy, 3)
            } else {
                gs.controls.selectedTrapIdx = -1
            }
        } else {
            if (gs.controls.isTrapMenuOpen) {
                if (gs.controls.selectedTrapIdx != -1) {
                    gs.controls.currentTrap = gs.controls.selectedTrapIdx
                    gs.controls.isTrapPressed = false // Safety: prevent trigger on release
                }
                gs.controls.isTrapMenuOpen = false
                gs.controls.selectedTrapIdx = -1
            }
        }

        // --- SONAR MENU ---
        if (gs.controls.isSonarPressed) {
            val sdx = gs.controls.sonarTouchX - gs.hudLayout.pulseX
            val sdy = gs.controls.sonarTouchY - gs.hudLayout.pulseY
            val sDist = sqrt(sdx * sdx + sdy * sdy)

            if (sDist > menuThreshold) {
                gs.controls.isSonarMenuOpen = true
                gs.controls.selectedSonarIdx = getIndexByAngle(sdx, sdy, 2)
            } else {
                gs.controls.selectedSonarIdx = -1
            }
        } else {
            if (gs.controls.isSonarMenuOpen) {
                if (gs.controls.selectedSonarIdx != -1) {
                    gs.controls.isAutoSonarLocked = (gs.controls.selectedSonarIdx == 1)
                    gs.controls.isSonarPressed = false // Safety: prevent pulse on release
                }
                gs.controls.isSonarMenuOpen = false
                gs.controls.selectedSonarIdx = -1
            }
        }

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

    private fun getIndexByAngle(dx: Float, dy: Float, count: Int): Int {
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        // Convert angle to 0-360 range
        val normalized = (angle + 360) % 360
        
        // Shifted Arc distribution: 160 to 280 degrees (Safe for Right Edge)
        val startArc = 160f
        val arcRange = 120f
        
        // Exclude the right/bottom area
        if (normalized < 100 || normalized > 340) return -1 
        
        // Map 160...280 to 0...count-1
        var relativeAngle = normalized - startArc
        if (relativeAngle < 0) relativeAngle += 360
        
        val idx = (relativeAngle / (arcRange / (count - 0.5f))).toInt().coerceIn(0, count - 1)
        return idx
    }

    private fun handleDefaultAttack() {
        gs.controls.attackRequested = gs.controls.isAttackTouching && !gs.controls.isWeaponMenuOpen
        gs.controls.aimDirX = if (gs.lastFacingX == 0f && gs.lastFacingY == 0f) 1f else gs.lastFacingX
        gs.controls.aimDirY = gs.lastFacingY
        gs.controls.attackPullDist = 0f
    }

    private fun handleAutoAim(enemySys: EnemySystem?) {
        gs.controls.attackRequested = gs.controls.isAttackTouching && !gs.controls.isWeaponMenuOpen
        gs.controls.attackPullDist = 0f

        if (enemySys != null) {
            // Update boss lock timer
            if (gs.bossLockTimer > 0f) {
                gs.bossLockTimer -= gs.lastDt // Use a delta time if available, or just subtract a small amount
            }

            // Priority 0: Forced Boss Lock (1 second when boss first appears)
            if (gs.bossActive && gs.bossLockTimer > 0f) {
                val dx = gs.bossX - gs.px
                val dy = gs.bossY - gs.py
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0f) {
                    gs.controls.aimDirX = dx / dist
                    gs.controls.aimDirY = dy / dist
                    return
                }
            }

            // Target search logic
            var bestTargetIdx = -1
            var bestScore = Float.MAX_VALUE

            // Define ranges (in world units)
            val normalProximityThreshold = 600f
            val bossProximityThreshold = 1200f // Priority targets can be aimed from further away
            val immediateThreatThreshold = 250f // If something is this close, aim it regardless of priority

            // 1. Evaluate Boss
            if (gs.bossActive) {
                val dx = gs.bossX - gs.px
                val dy = gs.bossY - gs.py
                val d2 = dx * dx + dy * dy
                val dist = sqrt(d2)

                if (dist < immediateThreatThreshold) {
                    bestScore = d2 // Immediate threat priority
                    bestTargetIdx = -2 // -2 represents boss
                } else if (dist < bossProximityThreshold) {
                    // Score for boss is prioritized by subtracting a "priority bonus" from its distance
                    // This makes it "feel" closer to the auto-aim logic
                    val prioritizedDistSq = d2 * 0.4f // Multiplier < 1 means higher priority
                    if (prioritizedDistSq < bestScore) {
                        bestScore = prioritizedDistSq
                        bestTargetIdx = -2
                    }
                }
            }

            // 2. Evaluate Normal Enemies
            for (i in 0 until enemySys.n) {
                if (enemySys.ex[i] > -1000f) {
                    val dx = enemySys.ex[i] - gs.px
                    val dy = enemySys.ey[i] - gs.py
                    val d2 = dx * dx + dy * dy
                    val dist = sqrt(d2)

                    if (dist < immediateThreatThreshold) {
                        if (d2 < bestScore) {
                            bestScore = d2
                            bestTargetIdx = i
                        }
                    } else if (dist < normalProximityThreshold) {
                        var score = d2
                        // If it's a high value target (type 3), give it a priority bonus
                        if (enemySys.type[i] == 3) {
                            score *= 0.6f
                        }

                        if (score < bestScore) {
                            bestScore = score
                            bestTargetIdx = i
                        }
                    }
                }
            }

            // Apply best target
            if (bestTargetIdx == -2) { // Boss
                val dx = gs.bossX - gs.px
                val dy = gs.bossY - gs.py
                val dist = sqrt(dx * dx + dy * dy)
                gs.controls.aimDirX = dx / dist
                gs.controls.aimDirY = dy / dist
                return
            } else if (bestTargetIdx != -1) { // Normal Enemy
                val dx = enemySys.ex[bestTargetIdx] - gs.px
                val dy = enemySys.ey[bestTargetIdx] - gs.py
                val dist = sqrt(dx * dx + dy * dy)
                gs.controls.aimDirX = dx / dist
                gs.controls.aimDirY = dy / dist
                return
            }
        }
        // Fallback to last facing direction if no targets in range
        gs.controls.aimDirX = gs.lastFacingX
        gs.controls.aimDirY = gs.lastFacingY
    }

    private fun handleManualAim(scale: Float) {
        if (!gs.controls.isManualAimUnlocked) {
            gs.controls.attackRequested = gs.controls.isAttackTouching && !gs.controls.isWeaponMenuOpen
            return
        }

        if (gs.controls.manualAimActive) {
            val dx = gs.touch.manualAimCurrentX - gs.touch.manualAimBaseX
            val dy = gs.touch.manualAimCurrentY - gs.touch.manualAimBaseY
            val dist = sqrt(dx * dx + dy * dy)
            val joyMaxRadius = scale * 0.15f

            if (dist > 0.005f) {
                val ratio = if (dist > joyMaxRadius) joyMaxRadius / dist else 1f
                gs.touch.manualAimKnobX = gs.touch.manualAimBaseX + dx * ratio
                gs.touch.manualAimKnobY = gs.touch.manualAimBaseY + dy * ratio
                
                gs.controls.aimDirX = dx / dist
                gs.controls.aimDirY = dy / dist
                gs.controls.attackPullDist = (dist / joyMaxRadius).coerceIn(0f, 1f)
                
                // Lower fire deadzone (8% instead of 20%)
                gs.controls.attackRequested = dist > (joyMaxRadius * 0.08f)
            } else {
                gs.touch.manualAimKnobX = gs.touch.manualAimBaseX
                gs.touch.manualAimKnobY = gs.touch.manualAimBaseY
                gs.controls.attackRequested = false
            }
            
            gs.lastFacingX = gs.controls.aimDirX
            gs.lastFacingY = gs.controls.aimDirY
        } else {
            gs.touch.manualAimKnobX = gs.touch.manualAimBaseX
            gs.touch.manualAimKnobY = gs.touch.manualAimBaseY
            gs.controls.attackRequested = false
            gs.controls.attackPullDist = 0f
        }
    }
}
