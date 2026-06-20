package com.appsbyalok.echohunter.systems

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max
import kotlin.math.sqrt

// ArsenalSystem: Modular class handling all Weapons and Traps
class ArsenalSystem(private val gs: GameState, private val effectSys: EffectSystem) {

    fun update(dt: Float, scale: Float) {
        if (gs.trapCooldownTimer > 0f) gs.trapCooldownTimer -= dt

        if (gs.camoTimer > 0f) {
            gs.camoTimer -= dt
            if (gs.camoTimer <= 0f) gs.isCamouflaged = false
        }
        if (gs.decoyTimer > 0f) {
            gs.decoyTimer -= dt
            if (gs.decoyTimer <= 0f) gs.isDecoyActive = false
        }

        // Logic check for firing/sonar
        // Use the new attackRequested intent from InputSystem
        if (gs.controls.attackRequested && gs.attackCooldown <= 0f) {
            fireWeapon(scale)
        }
        
        if (gs.controls.isSonarPressed && gs.sonarTimer <= 0f) {
            deploySonar()
            gs.controls.isSonarPressed = false
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
        when (gs.controls.currentWeapon) {
            0 -> fireSingle(dx, dy, scale, 0)
            1 -> { // SHOTGUN
                fireSingle(dx, dy, scale, 1)
                fireSingle(dx - dy * 0.3f, dy + dx * 0.3f, scale, 1)
                fireSingle(dx + dy * 0.3f, dy - dx * 0.3f, scale, 1)
                gs.attackCooldown *= 1.8f
            }
            2 -> { // SNIPER
                fireSingle(dx, dy, scale * 2.5f, 2)
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
        gs.sonarTimer = 0.25f * UpgradeSystem.getPulseCooldownMultiplier()
        if (gs.isDarknessLevel) {
            gs.visionClarity = max(0.0f, gs.visionClarity - 0.25f)
        }
        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
        gs.globalSonarAlert = true
    }

    fun deployTrap() {
        if (gs.modInfinityTraps) {
            gs.trapCooldownTimer = 0f
        } else {
            gs.trapCooldownTimer = 8f // Traps have a long cooldown
        }
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)

        // DYNAMIC TRAP DEPLOYMENT
        when (gs.controls.currentTrap) {
            0 -> { // Camouflage (Invisibility)
                gs.isCamouflaged = true
                gs.camoTimer = 4f
            }
            1 -> { // Decoy Hologram
                gs.isDecoyActive = true
                gs.decoyX = gs.px
                gs.decoyY = gs.py
                gs.decoyTimer = 5f
            }
            2 -> { // EMP Mine
                gs.empMineActive = true
                gs.empMineX = gs.px
                gs.empMineY = gs.py
            }
        }
        gs.controls.isTrapPressed = false
    }
}
