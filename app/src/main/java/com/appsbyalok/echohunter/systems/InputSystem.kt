package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.input.AttackMode
import kotlin.math.atan2
import kotlin.math.sqrt

class InputSystem(private val gs: GameState) {

    fun update(dt: Float, scale: Float, enemySys: EnemySystem?) {
        val menuThreshold = scale * 0.22f // Increased to prevent accidental triggers

        // --- ATTACK / WEAPON MENU ---
        if (gs.controls.isAttackTouching) {
            val adx = gs.controls.attackTouchX - gs.hudLayout.atkX
            val ady = gs.controls.attackTouchY - gs.hudLayout.atkY
            val aDist = sqrt(adx * adx + ady * ady)

            // Sniper Menu Block: If charging the sniper, we prevent the menu from popping up
            // unless the user really drags it out far (intentional cancel/switch).
            val isChargingSniper = gs.controls.currentWeapon == 2 && gs.controls.isSniperCharging
            val menuTriggerThreshold = if (isChargingSniper) scale * 0.55f else menuThreshold

            // FIX: Very large threshold in manual/directional modes to prevent accidental weapon menu triggers during aiming swipes
            val dynamicThreshold = if (gs.controls.activeAttackMode == AttackMode.MANUAL_AIM || 
                                       gs.controls.activeAttackMode == AttackMode.DIRECTIONAL) scale * 0.65f else menuTriggerThreshold

            if (aDist > dynamicThreshold) {
                val unlocked = com.appsbyalok.echohunter.data.SaveManager.unlockedWeapons
                if (unlocked.size > 1) {
                    gs.controls.isWeaponMenuOpen = true
                    gs.controls.selectedWeaponIdx = getIndexByAngle(adx, ady, unlocked.size)
                    // Cancel sniper charge if menu opens
                    gs.controls.isSniperCharging = false
                    gs.controls.sniperCharge = 0f
                }
            } else {
                if (!gs.controls.isWeaponMenuOpen) gs.controls.selectedWeaponIdx = -1
            }

            // SNIPER CHARGE LOGIC (Hold to charge)
            if (gs.controls.currentWeapon == 2 && !gs.controls.isWeaponMenuOpen) {
                gs.controls.isSniperCharging = true
                val chargeSpeed = 1.3f * com.appsbyalok.echohunter.data.UpgradeSystem.getSniperChargeSpeedMultiplier()
                gs.controls.sniperCharge = (gs.controls.sniperCharge + dt * chargeSpeed).coerceAtMost(1.5f) // Max 1.5x boost
            }
        } else {
            if (gs.controls.isWeaponMenuOpen) {
                val unlocked = com.appsbyalok.echohunter.data.SaveManager.unlockedWeapons
                val idx = gs.controls.selectedWeaponIdx
                if (idx != -1 && idx < unlocked.size) {
                    gs.controls.currentWeapon = unlocked[idx]
                    com.appsbyalok.echohunter.data.SaveManager.setActiveWeapon(gs.controls.currentWeapon)
                }
                gs.controls.isWeaponMenuOpen = false
                gs.controls.selectedWeaponIdx = -1
            } else if (gs.controls.attackTapQueued) {
                // QUICK SWITCH: Tap to cycle weapons if multiple are unlocked
                val unlocked = com.appsbyalok.echohunter.data.SaveManager.unlockedWeapons
                if (unlocked.size > 1) {
                    val currIdx = unlocked.indexOf(gs.controls.currentWeapon)
                    val nextIdx = (currIdx + 1) % unlocked.size
                    gs.controls.currentWeapon = unlocked[nextIdx]
                    com.appsbyalok.echohunter.data.SaveManager.setActiveWeapon(gs.controls.currentWeapon)
                    gs.controls.attackTapQueued = false 
                    // Reset sniper charge on switch
                    gs.controls.isSniperCharging = false
                    gs.controls.sniperCharge = 0f
                }
            }
        }

        // --- TRAP MENU ---
        if (gs.controls.isTrapPressed) {
            val tdx = gs.controls.trapTouchX - gs.hudLayout.trapX
            val tdy = gs.controls.trapTouchY - gs.hudLayout.trapY
            val tDist = sqrt(tdx * tdx + tdy * tdy)

            if (tDist > menuThreshold) {
                val unlocked = com.appsbyalok.echohunter.data.SaveManager.unlockedTraps
                if (unlocked.size > 1) {
                    gs.controls.isTrapMenuOpen = true
                    gs.controls.selectedTrapIdx = getIndexByAngle(tdx, tdy, unlocked.size)
                    gs.controls.trapRequested = false // Cancel deploy if we are selecting from menu
                }
            } else {
                gs.controls.selectedTrapIdx = -1
            }
        } else {
            if (gs.controls.isTrapMenuOpen) {
                val idx = gs.controls.selectedTrapIdx
                val unlocked = com.appsbyalok.echohunter.data.SaveManager.unlockedTraps
                if (idx != -1 && idx < unlocked.size) {
                    gs.controls.currentTrap = unlocked[idx]
                    gs.controls.trapRequested = false // Safety: prevent trigger on release
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
                gs.controls.moveDirX = if (dist > 0.001f) dx / joyMaxRadius else 0f
                gs.controls.moveDirY = if (dist > 0.001f) dy / joyMaxRadius else 0f
            }

            // Always update facing if there's any intentional movement tilt
            val movementDist = sqrt(gs.controls.moveDirX * gs.controls.moveDirX + gs.controls.moveDirY * gs.controls.moveDirY)
            if (movementDist > 0.05f) {
                gs.lastFacingX = gs.controls.moveDirX / movementDist
                gs.lastFacingY = gs.controls.moveDirY / movementDist
            }
        } else {
            gs.touch.moveKnobX = gs.touch.moveBaseX
            gs.touch.moveKnobY = gs.touch.moveBaseY
            gs.controls.moveDirX = 0f
            gs.controls.moveDirY = 0f
        }

        // 2. ATTACK LOGIC STRATEGY
        when (gs.controls.activeAttackMode) {
            AttackMode.DIRECTIONAL -> handleDirectionalAttack(scale)
            AttackMode.AUTO_AIM -> handleAutoAim(enemySys)
            AttackMode.MANUAL_AIM -> handleManualAim(scale)
        }
        gs.controls.attackTapQueued = false
    }

    private fun getIndexByAngle(dx: Float, dy: Float, count: Int): Int {
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        // Convert angle to 0-360 range
        val normalized = (angle + 360) % 360
        
        // Shifted Arc distribution: 140 to 310 degrees (Top-Left quadrant to Top-Right)
        val startArc = 140f
        val arcRange = 170f
        
        // Exclude the right/bottom area (Dead zone for thumb placement)
        if (normalized !in 110.0..340.0) return -1
        
        // Map 140...310 to 0...count-1
        var relativeAngle = normalized - startArc
        if (relativeAngle < 0) relativeAngle += 360
        
        val idx = (relativeAngle / (arcRange / (count))).toInt().coerceIn(0, count - 1)
        return idx
    }

    private fun handleDirectionalAttack(scale: Float) {
        val isSniper = gs.controls.currentWeapon == 2
        
        if (isSniper) {
            // Sniper: Release to fire if charged or Tap
            gs.controls.attackRequested = (!gs.controls.isAttackTouching && gs.controls.isSniperCharging) || gs.controls.attackTapQueued
        } else {
            // Others: Hold to fire
            gs.controls.attackRequested = (gs.controls.isAttackTouching || gs.controls.attackTapQueued) && !gs.controls.isWeaponMenuOpen
        }
        
        val dx = gs.touch.manualAimCurrentX - gs.touch.manualAimBaseX
        val dy = gs.touch.manualAimCurrentY - gs.touch.manualAimBaseY
        val dist = sqrt(dx * dx + dy * dy)
        val joyMaxRadius = scale * 0.15f
        // Manual override threshold: 25% of button radius
        val overrideThreshold = gs.hudLayout.btnRadius * 0.25f

        if (gs.controls.manualAimActive && dist > overrideThreshold) {
            // Manual Joystick Override
            val ratio = if (dist > joyMaxRadius) joyMaxRadius / dist else 1f
            gs.touch.manualAimKnobX = gs.touch.manualAimBaseX + dx * ratio
            gs.touch.manualAimKnobY = gs.touch.manualAimBaseY + dy * ratio

            gs.controls.aimDirX = dx / dist
            gs.controls.aimDirY = dy / dist
            
            // Sync facing for character sprite
            gs.lastFacingX = gs.controls.aimDirX
            gs.lastFacingY = gs.controls.aimDirY
        } else {
            // Default: Look where moving, OR last faced direction
            // We give priority to active movement for the firing direction
            val moveDist = sqrt(gs.controls.moveDirX * gs.controls.moveDirX + gs.controls.moveDirY * gs.controls.moveDirY)
            if (moveDist > 0.1f) {
                gs.controls.aimDirX = gs.controls.moveDirX / moveDist
                gs.controls.aimDirY = gs.controls.moveDirY / moveDist
            } else {
                gs.controls.aimDirX = if (gs.lastFacingX == 0f && gs.lastFacingY == 0f) 1f else gs.lastFacingX
                gs.controls.aimDirY = gs.lastFacingY
            }
            
            // Visuals for the attack button knob
            if (gs.controls.manualAimActive) {
                val clampDist = if (dist > joyMaxRadius) joyMaxRadius else dist
                val ratio = if (dist > 0.001f) clampDist / dist else 0f
                gs.touch.manualAimKnobX = gs.touch.manualAimBaseX + dx * ratio
                gs.touch.manualAimKnobY = gs.touch.manualAimBaseY + dy * ratio
            } else {
                gs.touch.manualAimKnobX = gs.touch.manualAimBaseX
                gs.touch.manualAimKnobY = gs.touch.manualAimBaseY
            }
        }
        gs.controls.attackPullDist = 0f
    }

    private fun handleAutoAim(enemySys: EnemySystem?) {
        val isSniper = gs.controls.currentWeapon == 2
        if (isSniper) {
            gs.controls.attackRequested = (!gs.controls.isAttackTouching && gs.controls.isSniperCharging) || gs.controls.attackTapQueued
        } else {
            gs.controls.attackRequested = (gs.controls.isAttackTouching || gs.controls.attackTapQueued) && !gs.controls.isWeaponMenuOpen
        }
        gs.controls.attackPullDist = 0f

        if (enemySys != null) {
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
            var bestTargetIdx = -1 // -1: none, -2: boss, -3: security compiler, >=0: enemy
            var bestScore = Float.MAX_VALUE
            var tX = 0f
            var tY = 0f

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
                    tX = gs.bossX; tY = gs.bossY
                } else if (dist < bossProximityThreshold) {
                    val prioritizedDistSq = d2 * 0.4f // Multiplier < 1 means higher priority
                    if (prioritizedDistSq < bestScore) {
                        bestScore = prioritizedDistSq
                        bestTargetIdx = -2
                        tX = gs.bossX; tY = gs.bossY
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
                            tX = enemySys.ex[i]; tY = enemySys.ey[i]
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
                            tX = enemySys.ex[i]; tY = enemySys.ey[i]
                        }
                    }
                }
            }

