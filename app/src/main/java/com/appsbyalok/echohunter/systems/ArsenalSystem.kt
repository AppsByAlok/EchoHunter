package com.appsbyalok.echohunter.systems

import android.media.ToneGenerator
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.EchoAudioManager
import kotlin.math.max

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
        if ((gs.isAttackPressed || gs.isAutoFireLocked) && gs.attackCooldown <= 0f) fireWeapon(scale)
        if ((gs.isSonarPressed || gs.isAutoSonarLocked) && gs.cooldownTimer <= 0f) deploySonar()

//        gs.isSonarPressed = false
    }

    fun fireWeapon(scale: Float) {
        val baseCooldown = 0.25f
        gs.attackCooldown = baseCooldown * UpgradeSystem.getSpikeCooldownMultiplier()

        val dirX = if (gs.lastFacingX == 0f && gs.lastFacingY == 0f) 1f else gs.lastFacingX
        val dirY = if (gs.lastFacingX == 0f && gs.lastFacingY == 0f) 0f else gs.lastFacingY

        EchoAudioManager.playSound(ToneGenerator.TONE_CDMA_PIP, 50)

        // DYNAMIC WEAPON FIRE
        when (gs.currentWeapon) {
            0 -> fireSingle(dirX, dirY, scale, 0) // Normal
            1 -> { // Shotgun (Spread)
                fireSingle(dirX, dirY, scale, 1)
                fireSingle(dirX + dirY * 0.2f, dirY + dirX * 0.2f, scale, 1)
                fireSingle(dirX - dirY * 0.2f, dirY - dirX * 0.2f, scale, 1)
                gs.attackCooldown *= 1.5f // Shotgun takes longer to reload
            }
            2 -> fireSingle(dirX, dirY, scale * 2.5f, 2) // Sniper (Fast)
        }

        gs.isAttackPressed = false
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
                gs.spikeVx[i] = dx * speed
                gs.spikeVy[i] = dy * speed

                effectSys.spawnParticles(gs.px, gs.py, 1, scale)
                break
            }
        }
    }

    private fun deploySonar() {
        gs.pulse = true
        gs.pulseR = 0f
        gs.cooldownTimer = 0.25f * UpgradeSystem.getPulseCooldownMultiplier()
        gs.visionClarity = max(0.0f, gs.visionClarity - 0.25f)
        EchoAudioManager.playSound(ToneGenerator.TONE_SUP_CONFIRM, 150)
        gs.globalSonarAlert = true
    }

    fun deployTrap() {
        gs.trapCooldownTimer = 8f // Traps have a long cooldown
        EchoAudioManager.playSound(ToneGenerator.TONE_PROP_ACK, 100)

        // DYNAMIC TRAP DEPLOYMENT
        when (gs.currentTrap) {
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
        gs.isTrapPressed = false
    }
}