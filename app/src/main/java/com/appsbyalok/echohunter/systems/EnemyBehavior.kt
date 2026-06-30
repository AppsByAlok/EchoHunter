package com.appsbyalok.echohunter.systems

import com.appsbyalok.echohunter.engine.GameState
import kotlin.math.sqrt

// =========================================================
// REGULAR ENEMY BEHAVIORS
// =========================================================

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
        
        // Intelligence Scaling: Reaction speed increases with level
        val reactionThreshold = scale * (1.5f + (gs.currentLevel * 0.02f))
        
        val speedMult = 1.0f + (gs.currentLevel * 0.005f)
        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.4f) * speedMult
        
        val hitByPulse = (gs.pulse && td2 in gs.innerRSq..gs.outerRSq)
        val heardSound = hitByPulse || (gs.localAttackAlert && td2 < reactionThreshold * reactionThreshold)

        if (heardSound && (enemySys.eState[i] != 2 || enemySys.investigateTimer[i] < 2.5f)) {
            enemySys.eState[i] = 2
            enemySys.invX[i] = gs.px
            enemySys.invY[i] = gs.py
            enemySys.investigateTimer[i] = 3.0f + (gs.currentLevel * 0.01f) // Stay alert longer
            if (gs.gridMap != null) ai.updateAlertHeatMap(gs, gs.px, gs.py)
        }

        if (enemySys.eState[i] == 2) {
            // High level enemies use predictive pathing (simulated by better LoS checks)
            val losRange = (scale * 7f) + (gs.currentLevel * 0.1f)
            val inLoS = td2 < losRange * losRange && ai.hasLineOfSight(enemySys.ex[i], enemySys.ey[i], gs.px, gs.py, gs)
            
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

        val speedMult = 1.0f + (gs.currentLevel * 0.008f)
        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.4f) * speedMult
        
        // Predictive Movement Logic: High level enemies anticipate player movement
        val leadAmount = (gs.currentLevel * 0.05f).coerceAtMost(1.0f)
        val pvx = gs.pvx
        val pvy = gs.pvy
        
        val predX = targetX + pvx * leadAmount
        val predY = targetY + pvy * leadAmount
        
        val ptdx = predX - enemySys.ex[i]
        val ptdy = predY - enemySys.ey[i]
        val ptd2 = ptdx * ptdx + ptdy * ptdy

        val inLoS = ai.hasLineOfSight(enemySys.ex[i], enemySys.ey[i], targetX, targetY, gs)

        if (inLoS) {
            enemySys.eState[i] = 1
            val eDist = sqrt(ptd2)
            val chaseSpeed = speed * (if (gs.isOverclocked && !gs.isDecoyActive) -0.5f else 1.2f)
            if (eDist > 0f) {
                enemySys.evx[i] = (enemySys.evx[i] * 0.9f) + ((ptdx / eDist) * chaseSpeed * 0.1f)
                enemySys.evy[i] = (enemySys.evy[i] * 0.9f) + ((ptdy / eDist) * chaseSpeed * 0.1f)
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
        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.35f)
        // Relentlessly pathfind to the Core Map
        if (gs.gridMap != null) {
            val (nvx, nvy) = ai.steerByCoreHeatMap(enemySys.ex[i], enemySys.ey[i], enemySys.evx[i], enemySys.evy[i], speed, gs)
            enemySys.evx[i] = nvx
            enemySys.evy[i] = nvy
        }
    }
}


// 4. TARGET BEHAVIOR (High-Value Target - Runs away when spotted)
object TargetBehavior : IEnemyBehavior {
    override fun updateBehavior(i: Int, dt: Float, gs: GameState, enemySys: EnemySystem, ai: EnemyAI, targetW: Float, targetH: Float, scale: Float) {
        val targetX = if (gs.isDecoyActive) gs.decoyX else gs.px
        val targetY = if (gs.isDecoyActive) gs.decoyY else gs.py
        val tdx = targetX - enemySys.ex[i]
        val tdy = targetY - enemySys.ey[i]
        val td2 = tdx * tdx + tdy * tdy

        val speedMult = 1.0f + (gs.currentLevel * 0.006f)
        val speed = scale * (if (gs.difficulty == 0) 0.25f else 0.35f) * speedMult
        
        val panicDist = scale * (0.8f + (gs.currentLevel * 0.01f))
        val inLoS = ai.hasLineOfSight(enemySys.ex[i], enemySys.ey[i], targetX, targetY, gs)

        // Agar player close hai aur line of sight me hai, toh panic mode (Run!)
        if (inLoS && td2 < panicDist * panicDist) {
            enemySys.eState[i] = 2 // Alert state
            val dist = sqrt(td2)
            if (dist > 0f) {
                enemySys.evx[i] = -(tdx / dist) * speed * 1.5f
                enemySys.evy[i] = -(tdy / dist) * speed * 1.5f
            }
        } else {
            if (enemySys.eState[i] != 2) {
                enemySys.eState[i] = 0
                if (kotlin.random.Random.nextFloat() < 0.02f) {
                    val angle = kotlin.random.Random.nextFloat() * 6.28f
                    enemySys.evx[i] = kotlin.math.cos(angle) * speed * 0.7f
                    enemySys.evy[i] = kotlin.math.sin(angle) * speed * 0.7f
                }
            }
        }
    }
}

