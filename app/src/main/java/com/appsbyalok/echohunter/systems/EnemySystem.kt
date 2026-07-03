package com.appsbyalok.echohunter.systems

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.appsbyalok.echohunter.engine.GameState
import com.appsbyalok.echohunter.utils.GameColors
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EnemySystem {
    private val p = Paint().apply { isAntiAlias = true }
    private val pText = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val entityPath = Path()

    val ai = EnemyAI()
    private var heatTimer = 0f

    val n = 25
    val enemyBrains = Array<IEnemyBehavior>(n) { PatrolBehavior } // MODULAR BRAIN ARRAY

    val ex = FloatArray(n)
    val ey = FloatArray(n)
    val evx = FloatArray(n)
    val evy = FloatArray(n)
    val vis = FloatArray(n)
    val type = IntArray(n)
    val eState = IntArray(n)
    val investigateTimer = FloatArray(n)
    
    // --- NEW: ENEMY HEALTH SYSTEM ---
    val hp = IntArray(n)
    val maxHp = IntArray(n)
    val invX = FloatArray(n)
    val invY = FloatArray(n)

    val pwn = 4
    val pwX = FloatArray(pwn)
    val pwY = FloatArray(pwn)
    val pwType = IntArray(pwn)
    val pwVis = FloatArray(pwn)
    val pwActive = BooleanArray(pwn)
    private val puIcons = arrayOf("+", "V", "S")

    fun respawnAll(gs: GameState, width: Float, height: Float) {
        for (i in 0 until pwn) pwActive[i] = false
        // Clear map on level start. SpawnerSystem handles enemy spawns.
        for (i in 0 until n) {
            ex[i] = -9999f
            ey[i] = -9999f
            vis[i] = 0f
        }
    }

    fun killEnemy(i: Int, gs: GameState, width: Float, height: Float) {
        if (ex[i] < -1000f) return
        ex[i] = -9999f
        ey[i] = -9999f

        val config = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        if (config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE)) {
            gs.defEnemiesAlive--
            if (gs.defEnemiesAlive < 0) gs.defEnemiesAlive = 0
        }
        // NEW: Campaign aur Story mode mein turant respawn band.
        // SpawnerSystem handles respawning now via queue.
    }

    // NEW: Enemy now spawns at a specified Node (nx, ny)
    fun spawnAt(i: Int, nx: Float, ny: Float, gs: GameState, width: Float, height: Float, nodeType: Int = -1) {
        val config = com.appsbyalok.echohunter.data.LevelEngine.getSaturatedValue(gs.currentLevel, 1f, 18f, 200f).let {
            com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel)
        }
        val isElimination = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.ELIMINATION)
        val hasDefense = config.features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE)

        ex[i] = nx
        ey[i] = ny

        val hunterProb = 0.2f + (gs.difficulty * 0.15f)

        // NEW: If it's a Defense level, force BLUE (Kamikaze) enemies for the entire level
        // This ensures red/yellow enemies don't leak in during prep phases or transitions.
        if (hasDefense) {
            type[i] = 2; enemyBrains[i] = KamikazeBehavior
        } else {
            when (nodeType) {
                1 -> { // Glitch Tear (Swarmers)
                    type[i] = 1; enemyBrains[i] = HunterBehavior
                }
                2 -> { // Admin Gateway (HVTs or Elites)
                    if (isElimination) {
                        type[i] = 3; enemyBrains[i] = TargetBehavior
                    } else {
                        type[i] = 1; enemyBrains[i] = HunterBehavior
                    }
                }
                else -> { // Compiler (Normal)
                    type[i] = if (kotlin.random.Random.nextFloat() < hunterProb) 1 else 0
                    enemyBrains[i] = if (type[i] == 1) HunterBehavior else PatrolBehavior
                }
            }
        }

        eState[i] = 0
        evx[i] = 0f
        evy[i] = 0f
        vis[i] = 0f
        invX[i] = -9999f
        invY[i] = -9999f
        investigateTimer[i] = 0f

        // --- NEW: DYNAMIC HP SCALING (Saturated Growth Curve via LevelEngine) ---
        // Level 1-10: 1 HP | Level 50: ~4 HP | Level 100: ~7 HP | Level 500: ~13 HP | Max Cap: 19 HP
        val baseHp = com.appsbyalok.echohunter.data.LevelEngine.getSaturatedValue(gs.currentLevel, 1f, 18f, 200f).toInt()

        maxHp[i] = when (nodeType) {
            1 -> 1 // Swarmers bahut kamzor hote hain, 1 hit kill
            2 -> baseHp * 2 // HVT ya Guards ki HP zyada hogi
            else -> baseHp
        }
        hp[i] = maxHp[i]
    }

    fun updateEnemies(dt: Float, gs: GameState, width: Float, height: Float, scale: Float) {
        gs.isEnemyNear = false
        gs.isEnemyVeryNear = false

        if (gs.gridMap != null) {
            heatTimer -= dt
            if (heatTimer <= 0f) {
                ai.updatePlayerHeatMap(gs)
                if (gs.coreRadius > 0f) ai.updateCoreHeatMap(gs)
                heatTimer = 0.5f
            }
        }

        val enemyRadius = scale * 0.02f
        val stealthCamoMult = 1.0f - (com.appsbyalok.echohunter.data.UpgradeSystem.getLevel(com.appsbyalok.echohunter.data.UpgradeType.STEALTH_CAMO) * 0.1f)

        for (i in 0 until n) {
            if (ex[i] < -1000f) continue
            val nextEx = ex[i] + evx[i] * dt
            if (!isCollidingWithWall(nextEx, ey[i], enemyRadius, gs)) ex[i] = nextEx
            else evx[i] = -evx[i]

            val nextEy = ey[i] + evy[i] * dt
            if (!isCollidingWithWall(ex[i], nextEy, enemyRadius, gs)) ey[i] = nextEy
            else evy[i] = -evy[i]

            // DELEGATE TO MODULAR AI BRAIN
            enemyBrains[i].updateBehavior(i, dt, gs, this, ai, width, height, scale)

            val d2 = (gs.px - ex[i]) * (gs.px - ex[i]) + (gs.py - ey[i]) * (gs.py - ey[i])
            val hitByPulse = (gs.pulse && d2 in gs.innerRSq..gs.outerRSq)

            if (type[i] == 1) {
                val hitDistSq = (scale * 0.045f) * (scale * 0.045f)
                val detectionDistSq = hitDistSq * 15f * stealthCamoMult * stealthCamoMult
                val immediateDistSq = hitDistSq * 4f * stealthCamoMult * stealthCamoMult

                if (d2 < detectionDistSq) gs.isEnemyNear = true
                if (d2 < immediateDistSq) gs.isEnemyVeryNear = true
            }

            // --- NEW: PERSISTENT VISIBILITY LOGIC ---
            if (d2 < gs.passiveAuraRadiusSq) {
                vis[i] = 1f
            } else {
                if (hitByPulse) vis[i] = 1f
                
                // Linear decay instead of exponential for predictable duration
                val duration = com.appsbyalok.echohunter.data.UpgradeSystem.getSonarDurationBonus()
                vis[i] = max(0f, vis[i] - dt / duration)
            }

            val maxAllowedDistSq = if (gs.bossActive || gs.coreRadius > 0f) (width * 1.5f) * (width * 1.5f) else (width * 4.0f) * (width * 4.0f)
            if (d2 > maxAllowedDistSq) {
                // Using killEnemy ensures defEnemiesAlive is decremented if in Defense mode.
                killEnemy(i, gs, width, height)
                vis[i] = 0f
            }
        }
        gs.globalSonarAlert = false
        gs.localAttackAlert = false
    }

    private fun isCollidingWithWall(cx: Float, cy: Float, radius: Float, gs: GameState): Boolean {
        val grid = gs.gridMap ?: return false
        val ts = gs.tileSize
        val hitbox = radius * 0.8f

        val left = ((cx - hitbox) / ts).toInt(); val right = ((cx + hitbox) / ts).toInt()
        val top = ((cy - hitbox) / ts).toInt(); val bottom = ((cy + hitbox) / ts).toInt()

        for (x in left..right) {
            for (y in top..bottom) {
                if (x in grid.indices && y in grid[0].indices) {
                    if (grid[x][y] == 1) return true
                } else return true
            }
        }
        return false
    }

    fun spawnSwarmIfNeeded(gs: GameState, width: Float, height: Float) {
        // Find an empty slot and spawn a Hunter near the boss
        for (i in 0 until n) {
            if (ex[i] < -1000f) {
                val angle = kotlin.random.Random.nextFloat() * 6.28f
                val dist = width * 0.1f // Spawn close to boss
                spawnAt(i, gs.bossX + kotlin.math.cos(angle) * dist, gs.bossY + kotlin.math.sin(angle) * dist, gs, width, height, 1)
                break
            }
        }
    }

    fun updateBoss(dt: Float, gs: GameState, scale: Float) {
        if (!gs.bossActive) return

        val behavior = when (gs.bossType) {
            1 -> GuardianBossBehavior
            2 -> StalkerBossBehavior
            3 -> GlitchBossBehavior
            4 -> OmegaBossBehavior
            5 -> UltimaBossBehavior
            else -> GuardianBossBehavior
        }

        // Initialize Boss Data on first frame or if needed
        if (gs.bossHp <= 0 && gs.bossMaxHp == 0) { // Safety check or first spawn logic
             // Assuming hp is already set by GameView or LevelEngine, but we can set metadata
        }

        val bdx = gs.px - gs.bossX; val bdy = gs.py - gs.bossY
        val bDistSq = bdx * bdx + bdy * bdy
        val bDist = sqrt(bDistSq)

        // Speed calculation using behavior multiplier
        val baseSpeed = scale * 0.45f * behavior.baseSpeedMult
        val difficultyMult = if (gs.difficulty == 0) 0.7f else 1.0f
        val rageMult = if (gs.isBossRage) 2.0f else 1.0f
        val bSpeed = baseSpeed * difficultyMult * rageMult

        if (bDist > 0f) {
            val hasDefense = com.appsbyalok.echohunter.data.LevelEngine.getLevelConfig(gs.currentLevel).features.contains(com.appsbyalok.echohunter.data.LevelFeature.DEFENSE)
            
            // Determine primary target distance for "Closing In" logic
            val targetDistSq = if (hasDefense && gs.coreRadius > 0f) {
                val cdx = gs.coreX - gs.bossX; val cdy = gs.coreY - gs.bossY
                cdx * cdx + cdy * cdy
            } else bDistSq

            val engagementRange = scale * 1.5f
            val isClosingIn = targetDistSq > engagementRange * engagementRange

            val (vx, vy) = if (gs.gridMap != null) {
                if (hasDefense && gs.coreRadius > 0f) {
                    // Hybrid Targeting: Switch to Player only if they are aggressively close
                    if (bDistSq < (scale * 1.0f) * (scale * 1.0f)) {
                        ai.steerByPlayerHeatMap(gs.bossX, gs.bossY, gs.bossVx, gs.bossVy, bSpeed * 5f, gs, dt)
                    } else {
                        ai.steerByCoreHeatMap(gs.bossX, gs.bossY, gs.bossVx, gs.bossVy, bSpeed * 5f, gs, dt)
                    }
                } else {
                    ai.steerByPlayerHeatMap(gs.bossX, gs.bossY, gs.bossVx, gs.bossVy, bSpeed * 5f, gs, dt)
                }
            } else {
                val tx = if (gs.isDecoyActive) gs.decoyX else if (hasDefense && gs.coreRadius > 0f && bDistSq > (scale * 1.5f) * (scale * 1.5f)) gs.coreX else gs.px
                val ty = if (gs.isDecoyActive) gs.decoyY else if (hasDefense && gs.coreRadius > 0f && bDistSq > (scale * 1.5f) * (scale * 1.5f)) gs.coreY else gs.py
                val tdx = tx - gs.bossX; val tdy = ty - gs.bossY
                val tDist = sqrt(tdx * tdx + tdy * tdy)
                
                if (tDist > 0f) {
                    val lerpFactor = (dt * 5f).coerceIn(0f, 1f)
                    val targetVx = (tdx / tDist) * bSpeed
                    val targetVy = (tdy / tDist) * bSpeed
                    Pair(gs.bossVx + (targetVx - gs.bossVx) * lerpFactor, gs.bossVy + (targetVy - gs.bossVy) * lerpFactor)
                } else Pair(0f, 0f)
            }

            // Update Boss Velocity State
            gs.bossVx = vx
            gs.bossVy = vy

            // --- NEW: FAST CLOSING LOGIC ---
            val (finalVx, finalVy) = if (isClosingIn) {
                Pair(vx * 1.5f, vy * 1.5f)
            } else {
                behavior.applyMovementPattern(vx, vy, dt, gs, scale)
            }

            val bossRadius = scale * 0.08f
            val nextBx = gs.bossX + finalVx * dt
            if (!isCollidingWithWall(nextBx, gs.bossY, bossRadius * 0.8f, gs)) gs.bossX = nextBx
            val nextBy = gs.bossY + finalVy * dt
            if (!isCollidingWithWall(gs.bossX, nextBy, bossRadius * 0.8f, gs)) gs.bossY = nextBy
        }

        behavior.updateSpecial(dt, gs, this, scale)

        // --- NEW: BOSS PERSISTENT VISIBILITY ---
        if (gs.isDarknessLevel) {
            if ((gs.pulse && bDistSq in gs.innerRSq..gs.outerRSq) || bDistSq < gs.passiveAuraRadiusSq) {
                gs.bossVis = 1.0f
            } else {
                val duration = com.appsbyalok.echohunter.data.UpgradeSystem.getSonarDurationBonus()
                gs.bossVis = max(0f, gs.bossVis - dt / duration)
            }
        } else {
            gs.bossVis = 1.0f
        }
    }

    fun updatePowerups(dt: Float, gs: GameState, width: Float, height: Float) {
        val puDropRate = if (gs.difficulty == 0) 0.004f else 0.002f
        if (Random.nextFloat() < puDropRate * dt * 60f && gs.score > 15 && !gs.bossActive) {
            for (i in 0 until pwn) {
                if (!pwActive[i]) {
                    pwX[i] = gs.cameraX + width * 0.2f + Random.nextFloat() * width * 0.8f
                    pwY[i] = gs.cameraY + Random.nextFloat() * height
                    pwType[i] = Random.nextInt(3)
                    pwActive[i] = true
                    pwVis[i] = 0f
                    break
                }
            }
        }
    }

    fun drawEntities(c: Canvas, gs: GameState, width: Float, scale: Float) {
        val entityRadius = scale * 0.03f

        for (i in 0 until pwn) {
            if (pwActive[i]) {
                val dx = pwX[i] - gs.px
                val dy = pwY[i] - gs.py
                val d2 = dx * dx + dy * dy

                if ((gs.pulse && d2 in gs.innerRSq..gs.outerRSq) || d2 < gs.passiveAuraRadiusSq) pwVis[i] = 1f
                pwVis[i] *= gs.fadeMultiplier

                val screenPwX = pwX[i] - gs.cameraX
                val screenPwY = pwY[i] - gs.cameraY

                if (screenPwX < -scale || screenPwX > width + scale) pwActive[i] = false

                val effectivePwVis = max(0.15f, pwVis[i])

                if (effectivePwVis > 0.02f) {
                    p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.005f
                    p.color = ((effectivePwVis * 255).toInt() shl 24) or (when (pwType[i]) {
                        0 -> GameColors.HP; 1 -> GameColors.CLARITY; else -> GameColors.SHIELD
                    } and 0xFFFFFF)

                    c.drawCircle(screenPwX, screenPwY, entityRadius * 0.8f, p)

                    pText.color = GameColors.BG
                    pText.textSize = scale * 0.04f
                    c.drawText(puIcons[pwType[i]], screenPwX, screenPwY + scale * 0.012f, pText)
                }
            }
        }

        for (i in 0 until n) {
            val screenEx = ex[i] - gs.cameraX
            val screenEy = ey[i] - gs.cameraY
            
            // NEW: Strict Visibility - Darkness mein bina sonar/aura ke 0% 
            val effectiveVis = if (gs.isDarknessLevel) vis[i] else max(0.1f, vis[i])

            if (effectiveVis > 0.02f) {
                val a = (effectiveVis * 255).toInt()
                val entitySize = entityRadius * (if (type[i] == 2) 1.2f else 0.8f)

                p.style = Paint.Style.FILL

                when (type[i]) {
                    1 -> {
                        p.color = (a shl 24) or (GameColors.RED and 0xFFFFFF)
                        c.drawRect(screenEx - entitySize, screenEy - entitySize, screenEx + entitySize, screenEy + entitySize, p)
                        p.color = (a shl 24) or (GameColors.BG and 0xFFFFFF)
                        c.drawRect(screenEx - entitySize / 2f, screenEy - entitySize / 2f, screenEx + entitySize / 2f, screenEy + entitySize / 2f, p)

                        if (eState[i] == 2 || eState[i] == 1) {
                            pText.color = (a shl 24) or 0xFFFFFF
                            pText.textSize = scale * 0.03f
                            c.drawText("!", screenEx, screenEy - entitySize - scale * 0.01f, pText)
                        }
                    }
                    2 -> {
                        p.color = (a shl 24) or (GameColors.COOLANT and 0xFFFFFF)
                        entityPath.reset()
                        entityPath.moveTo(screenEx, screenEy - entitySize)
                        entityPath.lineTo(screenEx + entitySize, screenEy)
                        entityPath.lineTo(screenEx, screenEy + entitySize)
                        entityPath.lineTo(screenEx - entitySize, screenEy)
                        entityPath.close()
                        c.drawPath(entityPath, p)
                        p.color = (a shl 24) or (GameColors.CLARITY and 0xFFFFFF)
                        c.drawCircle(screenEx, screenEy, entityRadius * 0.3f, p)
                    }
                    3 -> {
                        // --- NEW: ELIMINATION TARGET (HVT) ---
                        // Large Bright Red Circle
                        p.color = (a shl 24) or (0xFFFF2A4D.toInt() and 0xFFFFFF)
                        c.drawCircle(screenEx, screenEy, entitySize, p)

                        // Inner black circle (Holo-look)
                        p.color = (a shl 24) or (GameColors.BG and 0xFFFFFF)
                        c.drawCircle(screenEx, screenEy, entitySize * 0.5f, p)

                        // "TARGET" label above
                        pText.color = (a shl 24) or (0xFFFF2A4D.toInt() and 0xFFFFFF)
                        pText.textSize = scale * 0.025f
                        c.drawText("TARGET", screenEx, screenEy - entitySize * 1.5f, pText)
                    }
                    else -> {
                        p.color = (a shl 24) or (GameColors.YELLOW and 0xFFFFFF)
                        entityPath.reset()
                        entityPath.moveTo(screenEx, screenEy - entitySize)
                        entityPath.lineTo(screenEx + entitySize, screenEy)
                        entityPath.lineTo(screenEx, screenEy + entitySize)
                        entityPath.lineTo(screenEx - entitySize, screenEy)
                        entityPath.close()
                        c.drawPath(entityPath, p)

                        // --- SUSPICIOUS QUESTION MARK ---
                        if (eState[i] == 2) {
                            pText.color = (a shl 24) or 0xFFFFFF
                            pText.textSize = scale * 0.035f
                            c.drawText("?", screenEx, screenEy - entitySize - scale * 0.01f, pText)
                        }
                    }
                }

                // --- NEW: DRAW HEALTH BAR (When HP > 1 and visible) ---
                if (maxHp[i] > 1 && effectiveVis > 0.5f) {
                    val hpRatio = kotlin.math.max(0f, hp[i].toFloat() / maxHp[i].toFloat())
                    val barW = scale * 0.06f
                    val barH = scale * 0.008f
                    val bx = screenEx - barW / 2f
                    val by = screenEy - entitySize - scale * 0.02f
                    
                    // Background
                    p.color = 0xAA000000.toInt()
                    c.drawRect(bx, by, bx + barW, by + barH, p)
                    // Foreground HP
                    p.color = GameColors.HP
                    c.drawRect(bx, by, bx + barW * hpRatio, by + barH, p)
                }
            }
        }

        if (gs.bossActive) {
            val behavior = when (gs.bossType) {
                1 -> GuardianBossBehavior
                2 -> StalkerBossBehavior
                3 -> GlitchBossBehavior
                4 -> OmegaBossBehavior
                5 -> UltimaBossBehavior
                else -> GuardianBossBehavior
            }

            val bossRadius = scale * 0.08f * behavior.sizeMult
            val screenBx = gs.bossX - gs.cameraX
            val screenBy = gs.bossY - gs.cameraY - gs.bossZ // Apply Jump Offset
            
            // NEW: Boss is always slightly visible (15%) in darkness as a shadow/ghost
            val effectiveBossVis = if (gs.isDarknessLevel) max(0.15f, gs.bossVis) else gs.bossVis
            val bossAlpha = (effectiveBossVis * 255).toInt()

            p.style = Paint.Style.FILL
            val pulseOffset = sin(gs.timeSinceStart * 15f) * (scale * 0.015f)

            // DRAW SHADOW WHEN JUMPING
            if (gs.bossZ > 0) {
                p.color = (bossAlpha / 2 shl 24) or 0x000000
                c.drawOval(
                    gs.bossX - gs.cameraX - bossRadius * 0.6f,
                    gs.bossY - gs.cameraY - bossRadius * 0.2f,
                    gs.bossX - gs.cameraX + bossRadius * 0.6f,
                    gs.bossY - gs.cameraY + bossRadius * 0.2f,
                    p
                )
            }

            p.color = (bossAlpha shl 24) or (behavior.color and 0xFFFFFF)
            c.drawCircle(screenBx, screenBy, bossRadius + pulseOffset, p)

            p.color = if (gs.bossIframe > 0f) (bossAlpha shl 24) or 0xFFFFFF else (bossAlpha shl 24) or (GameColors.RED and 0xFFFFFF)
            
            // Draw core shape based on attack type
            when (behavior.attackType) {
                "MELEE_SEISMIC" -> c.drawRect(screenBx - bossRadius*0.3f, screenBy - bossRadius*0.3f, screenBx + bossRadius*0.3f, screenBy + bossRadius*0.3f, p)
                "RANGED_KINETIC" -> {
                    entityPath.reset()
                    entityPath.moveTo(screenBx, screenBy - bossRadius*0.4f)
                    entityPath.lineTo(screenBx + bossRadius*0.4f, screenBy + bossRadius*0.4f)
                    entityPath.lineTo(screenBx - bossRadius*0.4f, screenBy + bossRadius*0.4f)
                    entityPath.close()
                    c.drawPath(entityPath, p)
                }
                "HACKER_PHASE" -> {
                    p.style = Paint.Style.STROKE; p.strokeWidth = scale * 0.01f
                    c.drawCircle(screenBx, screenBy, bossRadius * 0.4f, p)
                    p.style = Paint.Style.FILL
                }
                else -> c.drawCircle(screenBx, screenBy, bossRadius * 0.4f, p)
            }

            // BOSS NAME & ATTACK TYPE
            if (gs.bossVis > 0.8f) {
                pText.color = (bossAlpha shl 24) or (behavior.color and 0xFFFFFF)
                pText.textSize = scale * 0.03f
                c.drawText(behavior.name, screenBx, screenBy - bossRadius - scale * 0.06f, pText)
                pText.textSize = scale * 0.02f
                pText.color = (bossAlpha shl 24) or 0xBBBBBB
                c.drawText(behavior.attackType, screenBx, screenBy - bossRadius - scale * 0.035f, pText)
            }

            // HP BAR follows boss
            val hpY = gs.bossY - gs.cameraY - bossRadius - scale * 0.02f
            p.color = (bossAlpha shl 24) or (GameColors.RED and 0xFFFFFF)
            c.drawRect(screenBx - bossRadius, hpY, screenBx + bossRadius, hpY + scale * 0.01f, p)

            p.color = (bossAlpha shl 24) or (GameColors.HP and 0xFFFFFF)
            val hpWidth = (bossRadius * 2f) * (gs.bossHp.toFloat() / gs.bossMaxHp)
            c.drawRect(screenBx - bossRadius, hpY, screenBx - bossRadius + hpWidth, hpY + scale * 0.01f, p)
        }
    }
}