            // 3. Evaluate Security Compilers (Spawn Nodes)
            for (node in gs.spawnerNodes) {
                if (node.state == SpawnState.DESTROYED || node.visibility < 0.2f) continue
                
                val dx = node.x - gs.px
                val dy = node.y - gs.py
                val d2 = dx * dx + dy * dy
                val dist = sqrt(d2)

                if (dist < normalProximityThreshold) {
                    var score = d2
                    // Compilers are primary targets in Clean Sweep
                    if (gs.activeObjective is com.appsbyalok.echohunter.modes.CleanSweepObjective) {
                        score *= 0.5f 
                    } else {
                        score *= 5.0f // Low priority in other modes (further reduced)
                    }

                    if (score < bestScore) {
                        bestScore = score
                        bestTargetIdx = -3
                        tX = node.x; tY = node.y
                    }
                }
            }

            // Apply best target
            if (bestTargetIdx != -1) {
                val dx = tX - gs.px
                val dy = tY - gs.py
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0f) {
                    gs.controls.aimDirX = dx / dist
                    gs.controls.aimDirY = dy / dist
                    return
                }
            }
        }
        // Fallback to last facing direction if no targets in range
        gs.controls.aimDirX = gs.lastFacingX
        gs.controls.aimDirY = gs.lastFacingY
    }

    private fun handleManualAim(scale: Float) {
        val dx = gs.touch.manualAimCurrentX - gs.touch.manualAimBaseX
        val dy = gs.touch.manualAimCurrentY - gs.touch.manualAimBaseY
        val dist = sqrt(dx * dx + dy * dy)
        val joyMaxRadius = scale * 0.15f
        val isSniper = gs.controls.currentWeapon == 2

        if (gs.controls.manualAimActive) {
            // Lower deadzone for manual mode
            if (dist > 0.01f * scale) {
                val ratio = if (dist > joyMaxRadius) joyMaxRadius / dist else 1f
                gs.touch.manualAimKnobX = gs.touch.manualAimBaseX + dx * ratio
                gs.touch.manualAimKnobY = gs.touch.manualAimBaseY + dy * ratio
                
                gs.controls.aimDirX = dx / dist
                gs.controls.aimDirY = dy / dist
                gs.controls.attackPullDist = (dist / joyMaxRadius).coerceIn(0f, 1f)
                
                // Fire Logic
                if (isSniper) {
                    // Release is handled in onUp usually, but here we track if it *should* fire
                    // Manual aim is a bit tricky with release-to-fire.
                    // For now, let's keep it simple: Release to fire is handled by the !isAttackTouching check in update.
                    gs.controls.attackRequested = false // Will be set by release logic
                } else {
                    // Fire when pulled past 15% (Hold to fire)
                    gs.controls.attackRequested = dist > (joyMaxRadius * 0.15f)
                }
                
                // Update facing
                gs.lastFacingX = gs.controls.aimDirX
                gs.lastFacingY = gs.controls.aimDirY
            } else {
                gs.touch.manualAimKnobX = gs.touch.manualAimBaseX
                gs.touch.manualAimKnobY = gs.touch.manualAimBaseY
                gs.controls.attackRequested = false
                
                // Fallback to movement-based facing when not aiming
                gs.controls.aimDirX = gs.lastFacingX
                gs.controls.aimDirY = gs.lastFacingY
            }
        } else {
            gs.touch.manualAimKnobX = gs.touch.manualAimBaseX
            gs.touch.manualAimKnobY = gs.touch.manualAimBaseY
            
            if (isSniper) {
                // Sniper in manual mode: ONLY fire on release (if it was charging) or tap
                gs.controls.attackRequested = (gs.controls.isSniperCharging && !gs.controls.isAttackTouching) || gs.controls.attackTapQueued
            } else {
                gs.controls.attackRequested = gs.controls.attackTapQueued
            }
            gs.controls.attackPullDist = 0f
            
            // Always sync aimDir with lastFacing when idle
            gs.controls.aimDirX = gs.lastFacingX
            gs.controls.aimDirY = gs.lastFacingY
        }
    }
}