// =========================================================
// BOSS BEHAVIORS (Modular Patterns for Boss Entities)
// =========================================================

interface IBossBehavior {
    val name: String
    val color: Int
    val sizeMult: Float
    val baseHpMult: Float
    val baseSpeedMult: Float
    val attackType: String
    val spawnMessage: String

    fun applyMovementPattern(vx: Float, vy: Float, dt: Float, gs: GameState, scale: Float): Pair<Float, Float>
    fun updateSpecial(dt: Float, gs: GameState, enemySys: EnemySystem, scale: Float)
}

// 0. ULTIMA (Type 5 - Level 100 Ultra Boss)
object UltimaBossBehavior : IBossBehavior {
    override val name = "ULTIMA_OVERLORD"
    override val color = 0xFFFF00FF.toInt() // Neon Magenta
    override val sizeMult = 1.8f
    override val baseHpMult = 5.0f
    override val baseSpeedMult = 1.2f
    override val attackType = "OMNI-HYBRID"
    override val spawnMessage = "CRITICAL: ADMINISTRATIVE ENTITY 'ULTIMA' DETECTED."

    override fun applyMovementPattern(vx: Float, vy: Float, dt: Float, gs: GameState, scale: Float): Pair<Float, Float> {
        val rageMult = if (gs.isBossRage) 1.5f else 1.0f
        return Pair(vx * 0.4f * rageMult, vy * 0.4f * rageMult)
    }
    // ... existing updateSpecial ...

    override fun updateSpecial(dt: Float, gs: GameState, enemySys: EnemySystem, scale: Float) {
        gs.bossAttackTimer += dt
        
        // Phase-based multi-attack logic
        val cycle = (gs.bossAttackTimer % 15f)
        
        when {
            cycle < 4f -> { // Phase 1: Drone Rain
                if (gs.bossAttackTimer % 1.5f < dt) {
                    enemySys.spawnSwarmIfNeeded(gs, scale * 10f, scale * 10f)
                }
            }
            cycle < 8f -> { // Phase 2: Glitch Storm
                gs.chromaticIntensity = 0.5f
                if (gs.bossAttackTimer % 0.5f < dt) {
                    gs.empFlashTimer = 0.2f
                    // Random projectile bursts
                    spawnProjectile(gs, scale, (Math.random() * 6.28f).toFloat())
                }
            }
            cycle < 12f -> { // Phase 3: Seismic Pulsar
                if (gs.bossAttackTimer % 3f < dt) {
                    gs.shakeAmount = scale * 0.3f
                    gs.shockwaveActive = true
                    gs.shockwaveX = gs.bossX
                    gs.shockwaveY = gs.bossY
                    gs.shockwaveR = 0f
                    // Global damage if not moving (hypothetical mechanic: stay in motion)
                }
            }
            else -> { // Phase 4: Recovery/Teleport
                if (cycle > 14.5f) {
                    val angle = (Math.random() * 6.28).toFloat()
                    gs.bossX = gs.px + kotlin.math.cos(angle) * scale * 1.5f
                    gs.bossY = gs.py + kotlin.math.sin(angle) * scale * 1.5f
                }
            }
        }
    }

    private fun spawnProjectile(gs: GameState, scale: Float, angle: Float) {
        for (i in 0 until gs.maxSpikes) {
            if (!gs.spikeActive[i]) {
                gs.spikeActive[i] = true
                gs.spikeX[i] = gs.bossX
                gs.spikeY[i] = gs.bossY
                gs.spikeLife[i] = 3.0f
                gs.spikeType[i] = 3
                val speed = scale * 1.2f
                gs.spikeVx[i] = kotlin.math.cos(angle) * speed
                gs.spikeVy[i] = kotlin.math.sin(angle) * speed
                break
            }
        }
    }
}

