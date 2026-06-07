package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt

// Blueprint for Enemy Brains
interface IEnemyBehavior {
    fun updateBehavior(i: Int, dt: Float, gs: GameState, enemySys: EnemySystem, ai: EnemyAI, targetW: Float, targetH: Float, scale: Float)
}

// 1. PATROL BEHAVIOR (Yellow - Investigates sounds)
object PatrolBehavior : IEnemyBehavior {
    override fun updateBehavior(i: Int, dt: Float, gs: GameState, enemySys: EnemySystem, ai: EnemyAI, targetW: Float, targetH: Float, scale: Float) {
        val targetX = if (gs.isDecoyActive) gs.decoyX else gs.px
        val targetY = if (gs.isDecoyActive) gs.decoyY else gs.py
        val tdx = targetX - enemySys.ex[i]
        val tdy = targetY - enemySys.ey[i]
        val td2 = tdx * tdx + tdy * tdy

        val hitDistSq = (scale * 0.045f) * (scale * 0.045f)
        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.4f)
        val hitByPulse = (gs.pulse && td2 in gs.innerRSq..gs.outerRSq)

        val heardSound = hitByPulse || (gs.localAttackAlert && td2 < (scale * 1.5f) * (scale * 1.5f))

        if (heardSound && (enemySys.eState[i] != 2 || enemySys.investigateTimer[i] < 2.5f)) {
            enemySys.eState[i] = 2
            enemySys.invX[i] = gs.px
            enemySys.invY[i] = gs.py
            enemySys.investigateTimer[i] = 3.0f
            if (gs.gridMap != null) ai.updateAlertHeatMap(gs, gs.px, gs.py)
        }

        if (enemySys.eState[i] == 2) {
            val inLoS = ai.hasLineOfSight(enemySys.ex[i], enemySys.ey[i], gs.px, gs.py, gs)
            if (inLoS && td2 < hitDistSq * 50f) {
                // Transform to Hunter
                enemySys.type[i] = 1
                enemySys.enemyBrains[i] = HunterBehavior
                enemySys.eState[i] = 1
                return
            }

            val idx = enemySys.invX[i] - enemySys.ex[i]
            val idy = enemySys.invY[i] - enemySys.ey[i]
            val distToInvSq = idx * idx + idy * idy
            if (distToInvSq > (scale * 0.05f) * (scale * 0.05f)) {
                if (gs.gridMap != null) {
                    val (nvx, nvy) = ai.steerByAlertHeatMap(enemySys.ex[i], enemySys.ey[i], enemySys.evx[i], enemySys.evy[i], speed * 0.7f, gs)
                    enemySys.evx[i] = nvx
                    enemySys.evy[i] = nvy
                } else {
                    val invDist = sqrt(distToInvSq)
                    val investigateSpeed = speed * 0.7f
                    enemySys.evx[i] = (enemySys.evx[i] * 0.8f) + ((idx / invDist) * investigateSpeed * 0.2f)
                    enemySys.evy[i] = (enemySys.evy[i] * 0.8f) + ((idy / invDist) * investigateSpeed * 0.2f)
                }
            } else {
                enemySys.investigateTimer[i] -= dt
                enemySys.evx[i] *= 0.8f; enemySys.evy[i] *= 0.8f
                if (enemySys.investigateTimer[i] <= 0f) enemySys.eState[i] = 0
            }
        }
    }
}

// 2. HUNTER BEHAVIOR (Red - Chases Player Aggressively)
object HunterBehavior : IEnemyBehavior {
    override fun updateBehavior(i: Int, dt: Float, gs: GameState, enemySys: EnemySystem, ai: EnemyAI, targetW: Float, targetH: Float, scale: Float) {
        val targetX = if (gs.isDecoyActive) gs.decoyX else gs.px
        val targetY = if (gs.isDecoyActive) gs.decoyY else gs.py
        val tdx = targetX - enemySys.ex[i]
        val tdy = targetY - enemySys.ey[i]
        val td2 = tdx * tdx + tdy * tdy

        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.4f)
        val inLoS = ai.hasLineOfSight(enemySys.ex[i], enemySys.ey[i], targetX, targetY, gs)

        if (inLoS) {
            enemySys.eState[i] = 1
            val eDist = sqrt(td2)
            val chaseSpeed = speed * (if (gs.isOverclocked && !gs.isDecoyActive) -0.5f else 1.2f)
            if (eDist > 0f) {
                enemySys.evx[i] = (enemySys.evx[i] * 0.9f) + ((tdx / eDist) * chaseSpeed * 0.1f)
                enemySys.evy[i] = (enemySys.evy[i] * 0.9f) + ((tdy / eDist) * chaseSpeed * 0.1f)
            }
        } else {
            enemySys.eState[i] = 2
        }

        if (enemySys.eState[i] == 2 && gs.gridMap != null) {
            val (nvx, nvy) = ai.steerByPlayerHeatMap(enemySys.ex[i], enemySys.ey[i], enemySys.evx[i], enemySys.evy[i], speed, gs)
            enemySys.evx[i] = nvx
            enemySys.evy[i] = nvy
        }
    }
}

// 3. KAMIKAZE BEHAVIOR (Bombers for Core Defense)
object KamikazeBehavior : IEnemyBehavior {
    override fun updateBehavior(i: Int, dt: Float, gs: GameState, enemySys: EnemySystem, ai: EnemyAI, targetW: Float, targetH: Float, scale: Float) {
        val speed = scale * (if (gs.difficulty == 0) 0.3f else 0.45f)
        // Relentlessly pathfind to the Core Map
        if (gs.gridMap != null) {
            val (nvx, nvy) = ai.steerByCoreHeatMap(enemySys.ex[i], enemySys.ey[i], enemySys.evx[i], enemySys.evy[i], speed, gs)
            enemySys.evx[i] = nvx
            enemySys.evy[i] = nvy
        }
    }
}
