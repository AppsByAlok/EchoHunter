package com.appsbyalok.echohunter.systems

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max
import kotlin.math.sqrt

// ArsenalSystem: Modular class handling all Weapons and Traps
class ArsenalSystem(private val gs: GameState, private val effectSys: EffectSystem, private val spawnerSys: SpawnerSystem, private val enemySys: EnemySystem) {

    fun update(dt: Float, scale: Float) {
        if (gs.trapCooldownTimer > 0f) gs.trapCooldownTimer -= dt

        val iterator = gs.activeTraps.iterator()
        while (iterator.hasNext()) {
            val trap = iterator.next()
            trap.timer -= dt
            if (trap.timer <= 0f) {
                iterator.remove()
            }
        }

        // Logic check for firing/sonar
        // Use the new attackRequested intent from InputSystem
        if (gs.controls.attackRequested && gs.attackCooldown <= 0f) {
            fireWeapon(scale)
        }
        
        // Manual Sonar or Auto-Sonar Lock
        val canPulse = gs.isDarknessLevel || com.appsbyalok.echohunter.data.StoryProtocol.isBlackoutActive
        if (canPulse && gs.sonarTimer <= 0f) {
            if (gs.controls.isSonarPressed || gs.controls.isAutoSonarLocked) {
                deploySonar()
                gs.controls.isSonarPressed = false
            }
        } else if (gs.controls.isSonarPressed) {
            // If not in darkness or still on CD, just consume the press
            gs.controls.isSonarPressed = false
        }

        if (gs.controls.trapRequested && gs.trapCooldownTimer <= 0f) {
            deployTrap(scale)
        }

        // --- NEW: TRAP EFFECT LOGIC ---
        for (trap in gs.activeTraps) {
            if (trap.type == 3) { // Stasis Pulse
                // This logic is better handled inside EnemySystem's movement loop
                // to avoid double iterations and provide access to enemy arrays.
            }
        }
    }

    fun fireWeapon(scale: Float) {
        var dx = gs.controls.aimDirX
        var dy = gs.controls.aimDirY

        if (dx == 0f && dy == 0f) {
            dx = 1f
            dy = 0f
        } else {
            val magnitude = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            dx /= magnitude
            dy /= magnitude
        }

        val baseCooldown = 0.25f
        gs.attackCooldown = baseCooldown * UpgradeSystem.getSpikeCooldownMultiplier()
        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 50)

        // Reset attack request immediately so we don't double fire in the same frame
        gs.controls.attackRequested = false

        // DYNAMIC WEAPON FIRE
        val multiShot = UpgradeSystem.getMultiShotCount()
        
        when (gs.controls.currentWeapon) {
            0 -> { // STANDARD
                fireSingle(dx, dy, scale, 0)
                for (i in 1..multiShot) {
                    val spread = i * 0.15f
                    fireSingle(dx - dy * spread, dy + dx * spread, scale, 0)
                }
            }
            1 -> { // SHOTGUN
                val extraPellets = multiShot * 2
                val totalPellets = 3 + extraPellets
                for (i in 0 until totalPellets) {
                    val spread = (i - totalPellets / 2f) * 0.25f
                    fireSingle(dx - dy * spread, dy + dx * spread, scale, 1)
                }
                gs.attackCooldown *= 1.8f
            }
            2 -> { // SNIPER
                fireSingle(dx, dy, scale * 2.5f, 2)
                if (multiShot >= 1) {
                    // Sniper gets a trailing second shot or narrow spread
                    fireSingle(dx - dy * 0.05f, dy + dx * 0.05f, scale * 2.5f, 2)
                }
                gs.attackCooldown *= 3.0f
            }
        }
        gs.localAttackAlert = true
    }

    private fun fireSingle(dx: Float, dy: Float, scale: Float, type: Int) {
        for (i in 0 until gs.maxSpikes) {
            if (!gs.spikeActive[i]) {
                gs.spikeActive[i] = true
                gs.spikeX[i] = gs.px
                gs.spikeY[i] = gs.py
                gs.spikeLife[i] = if (type == 2) 0.6f else 0.4f
                gs.spikeType[i] = type

                val speed = scale * 2.0f
                // Add player velocity to projectile for more natural physics
                gs.spikeVx[i] = dx * speed + (gs.controls.moveDirX * 0.5f * scale)
                gs.spikeVy[i] = dy * speed + (gs.controls.moveDirY * 0.5f * scale)
                break
            }
        }
    }

    private fun deploySonar() {
        gs.pulse = true
        gs.pulseR = 0f
        // Increased base cooldown from 0.25s to 3.0s to make upgrades meaningful
        val baseCD = 3.0f 
        gs.sonarTimer = baseCD * UpgradeSystem.getPulseCooldownMultiplier()
        if (gs.isDarknessLevel) {
            gs.visionClarity = max(0.0f, gs.visionClarity - 0.25f)
        }
        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
        gs.globalSonarAlert = true
    }

    fun deployTrap(scale: Float) {
        if (gs.modInfinityTraps) {
            gs.trapCooldownTimer = 0f
        } else {
            val baseCooldown = 8f
            gs.trapCooldownTimer = baseCooldown * UpgradeSystem.getTrapCooldownMultiplier()
        }
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)

        // DYNAMIC TRAP DEPLOYMENT
        val duration = when (gs.controls.currentTrap) {
            0 -> 4f
            1 -> 5f
            2 -> 8f
            3 -> 6f // Stasis Pulse
            4 -> 5f // Sonic Decoy
            else -> 5f
        }

        val newTrap = GameState.ActiveTrap(
            gs.controls.currentTrap,
            gs.px,
            gs.py,
            duration,
            duration
        )

        // Special handling for deployment logic
        when (newTrap.type) {
            0 -> { // Camouflage - Break existing aggro
                for (i in 0 until gs.activeTraps.size) {
                    if (gs.activeTraps[i].type == 0) {
                        gs.activeTraps.removeAt(i)
                        break
                    }
                }
                // Enemies in chase state lose target
                for (i in 0 until enemySys.n) {
                    // Note: We don't have direct access to enemySys here for state changes easily,
                    // but they will check isCamouflaged in their next updateBehavior call.
                }
            }
        }

        gs.activeTraps.add(newTrap)
        gs.controls.trapRequested = false
    }
}