// 1. GUARDIAN (Type 0/1 - Seismic Jump & Tanky Movement)
object GuardianBossBehavior : IBossBehavior {
    override val name = "GUARDIAN_UNIT"
    override val color = 0xFF555555.toInt() // Iron Grey
    override val sizeMult = 1.4f
    override val baseHpMult = 2.5f
    override val baseSpeedMult = 0.7f
    override val attackType = "MELEE_SEISMIC"
    override val spawnMessage = "GUARDIAN: SECTOR LOCKDOWN INITIATED."

    override fun applyMovementPattern(vx: Float, vy: Float, dt: Float, gs: GameState, scale: Float): Pair<Float, Float> {
        // Guardian is slow but steady. Stops when jumping.
        if (gs.bossAttackState != 0) return Pair(0f, 0f)

        val rageMult = if (gs.isBossRage) 1.5f else 1.0f
        return Pair(vx * 0.8f, vy + kotlin.math.sin(gs.timeSinceStart * (3f * rageMult)) * scale * 0.3f)
    }

    override fun updateSpecial(dt: Float, gs: GameState, enemySys: EnemySystem, scale: Float) {
        val attackCooldown = if (gs.isBossRage) 3f else 5f
        gs.bossAttackTimer += dt

        if (gs.bossAttackState == 0 && gs.bossAttackTimer > attackCooldown) {
            gs.bossAttackState = 1 // Charging Jump
            gs.bossAttackTimer = 0f
        }

        if (gs.bossAttackState == 1) {
            if (gs.bossAttackTimer > 1.0f) {
                gs.bossAttackState = 2 // In Air
                gs.bossAttackTimer = 0f
                com.appsbyalok.echohunter.utils.EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_PIP, 100)
            }
        } else if (gs.bossAttackState == 2) {
            // Parabolic Jump
            val jumpDuration = 1.0f
            val t = gs.bossAttackTimer / jumpDuration
            gs.bossZ = kotlin.math.sin(t * kotlin.math.PI.toFloat()) * scale * 0.5f

            if (t >= 1.0f) {
                gs.bossAttackState = 0
                gs.bossAttackTimer = 0f
                gs.bossZ = 0f
                // SEISMIC SLAM EFFECT
                gs.shakeAmount = scale * 0.2f
                gs.shockwaveActive = true
                gs.shockwaveX = gs.bossX
                gs.shockwaveY = gs.bossY
                gs.shockwaveR = 0f
                com.appsbyalok.echohunter.utils.EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)

                // Damage player if close
                val dx = gs.px - gs.bossX
                val dy = gs.py - gs.bossY
                if (dx * dx + dy * dy < (scale * 0.6f) * (scale * 0.6f) && gs.playerIframe <= 0f) {
                    gs.playerIframe = 1.0f
                    gs.hp--
                    gs.damageFlash = 0.5f
                }
            }
        }
    }
}

// 2. STALKER (Type 2 - Debris Throw & Quick Dashes)
object StalkerBossBehavior : IBossBehavior {
    override val name = "STALKER_PROTOTYPE"
    override val color = 0xFF44FF44.toInt() // Toxic Green
    override val sizeMult = 1.0f
    override val baseHpMult = 1.5f
    override val baseSpeedMult = 1.6f
    override val attackType = "RANGED_KINETIC"
    override val spawnMessage = "STALKER: TARGET ACQUIRED. NO ESCAPE."

    override fun applyMovementPattern(vx: Float, vy: Float, dt: Float, gs: GameState, scale: Float): Pair<Float, Float> {
        val rageMult = if (gs.isBossRage) 2.0f else 1.0f
        var nvx = vx; var nvy = vy
        // Occasional Dash
        if (gs.timeSinceStart % (3.0f / rageMult) < 0.3f) {
            nvx *= 4.0f; nvy *= 4.0f
        }
        return Pair(nvx, nvy)
    }

    override fun updateSpecial(dt: Float, gs: GameState, enemySys: EnemySystem, scale: Float) {
        gs.bossAttackTimer += dt
        val throwCooldown = if (gs.isBossRage) 2f else 4f

        if (gs.bossAttackTimer > throwCooldown) {
            gs.bossAttackTimer = 0f
            // DEBRIS THROW: Spawns a custom projectile (spike type 3)
            for (i in 0 until gs.maxSpikes) {
                if (!gs.spikeActive[i]) {
                    gs.spikeActive[i] = true
                    gs.spikeX[i] = gs.bossX
                    gs.spikeY[i] = gs.bossY
                    gs.spikeLife[i] = 2.0f
                    gs.spikeType[i] = 3 // NEW TYPE: Enemy Projectile
                    val dx = gs.px - gs.bossX
                    val dy = gs.py - gs.bossY
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val speed = scale * 1.5f
                    gs.spikeVx[i] = (dx / dist) * speed
                    gs.spikeVy[i] = (dy / dist) * speed
                    com.appsbyalok.echohunter.utils.EchoAudioManager.playSound(android.media.ToneGenerator.TONE_SUP_DIAL, 50)
                    break
                }
            }
        }

        if (kotlin.random.Random.nextFloat() < 0.3f * dt) {
            gs.empFlashTimer = 0.5f
        }
    }
}

// 3. GLITCH (Type 3 - Teleporting & Glitch Pulse)
object GlitchBossBehavior : IBossBehavior {
    override val name = "GLITCH_ENTITY"
    override val color = 0xFF00FFFF.toInt() // Neon Cyan
    override val sizeMult = 1.2f
    override val baseHpMult = 1.8f
    override val baseSpeedMult = 1.1f
    override val attackType = "HACKER_PHASE"
    override val spawnMessage = "GLITCH: SYST3M... ERR0R... DELETE(YOU)."

    override fun applyMovementPattern(vx: Float, vy: Float, dt: Float, gs: GameState, scale: Float): Pair<Float, Float> {
        val rageMult = if (gs.isBossRage) 2.0f else 1.0f
        // Jittery movement
        val jx = (kotlin.random.Random.nextFloat() - 0.5f) * scale * 1.2f * rageMult
        val jy = (kotlin.random.Random.nextFloat() - 0.5f) * scale * 1.2f * rageMult
        return Pair(vx * 0.5f + jx, vy * 0.5f + jy)
    }

    override fun updateSpecial(dt: Float, gs: GameState, enemySys: EnemySystem, scale: Float) {
        gs.bossAttackTimer += dt
        if (gs.bossAttackTimer > 6f) {
            gs.bossAttackTimer = 0f
            // Teleport near player
            val angle = kotlin.random.Random.nextFloat() * 6.28f
            gs.bossX = gs.px + kotlin.math.cos(angle) * scale * 1.0f
            gs.bossY = gs.py + kotlin.math.sin(angle) * scale * 1.0f
            com.appsbyalok.echohunter.utils.EchoAudioManager.playSound(android.media.ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 100)
            gs.chromaticIntensity = 0.8f
        }
    }
}

// 4. OMEGA (Type 4 - Multi-Threat & Drone Swarm)
object OmegaBossBehavior : IBossBehavior {
    override val name = "OMEGA_COMMANDER"
    override val color = 0xFFFF3333.toInt() // Crimson Red
    override val sizeMult = 1.5f
    override val baseHpMult = 3.0f
    override val baseSpeedMult = 1.0f
    override val attackType = "HYBRID_COMMAND"
    override val spawnMessage = "OMEGA: WE ARE THE SWARM. WE ARE MANY."

    override fun applyMovementPattern(vx: Float, vy: Float, dt: Float, gs: GameState, scale: Float): Pair<Float, Float> {
        val rageMult = if (gs.isBossRage) 2.0f else 1.0f
        val svy = vy + kotlin.math.cos(gs.timeSinceStart * (5f * rageMult)) * scale * 0.6f
        val svx = vx + kotlin.math.sin(gs.timeSinceStart * (3f * rageMult)) * scale * 0.3f
        return Pair(svx, svy)
    }

    override fun updateSpecial(dt: Float, gs: GameState, enemySys: EnemySystem, scale: Float) {
        gs.bossAttackTimer += dt
        if (gs.bossAttackTimer > 8f) {
            gs.bossAttackTimer = 0f
            // Spawn Hunter Swarm
            for (i in 0 until 3) {
                enemySys.spawnSwarmIfNeeded(gs, scale * 10f, scale * 10f) // Simplified call
            }
            com.appsbyalok.echohunter.data.StoryProtocol.showIngameMessage("OMEGA: DEPLOYING SWARM UNITS", 2f)
        }
    }
}
